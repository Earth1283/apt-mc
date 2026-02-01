package io.github.Earth1283.aptMc.commands.sub;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.commands.SubCommand;
import io.github.Earth1283.aptMc.managers.PackageManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class CompileCommand extends SubCommand {

    private enum BuildSystem {
        GRADLE,
        MAVEN,
        UNKNOWN
    }

    private final Map<Player, CompletableFuture<String>> pendingInputs = new ConcurrentHashMap<>();

    public CompileCommand(AptMc plugin, PackageManager packageManager) {
        super(plugin, packageManager);
    }

    @Override
    public void execute(CommandSender sender, List<String> args) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("errors.compile-no-url"));
            return;
        }

        // Parse arguments
        String gitUrl = args.get(0);
        String branch = "main"; // default branch

        // Parse --branch flag
        for (int i = 1; i < args.size(); i++) {
            if (args.get(i).equalsIgnoreCase("--branch") && i + 1 < args.size()) {
                branch = args.get(i + 1);
                i++; // skip next arg
            }
        }

        // Validate URL
        if (!isValidGitUrl(gitUrl)) {
            sender.sendMessage(plugin.getMessage("errors.compile-invalid-url", Placeholder.unparsed("arg", gitUrl)));
            return;
        }

        // Determine if interactive mode (player) or automatic mode (console)
        boolean interactive = sender instanceof Player;

        // Start compilation process
        compileFromGit(sender, gitUrl, branch, interactive);
    }

    private boolean isValidGitUrl(String url) {
        return url.matches("^(https?|git)://.*\\.git$") ||
                url.matches("^(https?|git)://github\\.com/.*") ||
                url.matches("^(https?|git)://gitlab\\.com/.*") ||
                url.matches("^git@.*:.*\\.git$") ||
                url.matches("^git@github\\.com:.*") ||
                url.matches("^git@gitlab\\.com:.*");
    }

    private void compileFromGit(CommandSender sender, String gitUrl, String branch, boolean interactive) {
        // Get cache directory
        String cacheDir = plugin.getConfig().getString("compile.cache-directory", "~/plugins/apt-mc/build-caches");

        // Replace ~ with server root directory
        if (cacheDir.startsWith("~")) {
            // Use Bukkit's getWorldContainer() which returns the server root
            File serverRoot = plugin.getServer().getWorldContainer().getAbsoluteFile();
            cacheDir = cacheDir.replace("~", serverRoot.getAbsolutePath());
        }

        File cacheRoot = new File(cacheDir);
        if (!cacheRoot.exists()) {
            cacheRoot.mkdirs();
        }

        // Create unique directory for this repo
        String repoName = extractRepoName(gitUrl);
        File repoDir = new File(cacheRoot, repoName + "_" + System.currentTimeMillis());

        try {
            // Clone repository
            sendStatus(sender, plugin.getMessage("status.compile-cloning", Placeholder.unparsed("arg", gitUrl)));
            cloneRepository(gitUrl, branch, repoDir);
            sendStatus(sender, plugin.getMessage("status.compile-cloned"));

            // Detect build system
            sendStatus(sender, plugin.getMessage("status.compile-detecting"));
            BuildSystem buildSystem = detectBuildSystem(repoDir);

            if (buildSystem == BuildSystem.UNKNOWN) {
                sender.sendMessage(plugin.getMessage("errors.compile-no-build-system"));
                cleanup(repoDir);
                return;
            }

            if (buildSystem == BuildSystem.GRADLE) {
                sendStatus(sender, plugin.getMessage("status.compile-detected-gradle"));
            } else {
                sendStatus(sender, plugin.getMessage("status.compile-detected-maven"));
            }

            // Build task selection (interactive or automatic)
            String buildTask;
            if (interactive) {
                buildTask = promptForBuildTask((Player) sender, buildSystem);
                if (buildTask == null) {
                    // User cancelled
                    sender.sendMessage(plugin.getMessage("status.compile-cancelled"));
                    cleanup(repoDir);
                    return;
                }
            } else {
                // Console mode: use first default task
                buildTask = getDefaultBuildTask(buildSystem);
                sender.sendMessage(plugin.getMessage("status.compile-using-default-task",
                        Placeholder.unparsed("task", buildTask)));
            }

            // Execute build
            sendStatus(sender, plugin.getMessage("status.compile-building", Placeholder.unparsed("arg", buildTask)));
            boolean buildSuccess = executeBuild(sender, repoDir, buildSystem, buildTask);

            if (!buildSuccess) {
                cleanup(repoDir);
                return;
            }

            sendStatus(sender, plugin.getMessage("status.compile-build-success"));

            // Find compiled JARs
            sendStatus(sender, plugin.getMessage("status.compile-finding-jars"));
            List<File> jars = findCompiledJars(repoDir, buildSystem);

            if (jars.isEmpty()) {
                sender.sendMessage(plugin.getMessage("errors.compile-no-jars"));
                cleanup(repoDir);
                return;
            }

            // Select JAR (interactive or automatic)
            File selectedJar;
            if (jars.size() == 1) {
                selectedJar = jars.get(0);
            } else if (interactive) {
                selectedJar = selectJar((Player) sender, jars);
                if (selectedJar == null) {
                    sender.sendMessage(plugin.getMessage("status.compile-cancelled"));
                    cleanup(repoDir);
                    return;
                }
            } else {
                // Console mode: select largest JAR automatically
                selectedJar = jars.get(0); // Already sorted by size
                sender.sendMessage(plugin.getMessage("status.compile-auto-selected-jar",
                        Placeholder.unparsed("name", selectedJar.getName()),
                        Placeholder.unparsed("size", formatFileSize(selectedJar.length()))));
            }

            sendStatus(sender,
                    plugin.getMessage("status.compile-jar-found", Placeholder.unparsed("arg", selectedJar.getName())));

            // Copy to plugins directory
            sendStatus(sender,
                    plugin.getMessage("status.compile-copying", Placeholder.unparsed("arg", selectedJar.getName())));
            copyToPlugins(selectedJar);

            sender.sendMessage(plugin.getMessage("status.compile-complete"));

            // Cleanup based on config
            boolean keepRepos = plugin.getConfig().getBoolean("compile.keep-repos-by-default", true);
            if (!keepRepos) {
                cleanup(repoDir);
            }

        } catch (Exception e) {
            sender.sendMessage(
                    plugin.getMessage("errors.compile-clone-failed", Placeholder.unparsed("error", e.getMessage())));
            cleanup(repoDir);
            e.printStackTrace();
        }
    }

    private String extractRepoName(String gitUrl) {
        // Extract repository name from URL
        String name = gitUrl;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash != -1) {
            name = name.substring(lastSlash + 1);
        }
        int lastColon = name.lastIndexOf(':');
        if (lastColon != -1) {
            name = name.substring(lastColon + 1);
        }
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private void cloneRepository(String url, String branch, File destDir) throws GitAPIException {
        int timeout = plugin.getConfig().getInt("compile.clone-timeout", 300);
        int depth = plugin.getConfig().getInt("compile.clone-depth", 1);

        Git.cloneRepository()
                .setURI(url)
                .setBranch(branch)
                .setDirectory(destDir)
                .setDepth(depth > 0 ? depth : null) // Shallow clone if depth > 0
                .setTimeout(timeout)
                .setCloneAllBranches(false) // Only clone specified branch
                .call()
                .close();
    }

    private BuildSystem detectBuildSystem(File repoDir) {
        // Check for Gradle (prefer gradlew wrapper)
        if (new File(repoDir, "gradlew").exists() || new File(repoDir, "build.gradle").exists()
                || new File(repoDir, "build.gradle.kts").exists()) {
            return BuildSystem.GRADLE;
        }
        // Check for Maven
        if (new File(repoDir, "pom.xml").exists()) {
            return BuildSystem.MAVEN;
        }
        return BuildSystem.UNKNOWN;
    }

    private String promptForBuildTask(Player player, BuildSystem buildSystem) {
        try {
            List<String> options = new ArrayList<>();

            if (buildSystem == BuildSystem.GRADLE) {
                options.addAll(plugin.getConfig().getStringList("compile.default-gradle-tasks"));
            } else {
                String defaultGoal = plugin.getConfig().getString("compile.default-maven-goal", "package");
                options.add(defaultGoal);
                options.add("clean " + defaultGoal);
                options.add("install");
            }

            // Display menu
            player.sendMessage(plugin.getMessage("status.compile-prompt-task"));
            for (int i = 0; i < options.size(); i++) {
                player.sendMessage(plugin.getMessage("status.compile-task-option",
                        Placeholder.unparsed("index", String.valueOf(i + 1)),
                        Placeholder.unparsed("task", options.get(i))));
            }
            player.sendMessage(plugin.getMessage("status.compile-task-custom",
                    Placeholder.unparsed("custom_index", String.valueOf(options.size() + 1))));
            player.sendMessage(plugin.getMessage("status.compile-task-cancel",
                    Placeholder.unparsed("cancel_index", String.valueOf(options.size() + 2))));

            // Wait for input
            String response = waitForPlayerInput(player, 30);
            if (response == null) {
                return null; // timeout or cancelled
            }

            try {
                int selection = Integer.parseInt(response.trim());
                if (selection >= 1 && selection <= options.size()) {
                    return options.get(selection - 1);
                } else if (selection == options.size() + 1) {
                    // Custom task
                    player.sendMessage(plugin.getMessage("status.compile-prompt-custom-task"));
                    String customTask = waitForPlayerInput(player, 30);
                    return customTask != null && !customTask.trim().isEmpty() ? customTask.trim() : null;
                } else if (selection == options.size() + 2) {
                    // Cancel
                    return null;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getMessage("errors.compile-invalid-selection"));
                return null;
            }

            player.sendMessage(plugin.getMessage("errors.compile-invalid-selection"));
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getDefaultBuildTask(BuildSystem buildSystem) {
        if (buildSystem == BuildSystem.GRADLE) {
            List<String> tasks = plugin.getConfig().getStringList("compile.default-gradle-tasks");
            return tasks.isEmpty() ? "build" : tasks.get(0);
        } else {
            return plugin.getConfig().getString("compile.default-maven-goal", "package");
        }
    }

    private boolean executeBuild(CommandSender sender, File repoDir, BuildSystem buildSystem, String task) {
        int timeout = plugin.getConfig().getInt("compile.build-timeout", 600);

        try {
            ProcessBuilder pb;
            if (buildSystem == BuildSystem.GRADLE) {
                File gradlew = new File(repoDir, "gradlew");
                if (!gradlew.exists()) {
                    gradlew = new File(repoDir, "gradlew.bat");
                }

                if (gradlew.exists()) {
                    // Make gradlew executable on Unix systems
                    gradlew.setExecutable(true);
                    pb = new ProcessBuilder(gradlew.getAbsolutePath(), task, "--console=plain");
                } else {
                    // Fallback to system gradle
                    pb = new ProcessBuilder("gradle", task, "--console=plain");
                }
            } else {
                pb = new ProcessBuilder("mvn", task, "-B"); // -B = batch mode (less verbose)
            }

            pb.directory(repoDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Stream output in real-time
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder fullOutput = new StringBuilder();

            CompletableFuture<Void> outputReader = CompletableFuture.runAsync(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        fullOutput.append(line).append("\n");

                        // Log interesting lines to the sender
                        if (isInterestingBuildLine(line)) {
                            sender.sendMessage(plugin.getMessage("status.compile-build-log",
                                    Placeholder.unparsed("line", line.trim())));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            // Wait for build with timeout
            boolean completed;
            if (timeout > 0) {
                completed = process.waitFor(timeout, TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    sender.sendMessage(plugin.getMessage("errors.compile-timeout",
                            Placeholder.unparsed("arg", String.valueOf(timeout))));
                    return false;
                }
            } else {
                process.waitFor();
                completed = true;
            }

            // Wait for output reader to finish
            try {
                outputReader.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Ignore timeout on output reading
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                sender.sendMessage(plugin.getMessage("errors.compile-build-failed",
                        Placeholder.unparsed("code", String.valueOf(exitCode)),
                        Placeholder.unparsed("error", getLastLines(fullOutput.toString(), 5))));
                return false;
            }

            return true;

        } catch (Exception e) {
            sender.sendMessage(plugin.getMessage("errors.compile-build-failed",
                    Placeholder.unparsed("code", "N/A"),
                    Placeholder.unparsed("error", e.getMessage())));
            e.printStackTrace();
            return false;
        }
    }

    private boolean isInterestingBuildLine(String line) {
        String lower = line.toLowerCase();

        // Show task execution
        if (line.startsWith("> Task :") || line.startsWith("Task :")) {
            return true;
        }

        // Show compilation progress
        if (lower.contains("compiling") || lower.contains("processing")) {
            return true;
        }

        // Show test results
        if (lower.contains("test") && (lower.contains("passed") || lower.contains("failed"))) {
            return true;
        }

        // Show BUILD status
        if (lower.contains("build success") || lower.contains("build fail")) {
            return true;
        }

        // Show downloading dependencies
        if (lower.contains("download")) {
            return true;
        }

        // Show warnings and errors
        if (lower.contains("warning") || lower.contains("error")) {
            return true;
        }

        return false;
    }

    private String getLastLines(String text, int lineCount) {
        String[] lines = text.split("\n");
        int start = Math.max(0, lines.length - lineCount);
        StringBuilder result = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (i > start)
                result.append("\n");
            result.append(lines[i]);
        }
        return result.toString();
    }

    private List<File> findCompiledJars(File repoDir, BuildSystem buildSystem) {
        List<File> jars = new ArrayList<>();
        File searchDir;

        if (buildSystem == BuildSystem.GRADLE) {
            searchDir = new File(repoDir, "build/libs");
        } else {
            searchDir = new File(repoDir, "target");
        }

        if (searchDir.exists() && searchDir.isDirectory()) {
            File[] files = searchDir.listFiles((dir, name) -> name.endsWith(".jar") && !name.endsWith("-sources.jar")
                    && !name.endsWith("-javadoc.jar"));
            if (files != null) {
                jars.addAll(Arrays.asList(files));
            }
        }

        // Sort by size (largest first) as main JAR is usually largest
        jars.sort((a, b) -> Long.compare(b.length(), a.length()));
        return jars;
    }

    private File selectJar(Player player, List<File> jars) {
        try {
            player.sendMessage(plugin.getMessage("status.compile-select-jar"));
            for (int i = 0; i < jars.size(); i++) {
                File jar = jars.get(i);
                String size = formatFileSize(jar.length());
                player.sendMessage(plugin.getMessage("status.compile-jar-option",
                        Placeholder.unparsed("index", String.valueOf(i + 1)),
                        Placeholder.unparsed("name", jar.getName()),
                        Placeholder.unparsed("size", size)));
            }

            String response = waitForPlayerInput(player, 30);
            if (response == null) {
                return null;
            }

            try {
                int selection = Integer.parseInt(response.trim());
                if (selection >= 1 && selection <= jars.size()) {
                    return jars.get(selection - 1);
                }
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getMessage("errors.compile-invalid-selection"));
                return null;
            }

            player.sendMessage(plugin.getMessage("errors.compile-invalid-selection"));
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void copyToPlugins(File jarFile) throws IOException {
        File pluginsDir = packageManager.getPluginsDir();
        File dest = new File(pluginsDir, jarFile.getName());
        Files.copy(jarFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void cleanup(File directory) {
        if (directory != null && directory.exists()) {
            deleteRecursively(directory);
        }
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private String waitForPlayerInput(Player player, int timeoutSeconds) {
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingInputs.put(player, future);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingInputs.remove(player);
            return null;
        } catch (Exception e) {
            pendingInputs.remove(player);
            e.printStackTrace();
            return null;
        }
    }

    public void handlePlayerChat(Player player, String message) {
        CompletableFuture<String> future = pendingInputs.remove(player);
        if (future != null) {
            future.complete(message);
        }
    }

    public boolean hasPendingInput(Player player) {
        return pendingInputs.containsKey(player);
    }
}
