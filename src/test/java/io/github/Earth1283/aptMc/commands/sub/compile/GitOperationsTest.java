package io.github.Earth1283.aptMc.commands.sub.compile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GitOperationsTest {

    // --- isValidGitUrl ---

    @Test
    void validUrl_httpsGithub() {
        assertTrue(GitOperations.isValidGitUrl("https://github.com/owner/repo"));
    }

    @Test
    void validUrl_httpsGitExtension() {
        assertTrue(GitOperations.isValidGitUrl("https://example.com/repo.git"));
    }

    @Test
    void validUrl_sshGithub() {
        assertTrue(GitOperations.isValidGitUrl("git@github.com:owner/repo"));
    }

    @Test
    void validUrl_gitProtocol() {
        assertTrue(GitOperations.isValidGitUrl("git://github.com/owner/repo"));
    }

    @Test
    void invalidUrl_plainHttp() {
        assertFalse(GitOperations.isValidGitUrl("http://example.com/not-a-repo"));
    }

    @Test
    void invalidUrl_randomString() {
        assertFalse(GitOperations.isValidGitUrl("not-a-url"));
    }

    @Test
    void invalidUrl_empty() {
        assertFalse(GitOperations.isValidGitUrl(""));
    }

    // --- extractRepoName ---

    @Test
    void extractRepoName_httpsWithGitExtension() {
        assertEquals("my-plugin", GitOperations.extractRepoName("https://github.com/owner/my-plugin.git"));
    }

    @Test
    void extractRepoName_httpsWithoutExtension() {
        assertEquals("my-plugin", GitOperations.extractRepoName("https://github.com/owner/my-plugin"));
    }

    @Test
    void extractRepoName_sshFormat() {
        assertEquals("my-plugin", GitOperations.extractRepoName("git@github.com:owner/my-plugin.git"));
    }

    @Test
    void extractRepoName_sanitizesSpecialChars() {
        // Any char that's not alphanumeric, dash, or underscore becomes _
        assertEquals("my_plugin", GitOperations.extractRepoName("https://github.com/owner/my plugin"));
    }
}
