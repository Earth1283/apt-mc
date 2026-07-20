package io.github.Earth1283.aptMc.commands.sub

import com.google.gson.JsonObject
import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportCommand(plugin: AptMc, packageManager: PackageManager) : SubCommand(plugin, packageManager) {

    private val flags = listOf("--include-all", "--compress", "--no-configs")

    companion object {
        private const val LARGE_FILE_THRESHOLD = 25L * 1024 * 1024
    }

    override fun onTabComplete(sender: CommandSender, args: List<String>): List<String> {
        val current = args.lastOrNull() ?: ""
        return flags.filter { it.startsWith(current, ignoreCase = true) && !args.dropLast(1).contains(it) }
    }

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        val remaining = ArrayList(args)
        val includeAll = remaining.removeIf { it.equals("--include-all", ignoreCase = true) }
        val compress = remaining.removeIf { it.equals("--compress", ignoreCase = true) }
        val noConfigs = remaining.removeIf { it.equals("--no-configs", ignoreCase = true) }

        val rawName = if (remaining.isEmpty()) "apt-manifest" else remaining[0]
        val baseName = rawName.removeSuffix(".yml").removeSuffix(".zip")
        val outputFile = File(plugin.dataFolder, baseName + if (compress) ".zip" else ".yml")

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Export to " + outputFile.name)))
            return
        }

        sendStatus(sender, plugin.getMessage("status.exporting", Placeholder.unparsed("arg", outputFile.name)))

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val installed = packageManager.getInstalledPlugins()
            if (installed.isEmpty()) {
                sender.sendMessage(plugin.getMessage("status.export-empty"))
                return@Runnable
            }

            val versions: Map<String, JsonObject>
            try {
                versions = resolveVersions(ArrayList(installed.values))
            } catch (e: Exception) {
                sender.sendMessage(plugin.getMessage("status.export-failed", Placeholder.unparsed("error", "Failed to resolve versions: " + (e.message ?: ""))))
                return@Runnable
            }

            val yaml = YamlConfiguration()

            yaml.set("project-details.title", "Server Export")
            yaml.set("project-details.author", sender.name)
            yaml.set("project-details.created", System.currentTimeMillis())

            val pluginList = ArrayList<Map<String, String>>()
            val unrecognisedList = ArrayList<String>()
            var count = 0

            for ((pluginFilename, sha1) in installed) {
                val v = versions[sha1]
                if (v != null) {
                    val projectId = v.get("project_id").asString
                    val verNum = v.get("version_number").asString

                    val entryMap = LinkedHashMap<String, String>()
                    entryMap["file"] = pluginFilename
                    entryMap["source"] = "modrinth:$projectId/$verNum"
                    pluginList.add(entryMap)
                    count++
                } else if (includeAll) {
                    try {
                        val jarFile = File(packageManager.getPluginsDir(), pluginFilename)
                        val b64 = Base64.getEncoder().encodeToString(jarFile.readBytes())

                        val entryMap = LinkedHashMap<String, String>()
                        entryMap["file"] = pluginFilename
                        entryMap["source"] = "embedded"
                        entryMap["data"] = b64
                        pluginList.add(entryMap)
                        count++
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to embed unrecognised plugin: $pluginFilename")
                        unrecognisedList.add(pluginFilename)
                    }
                } else {
                    unrecognisedList.add(pluginFilename)
                }
            }
            yaml.set("plugins", pluginList)
            if (unrecognisedList.isNotEmpty()) {
                yaml.set("unrecognised", unrecognisedList)
            }

            if (!noConfigs) {
                sendStatus(sender, plugin.getMessage("status.exporting-configs"))
                val configsMap = HashMap<String, MutableMap<String, String>>()
                val pluginsDir = plugin.dataFolder.parentFile

                val totalFiles = countFilesRecursive(pluginsDir)
                val processedFiles = AtomicInteger(0)
                val progressCallback = createProgressCallback(sender, "Bundling configurations", "status.config-progress")

                exportConfigsRecursive(pluginsDir, pluginsDir, configsMap, processedFiles, totalFiles, progressCallback)
                progressCallback.accept(1.0)

                val configExportList = ArrayList<Map<String, String>>()
                for ((pluginName, fileMap) in configsMap) {
                    for ((path, data) in fileMap) {
                        val cMap = LinkedHashMap<String, String>()
                        cMap["plugin"] = pluginName
                        cMap["path"] = path
                        cMap["data"] = data
                        configExportList.add(cMap)
                    }
                }
                yaml.set("configs", configExportList)
            }

            try {
                if (compress) {
                    val yamlName = baseName + ".yml"
                    ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                        zos.putNextEntry(ZipEntry(yamlName))
                        zos.write(yaml.saveToString().toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                } else {
                    yaml.save(outputFile)
                }
                sender.sendMessage(plugin.getMessage("status.export-success", Placeholder.unparsed("arg1", count.toString()), Placeholder.unparsed("arg2", outputFile.path)))
                if (unrecognisedList.isNotEmpty()) {
                    sender.sendMessage(plugin.getMessage("status.export-unrecognised-warning", Placeholder.unparsed("arg", unrecognisedList.size.toString())))
                }
                if (!compress && outputFile.length() > LARGE_FILE_THRESHOLD) {
                    sender.sendMessage(plugin.getMessage("status.export-large-file-warning"))
                }
            } catch (e: IOException) {
                sender.sendMessage(plugin.getMessage("status.export-failed", Placeholder.unparsed("error", e.message ?: "")))
            }
        })
    }

    private fun countFilesRecursive(dir: File): Int {
        var count = 0
        val files = dir.listFiles() ?: return 0

        for (f in files) {
            if (f.isDirectory) {
                if (f == plugin.dataFolder) continue
                count += countFilesRecursive(f)
            } else {
                if (shouldExport(f)) {
                    count++
                }
            }
        }
        return count
    }

    private fun exportConfigsRecursive(
        dir: File,
        root: File,
        configs: MutableMap<String, MutableMap<String, String>>,
        processed: AtomicInteger,
        total: Int,
        callback: Consumer<Double>
    ) {
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.isDirectory) {
                if (f == plugin.dataFolder) continue
                exportConfigsRecursive(f, root, configs, processed, total, callback)
            } else {
                if (shouldExport(f)) {
                    try {
                        val content = ByteArray(f.length().toInt())
                        FileInputStream(f).use { fis ->
                            fis.read(content)
                        }
                        val b64 = Base64.getEncoder().encodeToString(content)

                        val relPath = f.path.substring(root.path.length + 1)
                        val firstSlash = relPath.indexOf(File.separatorChar)
                        if (firstSlash != -1) {
                            val pluginName = relPath.substring(0, firstSlash)
                            val subPath = relPath.substring(firstSlash + 1)

                            configs.computeIfAbsent(pluginName) { HashMap() }[subPath] = b64
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to export config: " + f.name)
                    }

                    val p = processed.incrementAndGet()
                    if (total > 0 && p % 5 == 0) {
                        callback.accept(p.toDouble() / total)
                    }
                }
            }
        }
    }

    private fun shouldExport(f: File): Boolean {
        val config = plugin.getConfig()
        val mode = config.getString("export.filter-mode", "blacklist")
        val extensions = config.getStringList("export.extensions")
        val name = f.name
        var ext = ""
        val i = name.lastIndexOf('.')
        if (i > 0) ext = name.substring(i + 1)

        return if (mode.equals("whitelist", ignoreCase = true)) {
            extensions.contains(ext)
        } else {
            !extensions.contains(ext)
        }
    }
}
