package io.github.Earth1283.aptMc.commands.sub.compile;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;

public final class GitOperations {

    private GitOperations() {}

    public static boolean isValidGitUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        return url.matches("^(https?|git)://.*\\.git$") ||
                url.matches("^(https?|git)://github\\.com/.*") ||
                url.matches("^(https?|git)://gitlab\\.com/.*") ||
                url.matches("^git@.*:.*\\.git$") ||
                url.matches("^git@github\\.com:.*") ||
                url.matches("^git@gitlab\\.com:.*");
    }

    public static String extractRepoName(String gitUrl) {
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

    /**
     * Shallow-clones {@code url} at {@code branch} into {@code destDir}.
     *
     * @param timeout seconds before the clone is aborted (0 = no timeout)
     * @param depth   shallow clone depth (0 = full history)
     */
    public static void cloneRepository(String url, String branch, File destDir, int timeout, int depth)
            throws GitAPIException {
        Git.cloneRepository()
                .setURI(url)
                .setBranch(branch)
                .setDirectory(destDir)
                .setDepth(depth > 0 ? depth : null)
                .setTimeout(timeout > 0 ? timeout : 0)
                .setCloneAllBranches(false)
                .call()
                .close();
    }
}
