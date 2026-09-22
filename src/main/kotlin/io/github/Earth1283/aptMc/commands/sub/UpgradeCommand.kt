package io.github.Earth1283.aptMc.commands.sub

import com.google.gson.JsonObject
import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.api.ModrinthAPI
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.managers.AsyncExecutor
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import java.util.Collections
import java.util.concurrent.CompletableFuture

class UpgradeCommand(plugin: AptMc, packageManager: PackageManager) : SubCommand(plugin, packageManager) {

    private class UpgradeInfo(
        val filename: String,
        val projectId: String,
        val currentVersion: String,
        val newVersion: String,
        val latestObj: JsonObject
    )

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        sendStatus(sender, plugin.getMessage("status.update-reading"))
        sendStatus(sender, plugin.getMessage("status.update-building"))
        sendStatus(sender, plugin.getMessage("status.update-state"))
        sendStatus(sender, plugin.getMessage("status.calculating-upgrades"))

        val installed = packageManager.getInstalledPlugins()
        if (installed.isEmpty()) {
            sender.sendMessage(plugin.getMessage("status.done"))
            sender.sendMessage(plugin.getMessage("status.upgrade-summary-empty"))
            return
        }

        try {
            val versionsMap = resolveVersions(ArrayList(installed.values))
            val updates: MutableList<UpgradeInfo> = Collections.synchronizedList(ArrayList())

            val checkFutures = ArrayList<CompletableFuture<Void>>()
            var heldBack = 0

            for ((filename, sha1) in installed) {
                val currentVersionInfo = versionsMap[sha1]
                if (currentVersionInfo != null) {
                    val projectId = currentVersionInfo.get("project_id").asString
                    val currentVerId = currentVersionInfo.get("id").asString

                    if (packageManager.isPinned(projectId)) {
                        heldBack++
                        continue
                    }

                    checkFutures.add(CompletableFuture.runAsync({
                        try {
                            val availableVersions = ModrinthAPI.getVersions(projectId, listOf("spigot", "paper", "purpur", "bukkit"))
                            if (availableVersions.size() > 0) {
                                val latest = availableVersions[0].asJsonObject
                                if (latest.get("id").asString != currentVerId) {
                                    updates.add(UpgradeInfo(filename, projectId, currentVersionInfo.get("version_number").asString, latest.get("version_number").asString, latest))
                                }
                            }
                        } catch (ignored: Exception) {
                        }
                    }, AsyncExecutor.POOL))
                }
            }

            CompletableFuture.allOf(*checkFutures.toTypedArray()).join()

            sender.sendMessage(plugin.getMessage("status.done"))

            if (updates.isEmpty()) {
                sender.sendMessage(plugin.getMessage("status.upgrade-summary-empty", Placeholder.unparsed("arg", heldBack.toString())))
                return
            }

            if (dryRun) {
                sender.sendMessage(plugin.getMessage("status.dry-run-header"))
                for (up in updates) {
                    sender.sendMessage(plugin.getMessage("status.upgrade-list-item", Placeholder.unparsed("arg1", up.filename), Placeholder.unparsed("arg2", up.currentVersion), Placeholder.unparsed("arg3", up.newVersion)))
                }
                sender.sendMessage(plugin.getMessage("status.dry-run-complete"))
                return
            }

            sender.sendMessage(plugin.getMessage("status.upgrade-list-header"))
            for (up in updates) {
                sender.sendMessage(plugin.getMessage("status.upgrade-list-item", Placeholder.unparsed("arg1", up.filename), Placeholder.unparsed("arg2", up.currentVersion), Placeholder.unparsed("arg3", up.newVersion)))
            }

            sender.sendMessage(plugin.getMessage("status.upgrade-summary", Placeholder.unparsed("arg1", updates.size.toString()), Placeholder.unparsed("arg2", heldBack.toString())))
            sender.sendMessage(plugin.getMessage("status.upgrading"))

            val upgradeFutures = updates.map { up ->
                CompletableFuture.runAsync(Runnable {
                    val files = up.latestObj.getAsJsonArray("files")
                    if (files.size() == 0) return@Runnable

                    var primaryFile = files[0].asJsonObject
                    for (f in files) {
                        if (f.asJsonObject.has("primary") && f.asJsonObject.get("primary").asBoolean) {
                            primaryFile = f.asJsonObject
                            break
                        }
                    }

                    try {
                        val sha1 = ModrinthAPI.getSha1(primaryFile)
                        packageManager.downloadFile(primaryFile.get("url").asString, packageManager.getUpdateDir(), primaryFile.get("filename").asString, primaryFile.get("size").asLong, createProgressCallback(sender, up.filename), sha1)
                        sendStatus(sender, plugin.getMessage("status.upgraded", Placeholder.unparsed("arg", up.filename)))
                    } catch (e: Exception) {
                        sender.sendMessage(plugin.getMessage("errors.upgrade-failed", Placeholder.unparsed("arg", up.filename), Placeholder.unparsed("error", e.message ?: "")))
                    }
                }, AsyncExecutor.POOL)
            }

            CompletableFuture.allOf(*upgradeFutures.toTypedArray()).join()
            sender.sendMessage(plugin.getMessage("status.upgrade-complete"))
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", "upgrades"), Placeholder.unparsed("error", e.message ?: "")))
        }
    }
}
