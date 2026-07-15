package io.github.Earth1283.aptMc.commands.sub

import com.google.gson.JsonObject
import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender

class ListCommand(plugin: AptMc, packageManager: PackageManager) : SubCommand(plugin, packageManager) {

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        packageManager.ensureDir()
        val installedPlugins = packageManager.getInstalledPlugins()

        if (installedPlugins.isEmpty()) {
            sender.sendMessage(plugin.getMessage("status.list-empty"))
            return
        }

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "List installed plugins")))
            return
        }

        sender.sendMessage(plugin.getMessage("status.listing"))

        try {
            val versionsMap: Map<String, JsonObject> = resolveVersions(ArrayList(installedPlugins.values))

            for ((filename, sha1) in installedPlugins) {
                var pkgName = filename
                var verNum = "unknown"

                val v = versionsMap[sha1]
                if (v != null) {
                    pkgName = v.get("project_id").asString
                    verNum = v.get("version_number").asString
                }

                sender.sendMessage(plugin.getMessage("status.list-item", Placeholder.unparsed("arg1", pkgName), Placeholder.unparsed("arg2", verNum)))
            }
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", "list"), Placeholder.unparsed("error", e.message ?: "")))
        }
    }
}
