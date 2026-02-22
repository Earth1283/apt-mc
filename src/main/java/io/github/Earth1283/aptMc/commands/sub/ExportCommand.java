package io.github.Earth1283.aptMc.commands.sub;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.commands.SubCommand;
import io.github.Earth1283.aptMc.managers.PackageManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class ExportCommand extends SubCommand {

    public ExportCommand(AptMc plugin, PackageManager packageManager) {
        super(plugin, packageManager);
    }

    @Override
    public void execute(CommandSender sender, List<String> args, boolean dryRun) {
        String filename = args.isEmpty() ? "apt-manifest.yml" : args.get(0);
        if (!filename.endsWith(".yml")) filename += ".yml";
        File file = new File(plugin.getDataFolder(), filename);

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Export to " + filename)));
            return;
        }

        sendStatus(sender, plugin.getMessage("status.exporting", Placeholder.unparsed("arg", filename)));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, String> installed = packageManager.getInstalledPlugins();
            if (installed.isEmpty()) {
                sender.sendMessage(plugin.getMessage("status.export-empty"));
                return;
            }

            Map<String, JsonObject> versions;
            try {
                versions = resolveVersions(new ArrayList<>(installed.values()));
            } catch (Exception e) {
                sender.sendMessage(plugin.getMessage("status.export-failed", Placeholder.unparsed("error", "Failed to resolve versions: " + e.getMessage())));
                return;
            }

            FileConfiguration yaml = new YamlConfiguration();

            // Add Header
            yaml.set("project-details.title", "Server Export");
            yaml.set("project-details.author", sender.getName());
            yaml.set("project-details.created", System.currentTimeMillis());

            List<Map<String, String>> pluginList = new ArrayList<>();
            List<String> unrecognisedList = new ArrayList<>();
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
                } else {
                    unrecognisedList.add(pluginFilename);
                }
            }
            yaml.set("plugins", pluginList);
            if (!unrecognisedList.isEmpty()) {
                yaml.set("unrecognised", unrecognisedList);
            }

            // Config bundling
            sendStatus(sender, plugin.getMessage("exporting-configs")); // Fixed key name
            Map<String, Map<String, String>> configsMap = new HashMap<>();
            File pluginsDir = plugin.getDataFolder().getParentFile();
            
            // Count total files first for progress
            int totalFiles = countFilesRecursive(pluginsDir, pluginsDir);
            java.util.concurrent.atomic.AtomicInteger processedFiles = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.function.Consumer<Double> progressCallback = createProgressCallback(sender, "Exporting Configs");
            
            exportConfigsRecursive(pluginsDir, pluginsDir, configsMap, processedFiles, totalFiles, progressCallback);
            progressCallback.accept(1.0); // Ensure 100% at end
            
            List<Map<String, String>> configExportList = new ArrayList<>();
            for (Map.Entry<String, Map<String, String>> entry : configsMap.entrySet()) {
                String pluginName = entry.getKey();
                for (Map.Entry<String, String> fileEntry : entry.getValue().entrySet()) {
                   Map<String, String> cMap = new LinkedHashMap<>();
                   cMap.put("plugin", pluginName);
                   cMap.put("path", fileEntry.getKey());
                   cMap.put("data", fileEntry.getValue());
                   configExportList.add(cMap);
                }
            }
            yaml.set("configs", configExportList);


            try {
                yaml.save(file);
                sender.sendMessage(plugin.getMessage("status.export-success", Placeholder.unparsed("arg1", String.valueOf(count)), Placeholder.unparsed("arg2", file.getPath())));
                if (!unrecognisedList.isEmpty()) {
                    sender.sendMessage(plugin.getMessage("status.export-unrecognised-warning", Placeholder.unparsed("arg", String.valueOf(unrecognisedList.size()))));
                }
            } catch (IOException e) {
                sender.sendMessage(plugin.getMessage("status.export-failed", Placeholder.unparsed("error", e.getMessage())));
            }
        });
    }

    private int countFilesRecursive(File dir, File root) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;

        for (File f : files) {
            if (f.isDirectory()) {
                // Skip apt-mc data folder
                if (f.equals(plugin.getDataFolder())) continue;
                count += countFilesRecursive(f, root);
            } else {
                if (shouldExport(f)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void exportConfigsRecursive(File dir, File root, Map<String, Map<String, String>> configs, java.util.concurrent.atomic.AtomicInteger processed, int total, java.util.function.Consumer<Double> callback) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
           if (f.isDirectory()) {
                if (f.equals(plugin.getDataFolder())) continue;
                exportConfigsRecursive(f, root, configs, processed, total, callback);
           } else {
               if (shouldExport(f)) {
                   try {
                       byte[] content = new byte[(int) f.length()];
                       try (FileInputStream fis = new FileInputStream(f)) {
                           fis.read(content);
                       }
                       String b64 = Base64.getEncoder().encodeToString(content);
                       
                       // Determine plugin name from path relative to plugins dir
                       // Structure: plugins/PluginName/config.yml
                       String relPath = f.getPath().substring(root.getPath().length() + 1);
                       int firstSlash = relPath.indexOf(File.separator);
                       if (firstSlash != -1) {
                           String pluginName = relPath.substring(0, firstSlash);
                           String subPath = relPath.substring(firstSlash + 1);
                           
                           configs.computeIfAbsent(pluginName, k -> new HashMap<>()).put(subPath, b64);
                       }
                   } catch (Exception e) {
                       plugin.getLogger().warning("Failed to export config: " + f.getName());
                   }
                   
                   int p = processed.incrementAndGet();
                   if (total > 0 && p % 5 == 0) { // Update every 5 files to reduce spam
                        callback.accept((double) p / total);
                   }
               }
           }
        }
    }

    private boolean shouldExport(File f) {
        FileConfiguration config = plugin.getConfig();
        String mode = config.getString("export.filter-mode", "blacklist");
        List<String> extensions = config.getStringList("export.extensions");
        String name = f.getName();
        String ext = "";
        int i = name.lastIndexOf('.');
        if (i > 0) ext = name.substring(i + 1);

        if (mode.equalsIgnoreCase("whitelist")) {
             return extensions.contains(ext);
        } else {
             return !extensions.contains(ext);
        }
    }

}
