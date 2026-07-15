package io.github.Earth1283.aptMc.commands.sub.compile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GitOperationsTest {

    // --- isValidGitUrl ---

    @Test
    fun validUrl_httpsGithub() {
        assertTrue(GitOperations.isValidGitUrl("https://github.com/owner/repo"))
    }

    @Test
    fun validUrl_httpsGitExtension() {
        assertTrue(GitOperations.isValidGitUrl("https://example.com/repo.git"))
    }

    @Test
    fun validUrl_sshGithub() {
        assertTrue(GitOperations.isValidGitUrl("git@github.com:owner/repo"))
    }

    @Test
    fun validUrl_gitProtocol() {
        assertTrue(GitOperations.isValidGitUrl("git://github.com/owner/repo"))
    }

    @Test
    fun invalidUrl_plainHttp() {
        assertFalse(GitOperations.isValidGitUrl("http://example.com/not-a-repo"))
    }

    @Test
    fun invalidUrl_randomString() {
        assertFalse(GitOperations.isValidGitUrl("not-a-url"))
    }

    @Test
    fun invalidUrl_empty() {
        assertFalse(GitOperations.isValidGitUrl(""))
    }

    // --- extractRepoName ---

    @Test
    fun extractRepoName_httpsWithGitExtension() {
        assertEquals("my-plugin", GitOperations.extractRepoName("https://github.com/owner/my-plugin.git"))
    }

    @Test
    fun extractRepoName_httpsWithoutExtension() {
        assertEquals("my-plugin", GitOperations.extractRepoName("https://github.com/owner/my-plugin"))
    }

    @Test
    fun extractRepoName_sshFormat() {
        assertEquals("my-plugin", GitOperations.extractRepoName("git@github.com:owner/my-plugin.git"))
    }

    @Test
    fun extractRepoName_sanitizesSpecialChars() {
        assertEquals("my_plugin", GitOperations.extractRepoName("https://github.com/owner/my plugin"))
    }
}
