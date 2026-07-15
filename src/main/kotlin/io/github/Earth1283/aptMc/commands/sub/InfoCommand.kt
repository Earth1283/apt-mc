package io.github.Earth1283.aptMc.commands.sub

import com.google.gson.JsonObject
import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.api.ModrinthAPI
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender

class InfoCommand(plugin: AptMc, packageManager: PackageManager) : SubCommand(plugin, packageManager) {

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("usage.info"))
            return
        }
        val pkg = args[0]

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Show info for $pkg")))
            return
        }

        try {
            val project = ModrinthAPI.getProject(pkg)
            if (project == null) {
                sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", pkg)))
                return
            }

            val installedPlugins = packageManager.getInstalledPlugins()
            var statusKey = "status.not-installed"

            if (installedPlugins.isNotEmpty()) {
                val hashes = ArrayList(installedPlugins.values)
                val installedVersions = resolveVersions(hashes)

                for (v in installedVersions.values) {
                    if (v.get("project_id").asString == project.get("id").asString) {
                        statusKey = "status.installed"
                        break
                    }
                }
            }
            val statusComp = plugin.getMessage(statusKey)

            var dependencies = "None"
            try {
                val versions = ModrinthAPI.getVersions(project.get("id").asString, listOf("spigot", "paper", "purpur", "bukkit"))
                if (versions.size() > 0) {
                    val latest = versions[0].asJsonObject
                    val deps = latest.getAsJsonArray("dependencies")
                    if (deps != null && deps.size() > 0) {
                        val depNames = ArrayList<String>()
                        for (d in deps) {
                            val depObj = d.asJsonObject
                            if (depObj.has("dependency_type") && depObj.get("dependency_type").asString == "required") {
                                if (depObj.has("project_id") && !depObj.get("project_id").isJsonNull) {
                                    depNames.add(depObj.get("project_id").asString)
                                }
                            }
                        }
                        if (depNames.isNotEmpty()) dependencies = depNames.joinToString(", ")
                    }
                }
            } catch (ignored: Exception) {
            }

            sender.sendMessage(plugin.getMessage("status.package-info.name", Placeholder.unparsed("arg", project.get("slug").asString)))
            sender.sendMessage(plugin.getMessage("status.package-info.id", Placeholder.unparsed("arg", project.get("id").asString)))
            sender.sendMessage(plugin.getMessage("status.package-info.status", Placeholder.component("arg", statusComp)))
            sender.sendMessage(plugin.getMessage("status.package-info.description", Placeholder.unparsed("arg", project.get("description").asString)))
            sender.sendMessage(plugin.getMessage("status.package-info.downloads", Placeholder.unparsed("arg", project.get("downloads").asInt.toString())))
            sender.sendMessage(plugin.getMessage("status.package-info.dependencies", Placeholder.unparsed("arg", dependencies)))
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", pkg), Placeholder.unparsed("error", e.message ?: "")))
        }
    }
}
