package io.github.Earth1283.aptMc;

import org.bukkit.plugin.java.JavaPlugin;

public final class AptMc extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        getCommand("apt").setExecutor(new AptCommand(this));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
