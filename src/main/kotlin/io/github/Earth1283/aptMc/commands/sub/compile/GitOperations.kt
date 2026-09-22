package io.github.Earth1283.aptMc.commands.sub.compile

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.GitAPIException
import java.io.File
import java.io.IOException

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

    /** True if [dir] already holds a git working copy (used to reuse a cached clone). */
    @JvmStatic
    fun isGitRepository(dir: File): Boolean {
        return File(dir, ".git").isDirectory
    }

    /**
     * Updates an existing cached clone in place: fetches [branch] from origin, hard-resets
     * to it, and discards any leftover build output/untracked files - much cheaper than a
     * fresh clone for repeat compiles of the same repo/branch.
     */
    @JvmStatic
    @Throws(GitAPIException::class, IOException::class)
    fun updateRepository(repoDir: File, branch: String, timeout: Int) {
        Git.open(repoDir).use { git ->
            git.fetch()
                .setTimeout(if (timeout > 0) timeout else 0)
                .call()
            git.reset()
                .setMode(ResetCommand.ResetType.HARD)
                .setRef("origin/$branch")
                .call()
            git.clean()
                .setForce(true)
                .setCleanDirectories(true)
                .call()
        }
    }
}
