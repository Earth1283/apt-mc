package io.github.Earth1283.aptMc.commands

import com.google.gson.JsonObject
import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.api.ModrinthAPI
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.function.Consumer
import java.util.logging.Level

abstract class SubCommand(
    protected val plugin: AptMc,
    protected val packageManager: PackageManager
) {

    abstract fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean)

    open fun execute(sender: CommandSender, args: List<String>) {
        execute(sender, args, false)
    }

    open fun onTabComplete(sender: CommandSender, args: List<String>): List<String> {
        return emptyList()
    }

    protected fun sendStatus(sender: CommandSender, message: Component) {
        if (plugin.getConfig().getBoolean("use-action-bar") && sender is Player) {
            sender.sendActionBar(message)
        } else {
            sender.sendMessage(message)
        }
    }

    protected fun createProgressCallback(sender: CommandSender, filename: String, messageKey: String = "status.downloading"): Consumer<Double> {
        val lastUpdate = longArrayOf(0)
        val intervalMs = plugin.getConfig().getInt("console-progress-interval", 5) * 1000

        return Consumer { progress ->
            if (sender is Player) {
                if (plugin.getConfig().getBoolean("use-action-bar")) {
                    val percent = (progress * 100).toInt()
                    val bars = percent / 5
                    val bar = StringBuilder("[")
                    for (i in 0 until 20) {
                        if (i < bars) bar.append("=") else bar.append("-")
                    }
                    bar.append("] ").append(percent).append("%")
                    sender.sendActionBar(plugin.getMessage(messageKey, Placeholder.unparsed("arg", "$filename $bar")))
                }
            } else {
                val now = System.currentTimeMillis()
                if (progress >= 1.0 || now - lastUpdate[0] >= intervalMs) {
                    lastUpdate[0] = now
                    val percent = (progress * 100).toInt()
                    sender.sendMessage(plugin.getMessage(messageKey, Placeholder.unparsed("arg", "$filename... $percent%")))
                }
            }
        }
    }

    protected fun resolveVersions(hashes: List<String>): Map<String, JsonObject> {
        val results = HashMap<String, JsonObject>()
        val missingHashes = ArrayList<String>()

        for (sha1 in hashes) {
            val cached = packageManager.getCachedInfo(sha1)
            if (cached != null) {
                results[sha1] = cached
            } else {
                missingHashes.add(sha1)
            }
        }

        if (missingHashes.isNotEmpty()) {
            try {
                val fromApi = ModrinthAPI.getVersionsByHashes(missingHashes)
                val newCacheEntries = HashMap<String, JsonObject>()
                for (hash in fromApi.keySet()) {
                    val value = fromApi.getAsJsonObject(hash)
                    results[hash] = value
                    newCacheEntries[hash] = value
                }
                packageManager.updateCache(newCacheEntries)
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to resolve version hashes from Modrinth", e)
            }
        }
        return results
    }
}
