package io.github.Earth1283.aptMc.commands.sub.compile

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

object BuildRunner {

    enum class BuildSystem { GRADLE, MAVEN, UNKNOWN }

    @JvmStatic
    fun detect(repoDir: File): BuildSystem {
        if (File(repoDir, "gradlew").exists()
            || File(repoDir, "build.gradle").exists()
            || File(repoDir, "build.gradle.kts").exists()
        ) {
            return BuildSystem.GRADLE
        }
        if (File(repoDir, "pom.xml").exists()) {
            return BuildSystem.MAVEN
        }
        return BuildSystem.UNKNOWN
    }

    /**
     * Runs the build and streams "interesting" lines to [lineCallback].
     *
     * @param timeout seconds before the build is killed (0 = no timeout)
     * @return true if build exited with code 0
     */
    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun run(
        repoDir: File,
        buildSystem: BuildSystem,
        task: String,
        timeout: Int,
        lineCallback: Consumer<String>
    ): Boolean {
        val pb: ProcessBuilder

        val javaHome = System.getProperty("java.home")
        var javaBin = File(File(javaHome, "bin"), "java")
        if (!javaBin.exists()) {
            javaBin = File(File(javaHome, "bin"), "java.exe")
        }
        val javaPath = if (javaBin.exists()) javaBin.absolutePath else "java"

        if (buildSystem == BuildSystem.GRADLE) {
            val wrapperJar = File(repoDir, "gradle/wrapper/gradle-wrapper.jar")
            if (wrapperJar.exists()) {
                pb = ProcessBuilder(
                    javaPath,
                    "-classpath",
                    wrapperJar.absolutePath,
                    "org.gradle.wrapper.GradleWrapperMain",
                    task,
                    "--console=plain",
                    "--no-daemon"
                )
            } else {
                var gradlew = File(repoDir, "gradlew")
                if (!gradlew.exists()) gradlew = File(repoDir, "gradlew.bat")
                pb = if (gradlew.exists()) {
                    gradlew.setExecutable(true)
                    ProcessBuilder(gradlew.absolutePath, task, "--console=plain", "--no-daemon")
                } else {
                    ProcessBuilder("gradle", task, "--console=plain", "--no-daemon")
                }
            }
        } else {
            val mavenWrapperJar = File(repoDir, ".mvn/wrapper/maven-wrapper.jar")
            if (mavenWrapperJar.exists()) {
                pb = ProcessBuilder(
                    javaPath,
                    "-classpath",
                    mavenWrapperJar.absolutePath,
                    "org.apache.maven.wrapper.MavenWrapperMain",
                    task,
                    "-B"
                )
            } else {
                var mvnw = File(repoDir, "mvnw")
                if (!mvnw.exists()) mvnw = File(repoDir, "mvnw.cmd")
                pb = if (mvnw.exists()) {
                    mvnw.setExecutable(true)
                    ProcessBuilder(mvnw.absolutePath, task, "-B")
                } else {
                    ProcessBuilder("mvn", task, "-B")
                }
            }
        }

        pb.directory(repoDir)
        pb.redirectErrorStream(true)
        val process = pb.start()

        val fullOutput = StringBuilder()
        val reader = CompletableFuture.runAsync {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { br ->
                    var line: String?
                    while ((br.readLine().also { line = it }) != null) {
                        fullOutput.append(line).append("\n")
                        if (isInterestingLine(line)) {
                            lineCallback.accept(line!!.trim())
                        }
                    }
                }
            } catch (ignored: IOException) {
            }
        }

        val finished: Boolean
        if (timeout > 0) {
            finished = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
        } else {
            process.waitFor()
            finished = true
        }

        if (!finished) {
            process.destroyForcibly()
            lineCallback.accept("BUILD TIMED OUT after ${timeout}s")
            return false
        }

        try {
            reader.get(10, TimeUnit.SECONDS)
        } catch (ignored: Exception) {
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            lineCallback.accept("BUILD FAILED (exit $exitCode): " + getLastLines(fullOutput.toString(), 5))
        }
        return exitCode == 0
    }

    @JvmStatic
    fun isInterestingLine(line: String?): Boolean {
        if (line.isNullOrEmpty()) return false
        val lower = line.lowercase()
        if (line.startsWith("> Task :") || line.startsWith("Task :")) return true
        if (lower.contains("compiling") || lower.contains("processing")) return true
        if (lower.contains("test") && (lower.contains("passed") || lower.contains("failed"))) return true
        if (lower.contains("build success") || lower.contains("build fail")) return true
        if (lower.contains("download")) return true
        if (lower.contains("warning") || lower.contains("error")) return true
        return false
    }

    @JvmStatic
    fun getLastLines(text: String, count: Int): String {
        val lines = text.split("\n").dropLastWhile { it.isEmpty() }
        val start = maxOf(0, lines.size - count)
        return lines.subList(start, lines.size).joinToString("\n")
    }

    @JvmStatic
    fun findJars(repoDir: File, buildSystem: BuildSystem): List<File> {
        val searchDir = if (buildSystem == BuildSystem.GRADLE)
            File(repoDir, "build/libs")
        else
            File(repoDir, "target")

        val jars = mutableListOf<File>()
        if (searchDir.exists() && searchDir.isDirectory) {
            val files = searchDir.listFiles { _, name ->
                name.endsWith(".jar")
                        && !name.endsWith("-sources.jar")
                        && !name.endsWith("-javadoc.jar")
            }
            if (files != null) jars.addAll(files)
        }
        jars.sortWith { a, b -> b.length().compareTo(a.length()) }
        return jars
    }
}
