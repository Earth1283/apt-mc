package io.github.Earth1283.aptMc.commands.sub

import com.google.gson.JsonObject
import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender

/**
 * Pins (holds) an installed plugin at its current version so `apt upgrade` skips it,
 * mirroring `apt-mark hold`. Pins are keyed by Modrinth project_id, resolved from the
 * installed jar's sha1 via the version cache.
 */
class PinCommand(plugin: AptMc, packageManager: PackageManager) : SubCommand(plugin, packageManager) {

    private val flags = listOf("--list", "--unpin")

    override fun onTabComplete(sender: CommandSender, args: List<String>): List<String> {
        val current = args.lastOrNull() ?: ""
        return flags.filter { it.startsWith(current, ignoreCase = true) }
    }

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("usage.pin"))
            return
        }

        if (args[0].equals("--list", ignoreCase = true)) {
            listPinned(sender)
            return
        }

        val unpin = args[0].equals("--unpin", ignoreCase = true)
        val query = if (unpin) args.getOrNull(1) else args[0]
        if (query == null) {
            sender.sendMessage(plugin.getMessage("usage.pin"))
            return
        }

        val installed = packageManager.getInstalledPlugins()
        val matches = installed.filterKeys { it.lowercase().contains(query.lowercase()) }

        if (matches.isEmpty()) {
            sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", query)))
            return
        }
        if (matches.size > 1) {
            sender.sendMessage(plugin.getMessage("errors.execution-error", Placeholder.unparsed("arg", "Multiple candidates found for $query")))
            return
        }

        val (filename, sha1) = matches.entries.first()

        try {
            val version = resolveVersions(listOf(sha1))[sha1]
            if (version == null) {
                sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", "$filename (not resolvable via Modrinth)")))
                return
            }
            val projectId = version.get("project_id").asString
            val versionNumber = version.get("version_number").asString

            if (unpin) {
                if (dryRun) {
                    sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Unpin $filename")))
                    return
                }
                if (packageManager.unpin(projectId)) {
                    sender.sendMessage(plugin.getMessage("status.unpinned", Placeholder.unparsed("arg", filename)))
                } else {
                    sender.sendMessage(plugin.getMessage("status.not-pinned", Placeholder.unparsed("arg", filename)))
                }
                return
            }

            if (dryRun) {
                sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Pin $filename at $versionNumber")))
                return
            }

            val info = JsonObject()
            info.addProperty("filename", filename)
            info.addProperty("version_number", versionNumber)
            packageManager.pin(projectId, info)
            sender.sendMessage(plugin.getMessage("status.pinned", Placeholder.unparsed("arg1", filename), Placeholder.unparsed("arg2", versionNumber)))
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", filename), Placeholder.unparsed("error", e.message ?: "")))
        }
    }

    private fun listPinned(sender: CommandSender) {
        val pinned = packageManager.getPinned()
        if (pinned.isEmpty()) {
            sender.sendMessage(plugin.getMessage("status.pin-list-empty"))
            return
        }
        sender.sendMessage(plugin.getMessage("status.pin-list-header"))
        for (info in pinned.values) {
            val filename = if (info.has("filename")) info.get("filename").asString else "unknown"
            val version = if (info.has("version_number")) info.get("version_number").asString else "unknown"
            sender.sendMessage(plugin.getMessage("status.pin-list-item", Placeholder.unparsed("arg1", filename), Placeholder.unparsed("arg2", version)))
        }
    }
}
