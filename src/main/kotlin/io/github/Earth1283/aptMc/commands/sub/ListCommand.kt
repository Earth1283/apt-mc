package io.github.Earth1283.aptMc.commands.sub

import com.google.gson.JsonObject
import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.api.ModrinthAPI
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import java.util.logging.Level

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
            val projectNames = resolveProjectNames(versionsMap.values)

            for ((filename, sha1) in installedPlugins) {
                val version = versionsMap[sha1]
                val pkgName = pluginDisplayName(filename, version, projectNames)
                var verNum = "unknown"

                if (version != null) {
                    verNum = version.get("version_number").asString
                }

                sender.sendMessage(plugin.getMessage("status.list-item", Placeholder.unparsed("arg1", pkgName), Placeholder.unparsed("arg2", verNum)))
            }
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", "list"), Placeholder.unparsed("error", e.message ?: "")))
        }
    }

    private fun resolveProjectNames(versions: Collection<JsonObject>): Map<String, String> {
        val projectIds = versions.mapNotNull { version ->
            version.get("project_id")?.takeUnless { it.isJsonNull }?.asString
        }.distinct()

        if (projectIds.isEmpty()) return emptyMap()

        return try {
            ModrinthAPI.getProjects(projectIds).associate { project ->
                val projectObject = project.asJsonObject
                projectObject.get("id").asString to projectObject.get("title").asString
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to resolve project names from Modrinth", e)
            emptyMap()
        }
    }
}

internal fun pluginDisplayName(
    filename: String,
    version: JsonObject?,
    projectNames: Map<String, String>
): String {
    if (version == null) return filename
    val projectId = version.get("project_id")?.takeUnless { it.isJsonNull }?.asString ?: return filename
    return projectNames[projectId] ?: filename
}
