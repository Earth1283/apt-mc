package io.github.Earth1283.aptMc.commands.sub

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.api.ModrinthAPI
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import java.util.zip.ZipInputStream

class ImportCommand(plugin: AptMc, packageManager: PackageManager) : SubCommand(plugin, packageManager) {

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        val rawName = if (args.isEmpty()) "apt-manifest" else args[0]
        val baseName = rawName.removeSuffix(".yml").removeSuffix(".zip")
        val zipFile = File(plugin.dataFolder, "$baseName.zip")
        val ymlFile = File(plugin.dataFolder, "$baseName.yml")
        val file = if (zipFile.exists()) zipFile else ymlFile

        if (!file.exists()) {
            sender.sendMessage(plugin.getMessage("errors.manifest-not-found", Placeholder.unparsed("arg", file.name)))
            return
        }

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Import from " + file.name)))
            return
        }

        performImport(sender, file)
    }

    private fun loadManifest(file: File): YamlConfiguration {
        val yaml = YamlConfiguration()
        if (file.name.endsWith(".zip")) {
            ZipInputStream(file.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".yml")) {
                        yaml.loadFromString(String(zis.readBytes(), Charsets.UTF_8))
                        return yaml
                    }
                    entry = zis.nextEntry
                }
            }
            throw IOException("No .yml manifest found inside " + file.name)
        }
        return YamlConfiguration.loadConfiguration(file)
    }

    fun performImport(sender: CommandSender, file: File) {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                plugin.logger.info("Starting import process for " + file.name)
                val yaml = loadManifest(file)
                if (!yaml.contains("plugins") && !yaml.contains("configs")) {
                    plugin.logger.warning("Manifest invalid (no plugins/configs): " + file.name)
                    sender.sendMessage(plugin.getMessage("errors.invalid-manifest"))
                    return@Runnable
                }

                plugin.logger.info("Manifest valid. Reading content...")
                sender.sendMessage(plugin.getMessage("status.reading-manifest"))

                if (yaml.contains("project-details")) {
                    val title = yaml.getString("project-details.title", "Unknown Project")!!
                    val author = yaml.getString("project-details.author", "Unknown Author")!!
                    sender.sendMessage(plugin.getMessage("status.import-header", Placeholder.unparsed("title", title), Placeholder.unparsed("author", author)))
                }

                if (yaml.contains("unrecognised")) {
                    val unrecognised = yaml.getStringList("unrecognised")
                    if (unrecognised.isNotEmpty()) {
                        sender.sendMessage(plugin.getMessage("status.import-unrecognised-warning"))
                        for (p in unrecognised) {
                            sender.sendMessage(plugin.getMessage("status.import-unrecognised-item", Placeholder.unparsed("arg", p)))
                        }
                    }
                }

                val futures = ArrayList<CompletableFuture<Void>>()
                val installedCount = AtomicInteger(0)
                val downloadExecutor = if (yaml.contains("plugins")) Executors.newFixedThreadPool(3) else null

                if (yaml.contains("plugins")) {
                    val pluginList = yaml.getMapList("plugins")
                    for (entry in pluginList) {
                        val value = entry["source"] as? String
                        if (value == null) continue

                        if (value == "embedded") {
                            val fname = entry["file"] as? String
                            val b64 = entry["data"] as? String
                            if (fname != null && b64 != null) {
                                importEmbeddedPlugin(sender, fname, b64, installedCount)
                            }
                            continue
                        }

                        if (!value.startsWith("modrinth:")) continue

                        val parts = value.substring(9).split("/")
                        if (parts.size < 2) continue

                        val projectId = parts[0]
                        val versionNum = parts[1]

                        futures.add(CompletableFuture.runAsync({
                            importOnePlugin(sender, projectId, versionNum, installedCount)
                        }, downloadExecutor!!))
                    }
                    sender.sendMessage(plugin.getMessage("status.importing-count", Placeholder.unparsed("arg", futures.size.toString())))
                }

                CompletableFuture.allOf(*futures.toTypedArray()).thenRun {
                    downloadExecutor?.shutdown()

                    if (yaml.contains("configs")) {
                        sendStatus(sender, plugin.getMessage("status.exporting-configs"))
                        val configs = yaml.getMapList("configs")
                        for (entry in configs) {
                            val pluginName = entry["plugin"] as? String
                            val subPath = entry["path"] as? String
                            val b64 = entry["data"] as? String

                            if (pluginName != null && subPath != null && b64 != null) {
                                try {
                                    val data = Base64.getDecoder().decode(b64)
                                    val targetFile = File(plugin.dataFolder.parentFile, pluginName + File.separator + subPath)
                                    if (!targetFile.parentFile.exists()) targetFile.parentFile.mkdirs()

                                    FileOutputStream(targetFile).use { fos ->
                                        fos.write(data)
                                    }
                                } catch (e: Exception) {
                                    sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", "Config $pluginName/$subPath"), Placeholder.unparsed("error", e.message ?: "")))
                                }
                            }
                        }
                    }

                    if (installedCount.get() > 0 || yaml.contains("configs")) {
                        sender.sendMessage(plugin.getMessage("status.import-complete"))
                    } else {
                        sender.sendMessage(plugin.getMessage("status.import-empty"))
                    }
                }
            } catch (e: Exception) {
                sender.sendMessage(plugin.getMessage("errors.execution-error", Placeholder.unparsed("arg", e.message ?: "")))
                plugin.logger.log(Level.SEVERE, "Import failed unexpectedly", e)
            }
        })
    }

    private fun importEmbeddedPlugin(sender: CommandSender, filename: String, b64: String, installedCount: AtomicInteger) {
        try {
            if (packageManager.getInstalledPlugins().containsKey(filename)) {
                return
            }
            val data = Base64.getDecoder().decode(b64)
            val targetFile = File(packageManager.getPluginsDir(), filename)
            FileOutputStream(targetFile).use { fos ->
                fos.write(data)
            }
            sender.sendMessage(plugin.getMessage("status.downloaded", Placeholder.unparsed("arg", filename)))
            installedCount.incrementAndGet()
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", filename), Placeholder.unparsed("error", e.message ?: "")))
        }
    }

    private fun importOnePlugin(sender: CommandSender, projectId: String, versionNum: String, installedCount: AtomicInteger) {
        try {
            val verList: JsonArray? = ModrinthAPI.getVersions(projectId, listOf("spigot", "paper", "purpur", "bukkit"))
            var targetVer: JsonObject? = null
            if (verList != null && verList.size() > 0) {
                if (versionNum.equals("latest", ignoreCase = true)) {
                    targetVer = verList[0].asJsonObject
                } else {
                    for (e in verList) {
                        if (e.asJsonObject.get("version_number").asString == versionNum) {
                            targetVer = e.asJsonObject
                            break
                        }
                    }
                }
            }

            if (targetVer == null) {
                sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", "version $versionNum of $projectId")))
                return
            }

            val files = targetVer.getAsJsonArray("files")
            if (files.size() == 0) return

            var primary = files[0].asJsonObject
            for (f in files) {
                if (f.asJsonObject.has("primary") && f.asJsonObject.get("primary").asBoolean) {
                    primary = f.asJsonObject
                    break
                }
            }

            val url = primary.get("url").asString
            val fname = primary.get("filename").asString
            val size = primary.get("size").asLong

            val sha1 = primary.getAsJsonObject("hashes").get("sha1").asString
            if (packageManager.getInstalledPlugins().containsValue(sha1)) {
                return
            }

            sendStatus(sender, plugin.getMessage("status.downloading", Placeholder.unparsed("arg", fname)))
            packageManager.downloadFile(url, packageManager.getPluginsDir(), fname, size, createProgressCallback(sender, fname))
            sendStatus(sender, plugin.getMessage("status.downloaded", Placeholder.unparsed("arg", fname)))
            installedCount.incrementAndGet()
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", projectId), Placeholder.unparsed("error", e.message ?: "")))
        }
    }
}
