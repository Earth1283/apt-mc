package io.github.Earth1283.aptMc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public final class AptMc extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        updateConfig("config.yml");
        updateConfig("messages.yml");
        
        getCommand("apt").setExecutor(new AptCommand(this));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void updateConfig(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
            return; // Created new file, no need to update
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        InputStream defConfigStream = getResource(fileName);
        if (defConfigStream == null) {
            return;
        }

        YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
        boolean changesMade = false;

        for (String key : defConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defConfig.get(key));
                changesMade = true;
            }
        }

        if (changesMade) {
            try {
                config.save(file);
                getLogger().info("Updated " + fileName + " with missing values.");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not save updated " + fileName, e);
            }
        }
    }
}
