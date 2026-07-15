package io.github.Earth1283.aptMc.commands.sub

import com.google.gson.JsonObject
import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.api.ModrinthAPI
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender

class UpdateCommand(plugin: AptMc, packageManager: PackageManager) : SubCommand(plugin, packageManager) {

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Update package lists")))
            return
        }
        sendStatus(sender, plugin.getMessage("status.update-hit", Placeholder.unparsed("arg1", "1"), Placeholder.unparsed("arg2", "https://api.modrinth.com/v2/search stable")))
        try { Thread.sleep(200) } catch (ignored: InterruptedException) {}

        sendStatus(sender, plugin.getMessage("status.update-get", Placeholder.unparsed("arg1", "2"), Placeholder.unparsed("arg2", "https://api.modrinth.com/v2/search plugins-updates"), Placeholder.unparsed("arg3", "42.0 kB")))
        try { Thread.sleep(300) } catch (ignored: InterruptedException) {}

        sendStatus(sender, plugin.getMessage("status.update-hit", Placeholder.unparsed("arg1", "3"), Placeholder.unparsed("arg2", "https://api.modrinth.com/v2/search spigot")))
        try { Thread.sleep(150) } catch (ignored: InterruptedException) {}

        sendStatus(sender, plugin.getMessage("status.update-hit", Placeholder.unparsed("arg1", "4"), Placeholder.unparsed("arg2", "https://api.modrinth.com/v2/search paper")))
        try { Thread.sleep(150) } catch (ignored: InterruptedException) {}

        if (plugin.getConfig().getBoolean("enable-hangar")) {
            sendStatus(sender, plugin.getMessage("status.update-hit", Placeholder.unparsed("arg1", "5"), Placeholder.unparsed("arg2", "https://hangar.papermc.io/api/v1/projects")))
            try { Thread.sleep(200) } catch (ignored: InterruptedException) {}
        }

        var updateFound = false
        var latestVersionObj: JsonObject? = null

        try {
            val hitNum = if (plugin.getConfig().getBoolean("enable-hangar")) 6 else 5
            sendStatus(sender, plugin.getMessage("status.update-hit", Placeholder.unparsed("arg1", hitNum.toString()), Placeholder.unparsed("arg2", "https://api.modrinth.com/v2/project/apt-mc stable")))

            val project = ModrinthAPI.getProject("apt-mc")
            if (project != null) {
                val projectId = project.get("id").asString
                val versions = ModrinthAPI.getVersions(projectId, listOf("spigot", "paper", "purpur", "bukkit"))

                if (versions.size() > 0) {
                    val latest = versions[0].asJsonObject
                    val latestVer = latest.get("version_number").asString
                    val currentVer = plugin.description.version

                    if (!latestVer.equals(currentVer, ignoreCase = true)) {
                        updateFound = true
                        latestVersionObj = latest
                    }
                }
            }
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessage("errors.self-update-check-failed", Placeholder.unparsed("error", e.message ?: "")))
        }

        sendStatus(sender, plugin.getMessage("status.update-fetched", Placeholder.unparsed("arg1", "42.0 kB"), Placeholder.unparsed("arg2", "51.2 kB")))

        sendStatus(sender, plugin.getMessage("status.update-reading"))
        sendStatus(sender, plugin.getMessage("status.update-building"))
        sendStatus(sender, plugin.getMessage("status.update-state"))

        if (updateFound && latestVersionObj != null) {
            val latestVer = latestVersionObj.get("version_number").asString
            val currentVer = plugin.description.version
            sendStatus(sender, plugin.getMessage("status.self-update-found", Placeholder.unparsed("arg1", latestVer), Placeholder.unparsed("arg2", currentVer)))

            val files = latestVersionObj.getAsJsonArray("files")
            if (files.size() > 0) {
                var primaryFile = files[0].asJsonObject
                for (f in files) {
                    if (f.asJsonObject.has("primary") && f.asJsonObject.get("primary").asBoolean) {
                        primaryFile = f.asJsonObject
                        break
                    }
                }

                try {
                    val url = primaryFile.get("url").asString
                    val filename = primaryFile.get("filename").asString
                    val size = primaryFile.get("size").asLong

                    sendStatus(sender, plugin.getMessage("status.self-update-downloading"))
                    packageManager.downloadFile(url, packageManager.getUpdateDir(), filename, size, createProgressCallback(sender, filename))
                    sendStatus(sender, plugin.getMessage("status.downloaded", Placeholder.unparsed("arg", "Update")))
                    return
                } catch (e: Exception) {
                    sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", "apt-mc"), Placeholder.unparsed("error", e.message ?: "")))
                }
            }
        }

        sender.sendMessage(plugin.getMessage("status.up-to-date"))
    }
}
