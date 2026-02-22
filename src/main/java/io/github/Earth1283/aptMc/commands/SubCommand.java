package io.github.Earth1283.aptMc.commands;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.managers.PackageManager;
import io.github.Earth1283.aptMc.api.ModrinthAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import com.google.gson.JsonObject;

import java.util.*;

public abstract class SubCommand {
    protected final AptMc plugin;
    protected final PackageManager packageManager;

    public SubCommand(AptMc plugin, PackageManager packageManager) {
        this.plugin = plugin;
        this.packageManager = packageManager;
    }

    public abstract void execute(CommandSender sender, List<String> args, boolean dryRun);

    public void execute(CommandSender sender, List<String> args) {
        execute(sender, args, false);
    }
    
    public List<String> onTabComplete(CommandSender sender, List<String> args) {
        return Collections.emptyList();
    }

    protected void sendStatus(CommandSender sender, net.kyori.adventure.text.Component message) {
        if (plugin.getConfig().getBoolean("use-action-bar") && sender instanceof org.bukkit.entity.Player) {
            sender.sendActionBar(message);
        } else {
            sender.sendMessage(message);
        }
    }

    protected java.util.function.Consumer<Double> createProgressCallback(CommandSender sender, String filename) {
        long[] lastUpdate = new long[]{0};
        int intervalMs = plugin.getConfig().getInt("console-progress-interval", 5) * 1000;
        
        return (progress) -> {
            if (sender instanceof org.bukkit.entity.Player) {
                if (plugin.getConfig().getBoolean("use-action-bar")) {
                    int percent = (int) (progress * 100);
                    int bars = percent / 5;
                    StringBuilder bar = new StringBuilder("[");
                    for (int i = 0; i < 20; i++) {
                        if (i < bars) bar.append("=");
                        else bar.append("-");
                    }
                    bar.append("] ").append(percent).append("%");
                    sender.sendActionBar(plugin.getMessage("status.downloading", Placeholder.unparsed("arg", filename + " " + bar.toString())));
                }
            } else {
                long now = System.currentTimeMillis();
                if (progress >= 1.0 || now - lastUpdate[0] >= intervalMs) {
                    lastUpdate[0] = now;
                    int percent = (int)(progress * 100);
                    sender.sendMessage(plugin.getMessage("status.downloading", Placeholder.unparsed("arg", filename + "... " + percent + "%")));
                }
            }
        };
    }

    protected Map<String, JsonObject> resolveVersions(List<String> hashes) {
        Map<String, JsonObject> results = new HashMap<>();
        List<String> missingHashes = new ArrayList<>();

        for (String sha1 : hashes) {
            JsonObject cached = packageManager.getCachedInfo(sha1);
            if (cached != null) {
                results.put(sha1, cached);
            } else {
                missingHashes.add(sha1);
            }
        }

        if (!missingHashes.isEmpty()) {
            try {
                JsonObject fromApi = ModrinthAPI.getVersionsByHashes(missingHashes);
                if (fromApi != null) {
                    Map<String, JsonObject> newCacheEntries = new HashMap<>();
                    for (String hash : fromApi.keySet()) {
                        JsonObject val = fromApi.getAsJsonObject(hash);
                        results.put(hash, val);
                        newCacheEntries.put(hash, val);
                    }
                    packageManager.updateCache(newCacheEntries);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return results;
    }
}
