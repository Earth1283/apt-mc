package io.github.Earth1283.aptMc.commands.sub;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.api.HangarAPI;
import io.github.Earth1283.aptMc.api.ModrinthAPI;
import io.github.Earth1283.aptMc.commands.SubCommand;
import io.github.Earth1283.aptMc.managers.PackageManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class SearchCommand extends SubCommand {

    public SearchCommand(AptMc plugin, PackageManager packageManager) {
        super(plugin, packageManager);
    }

    @Override
    public void execute(CommandSender sender, List<String> args, boolean dryRun) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("usage.search"));
            return;
        }
        String query = String.join(" ", args);

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Search for " + query)));
            return;
        }
        
        sendStatus(sender, plugin.getMessage("status.search-sorting"));
        sendStatus(sender, plugin.getMessage("status.search-text"));

        List<String> priority = plugin.getConfig().getStringList("source-priority");
        if (priority.isEmpty()) priority = List.of("modrinth", "hangar");

        List<JsonObject> modrinthHits = new ArrayList<>();
        List<JsonObject> hangarHits = new ArrayList<>();

        try {
            for (String source : priority) {
                if (source.equalsIgnoreCase("modrinth")) {
                    JsonArray hits = ModrinthAPI.search(query, 10);
                    for(JsonElement h : hits) modrinthHits.add(h.getAsJsonObject());
                } else if (source.equalsIgnoreCase("hangar") && plugin.getConfig().getBoolean("enable-hangar")) {
                    try {
                        JsonObject hRes = HangarAPI.search(query, 5, 0);
                        if (hRes.has("result")) {
                            JsonArray res = hRes.getAsJsonArray("result");
                            for (JsonElement e : res) {
                                JsonObject obj = e.getAsJsonObject();
                                obj.addProperty("source", "hangar");
                                hangarHits.add(obj);
                            }
                        }
                    } catch (Exception e) {
                        sender.sendMessage(plugin.getMessage("errors.hangar-search-failed", Placeholder.unparsed("error", e.getMessage())));
                    }
                }
            }
            
            if (modrinthHits.isEmpty() && hangarHits.isEmpty()) {
                sender.sendMessage(plugin.getMessage("status.search-empty", Placeholder.unparsed("arg", query)));
                return;
            }
            
            sender.sendMessage(plugin.getMessage("status.search-header"));

            // Display in priority order
            for (String source : priority) {
                if (source.equalsIgnoreCase("modrinth")) {
                    for (JsonObject h : modrinthHits) {
                        String slug = h.get("slug").getAsString();
                        String desc = h.get("description").getAsString();
                        if (desc.length() > 50) desc = desc.substring(0, 50) + "...";
                        String author = h.get("author").getAsString();
                        int downloads = h.get("downloads").getAsInt();
                        
                        sender.sendMessage(plugin.getMessage("status.search-result-modrinth",
                                Placeholder.unparsed("slug", slug),
                                Placeholder.unparsed("desc", desc),
                                Placeholder.unparsed("author", author),
                                Placeholder.unparsed("downloads", String.valueOf(downloads))));
                    }
                } else if (source.equalsIgnoreCase("hangar")) {
                    for (JsonObject h : hangarHits) {
                        String slug = h.get("name").getAsString();
                        String desc = h.has("description") && !h.get("description").isJsonNull() ? h.get("description").getAsString() : "No description";
                        if (desc.length() > 50) desc = desc.substring(0, 50) + "...";
                        String author = h.getAsJsonObject("namespace").get("owner").getAsString();
                        int downloads = h.getAsJsonObject("stats").get("downloads").getAsInt();
                        
                        sender.sendMessage(plugin.getMessage("status.search-result-hangar",
                                Placeholder.unparsed("slug", slug),
                                Placeholder.unparsed("desc", desc),
                                Placeholder.unparsed("author", author),
                                Placeholder.unparsed("downloads", String.valueOf(downloads))));
                    }
                }
            }
        } catch (Exception e) {
             sender.sendMessage(plugin.getMessage("errors.search-failed", Placeholder.unparsed("error", e.getMessage())));
        }
    }

}
