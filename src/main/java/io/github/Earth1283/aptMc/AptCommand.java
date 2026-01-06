package io.github.Earth1283.aptMc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AptCommand implements CommandExecutor, TabCompleter {
    private final AptMc plugin;
    private final PackageManager packageManager;

    public AptCommand(AptMc plugin) {
        this.plugin = plugin;
        this.packageManager = new PackageManager(plugin.getDataFolder(), plugin.getDataFolder().getParentFile());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        List<String> subArgs = new ArrayList<>(Arrays.asList(args).subList(1, args.length));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                switch (subCommand) {
                    case "info": handleInfo(sender, subArgs); break;
                    case "list": handleList(sender, subArgs); break;
                    case "update": handleUpdate(sender); break;
                    case "search": handleSearch(sender, subArgs); break;
                    case "install": handleInstall(sender, subArgs); break;
                    case "upgrade": handleUpgrade(sender); break;
                    case "remove": handleRemove(sender, subArgs); break;
                    case "export": handleExport(sender, subArgs); break;
                    case "import": handleImport(sender, subArgs); break;
                    default:
                        sender.sendMessage(plugin.getMessage("errors.unknown-command", Placeholder.unparsed("arg", subCommand)));
                }
            } catch (Exception e) {
                sender.sendMessage(plugin.getMessage("errors.execution-error", Placeholder.unparsed("arg", e.getMessage())));
                e.printStackTrace();
            }
        });

        return true;
    }

    private void sendHelp(CommandSender sender) {
        if (plugin.getConfig().getBoolean("apt-song-references")) {
             sender.sendMessage(plugin.getMessage("usage.song-ref"));
        }
        sender.sendMessage(plugin.getMessage("usage.header"));
        sender.sendMessage(plugin.getMessage("usage.install"));
        sender.sendMessage(plugin.getMessage("usage.remove"));
        sender.sendMessage(plugin.getMessage("usage.upgrade"));
        sender.sendMessage(plugin.getMessage("usage.search"));
        sender.sendMessage(plugin.getMessage("usage.info"));
        sender.sendMessage(plugin.getMessage("usage.list"));
        sender.sendMessage(plugin.getMessage("usage.update"));
        sender.sendMessage(plugin.getMessage("usage.export"));
        sender.sendMessage(plugin.getMessage("usage.import"));
    }

    private void handleInfo(CommandSender sender, List<String> args) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("usage.info"));
            return;
        }
        String pkg = args.get(0);

        try {
            JsonObject project = ModrinthAPI.getProject(pkg);
            if (project == null) {
                sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", pkg)));
                return;
            }

            Map<String, String> installedPlugins = packageManager.getInstalledPlugins();
            String status = "Not Installed";
            String statusKey = "status.not-installed";

            if (!installedPlugins.isEmpty()) {
                List<String> hashes = new ArrayList<>(installedPlugins.values());
                Map<String, JsonObject> installedVersions = resolveVersions(hashes);
                
                for(JsonObject v : installedVersions.values()) {
                    if (v.get("project_id").getAsString().equals(project.get("id").getAsString())) {
                        statusKey = "status.installed";
                        break;
                    }
                }
            }
            Component statusComp = plugin.getMessage(statusKey);

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

            sender.sendMessage(plugin.getMessage("status.package-info.name", Placeholder.unparsed("arg", project.get("slug").getAsString())));
            sender.sendMessage(plugin.getMessage("status.package-info.id", Placeholder.unparsed("arg", project.get("id").getAsString())));
            sender.sendMessage(plugin.getMessage("status.package-info.status", Placeholder.component("arg", statusComp)));
            sender.sendMessage(plugin.getMessage("status.package-info.description", Placeholder.unparsed("arg", project.get("description").getAsString())));
            sender.sendMessage(plugin.getMessage("status.package-info.downloads", Placeholder.unparsed("arg", String.valueOf(project.get("downloads").getAsInt()))));
            sender.sendMessage(plugin.getMessage("status.package-info.dependencies", Placeholder.unparsed("arg", dependencies)));

        } catch (Exception e) {
             sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", pkg), Placeholder.unparsed("error", e.getMessage())));
        }
    }

    private void handleList(CommandSender sender, List<String> args) {
        packageManager.ensureDir();
        Map<String, String> installedPlugins = packageManager.getInstalledPlugins();

        if (installedPlugins.isEmpty()) {
            sender.sendMessage(plugin.getMessage("status.list-empty"));
            return;
        }

        sender.sendMessage(plugin.getMessage("status.listing"));

        try {
            Map<String, JsonObject> versionsMap = resolveVersions(new ArrayList<>(installedPlugins.values()));
            
            for (Map.Entry<String, String> entry : installedPlugins.entrySet()) {
                String filename = entry.getKey();
                String sha1 = entry.getValue();
                
                String pkgName = filename;
                String verNum = "unknown";
                
                if (versionsMap.containsKey(sha1)) {
                    JsonObject v = versionsMap.get(sha1);
                    pkgName = v.get("project_id").getAsString();
                    verNum = v.get("version_number").getAsString();
                }
                
                sender.sendMessage(plugin.getMessage("status.list-item", Placeholder.unparsed("arg1", pkgName), Placeholder.unparsed("arg2", verNum)));
            }
        } catch (Exception e) {
            sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", "list"), Placeholder.unparsed("error", e.getMessage())));
        }
    }

    private void handleUpdate(CommandSender sender) {
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

    private void handleSearch(CommandSender sender, List<String> args) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("usage.search"));
            return;
        }
        String query = String.join(" ", args);
        
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

    private Set<String> getInstalledProjectIds() {
        Map<String, String> installed = packageManager.getInstalledPlugins();
        Map<String, JsonObject> resolved = resolveVersions(new ArrayList<>(installed.values()));
        Set<String> ids = new HashSet<>();
        for (JsonObject v : resolved.values()) {
            ids.add(v.get("project_id").getAsString());
        }
        return ids;
    }

    private void resolveDependenciesRecursive(CommandSender sender, JsonObject project, Map<String, JsonObject> toInstall, Set<String> installedIds) {
        String id = project.get("id").getAsString();
        String slug = project.get("slug").getAsString();
        
        if (installedIds.contains(id) || toInstall.containsKey(id)) return;
        
        toInstall.put(id, project);
        
        try {
            JsonArray versions = ModrinthAPI.getVersions(id, List.of("spigot", "paper", "purpur", "bukkit"));
            if (versions.size() > 0) {
                JsonObject latest = versions.get(0).getAsJsonObject();
                JsonArray deps = latest.getAsJsonArray("dependencies");
                if (deps != null) {
                    for (JsonElement d : deps) {
                        JsonObject depObj = d.getAsJsonObject();
                        if (depObj.has("dependency_type") && depObj.get("dependency_type").getAsString().equals("required")) {
                            if (depObj.has("project_id") && !depObj.get("project_id").isJsonNull()) {
                                String depId = depObj.get("project_id").getAsString();
                                if (!installedIds.contains(depId) && !toInstall.containsKey(depId)) {
                                    sendStatus(sender, plugin.getMessage("status.checking", Placeholder.unparsed("arg", "dependency: " + depId)));
                                    JsonObject depProj = ModrinthAPI.getProject(depId);
                                    if (depProj != null) {
                                        resolveDependenciesRecursive(sender, depProj, toInstall, installedIds);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            sender.sendMessage(plugin.getMessage("errors.failed-resolve-deps", Placeholder.unparsed("arg", slug), Placeholder.unparsed("error", e.getMessage())));
        }
    }

    private void handleInstall(CommandSender sender, List<String> args) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("errors.no-packages-specified"));
            return;
        }

        packageManager.ensureDir();
        sendStatus(sender, plugin.getMessage("status.update-reading"));
        sendStatus(sender, plugin.getMessage("status.update-building"));

        Set<String> installedIds = getInstalledProjectIds();
        Map<String, JsonObject> toInstallMap = new HashMap<>();
        List<String> priority = plugin.getConfig().getStringList("source-priority");
        if (priority.isEmpty()) priority = List.of("modrinth", "hangar");

        for (String pkgSlug : args) {
            boolean found = false;
            try {
                for (String source : priority) {
                    if (source.equalsIgnoreCase("modrinth")) {
                        sendStatus(sender, plugin.getMessage("status.checking", Placeholder.unparsed("arg", pkgSlug + " on Modrinth")));
                        JsonObject project = ModrinthAPI.getProject(pkgSlug);
                        if (project != null) {
                            resolveDependenciesRecursive(sender, project, toInstallMap, installedIds);
                            found = true;
                            break;
                        }
                    } else if (source.equalsIgnoreCase("hangar") && plugin.getConfig().getBoolean("enable-hangar")) {
                        sendStatus(sender, plugin.getMessage("status.checking", Placeholder.unparsed("arg", pkgSlug + " on Hangar")));
                        JsonObject hangarProject = HangarAPI.getProject(pkgSlug);
                        if (hangarProject != null) {
                            JsonObject versionInfo = HangarAPI.getLatestVersion(pkgSlug, "PAPER");
                            if (versionInfo != null) {
                                versionInfo.addProperty("source", "hangar");
                                versionInfo.addProperty("hangar_slug", hangarProject.get("name").getAsString());
                                toInstallMap.put(hangarProject.get("name").getAsString(), versionInfo);
                                found = true;
                                break;
                            }
                        }
                    }
                }
                
                if (!found) {
                    sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", pkgSlug)));
                }
            } catch (Exception e) {
                sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", pkgSlug), Placeholder.unparsed("error", e.getMessage())));
            }
        }

        List<JsonObject> toInstall = new ArrayList<>(toInstallMap.values());
        if (toInstall.isEmpty()) {
            sender.sendMessage(plugin.getMessage("status.up-to-date")); // Generic "nothing to do"
            return;
        }

        if (plugin.getConfig().getBoolean("apt-song-references")) {
            // sender.sendMessage(Component.text(getRandomAptQuote(), net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE)); 
            sender.sendMessage(plugin.getMessage("status.install-song-ref")); 
        }

        sender.sendMessage(plugin.getMessage("status.install-header"));
        for (JsonObject pkg : toInstall) {
            String name = pkg.has("slug") ? pkg.get("slug").getAsString() : (pkg.has("hangar_slug") ? pkg.get("hangar_slug").getAsString() : "unknown");
            sender.sendMessage(plugin.getMessage("status.install-item", Placeholder.unparsed("arg", name)));
        }
        sender.sendMessage(plugin.getMessage("status.install-summary", Placeholder.unparsed("arg", String.valueOf(toInstall.size()))));

        List<CompletableFuture<Void>> installFutures = toInstall.stream()
                .map(pkg -> CompletableFuture.runAsync(() -> {
                    if (pkg.has("source") && pkg.get("source").getAsString().equals("hangar")) {
                        try {
                            String url = HangarAPI.getDownloadUrl(pkg, "PAPER");
                            String filename = HangarAPI.getFileName(pkg, "PAPER");
                            
                            if (url == null || filename == null) {
                                 sender.sendMessage(plugin.getMessage("errors.hangar-resolve-failed", Placeholder.unparsed("arg", pkg.get("hangar_slug").getAsString())));
                                 return;
                            }
                            
                            sendStatus(sender, plugin.getMessage("status.downloading", Placeholder.unparsed("arg", filename)));
                            packageManager.downloadFile(url, packageManager.getPluginsDir(), filename, -1, createProgressCallback(sender, filename));
                            sendStatus(sender, plugin.getMessage("status.downloaded", Placeholder.unparsed("arg", filename)));
                        } catch (Exception e) {
                             sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", "Hangar Package"), Placeholder.unparsed("error", e.getMessage())));
                        }
                        return;
                    }

                    String slug = pkg.get("slug").getAsString();
                    try {
                        JsonArray versions = ModrinthAPI.getVersions(pkg.get("id").getAsString(), List.of("spigot", "paper", "purpur", "bukkit"));
                        if (versions.size() == 0) {
                            sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", slug), Placeholder.unparsed("error", "No compatible versions")));
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
                        
                        sendStatus(sender, plugin.getMessage("status.downloading", Placeholder.unparsed("arg", filename)));
                        packageManager.downloadFile(url, packageManager.getPluginsDir(), filename, size, createProgressCallback(sender, filename));
                        sendStatus(sender, plugin.getMessage("status.downloaded", Placeholder.unparsed("arg", filename)));
                        
                    } catch (Exception e) {
                        sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", slug), Placeholder.unparsed("error", e.getMessage())));
                    }
                }))
                .collect(Collectors.toList());

        CompletableFuture.allOf(installFutures.toArray(new CompletableFuture[0])).join();
        sender.sendMessage(plugin.getMessage("status.install-complete"));
    }

    private String getRandomAptQuote() {
        return "";
    }

    private void handleUpgrade(CommandSender sender) {
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

    private void handleExport(CommandSender sender, List<String> args) {
        String filename = args.isEmpty() ? "apt-manifest.yml" : args.get(0);
        if (!filename.endsWith(".yml")) filename += ".yml";
        File file = new File(plugin.getDataFolder(), filename);
        
        sendStatus(sender, plugin.getMessage("status.exporting", Placeholder.unparsed("arg", filename)));
        
        Map<String, String> installed = packageManager.getInstalledPlugins();
        if (installed.isEmpty()) {
             sender.sendMessage(plugin.getMessage("status.export-empty"));
             return;
        }

        Map<String, JsonObject> versions = resolveVersions(new ArrayList<>(installed.values()));
        FileConfiguration yaml = new YamlConfiguration();
        
        // Add Header
        yaml.set("project-details.title", "Server Export");
        yaml.set("project-details.author", sender.getName());
        
        List<Map<String, String>> pluginList = new ArrayList<>();
        int count = 0;
        for (Map.Entry<String, String> entry : installed.entrySet()) {
            String pluginFilename = entry.getKey();
            String sha1 = entry.getValue();
            
            if (versions.containsKey(sha1)) {
                JsonObject v = versions.get(sha1);
                String projectId = v.get("project_id").getAsString();
                String verNum = v.get("version_number").getAsString();
                
                Map<String, String> entryMap = new LinkedHashMap<>();
                entryMap.put("file", pluginFilename);
                entryMap.put("source", "modrinth:" + projectId + "/" + verNum);
                pluginList.add(entryMap);
                count++;
            }
        }
        yaml.set("plugins", pluginList);
        
        try {
            yaml.save(file);
             sender.sendMessage(plugin.getMessage("status.export-success", Placeholder.unparsed("arg1", String.valueOf(count)), Placeholder.unparsed("arg2", file.getPath())));
        } catch (IOException e) {
             sender.sendMessage(plugin.getMessage("status.export-failed", Placeholder.unparsed("error", e.getMessage())));
        }
    }

    private void handleImport(CommandSender sender, List<String> args) {
        String filename = args.isEmpty() ? "apt-manifest.yml" : args.get(0);
        if (!filename.endsWith(".yml")) filename += ".yml";
        File file = new File(plugin.getDataFolder(), filename);
        
        if (!file.exists()) {
             sender.sendMessage(plugin.getMessage("errors.manifest-not-found", Placeholder.unparsed("arg", filename)));
             return;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.contains("plugins")) {
             sender.sendMessage(plugin.getMessage("errors.invalid-manifest"));
             return;
        }
        
        sender.sendMessage(plugin.getMessage("status.reading-manifest"));
        
        if (yaml.contains("project-details")) {
            String title = yaml.getString("project-details.title", "Unknown Project");
            String author = yaml.getString("project-details.author", "Unknown Author");
            sender.sendMessage(plugin.getMessage("status.import-header", Placeholder.unparsed("title", title), Placeholder.unparsed("author", author)));
        }
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        final int[] installedCount = {0};
        java.util.concurrent.ExecutorService downloadExecutor = java.util.concurrent.Executors.newFixedThreadPool(3);
        
        List<Map<?, ?>> pluginList = yaml.getMapList("plugins");
        for (Map<?, ?> entry : pluginList) {
            String val = (String) entry.get("source");
            if (val == null || !val.startsWith("modrinth:")) continue;
            
            String[] parts = val.substring(9).split("/");
            if (parts.length < 2) continue;
            
            String projectId = parts[0];
            String versionNum = parts[1];
            
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    JsonArray verList = ModrinthAPI.getVersions(projectId, List.of("spigot", "paper", "purpur", "bukkit"));
                    JsonObject targetVer = null;
                    if (verList != null && verList.size() > 0) {
                        if (versionNum.equalsIgnoreCase("latest")) {
                             targetVer = verList.get(0).getAsJsonObject();
                        } else {
                            for(JsonElement e : verList) {
                                if (e.getAsJsonObject().get("version_number").getAsString().equals(versionNum)) {
                                    targetVer = e.getAsJsonObject();
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (targetVer == null) {
                         sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", "version " + versionNum + " of " + projectId)));
                         return;
                    }
                    
                    JsonArray files = targetVer.getAsJsonArray("files");
                    if (files.size() == 0) return;
                    
                    JsonObject primary = files.get(0).getAsJsonObject();
                    for(JsonElement f : files) {
                        if (f.getAsJsonObject().has("primary") && f.getAsJsonObject().get("primary").getAsBoolean()) {
                            primary = f.getAsJsonObject();
                            break;
                        }
                    }
                    
                    String fileName = primary.get("filename").getAsString();
                    String url = primary.get("url").getAsString();
                    long size = primary.get("size").getAsLong();
                    
                    File targetFile = new File(packageManager.getPluginsDir(), fileName);
                    if (!targetFile.exists()) {
                         sendStatus(sender, plugin.getMessage("status.downloading", Placeholder.unparsed("arg", fileName)));
                         packageManager.downloadFile(url, packageManager.getPluginsDir(), fileName, size, createProgressCallback(sender, fileName));
                         sendStatus(sender, plugin.getMessage("status.installed-success", Placeholder.unparsed("arg", fileName)));
                         synchronized(installedCount) { installedCount[0]++; }
                    }
                } catch (Exception e) {
                    sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", projectId), Placeholder.unparsed("error", e.getMessage())));
                }
            }, downloadExecutor));
        }
        
        if (futures.isEmpty()) {
            sender.sendMessage(plugin.getMessage("status.import-empty"));
            downloadExecutor.shutdown();
        } else {
            sender.sendMessage(plugin.getMessage("status.importing-count", Placeholder.unparsed("arg", String.valueOf(futures.size()))));
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            downloadExecutor.shutdown();
            sender.sendMessage(plugin.getMessage("status.import-complete"));
        }
    }

    private void handleRemove(CommandSender sender, List<String> args) {
        if (args.isEmpty()) {
             sender.sendMessage(plugin.getMessage("usage.remove"));
            return;
        }
        String pkg = args.get(0);
        sender.sendMessage(plugin.getMessage("status.update-reading"));
        sender.sendMessage(plugin.getMessage("status.update-building"));

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
            sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", pkg)));
            return;
        }
        
        if (candidates.size() > 1) {
            sender.sendMessage(plugin.getMessage("errors.execution-error", Placeholder.unparsed("arg", "Multiple candidates found for " + pkg)));
            return;
        }
        
        File target = candidates.get(0);
        if (target.delete()) {
            sender.sendMessage(plugin.getMessage("status.removing", Placeholder.unparsed("arg", pkg + " (" + target.getName() + ")")));
            sender.sendMessage(plugin.getMessage("status.removal-complete"));
        } else {
            sender.sendMessage(plugin.getMessage("errors.remove-failed", Placeholder.unparsed("arg", target.getName())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("info", "list", "update", "search", "install", "upgrade", "remove", "help", "export", "import");
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
