package io.github.Earth1283.aptMc.commands.sub;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.api.ModrinthAPI;
import io.github.Earth1283.aptMc.commands.SubCommand;
import io.github.Earth1283.aptMc.managers.PackageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ImportCommand extends SubCommand {

    public ImportCommand(AptMc plugin, PackageManager packageManager) {
        super(plugin, packageManager);
    }

    @Override
    public void execute(CommandSender sender, List<String> args, boolean dryRun) {
        String filename = args.isEmpty() ? "apt-manifest.yml" : args.get(0);
        if (!filename.endsWith(".yml")) filename += ".yml";
        File file = new File(plugin.getDataFolder(), filename);

        if (!file.exists()) {
             sender.sendMessage(plugin.getMessage("errors.manifest-not-found", Placeholder.unparsed("arg", filename)));
             return;
        }
        
        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Import from " + filename)));
            return;
        }

        performImport(sender, file);
    }


    
    public void performImport(CommandSender sender, File file) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLogger().info("Starting import process for " + file.getName());
                FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                if (!yaml.contains("plugins") && !yaml.contains("configs")) { // allow config-only backup?
                     plugin.getLogger().warning("Manifest invalid (no plugins/configs): " + file.getName());
                     sender.sendMessage(plugin.getMessage("errors.invalid-manifest"));
                     return;
                }
                
                plugin.getLogger().info("Manifest valid. Reading content...");
                sender.sendMessage(plugin.getMessage("status.reading-manifest"));
                
                if (yaml.contains("project-details")) {
                    String title = yaml.getString("project-details.title", "Unknown Project");
                    String author = yaml.getString("project-details.author", "Unknown Author");
                    sender.sendMessage(plugin.getMessage("status.import-header", Placeholder.unparsed("title", title), Placeholder.unparsed("author", author)));
                }
                
                // Handle unrecognised plugins warning
                if (yaml.contains("unrecognised")) {
                    List<String> unrecognised = yaml.getStringList("unrecognised");
                    if (!unrecognised.isEmpty()) {
                        sender.sendMessage(Component.text("The following plugins are unrecognised and must be installed manually:", NamedTextColor.YELLOW));
                        for (String p : unrecognised) {
                            sender.sendMessage(Component.text(" - " + p, NamedTextColor.WHITE));
                        }
                    }
                }

                List<CompletableFuture<Void>> futures = new ArrayList<>();
                final int[] installedCount = {0};
                final java.util.concurrent.ExecutorService downloadExecutor = yaml.contains("plugins") ? java.util.concurrent.Executors.newFixedThreadPool(3) : null;
                
                if (yaml.contains("plugins")) {
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
                                
                                String url = primary.get("url").getAsString();
                                String fname = primary.get("filename").getAsString();
                                long size = primary.get("size").getAsLong();
                                
                                // Check if installed
                                String sha1 = primary.getAsJsonObject("hashes").get("sha1").getAsString();
                                if (packageManager.getInstalledPlugins().containsValue(sha1)) {
                                    return; // Already installed
                                }

                                sendStatus(sender, plugin.getMessage("status.downloading", Placeholder.unparsed("arg", fname)));
                                packageManager.downloadFile(url, packageManager.getPluginsDir(), fname, size, createProgressCallback(sender, fname));
                                sendStatus(sender, plugin.getMessage("status.downloaded", Placeholder.unparsed("arg", fname)));
                                synchronized(installedCount) { installedCount[0]++; }
                                
                            } catch (Exception e) {
                                 sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", projectId), Placeholder.unparsed("error", e.getMessage())));
                            }
                        }, downloadExecutor));
                    }
                    sender.sendMessage(plugin.getMessage("status.importing-count", Placeholder.unparsed("arg", String.valueOf(futures.size()))));
                }
                
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                    if (downloadExecutor != null) {
                        downloadExecutor.shutdown();
                    }

                    // Restore Configs
                    if (yaml.contains("configs")) {
                         sendStatus(sender, plugin.getMessage("exporting-configs")); // Re-using message for now
                         List<Map<?, ?>> configs = yaml.getMapList("configs");
                         for (Map<?, ?> entry : configs) {
                             String pluginName = (String) entry.get("plugin");
                             String subPath = (String) entry.get("path");
                             String b64 = (String) entry.get("data");
                             
                             if (pluginName != null && subPath != null && b64 != null) {
                                try {
                                     byte[] data = Base64.getDecoder().decode(b64);
                                     File targetFile = new File(plugin.getDataFolder().getParentFile(), pluginName + File.separator + subPath);
                                     if (!targetFile.getParentFile().exists()) targetFile.getParentFile().mkdirs();
                                     
                                     try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                                         fos.write(data);
                                     }
                                } catch (Exception e) {
                                     sender.sendMessage(plugin.getMessage("errors.install-failed", Placeholder.unparsed("arg", "Config " + pluginName + "/" + subPath), Placeholder.unparsed("error", e.getMessage())));
                                }
                             }
                         }
                    }

                    if (installedCount[0] > 0 || yaml.contains("configs")) { // If plugins were installed OR configs were present
                         sender.sendMessage(plugin.getMessage("status.import-complete"));
                    } else {
                         sender.sendMessage(plugin.getMessage("status.import-empty"));
                    }
                });
            } catch (Exception e) {
                sender.sendMessage(plugin.getMessage("errors.execution-error", Placeholder.unparsed("arg", e.getMessage())));
                e.printStackTrace();
            }
        });
    }
}
