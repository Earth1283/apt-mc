package io.github.Earth1283.aptMc.commands.sub;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.api.ModrinthAPI;
import io.github.Earth1283.aptMc.commands.SubCommand;
import io.github.Earth1283.aptMc.managers.PackageManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class UpgradeCommand extends SubCommand {

    public UpgradeCommand(AptMc plugin, PackageManager packageManager) {
        super(plugin, packageManager);
    }

    @Override
    public void execute(CommandSender sender, List<String> args, boolean dryRun) {
        sendStatus(sender, plugin.getMessage("status.update-reading"));
        sendStatus(sender, plugin.getMessage("status.update-building"));
        sendStatus(sender, plugin.getMessage("status.update-state"));
        sendStatus(sender, plugin.getMessage("status.calculating-upgrades"));

        Map<String, String> installed = packageManager.getInstalledPlugins();
        if (installed.isEmpty()) {
            sender.sendMessage(plugin.getMessage("status.done"));
            sender.sendMessage(plugin.getMessage("status.upgrade-summary-empty"));
            return;
        }

        try {
            Map<String, JsonObject> versionsMap = resolveVersions(new ArrayList<>(installed.values()));
            List<UpgradeInfo> updates = Collections.synchronizedList(new ArrayList<>());
            
            List<CompletableFuture<Void>> checkFutures = new ArrayList<>();

            for (Map.Entry<String, String> entry : installed.entrySet()) {
                String filename = entry.getKey();
                String sha1 = entry.getValue();
                
                if (versionsMap.containsKey(sha1)) {
                    JsonObject currentVersionInfo = versionsMap.get(sha1);
                    String projectId = currentVersionInfo.get("project_id").getAsString();
                    String currentVerId = currentVersionInfo.get("id").getAsString();

                    checkFutures.add(CompletableFuture.runAsync(() -> {
                        try {
                            JsonArray availableVersions = ModrinthAPI.getVersions(projectId, List.of("spigot", "paper", "purpur", "bukkit"));
                            if (availableVersions.size() > 0) {
                                JsonObject latest = availableVersions.get(0).getAsJsonObject();
                                if (!latest.get("id").getAsString().equals(currentVerId)) {
                                    updates.add(new UpgradeInfo(filename, projectId, currentVersionInfo.get("version_number").getAsString(), latest.get("version_number").getAsString(), latest));
                                }
                            }
                        } catch (Exception ignored) {}
                    }));
                }
            }
            
            CompletableFuture.allOf(checkFutures.toArray(new CompletableFuture[0])).join();
            
            sender.sendMessage(plugin.getMessage("status.done"));
            
            if (updates.isEmpty()) {
                sender.sendMessage(plugin.getMessage("status.upgrade-summary-empty"));
                return;
            }

            if (dryRun) {
                sender.sendMessage(plugin.getMessage("status.dry-run-header"));
                for (UpgradeInfo up : updates) {
                    sender.sendMessage(plugin.getMessage("status.upgrade-list-item", Placeholder.unparsed("arg1", up.filename), Placeholder.unparsed("arg2", up.currentVersion), Placeholder.unparsed("arg3", up.newVersion)));
                }
                sender.sendMessage(plugin.getMessage("status.dry-run-complete"));
                return;
            }

            sender.sendMessage(plugin.getMessage("status.upgrade-list-header"));
            for (UpgradeInfo up : updates) {
                sender.sendMessage(plugin.getMessage("status.upgrade-list-item", Placeholder.unparsed("arg1", up.filename), Placeholder.unparsed("arg2", up.currentVersion), Placeholder.unparsed("arg3", up.newVersion)));
            }
            
            sender.sendMessage(plugin.getMessage("status.upgrade-summary", Placeholder.unparsed("arg", String.valueOf(updates.size()))));
            sender.sendMessage(plugin.getMessage("status.upgrading"));
            
            List<CompletableFuture<Void>> upgradeFutures = updates.stream()
                .map(up -> CompletableFuture.runAsync(() -> {
                    JsonArray files = up.latestObj.getAsJsonArray("files");
                    if (files.size() == 0) return;
                    
                    JsonObject primaryFile = files.get(0).getAsJsonObject();
                    for(JsonElement f : files) {
                        if (f.getAsJsonObject().has("primary") && f.getAsJsonObject().get("primary").getAsBoolean()) {
                            primaryFile = f.getAsJsonObject();
                            break;
                        }
                    }
                    
                    // Download new to update folder
                    try {
                        packageManager.downloadFile(primaryFile.get("url").getAsString(), packageManager.getUpdateDir(), primaryFile.get("filename").getAsString(), primaryFile.get("size").getAsLong(), createProgressCallback(sender, up.filename));
                         sendStatus(sender, plugin.getMessage("status.upgraded", Placeholder.unparsed("arg", up.filename)));
                    } catch (Exception e) {
                        sender.sendMessage(plugin.getMessage("errors.upgrade-failed", Placeholder.unparsed("arg", up.filename), Placeholder.unparsed("error", e.getMessage())));
                    }
                }))
                .collect(Collectors.toList());

             CompletableFuture.allOf(upgradeFutures.toArray(new CompletableFuture[0])).join();
             sender.sendMessage(plugin.getMessage("status.upgrade-complete"));

        } catch (Exception e) {
             sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", "upgrades"), Placeholder.unparsed("error", e.getMessage())));
        }
    }


    
    private static class UpgradeInfo {
        String filename;
        String projectId;
        String currentVersion;
        String newVersion;
        JsonObject latestObj;
        
        UpgradeInfo(String filename, String projectId, String currentVersion, String newVersion, JsonObject latestObj) {
            this.filename = filename;
            this.projectId = projectId;
            this.currentVersion = currentVersion;
            this.newVersion = newVersion;
            this.latestObj = latestObj;
        }
    }
}
