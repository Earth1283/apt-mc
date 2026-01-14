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

import java.util.List;

public class UpdateCommand extends SubCommand {

    public UpdateCommand(AptMc plugin, PackageManager packageManager) {
        super(plugin, packageManager);
    }

    @Override
    public void execute(CommandSender sender, List<String> args) {
        sendStatus(sender, plugin.getMessage("status.update-hit", Placeholder.unparsed("arg1", "1"), Placeholder.unparsed("arg2", "https://api.modrinth.com/v2/search stable")));
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        
        sendStatus(sender, plugin.getMessage("status.update-get", Placeholder.unparsed("arg1", "2"), Placeholder.unparsed("arg2", "https://api.modrinth.com/v2/search plugins-updates"), Placeholder.unparsed("arg3", "42.0 kB")));
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        
        sendStatus(sender, plugin.getMessage("status.update-hit", Placeholder.unparsed("arg1", "3"), Placeholder.unparsed("arg2", "https://api.modrinth.com/v2/search spigot")));
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
        
        sendStatus(sender, plugin.getMessage("status.update-hit", Placeholder.unparsed("arg1", "4"), Placeholder.unparsed("arg2", "https://api.modrinth.com/v2/search paper")));
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}

        if (plugin.getConfig().getBoolean("enable-hangar")) {
            sendStatus(sender, plugin.getMessage("status.update-hit", Placeholder.unparsed("arg1", "5"), Placeholder.unparsed("arg2", "https://hangar.papermc.io/api/v1/projects")));
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }

        boolean updateFound = false;
        JsonObject latestVersionObj = null;

        try {
            int hitNum = plugin.getConfig().getBoolean("enable-hangar") ? 6 : 5;
            sendStatus(sender, plugin.getMessage("status.update-hit", Placeholder.unparsed("arg1", String.valueOf(hitNum)), Placeholder.unparsed("arg2", "https://api.modrinth.com/v2/project/apt-mc stable")));
            
            JsonObject project = ModrinthAPI.getProject("apt-mc");
            if (project != null) {
                String projectId = project.get("id").getAsString();
                JsonArray versions = ModrinthAPI.getVersions(projectId, List.of("spigot", "paper", "purpur", "bukkit"));
                
                if (versions.size() > 0) {
                    JsonObject latest = versions.get(0).getAsJsonObject();
                    String latestVer = latest.get("version_number").getAsString();
                    String currentVer = plugin.getDescription().getVersion();

                    if (!latestVer.equalsIgnoreCase(currentVer)) {
                        updateFound = true;
                        latestVersionObj = latest;
                    }
                }
            }
        } catch (Exception e) {
             sender.sendMessage(plugin.getMessage("errors.self-update-check-failed", Placeholder.unparsed("error", e.getMessage())));
        }
        
        sendStatus(sender, plugin.getMessage("status.update-fetched", Placeholder.unparsed("arg1", "42.0 kB"), Placeholder.unparsed("arg2", "51.2 kB")));
        
        sendStatus(sender, plugin.getMessage("status.update-reading"));
        sendStatus(sender, plugin.getMessage("status.update-building"));
        sendStatus(sender, plugin.getMessage("status.update-state"));

        if (updateFound && latestVersionObj != null) {
            String latestVer = latestVersionObj.get("version_number").getAsString();
            String currentVer = plugin.getDescription().getVersion();
            // Using placeholder for self update message
            sendStatus(sender, plugin.getMessage("status.self-update-found", Placeholder.unparsed("arg1", latestVer), Placeholder.unparsed("arg2", currentVer)));
            
            JsonArray files = latestVersionObj.getAsJsonArray("files");
            if (files.size() > 0) {
                JsonObject primaryFile = files.get(0).getAsJsonObject();
                for(JsonElement f : files) {
                    if (f.getAsJsonObject().has("primary") && f.getAsJsonObject().get("primary").getAsBoolean()) {
                        primaryFile = f.getAsJsonObject();
                        break;
                    }
                }
                
                try {
                    String url = primaryFile.get("url").getAsString();
                    String filename = primaryFile.get("filename").getAsString();
                    long size = primaryFile.get("size").getAsLong();
                    
                    sendStatus(sender, plugin.getMessage("status.self-update-downloading"));
                    packageManager.downloadFile(url, packageManager.getUpdateDir(), filename, size, createProgressCallback(sender, filename));
                    sendStatus(sender, plugin.getMessage("status.downloaded", Placeholder.unparsed("arg", "Update")));
                    return;
                } catch (Exception e) {
                    sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", "apt-mc"), Placeholder.unparsed("error", e.getMessage())));
                }
            }
        }

        sender.sendMessage(plugin.getMessage("status.up-to-date"));
    }
}
