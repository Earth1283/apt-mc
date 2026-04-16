package io.github.Earth1283.aptMc.commands.sub.compile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildRunnerTest {

    // --- isInterestingLine ---

    @Test
    void interestingLine_gradleTask() {
        assertTrue(BuildRunner.isInterestingLine("> Task :compileJava"));
    }

    @Test
    void interestingLine_buildSuccess() {
        assertTrue(BuildRunner.isInterestingLine("BUILD SUCCESSFUL in 3s"));
    }

    @Test
    void interestingLine_buildFailed() {
        assertTrue(BuildRunner.isInterestingLine("BUILD FAILED"));
    }

    @Test
    void interestingLine_compiling() {
        assertTrue(BuildRunner.isInterestingLine("Compiling 3 source files"));
    }

    @Test
    void interestingLine_downloadingDep() {
        assertTrue(BuildRunner.isInterestingLine("Downloading https://repo.maven.apache.org/foo.jar"));
    }

    @Test
    void interestingLine_notInteresting() {
        assertFalse(BuildRunner.isInterestingLine("Note: uses unchecked operations."));
    }

    @Test
    void interestingLine_emptyString() {
        assertFalse(BuildRunner.isInterestingLine(""));
    }

    // --- getLastLines ---

    @Test
    void getLastLines_fewerLinesThanCount() {
        assertEquals("a\nb", BuildRunner.getLastLines("a\nb", 5));
    }

    @Test
    void getLastLines_exactCount() {
        assertEquals("a\nb\nc", BuildRunner.getLastLines("a\nb\nc", 3));
    }

    @Test
    void getLastLines_moreThanCount() {
        assertEquals("b\nc", BuildRunner.getLastLines("a\nb\nc", 2));
    }

    // --- detect ---

    @Test
    void detect_gradle_gradlew(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("gradlew"));
        assertEquals(BuildRunner.BuildSystem.GRADLE, BuildRunner.detect(tempDir.toFile()));
    }

    @Test
    void detect_gradle_buildGradle(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("build.gradle"));
        assertEquals(BuildRunner.BuildSystem.GRADLE, BuildRunner.detect(tempDir.toFile()));
    }

    @Test
    void detect_maven_pom(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("pom.xml"));
        assertEquals(BuildRunner.BuildSystem.MAVEN, BuildRunner.detect(tempDir.toFile()));
    }

    @Test
    void detect_unknown_emptyDir(@TempDir Path tempDir) {
        assertEquals(BuildRunner.BuildSystem.UNKNOWN, BuildRunner.detect(tempDir.toFile()));
    }

    // --- findJars ---

    @Test
    void findJars_gradle_returnsJarsFromBuildLibs(@TempDir Path tempDir) throws IOException {
        Path libs = tempDir.resolve("build/libs");
        Files.createDirectories(libs);
        Files.createFile(libs.resolve("plugin-1.0.jar"));
        Files.createFile(libs.resolve("plugin-1.0-sources.jar")); // should be excluded
        List<File> jars = BuildRunner.findJars(tempDir.toFile(), BuildRunner.BuildSystem.GRADLE);
        assertEquals(1, jars.size());
        assertEquals("plugin-1.0.jar", jars.get(0).getName());
    }

    @Test
    void findJars_maven_returnsJarsFromTarget(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("target");
        Files.createDirectories(target);
        Files.createFile(target.resolve("plugin-1.0.jar"));
        List<File> jars = BuildRunner.findJars(tempDir.toFile(), BuildRunner.BuildSystem.MAVEN);
        assertEquals(1, jars.size());
    }

    @Test
    void findJars_noOutputDir_returnsEmpty(@TempDir Path tempDir) {
        List<File> jars = BuildRunner.findJars(tempDir.toFile(), BuildRunner.BuildSystem.GRADLE);
        assertTrue(jars.isEmpty());
    }
}
