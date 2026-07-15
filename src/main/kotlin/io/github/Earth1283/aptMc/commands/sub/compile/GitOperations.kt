package io.github.Earth1283.aptMc.commands.sub.compile

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import java.io.File

object GitOperations {

    @JvmStatic
    fun isValidGitUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.matches(Regex("^(https?|git)://.*\\.git$")) ||
                url.matches(Regex("^(https?|git)://github\\.com/.*")) ||
                url.matches(Regex("^(https?|git)://gitlab\\.com/.*")) ||
                url.matches(Regex("^git@.*:.*\\.git$")) ||
                url.matches(Regex("^git@github\\.com:.*")) ||
                url.matches(Regex("^git@gitlab\\.com:.*"))
    }

    @JvmStatic
    fun extractRepoName(gitUrl: String): String {
        var name = gitUrl
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length - 4)
        }
        val lastSlash = name.lastIndexOf('/')
        if (lastSlash != -1) {
            name = name.substring(lastSlash + 1)
        }
        val lastColon = name.lastIndexOf(':')
        if (lastColon != -1) {
            name = name.substring(lastColon + 1)
        }
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    /**
     * Shallow-clones [url] at [branch] into [destDir].
     *
     * @param timeout seconds before the clone is aborted (0 = no timeout)
     * @param depth shallow clone depth (0 = full history)
     */
    @JvmStatic
    @Throws(GitAPIException::class)
    fun cloneRepository(url: String, branch: String, destDir: File, timeout: Int, depth: Int) {
        Git.cloneRepository()
            .setURI(url)
            .setBranch(branch)
            .setDirectory(destDir)
            .setDepth((if (depth > 0) depth else null)!!)
            .setTimeout(if (timeout > 0) timeout else 0)
            .setCloneAllBranches(false)
            .call()
            .close()
    }
}
