package io.github.Earth1283.aptMc.commands.sub;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.api.HangarAPI;
import io.github.Earth1283.aptMc.api.ModrinthAPI;
import io.github.Earth1283.aptMc.commands.SubCommand;
import io.github.Earth1283.aptMc.listeners.ConfirmationListener;
import io.github.Earth1283.aptMc.managers.PackageManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class InstallCommand extends SubCommand {
    private final ConfirmationListener confirmationListener;

    public InstallCommand(AptMc plugin, PackageManager packageManager, ConfirmationListener confirmationListener) {
        super(plugin, packageManager);
        this.confirmationListener = confirmationListener;
    }

    @Override
    public void execute(CommandSender sender, List<String> args, boolean dryRun) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("errors.no-packages-specified"));
            return;
        }

        // Remote install check
        if (args.size() == 1 && args.get(0).matches("^(http|https|ftp)://.*\\.yml$")) {
            String url = args.get(0);
            if (dryRun) {
                sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Import manifest from " + url)));
                return;
            }
            try {
                sendStatus(sender, plugin.getMessage("status.downloading", Placeholder.unparsed("arg", "manifest")));
                File tempDir = new File(plugin.getDataFolder(), "temp");
                if (!tempDir.exists()) tempDir.mkdirs();
                File tempFile = new File(tempDir, "remote_import_" + System.currentTimeMillis() + ".yml");
                
                // Use HttpClient for robust downloading with User-Agent
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .header("User-Agent", "apt-mc/1.2 (parody-cli)")
                        .GET()
                        .build();

                java.net.http.HttpResponse<java.nio.file.Path> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofFile(tempFile.toPath()));
                
                if (response.statusCode() != 200) {
                    sender.sendMessage(plugin.getMessage("errors.fetch-manifest-failed", Placeholder.unparsed("error", "HTTP " + response.statusCode())));
                    return;
                }

                if (!tempFile.exists() || tempFile.length() == 0) {
                    sender.sendMessage(plugin.getMessage("errors.fetch-manifest-failed", Placeholder.unparsed("error", "File empty or missing")));
                    return;
                }
                
                FileConfiguration yaml = YamlConfiguration.loadConfiguration(tempFile);
                String title = yaml.getString("project-details.title", "Unknown");
                String author = yaml.getString("project-details.author", "Unknown");
                
                sender.sendMessage(plugin.getMessage("status.import-header", Placeholder.unparsed("title", title), Placeholder.unparsed("author", author)));
                sender.sendMessage(plugin.getMessage("status.confirm-prompt"));
                
                if (confirmationListener != null) {
                    confirmationListener.addPending(sender, tempFile);
                } else {
                    sender.sendMessage(plugin.getMessage("errors.listener-missing"));
                }
                
            } catch (Exception e) {
                sender.sendMessage(plugin.getMessage("errors.fetch-manifest-failed", Placeholder.unparsed("error", e.getMessage())));
            }
            return;
        }

        packageManager.ensureDir();
        sendStatus(sender, plugin.getMessage("status.update-reading"));
        sendStatus(sender, plugin.getMessage("status.update-building"));

        Set<String> installedIds = getInstalledProjectIds();
        Map<String, JsonObject> toInstallMap = new HashMap<>();
        List<String> priority = plugin.getConfig().getStringList("source-priority");
        if (priority.isEmpty()) priority = List.of("modrinth", "hangar", "github");

        for (String pkgSlug : args) {
            boolean found = false;
            try {
                // Check if it's a GitHub slug (owner/repo)
                if (pkgSlug.contains("/") && !pkgSlug.startsWith("http")) {
                    sendStatus(sender, plugin.getMessage("status.checking", Placeholder.unparsed("arg", pkgSlug + " on GitHub")));
                    JsonObject release = io.github.Earth1283.aptMc.api.GitHubAPI.getLatestRelease(pkgSlug);
                    if (release != null) {
                        JsonObject asset = io.github.Earth1283.aptMc.api.GitHubAPI.getPrimaryJarAsset(release);
                        if (asset != null) {
                            JsonObject githubInfo = new JsonObject();
                            githubInfo.addProperty("source", "github");
                            githubInfo.addProperty("slug", pkgSlug);
                            githubInfo.addProperty("download_url", asset.get("browser_download_url").getAsString());
                            githubInfo.addProperty("filename", asset.get("name").getAsString());
                            githubInfo.addProperty("size", asset.get("size").getAsLong());
                            toInstallMap.put("github:" + pkgSlug, githubInfo);
                            found = true;
                        }
                    }
                }

                if (!found) {
                    for (String source : priority) {
                        if (source.equalsIgnoreCase("modrinth")) {
                            sendStatus(sender, plugin.getMessage("status.checking", Placeholder.unparsed("arg", pkgSlug + " on Modrinth")));
                            JsonObject project = ModrinthAPI.getProject(pkgSlug);
                            if (project != null) {
                                resolveDependenciesRecursive(sender, project, toInstallMap, installedIds);
                                found = true;
                                break;
                            }
                        } else if (source.equalsIgnoreCase("hangar") && plugin.getConfig().getBoolean("enable-hangar", true)) {
                            sendStatus(sender, plugin.getMessage("status.checking", Placeholder.unparsed("arg", pkgSlug + " on Hangar")));
                            JsonObject hangarProject = HangarAPI.getProject(pkgSlug);
                            if (hangarProject != null) {
                                JsonObject versionInfo = HangarAPI.getLatestVersion(pkgSlug, "PAPER");
                                if (versionInfo != null) {
                                    versionInfo.addProperty("source", "hangar");
                                    versionInfo.addProperty("hangar_slug", hangarProject.get("name").getAsString());
                                    toInstallMap.put("hangar:" + hangarProject.get("name").getAsString(), versionInfo);
                                    found = true;
                                    break;
                                }
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

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run-header"));
            for (JsonObject pkg : toInstall) {
                String name = getPkgName(pkg);
                sender.sendMessage(plugin.getMessage("status.install-item", Placeholder.unparsed("arg", name)));
            }
            sender.sendMessage(plugin.getMessage("status.dry-run-complete"));
            return;
        }

        if (plugin.getConfig().getBoolean("apt-song-references")) {
            sender.sendMessage(plugin.getMessage("status.install-song-ref")); 
        }

        sender.sendMessage(plugin.getMessage("status.install-header"));
        for (JsonObject pkg : toInstall) {
            String name = getPkgName(pkg);
            sender.sendMessage(plugin.getMessage("status.install-item", Placeholder.unparsed("arg", name)));
        }
        sender.sendMessage(plugin.getMessage("status.install-summary", Placeholder.unparsed("arg", String.valueOf(toInstall.size()))));

        List<CompletableFuture<Void>> installFutures = toInstall.stream()
                .map(pkg -> CompletableFuture.runAsync(() -> {
                    String source = pkg.has("source") ? pkg.get("source").getAsString() : "modrinth";
                    
                    if (source.equals("github")) {
                        try {
                            String url = pkg.get("download_url").getAsString();
                            String filename = pkg.get("filename").getAsString();
                            long size = pkg.get("size").getAsLong();
                            
                            sendStatus(sender, plugin.getMessage("status.downloading", Placeholder.unparsed("arg", filename)));
                            packageManager.downloadFile(url, packageManager.getPluginsDir(), filename, size, createProgressCallback(sender, filename));
                            sendStatus(sender, plugin.getMessage("status.downloaded", Placeholder.unparsed("arg", filename)));
                        } catch (Exception e) {
                             sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", pkg.get("slug").getAsString()), Placeholder.unparsed("error", e.getMessage())));
                        }
                        return;
                    }

                    if (source.equals("hangar")) {
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

    private String getPkgName(JsonObject pkg) {
        if (pkg.has("slug")) return pkg.get("slug").getAsString();
        if (pkg.has("hangar_slug")) return pkg.get("hangar_slug").getAsString();
        return "unknown";
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
}
