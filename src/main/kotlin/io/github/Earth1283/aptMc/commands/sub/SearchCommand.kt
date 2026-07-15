package io.github.Earth1283.aptMc.commands.sub

import com.google.gson.JsonObject
import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.api.HangarAPI
import io.github.Earth1283.aptMc.api.ModrinthAPI
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender

class SearchCommand(plugin: AptMc, packageManager: PackageManager) : SubCommand(plugin, packageManager) {

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("usage.search"))
            return
        }
        val query = args.joinToString(" ")

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Search for $query")))
            return
        }

        sendStatus(sender, plugin.getMessage("status.search-sorting"))
        sendStatus(sender, plugin.getMessage("status.search-text"))

        var priority = plugin.getConfig().getStringList("source-priority")
        if (priority.isEmpty()) priority = listOf("modrinth", "hangar")

        val modrinthHits = ArrayList<JsonObject>()
        val hangarHits = ArrayList<JsonObject>()

        try {
            for (source in priority) {
                if (source.equals("modrinth", ignoreCase = true)) {
                    val hits = ModrinthAPI.search(query, 10)
                    for (h in hits) modrinthHits.add(h.asJsonObject)
                } else if (source.equals("hangar", ignoreCase = true) && plugin.getConfig().getBoolean("enable-hangar")) {
                    try {
                        val hRes = HangarAPI.search(query, 5, 0)
                        if (hRes.has("result")) {
                            val res = hRes.getAsJsonArray("result")
                            for (e in res) {
                                val obj = e.asJsonObject
                                obj.addProperty("source", "hangar")
                                hangarHits.add(obj)
                            }
                        }
                    } catch (e: Exception) {
                        sender.sendMessage(plugin.getMessage("errors.hangar-search-failed", Placeholder.unparsed("error", e.message ?: "")))
                    }
                }
            }

            if (modrinthHits.isEmpty() && hangarHits.isEmpty()) {
                sender.sendMessage(plugin.getMessage("status.search-empty", Placeholder.unparsed("arg", query)))
                return
            }

            sender.sendMessage(plugin.getMessage("status.search-header"))

            for (source in priority) {
                if (source.equals("modrinth", ignoreCase = true)) {
                    for (h in modrinthHits) {
                        val slug = h.get("slug").asString
                        var desc = h.get("description").asString
                        if (desc.length > 50) desc = desc.substring(0, 50) + "..."
                        val author = h.get("author").asString
                        val downloads = h.get("downloads").asInt

                        sender.sendMessage(plugin.getMessage("status.search-result-modrinth",
                            Placeholder.unparsed("slug", slug),
                            Placeholder.unparsed("desc", desc),
                            Placeholder.unparsed("author", author),
                            Placeholder.unparsed("downloads", downloads.toString())))
                    }
                } else if (source.equals("hangar", ignoreCase = true)) {
                    for (h in hangarHits) {
                        val slug = h.get("name").asString
                        var desc = if (h.has("description") && !h.get("description").isJsonNull) h.get("description").asString else "No description"
                        if (desc.length > 50) desc = desc.substring(0, 50) + "..."
                        val author = h.getAsJsonObject("namespace").get("owner").asString
                        val downloads = h.getAsJsonObject("stats").get("downloads").asInt

                        sender.sendMessage(plugin.getMessage("status.search-result-hangar",
                            Placeholder.unparsed("slug", slug),
                            Placeholder.unparsed("desc", desc),
                            Placeholder.unparsed("author", author),
                            Placeholder.unparsed("downloads", downloads.toString())))
                    }
                }
            }
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessage("errors.search-failed", Placeholder.unparsed("error", e.message ?: "")))
        }
    }
}
