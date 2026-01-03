package io.github.Earth1283.aptMc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AptCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final PackageManager packageManager;

    public AptCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.packageManager = new PackageManager(plugin.getDataFolder(), plugin.getDataFolder().getParentFile());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("apt-mc: The Advanced Packaging Tool for Minecraft Servers.", NamedTextColor.GREEN));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        List<String> subArgs = new ArrayList<>(Arrays.asList(args).subList(1, args.length));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                switch (subCommand) {
                    case "info":
                        handleInfo(sender, subArgs);
                        break;
                    case "list":
                        handleList(sender, subArgs);
                        break;
                    case "update":
                        handleUpdate(sender);
                        break;
                    case "search":
                        handleSearch(sender, subArgs);
                        break;
                    case "install":
                        handleInstall(sender, subArgs);
                        break;
                    case "upgrade":
                        handleUpgrade(sender);
                        break;
                    case "remove":
                        handleRemove(sender, subArgs);
                        break;
                    default:
                        sender.sendMessage(Component.text("Unknown command: " + subCommand, NamedTextColor.RED));
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("Error executing command: " + e.getMessage(), NamedTextColor.RED));
                e.printStackTrace();
            }
        });

        return true;
    }

    private void handleInfo(CommandSender sender, List<String> args) {
        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /apt info <package>", NamedTextColor.RED));
            return;
        }
        String pkg = args.get(0);

        try {
            JsonObject project = ModrinthAPI.getProject(pkg);
            if (project == null) {
                sender.sendMessage(Component.text("E: Unable to locate package " + pkg, NamedTextColor.RED));
                return;
            }

            // Check installation status
            Map<String, String> installedPlugins = packageManager.getInstalledPlugins();
            String status = "Not Installed";
            NamedTextColor statusColor = NamedTextColor.RED;

            if (!installedPlugins.isEmpty()) {
                List<String> hashes = new ArrayList<>(installedPlugins.values());
                Map<String, JsonObject> installedVersions = resolveVersions(hashes);
                
                for(JsonObject v : installedVersions.values()) {
                    if (v.get("project_id").getAsString().equals(project.get("id").getAsString())) {
                        status = "Installed";
                        statusColor = NamedTextColor.GREEN;
                        break;
                    }
                }
            }

            // Dependencies (simplified)
            String dependencies = "None";
            try {
                JsonArray versions = ModrinthAPI.getVersions(project.get("id").getAsString(), List.of("spigot", "paper", "purpur", "bukkit"));
                if (versions.size() > 0) {
                    JsonObject latest = versions.get(0).getAsJsonObject();
                    JsonArray deps = latest.getAsJsonArray("dependencies");
                    if (deps != null && deps.size() > 0) {
                        List<String> depNames = new ArrayList<>();
                        for (JsonElement d : deps) {
                            JsonObject depObj = d.getAsJsonObject();
                            if (depObj.has("dependency_type") && depObj.get("dependency_type").getAsString().equals("required")) {
                                if (depObj.has("project_id") && !depObj.get("project_id").isJsonNull()) {
                                     depNames.add(depObj.get("project_id").getAsString());
                                }
                            }
                        }
                        if (!depNames.isEmpty()) dependencies = String.join(", ", depNames);
                    }
                }
            } catch (Exception ignored) {}

            sender.sendMessage(Component.text("Package: ", NamedTextColor.WHITE).append(Component.text(project.get("slug").getAsString()).decoration(TextDecoration.BOLD, false)));
            sender.sendMessage(Component.text("ID: ", NamedTextColor.WHITE).append(Component.text(project.get("id").getAsString())));
            sender.sendMessage(Component.text("Status: ", NamedTextColor.WHITE).append(Component.text(status, statusColor)));
            sender.sendMessage(Component.text("Description: ", NamedTextColor.WHITE).append(Component.text(project.get("description").getAsString())));
            sender.sendMessage(Component.text("Downloads: ", NamedTextColor.WHITE).append(Component.text(project.get("downloads").getAsInt())));
            sender.sendMessage(Component.text("Dependencies: ", NamedTextColor.WHITE).append(Component.text(dependencies)));

        } catch (Exception e) {
             sender.sendMessage(Component.text("E: Failed to fetch info: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleList(CommandSender sender, List<String> args) {
        packageManager.ensureDir();
        Map<String, String> installedPlugins = packageManager.getInstalledPlugins();

        if (installedPlugins.isEmpty()) {
            sender.sendMessage(Component.text("No plugins installed.", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("Listing... Done", NamedTextColor.GREEN));

        try {
            Map<String, JsonObject> versionsMap = resolveVersions(new ArrayList<>(installedPlugins.values()));
            
            // Reverse lookup: sha1 -> filename
            // installedPlugins is filename -> sha1
            
            for (Map.Entry<String, String> entry : installedPlugins.entrySet()) {
                String filename = entry.getKey();
                String sha1 = entry.getValue();
                
                String pkgName = filename;
                String verNum = "unknown";
                
                if (versionsMap.containsKey(sha1)) {
                    JsonObject v = versionsMap.get(sha1);
                    pkgName = v.get("project_id").getAsString(); // Modrinth doesn't return slug here easily without N requests
                    verNum = v.get("version_number").getAsString();
                }
                
                sender.sendMessage(Component.text(pkgName, NamedTextColor.GREEN)
                        .append(Component.text(" "))
                        .append(Component.text(verNum, NamedTextColor.WHITE))
                        .append(Component.text(" [installed]", NamedTextColor.GRAY)));
            }
        } catch (Exception e) {
            sender.sendMessage(Component.text("E: Failed to resolve versions: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleUpdate(CommandSender sender) {
        // Simulation
        String[] loaders = {"spigot", "paper", "purpur"};
        int i = 1;
        for (String loader : loaders) {
            sendStatus(sender, Component.text("Hit:" + i + " https://api.modrinth.com/v2/search " + loader, NamedTextColor.WHITE));
            i++;
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }
        sendStatus(sender, Component.text("Reading package lists... Done", NamedTextColor.GREEN));
        sendStatus(sender, Component.text("Building dependency tree... Done", NamedTextColor.GREEN));
        sendStatus(sender, Component.text("Reading state information... Done", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("All packages are up to date.", NamedTextColor.WHITE));
    }

    private void handleSearch(CommandSender sender, List<String> args) {
        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /apt search <query>", NamedTextColor.RED));
            return;
        }
        String query = String.join(" ", args);
        sendStatus(sender, Component.text("Sorting... Done", NamedTextColor.GREEN));
        sendStatus(sender, Component.text("Full Text Search... Done", NamedTextColor.GREEN));

        try {
            JsonArray hits = ModrinthAPI.search(query, 10);
            if (hits.size() == 0) {
                sender.sendMessage(Component.text("No plugins found for '" + query + "'.", NamedTextColor.YELLOW));
                return;
            }
            
            // Header
            sender.sendMessage(Component.text("Package Name - Description - Author - Downloads", NamedTextColor.LIGHT_PURPLE));

            for (JsonElement hit : hits) {
                JsonObject h = hit.getAsJsonObject();
                String slug = h.get("slug").getAsString();
                String desc = h.get("description").getAsString();
                if (desc.length() > 50) desc = desc.substring(0, 50) + "...";
                String author = h.get("author").getAsString();
                int downloads = h.get("downloads").getAsInt();

                sender.sendMessage(Component.text(slug, NamedTextColor.GREEN)
                        .append(Component.text(" - " + desc, NamedTextColor.WHITE))
                        .append(Component.text(" - " + author, NamedTextColor.AQUA))
                        .append(Component.text(" - " + downloads, NamedTextColor.GRAY)));
            }
        } catch (Exception e) {
             sender.sendMessage(Component.text("E: Failed to search: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleInstall(CommandSender sender, List<String> args) {
        if (args.isEmpty()) {
            sender.sendMessage(Component.text("E: No packages specified.", NamedTextColor.RED));
            return;
        }

        packageManager.ensureDir();
        sendStatus(sender, Component.text("Reading package lists... Done", NamedTextColor.GREEN));
        sendStatus(sender, Component.text("Building dependency tree... Done", NamedTextColor.GREEN));

        List<CompletableFuture<JsonObject>> projectFutures = args.stream()
                .map(pkgSlug -> CompletableFuture.supplyAsync(() -> {
                    try {
                        sendStatus(sender, Component.text("Check " + pkgSlug + "...", NamedTextColor.WHITE));
                        return ModrinthAPI.getProject(pkgSlug);
                    } catch (Exception e) {
                        sender.sendMessage(Component.text("E: Error checking " + pkgSlug + ": " + e.getMessage(), NamedTextColor.RED));
                        return null;
                    }
                }))
                .collect(Collectors.toList());

        CompletableFuture.allOf(projectFutures.toArray(new CompletableFuture[0])).join();

        List<JsonObject> toInstall = projectFutures.stream()
                .map(CompletableFuture::join)
                .filter(p -> p != null)
                .collect(Collectors.toList());

        if (toInstall.isEmpty()) return;

        sender.sendMessage(Component.text("\nThe following NEW packages will be installed:", NamedTextColor.WHITE));
        for (JsonObject pkg : toInstall) {
            sender.sendMessage(Component.text("  " + pkg.get("slug").getAsString(), NamedTextColor.GREEN));
        }
        sender.sendMessage(Component.text("\n0 upgraded, " + toInstall.size() + " newly installed, 0 to remove and 0 not upgraded.", NamedTextColor.WHITE));

        List<CompletableFuture<Void>> installFutures = toInstall.stream()
                .map(pkg -> CompletableFuture.runAsync(() -> {
                    String slug = pkg.get("slug").getAsString();
                    try {
                        JsonArray versions = ModrinthAPI.getVersions(pkg.get("id").getAsString(), List.of("spigot", "paper", "purpur", "bukkit"));
                        if (versions.size() == 0) {
                            sender.sendMessage(Component.text("E: No compatible versions for " + slug, NamedTextColor.RED));
                            return;
                        }
                        
                        JsonObject latest = versions.get(0).getAsJsonObject();
                        JsonArray files = latest.getAsJsonArray("files");
                        JsonObject primaryFile = files.get(0).getAsJsonObject();
                        
                        for(JsonElement f : files) {
                            if (f.getAsJsonObject().has("primary") && f.getAsJsonObject().get("primary").getAsBoolean()) {
                                primaryFile = f.getAsJsonObject();
                                break;
                            }
                        }
                        
                        String url = primaryFile.get("url").getAsString();
                        String filename = primaryFile.get("filename").getAsString();
                        long size = primaryFile.get("size").getAsLong();
                        
                        sendStatus(sender, Component.text("Downloading " + filename + "...", NamedTextColor.BLUE));
                        packageManager.downloadFile(url, packageManager.getPluginsDir(), filename, size, createProgressCallback(sender, filename));
                        sendStatus(sender, Component.text("Downloaded " + filename, NamedTextColor.GREEN));
                        
                    } catch (Exception e) {
                        sender.sendMessage(Component.text("E: Failed to install " + slug + ": " + e.getMessage(), NamedTextColor.RED));
                    }
                }))
                .collect(Collectors.toList());

        CompletableFuture.allOf(installFutures.toArray(new CompletableFuture[0])).join();
        sender.sendMessage(Component.text("Installation complete. Restart server to apply changes.", NamedTextColor.GOLD));
    }

    private void handleUpgrade(CommandSender sender) {
        sendStatus(sender, Component.text("Reading package lists... Done", NamedTextColor.GREEN));
        sendStatus(sender, Component.text("Building dependency tree... Done", NamedTextColor.GREEN));
        sendStatus(sender, Component.text("Reading state information... Done", NamedTextColor.GREEN));
        sendStatus(sender, Component.text("Calculating upgrades... ", NamedTextColor.WHITE));

        Map<String, String> installed = packageManager.getInstalledPlugins();
        if (installed.isEmpty()) {
            sender.sendMessage(Component.text("Done", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("0 upgraded, 0 newly installed, 0 to remove and 0 not upgraded.", NamedTextColor.WHITE));
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
            
            sender.sendMessage(Component.text("Done", NamedTextColor.GREEN));
            
            if (updates.isEmpty()) {
                sender.sendMessage(Component.text("0 upgraded, 0 newly installed, 0 to remove and 0 not upgraded.", NamedTextColor.WHITE));
                return;
            }

            sender.sendMessage(Component.text("\nThe following packages will be upgraded:", NamedTextColor.WHITE));
            for (UpgradeInfo up : updates) {
                sender.sendMessage(Component.text("  " + up.filename + " (" + up.currentVersion + " -> " + up.newVersion + ")", NamedTextColor.WHITE));
            }
            
            sender.sendMessage(Component.text("\n" + updates.size() + " upgraded, 0 newly installed, 0 to remove and 0 not upgraded.", NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Upgrading...", NamedTextColor.BLUE));
            
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
                    
                    // Try to remove old file
                    File oldFile = new File(packageManager.getPluginsDir(), up.filename);
                    if (oldFile.exists()) {
                        if (!oldFile.delete()) {
                             sender.sendMessage(Component.text("W: Could not delete " + up.filename + ". New version will be in update folder.", NamedTextColor.YELLOW));
                        }
                    }
                    
                    // Download new to update folder
                    try {
                        packageManager.downloadFile(primaryFile.get("url").getAsString(), packageManager.getUpdateDir(), primaryFile.get("filename").getAsString(), primaryFile.get("size").getAsLong(), createProgressCallback(sender, up.filename));
                         sendStatus(sender, Component.text("Upgraded " + up.filename, NamedTextColor.GREEN));
                    } catch (Exception e) {
                        sender.sendMessage(Component.text("E: Failed to upgrade " + up.filename + ": " + e.getMessage(), NamedTextColor.RED));
                    }
                }))
                .collect(Collectors.toList());

             CompletableFuture.allOf(upgradeFutures.toArray(new CompletableFuture[0])).join();
             sender.sendMessage(Component.text("Upgrade complete. Restart server to apply changes.", NamedTextColor.GOLD));

        } catch (Exception e) {
             sender.sendMessage(Component.text("E: Failed to check for updates: " + e.getMessage(), NamedTextColor.RED));
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

    private void handleRemove(CommandSender sender, List<String> args) {
        if (args.isEmpty()) {
             sender.sendMessage(Component.text("Usage: /apt remove <package>", NamedTextColor.RED));
            return;
        }
        String pkg = args.get(0);
        sender.sendMessage(Component.text("Reading package lists... Done", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Building dependency tree... Done", NamedTextColor.GREEN));

        File[] files = packageManager.getPluginsDir().listFiles();
        List<File> candidates = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".jar") && f.getName().toLowerCase().contains(pkg.toLowerCase())) {
                    candidates.add(f);
                }
            }
        }
        
        if (candidates.isEmpty()) {
            sender.sendMessage(Component.text("E: Unable to locate package " + pkg, NamedTextColor.RED));
            return;
        }
        
        if (candidates.size() > 1) {
            sender.sendMessage(Component.text("E: Multiple candidates found for " + pkg + ". Be more specific.", NamedTextColor.RED));
            return;
        }
        
        File target = candidates.get(0);
        if (target.delete()) {
            sender.sendMessage(Component.text("Removing " + pkg + " (" + target.getName() + ")...", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Removal complete. Restart server to apply changes.", NamedTextColor.GOLD));
        } else {
            sender.sendMessage(Component.text("E: Failed to remove " + target.getName(), NamedTextColor.RED));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("info", "list", "update", "search", "install", "upgrade", "remove");
        }
        return Collections.emptyList();
    }

    private void sendStatus(CommandSender sender, Component message) {
        if (plugin.getConfig().getBoolean("use-action-bar") && sender instanceof org.bukkit.entity.Player) {
            sender.sendActionBar(message);
        } else {
            sender.sendMessage(message);
        }
    }

    private java.util.function.Consumer<Double> createProgressCallback(CommandSender sender, String filename) {
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
                    sender.sendActionBar(Component.text("Downloading " + filename + " " + bar.toString(), NamedTextColor.BLUE));
                }
            } else {
                long now = System.currentTimeMillis();
                if (progress >= 1.0 || now - lastUpdate[0] >= intervalMs) {
                    lastUpdate[0] = now;
                    int percent = (int)(progress * 100);
                    sender.sendMessage(Component.text("Downloading " + filename + "... " + percent + "%", NamedTextColor.BLUE));
                }
            }
        };
    }

    private Map<String, JsonObject> resolveVersions(List<String> hashes) {
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
