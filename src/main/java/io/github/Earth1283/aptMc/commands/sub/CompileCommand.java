package io.github.Earth1283.aptMc.commands.sub;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.commands.SubCommand;
import io.github.Earth1283.aptMc.commands.sub.compile.BuildRunner;
import io.github.Earth1283.aptMc.commands.sub.compile.BuildRunner.BuildSystem;
import io.github.Earth1283.aptMc.commands.sub.compile.GitOperations;
import io.github.Earth1283.aptMc.managers.PackageManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

public class CompileCommand extends SubCommand {

    private final Map<Player, CompletableFuture<String>> pendingInputs = new ConcurrentHashMap<>();

    public CompileCommand(AptMc plugin, PackageManager packageManager) {
        super(plugin, packageManager);
    }

    @Override
    public void execute(CommandSender sender, List<String> args, boolean dryRun) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("errors.compile-no-url"));
            return;
        }

        String gitUrl = args.get(0);
        String branch = "main";
        for (int i = 1; i < args.size(); i++) {
            if (args.get(i).equalsIgnoreCase("--branch") && i + 1 < args.size()) {
                branch = args.get(++i);
            }
        }

        if (!GitOperations.isValidGitUrl(gitUrl)) {
            sender.sendMessage(plugin.getMessage("errors.compile-invalid-url", Placeholder.unparsed("arg", gitUrl)));
            return;
        }

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run",
                    Placeholder.unparsed("arg", "Compile from " + gitUrl + " (branch: " + branch + ")")));
            return;
        }

        compileFromGit(sender, gitUrl, branch, sender instanceof Player);
    }

    private void compileFromGit(CommandSender sender, String gitUrl, String branch, boolean interactive) {
        String cacheDir = plugin.getConfig().getString("compile.cache-directory", "~/plugins/apt-mc/build-caches");
        if (cacheDir.startsWith("~")) {
            File serverRoot = plugin.getServer().getWorldContainer().getAbsoluteFile();
            cacheDir = cacheDir.replace("~", serverRoot.getAbsolutePath());
        }

        File cacheRoot = new File(cacheDir);
        if (!cacheRoot.exists()) cacheRoot.mkdirs();

        String repoName = GitOperations.extractRepoName(gitUrl);
        File repoDir = new File(cacheRoot, repoName + "_" + System.currentTimeMillis());

        try {
            sendStatus(sender, plugin.getMessage("status.compile-cloning", Placeholder.unparsed("arg", gitUrl)));
            int timeout = plugin.getConfig().getInt("compile.clone-timeout", 300);
            int depth = plugin.getConfig().getInt("compile.clone-depth", 1);
            GitOperations.cloneRepository(gitUrl, branch, repoDir, timeout, depth);
            sendStatus(sender, plugin.getMessage("status.compile-cloned"));

            sendStatus(sender, plugin.getMessage("status.compile-detecting"));
            BuildSystem buildSystem = BuildRunner.detect(repoDir);
            if (buildSystem == BuildSystem.UNKNOWN) {
                sender.sendMessage(plugin.getMessage("errors.compile-no-build-system"));
                cleanup(repoDir);
                return;
            }

            sendStatus(sender, buildSystem == BuildSystem.GRADLE
                    ? plugin.getMessage("status.compile-detected-gradle")
                    : plugin.getMessage("status.compile-detected-maven"));

            String buildTask;
            if (interactive) {
                buildTask = promptForBuildTask((Player) sender, buildSystem);
                if (buildTask == null) {
                    sender.sendMessage(plugin.getMessage("status.compile-cancelled"));
                    cleanup(repoDir);
                    return;
                }
            } else {
                buildTask = getDefaultBuildTask(buildSystem);
                sender.sendMessage(plugin.getMessage("status.compile-using-default-task",
                        Placeholder.unparsed("task", buildTask)));
            }

            sendStatus(sender, plugin.getMessage("status.compile-building", Placeholder.unparsed("arg", buildTask)));
            int buildTimeout = plugin.getConfig().getInt("compile.build-timeout", 600);
            boolean ok = BuildRunner.run(repoDir, buildSystem, buildTask, buildTimeout,
                    line -> sender.sendMessage(plugin.getMessage("status.compile-build-log",
                            Placeholder.unparsed("line", line))));
            if (!ok) {
                cleanup(repoDir);
                return;
            }
            sendStatus(sender, plugin.getMessage("status.compile-build-success"));

            sendStatus(sender, plugin.getMessage("status.compile-finding-jars"));
            List<File> jars = BuildRunner.findJars(repoDir, buildSystem);
            if (jars.isEmpty()) {
                sender.sendMessage(plugin.getMessage("errors.compile-no-jars"));
                cleanup(repoDir);
                return;
            }

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
                selectedJar = jars.get(0);
                sender.sendMessage(plugin.getMessage("status.compile-auto-selected-jar",
                        Placeholder.unparsed("name", selectedJar.getName()),
                        Placeholder.unparsed("size", formatFileSize(selectedJar.length()))));
            }

            sendStatus(sender, plugin.getMessage("status.compile-copying",
                    Placeholder.unparsed("arg", selectedJar.getName())));
            Files.copy(selectedJar.toPath(),
                    new File(packageManager.getPluginsDir(), selectedJar.getName()).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            sender.sendMessage(plugin.getMessage("status.compile-complete"));

            if (!plugin.getConfig().getBoolean("compile.keep-repos-by-default", true)) {
                cleanup(repoDir);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sender.sendMessage(plugin.getMessage("errors.compile-clone-failed",
                    Placeholder.unparsed("error", "Interrupted")));
            plugin.getLogger().log(Level.WARNING, "Compile interrupted for " + gitUrl, e);
            cleanup(repoDir);
        } catch (GitAPIException | IOException e) {
            sender.sendMessage(plugin.getMessage("errors.compile-clone-failed",
                    Placeholder.unparsed("error", e.getMessage())));
            plugin.getLogger().log(Level.SEVERE, "Compile failed for " + gitUrl, e);
            cleanup(repoDir);
        }
    }

    private String promptForBuildTask(Player player, BuildSystem buildSystem) {
        try {
            List<String> options = buildSystem == BuildSystem.GRADLE
                    ? plugin.getConfig().getStringList("compile.default-gradle-tasks")
                    : List.of(plugin.getConfig().getString("compile.default-maven-goal", "package"),
                              "clean package", "install");

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

            String response = waitForPlayerInput(player, 30);
            if (response == null) return null;

            try {
                int sel = Integer.parseInt(response.trim());
                if (sel >= 1 && sel <= options.size()) return options.get(sel - 1);
                if (sel == options.size() + 1) {
                    player.sendMessage(plugin.getMessage("status.compile-prompt-custom-task"));
                    String custom = waitForPlayerInput(player, 30);
                    return (custom != null && !custom.trim().isEmpty()) ? custom.trim() : null;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getMessage("errors.compile-invalid-selection"));
            }
            return null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error prompting player for build task", e);
            return null;
        }
    }

    private String getDefaultBuildTask(BuildSystem buildSystem) {
        if (buildSystem == BuildSystem.GRADLE) {
            List<String> tasks = plugin.getConfig().getStringList("compile.default-gradle-tasks");
            return tasks.isEmpty() ? "build" : tasks.get(0);
        }
        return plugin.getConfig().getString("compile.default-maven-goal", "package");
    }

    private File selectJar(Player player, List<File> jars) {
        try {
            player.sendMessage(plugin.getMessage("status.compile-select-jar"));
            for (int i = 0; i < jars.size(); i++) {
                player.sendMessage(plugin.getMessage("status.compile-jar-option",
                        Placeholder.unparsed("index", String.valueOf(i + 1)),
                        Placeholder.unparsed("name", jars.get(i).getName()),
                        Placeholder.unparsed("size", formatFileSize(jars.get(i).length()))));
            }
            String response = waitForPlayerInput(player, 30);
            if (response == null) return null;
            try {
                int sel = Integer.parseInt(response.trim());
                if (sel >= 1 && sel <= jars.size()) return jars.get(sel - 1);
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getMessage("errors.compile-invalid-selection"));
            }
            return null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error prompting player for JAR selection", e);
            return null;
        }
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
            plugin.getLogger().log(Level.WARNING, "Interrupted waiting for player input", e);
            return null;
        }
    }

    public void handlePlayerChat(Player player, String message) {
        CompletableFuture<String> future = pendingInputs.remove(player);
        if (future != null) future.complete(message);
    }

    public boolean hasPendingInput(Player player) {
        return pendingInputs.containsKey(player);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
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
                for (File child : files) deleteRecursively(child);
            }
        }
        file.delete();
    }
}
