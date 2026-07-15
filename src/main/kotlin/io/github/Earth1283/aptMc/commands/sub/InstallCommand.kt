package io.github.Earth1283.aptMc.commands.sub

import com.google.gson.JsonObject
import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.api.GitHubAPI
import io.github.Earth1283.aptMc.api.HangarAPI
import io.github.Earth1283.aptMc.api.ModrinthAPI
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.listeners.ConfirmationListener
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

class InstallCommand(
    plugin: AptMc,
    packageManager: PackageManager,
    private val confirmationListener: ConfirmationListener?
) : SubCommand(plugin, packageManager) {

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("errors.no-packages-specified"))
            return
        }

        if (args.size == 1 && args[0].matches(Regex("^(http|https|ftp)://.*\\.yml$"))) {
            val url = args[0]
            if (dryRun) {
                sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Import manifest from $url")))
                return
            }
            try {
                sendStatus(sender, plugin.getMessage("status.downloading", Placeholder.unparsed("arg", "manifest")))
                val tempDir = File(plugin.dataFolder, "temp")
                if (!tempDir.exists()) tempDir.mkdirs()
                val tempFile = File(tempDir, "remote_import_" + System.currentTimeMillis() + ".yml")

                val client = HttpClient.newHttpClient()
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "apt-mc/1.2 (parody-cli)")
                    .GET()
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile.toPath()))

                if (response.statusCode() != 200) {
                    sender.sendMessage(plugin.getMessage("errors.fetch-manifest-failed", Placeholder.unparsed("error", "HTTP " + response.statusCode())))
                    return
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    sender.sendMessage(plugin.getMessage("errors.fetch-manifest-failed", Placeholder.unparsed("error", "File empty or missing")))
                    return
                }

                val yaml = YamlConfiguration.loadConfiguration(tempFile)
                val title = yaml.getString("project-details.title", "Unknown")!!
                val author = yaml.getString("project-details.author", "Unknown")!!

                sender.sendMessage(plugin.getMessage("status.import-header", Placeholder.unparsed("title", title), Placeholder.unparsed("author", author)))
                sender.sendMessage(plugin.getMessage("status.confirm-prompt"))

                if (confirmationListener != null) {
                    confirmationListener.addPending(sender, tempFile)
                } else {
                    sender.sendMessage(plugin.getMessage("errors.listener-missing"))
                }
            } catch (e: Exception) {
                sender.sendMessage(plugin.getMessage("errors.fetch-manifest-failed", Placeholder.unparsed("error", e.message ?: "")))
            }
            return
        }

        packageManager.ensureDir()
        sendStatus(sender, plugin.getMessage("status.update-reading"))
        sendStatus(sender, plugin.getMessage("status.update-building"))

        val installedIds = getInstalledProjectIds()
        val toInstallMap = LinkedHashMap<String, JsonObject>()
        var priority = plugin.getConfig().getStringList("source-priority")
        if (priority.isEmpty()) priority = listOf("modrinth", "hangar", "github")

        for (pkgSlug in args) {
            var found = false
            try {
                if (pkgSlug.contains("/") && !pkgSlug.startsWith("http")) {
                    sendStatus(sender, plugin.getMessage("status.checking", Placeholder.unparsed("arg", "$pkgSlug on GitHub")))
                    val release = GitHubAPI.getLatestRelease(pkgSlug)
                    if (release != null) {
                        val asset = GitHubAPI.getPrimaryJarAsset(release)
                        if (asset != null) {
                            val githubInfo = JsonObject()
                            githubInfo.addProperty("source", "github")
                            githubInfo.addProperty("slug", pkgSlug)
                            githubInfo.addProperty("download_url", asset.get("browser_download_url").asString)
                            githubInfo.addProperty("filename", asset.get("name").asString)
                            githubInfo.addProperty("size", asset.get("size").asLong)
                            toInstallMap["github:$pkgSlug"] = githubInfo
                            found = true
                        }
                    }
                }

                if (!found) {
                    for (source in priority) {
                        if (source.equals("modrinth", ignoreCase = true)) {
                            sendStatus(sender, plugin.getMessage("status.checking", Placeholder.unparsed("arg", "$pkgSlug on Modrinth")))
                            val project = ModrinthAPI.getProject(pkgSlug)
                            if (project != null) {
                                resolveDependenciesRecursive(sender, project, toInstallMap, installedIds)
                                found = true
                                break
                            }
                        } else if (source.equals("hangar", ignoreCase = true) && plugin.getConfig().getBoolean("enable-hangar", true)) {
                            sendStatus(sender, plugin.getMessage("status.checking", Placeholder.unparsed("arg", "$pkgSlug on Hangar")))
                            val hangarProject = HangarAPI.getProject(pkgSlug)
                            if (hangarProject != null) {
                                val versionInfo = HangarAPI.getLatestVersion(pkgSlug, "PAPER")
                                if (versionInfo != null) {
                                    versionInfo.addProperty("source", "hangar")
                                    versionInfo.addProperty("hangar_slug", hangarProject.get("name").asString)
                                    toInstallMap["hangar:" + hangarProject.get("name").asString] = versionInfo
                                    found = true
                                    break
                                }
                            }
                        }
                    }
                }

                if (!found) {
                    sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", pkgSlug)))
                }
            } catch (e: Exception) {
                sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", pkgSlug), Placeholder.unparsed("error", e.message ?: "")))
            }
        }

        val toInstall = ArrayList(toInstallMap.values)
        if (toInstall.isEmpty()) {
            sender.sendMessage(plugin.getMessage("status.up-to-date"))
            return
        }

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run-header"))
            for (pkg in toInstall) {
                val name = getPkgName(pkg)
                sender.sendMessage(plugin.getMessage("status.install-item", Placeholder.unparsed("arg", name)))
            }
            sender.sendMessage(plugin.getMessage("status.dry-run-complete"))
            return
        }

        if (plugin.getConfig().getBoolean("apt-song-references")) {
            sender.sendMessage(plugin.getMessage("status.install-song-ref"))
        }

        sender.sendMessage(plugin.getMessage("status.install-header"))
        for (pkg in toInstall) {
            val name = getPkgName(pkg)
            sender.sendMessage(plugin.getMessage("status.install-item", Placeholder.unparsed("arg", name)))
        }
        sender.sendMessage(plugin.getMessage("status.install-summary", Placeholder.unparsed("arg", toInstall.size.toString())))

        val installFutures = toInstall.map { pkg ->
            CompletableFuture.runAsync {
                val source = if (pkg.has("source")) pkg.get("source").asString else "modrinth"

                if (source == "github") {
                    try {
                        val url = pkg.get("download_url").asString
                        val filename = pkg.get("filename").asString
                        val size = pkg.get("size").asLong

                        sendStatus(sender, plugin.getMessage("status.downloading", Placeholder.unparsed("arg", filename)))
                        packageManager.downloadFile(url, packageManager.getPluginsDir(), filename, size, createProgressCallback(sender, filename))
                        sendStatus(sender, plugin.getMessage("status.downloaded", Placeholder.unparsed("arg", filename)))
                    } catch (e: Exception) {
                        sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", pkg.get("slug").asString), Placeholder.unparsed("error", e.message ?: "")))
                    }
                    return@runAsync
                }

                if (source == "hangar") {
                    try {
                        val url = HangarAPI.getDownloadUrl(pkg, "PAPER")
                        val filename = HangarAPI.getFileName(pkg, "PAPER")

                        if (url == null || filename == null) {
                            sender.sendMessage(plugin.getMessage("errors.hangar-resolve-failed", Placeholder.unparsed("arg", pkg.get("hangar_slug").asString)))
                            return@runAsync
                        }

                        sendStatus(sender, plugin.getMessage("status.downloading", Placeholder.unparsed("arg", filename)))
                        packageManager.downloadFile(url, packageManager.getPluginsDir(), filename, -1, createProgressCallback(sender, filename))
                        sendStatus(sender, plugin.getMessage("status.downloaded", Placeholder.unparsed("arg", filename)))
                    } catch (e: Exception) {
                        sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", "Hangar Package"), Placeholder.unparsed("error", e.message ?: "")))
                    }
                    return@runAsync
                }

                val slug = pkg.get("slug").asString
                try {
                    val versions = ModrinthAPI.getVersions(pkg.get("id").asString, listOf("spigot", "paper", "purpur", "bukkit"))
                    if (versions.size() == 0) {
                        sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", slug), Placeholder.unparsed("error", "No compatible versions")))
                        return@runAsync
                    }

                    val latest = versions[0].asJsonObject
                    val files = latest.getAsJsonArray("files")
                    var primaryFile = files[0].asJsonObject

                    for (f in files) {
                        if (f.asJsonObject.has("primary") && f.asJsonObject.get("primary").asBoolean) {
                            primaryFile = f.asJsonObject
                            break
                        }
                    }

                    val url = primaryFile.get("url").asString
                    val filename = primaryFile.get("filename").asString
                    val size = primaryFile.get("size").asLong

                    sendStatus(sender, plugin.getMessage("status.downloading", Placeholder.unparsed("arg", filename)))
                    packageManager.downloadFile(url, packageManager.getPluginsDir(), filename, size, createProgressCallback(sender, filename))
                    sendStatus(sender, plugin.getMessage("status.downloaded", Placeholder.unparsed("arg", filename)))
                } catch (e: Exception) {
                    sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", slug), Placeholder.unparsed("error", e.message ?: "")))
                }
            }
        }

        CompletableFuture.allOf(*installFutures.toTypedArray()).join()
        sender.sendMessage(plugin.getMessage("status.install-complete"))
    }

    private fun getPkgName(pkg: JsonObject): String {
        if (pkg.has("slug")) return pkg.get("slug").asString
        if (pkg.has("hangar_slug")) return pkg.get("hangar_slug").asString
        return "unknown"
    }

    private fun getInstalledProjectIds(): Set<String> {
        val installed = packageManager.getInstalledPlugins()
        val resolved = resolveVersions(ArrayList(installed.values))
        val ids = HashSet<String>()
        for (v in resolved.values) {
            ids.add(v.get("project_id").asString)
        }
        return ids
    }

    private fun resolveDependenciesRecursive(
        sender: CommandSender,
        project: JsonObject,
        toInstall: MutableMap<String, JsonObject>,
        installedIds: Set<String>
    ) {
        val id = project.get("id").asString
        val slug = project.get("slug").asString

        if (installedIds.contains(id) || toInstall.containsKey(id)) return

        toInstall[id] = project

        try {
            val versions = ModrinthAPI.getVersions(id, listOf("spigot", "paper", "purpur", "bukkit"))
            if (versions.size() > 0) {
                val latest = versions[0].asJsonObject
                val deps = latest.getAsJsonArray("dependencies")
                if (deps != null) {
                    for (d in deps) {
                        val depObj = d.asJsonObject
                        if (depObj.has("dependency_type") && depObj.get("dependency_type").asString == "required") {
                            if (depObj.has("project_id") && !depObj.get("project_id").isJsonNull) {
                                val depId = depObj.get("project_id").asString
                                if (!installedIds.contains(depId) && !toInstall.containsKey(depId)) {
                                    sendStatus(sender, plugin.getMessage("status.checking", Placeholder.unparsed("arg", "dependency: $depId")))
                                    val depProj = ModrinthAPI.getProject(depId)
                                    if (depProj != null) {
                                        resolveDependenciesRecursive(sender, depProj, toInstall, installedIds)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessage("errors.failed-resolve-deps", Placeholder.unparsed("arg", slug), Placeholder.unparsed("error", e.message ?: "")))
        }
    }
}
