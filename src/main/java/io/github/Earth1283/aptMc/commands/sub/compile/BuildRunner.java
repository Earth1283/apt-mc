package io.github.Earth1283.aptMc.commands.sub.compile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class BuildRunner {

    public enum BuildSystem { GRADLE, MAVEN, UNKNOWN }

    private BuildRunner() {}

    public static BuildSystem detect(File repoDir) {
        if (new File(repoDir, "gradlew").exists()
                || new File(repoDir, "build.gradle").exists()
                || new File(repoDir, "build.gradle.kts").exists()) {
            return BuildSystem.GRADLE;
        }
        if (new File(repoDir, "pom.xml").exists()) {
            return BuildSystem.MAVEN;
        }
        return BuildSystem.UNKNOWN;
    }

    /**
     * Runs the build and streams "interesting" lines to {@code lineCallback}.
     *
     * @param timeout seconds before the build is killed (0 = no timeout)
     * @return true if build exited with code 0
     */
    public static boolean run(File repoDir, BuildSystem buildSystem, String task,
                              int timeout, Consumer<String> lineCallback) throws IOException, InterruptedException {
        ProcessBuilder pb;
        if (buildSystem == BuildSystem.GRADLE) {
            File gradlew = new File(repoDir, "gradlew");
            if (!gradlew.exists()) gradlew = new File(repoDir, "gradlew.bat");
            if (gradlew.exists()) {
                gradlew.setExecutable(true);
                pb = new ProcessBuilder(gradlew.getAbsolutePath(), task, "--console=plain");
            } else {
                pb = new ProcessBuilder("gradle", task, "--console=plain");
            }
        } else {
            pb = new ProcessBuilder("mvn", task, "-B");
        }

        pb.directory(repoDir);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder fullOutput = new StringBuilder();
        CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    fullOutput.append(line).append("\n");
                    if (isInterestingLine(line)) {
                        lineCallback.accept(line.trim());
                    }
                }
            } catch (IOException ignored) {}
        });

        boolean finished = timeout > 0
                ? process.waitFor(timeout, TimeUnit.SECONDS)
                : (process.waitFor() == 0 || true);

        if (!finished) {
            process.destroyForcibly();
            lineCallback.accept("BUILD TIMED OUT after " + timeout + "s");
            return false;
        }

        try { reader.get(5, TimeUnit.SECONDS); } catch (Exception ignored) {}

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            lineCallback.accept("BUILD FAILED (exit " + exitCode + "): " + getLastLines(fullOutput.toString(), 5));
        }
        return exitCode == 0;
    }

    public static boolean isInterestingLine(String line) {
        if (line == null || line.isEmpty()) return false;
        String lower = line.toLowerCase();
        if (line.startsWith("> Task :") || line.startsWith("Task :")) return true;
        if (lower.contains("compiling") || lower.contains("processing")) return true;
        if (lower.contains("test") && (lower.contains("passed") || lower.contains("failed"))) return true;
        if (lower.contains("build success") || lower.contains("build fail")) return true;
        if (lower.contains("download")) return true;
        if (lower.contains("warning") || lower.contains("error")) return true;
        return false;
    }

    public static String getLastLines(String text, int count) {
        String[] lines = text.split("\n");
        int start = Math.max(0, lines.length - count);
        return String.join("\n", Arrays.copyOfRange(lines, start, lines.length));
    }

    public static List<File> findJars(File repoDir, BuildSystem buildSystem) {
        File searchDir = buildSystem == BuildSystem.GRADLE
                ? new File(repoDir, "build/libs")
                : new File(repoDir, "target");

        List<File> jars = new ArrayList<>();
        if (searchDir.exists() && searchDir.isDirectory()) {
            File[] files = searchDir.listFiles((dir, name) ->
                    name.endsWith(".jar")
                    && !name.endsWith("-sources.jar")
                    && !name.endsWith("-javadoc.jar"));
            if (files != null) jars.addAll(Arrays.asList(files));
        }
        jars.sort((a, b) -> Long.compare(b.length(), a.length()));
        return jars;
    }
}
