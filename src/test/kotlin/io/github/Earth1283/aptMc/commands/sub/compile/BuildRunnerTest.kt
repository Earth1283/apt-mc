package io.github.Earth1283.aptMc.commands.sub.compile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BuildRunnerTest {

    // --- isInterestingLine ---

    @Test
    fun interestingLine_gradleTask() {
        assertTrue(BuildRunner.isInterestingLine("> Task :compileJava"))
    }

    @Test
    fun interestingLine_buildSuccess() {
        assertTrue(BuildRunner.isInterestingLine("BUILD SUCCESSFUL in 3s"))
    }

    @Test
    fun interestingLine_buildFailed() {
        assertTrue(BuildRunner.isInterestingLine("BUILD FAILED"))
    }

    @Test
    fun interestingLine_compiling() {
        assertTrue(BuildRunner.isInterestingLine("Compiling 3 source files"))
    }

    @Test
    fun interestingLine_downloadingDep() {
        assertTrue(BuildRunner.isInterestingLine("Downloading https://repo.maven.apache.org/foo.jar"))
    }

    @Test
    fun interestingLine_notInteresting() {
        assertFalse(BuildRunner.isInterestingLine("Note: uses unchecked operations."))
    }

    @Test
    fun interestingLine_emptyString() {
        assertFalse(BuildRunner.isInterestingLine(""))
    }

    // --- getLastLines ---

    @Test
    fun getLastLines_fewerLinesThanCount() {
        assertEquals("a\nb", BuildRunner.getLastLines("a\nb", 5))
    }

    @Test
    fun getLastLines_exactCount() {
        assertEquals("a\nb\nc", BuildRunner.getLastLines("a\nb\nc", 3))
    }

    @Test
    fun getLastLines_moreThanCount() {
        assertEquals("b\nc", BuildRunner.getLastLines("a\nb\nc", 2))
    }

    // --- detect ---

    @Test
    fun detect_gradle_gradlew(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("gradlew"))
        assertEquals(BuildRunner.BuildSystem.GRADLE, BuildRunner.detect(tempDir.toFile()))
    }

    @Test
    fun detect_gradle_buildGradle(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("build.gradle"))
        assertEquals(BuildRunner.BuildSystem.GRADLE, BuildRunner.detect(tempDir.toFile()))
    }

    @Test
    fun detect_maven_pom(@TempDir tempDir: Path) {
        Files.createFile(tempDir.resolve("pom.xml"))
        assertEquals(BuildRunner.BuildSystem.MAVEN, BuildRunner.detect(tempDir.toFile()))
    }

    @Test
    fun detect_unknown_emptyDir(@TempDir tempDir: Path) {
        assertEquals(BuildRunner.BuildSystem.UNKNOWN, BuildRunner.detect(tempDir.toFile()))
    }

    // --- findJars ---

    @Test
    fun findJars_gradle_returnsJarsFromBuildLibs(@TempDir tempDir: Path) {
        val libs = tempDir.resolve("build/libs")
        Files.createDirectories(libs)
        Files.createFile(libs.resolve("plugin-1.0.jar"))
        Files.createFile(libs.resolve("plugin-1.0-sources.jar")) // should be excluded
        val jars = BuildRunner.findJars(tempDir.toFile(), BuildRunner.BuildSystem.GRADLE)
        assertEquals(1, jars.size)
        assertEquals("plugin-1.0.jar", jars[0].name)
    }

    @Test
    fun findJars_maven_returnsJarsFromTarget(@TempDir tempDir: Path) {
        val target = tempDir.resolve("target")
        Files.createDirectories(target)
        Files.createFile(target.resolve("plugin-1.0.jar"))
        val jars = BuildRunner.findJars(tempDir.toFile(), BuildRunner.BuildSystem.MAVEN)
        assertEquals(1, jars.size)
    }

    @Test
    fun findJars_noOutputDir_returnsEmpty(@TempDir tempDir: Path) {
        val jars = BuildRunner.findJars(tempDir.toFile(), BuildRunner.BuildSystem.GRADLE)
        assertTrue(jars.isEmpty())
    }
}
