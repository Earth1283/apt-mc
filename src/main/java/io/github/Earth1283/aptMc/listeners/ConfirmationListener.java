package io.github.Earth1283.aptMc.listeners;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.commands.AptCommand;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConfirmationListener implements Listener {
    private final AptMc plugin;
    private final AptCommand commandExecutor;
    private final Map<String, File> pending = new ConcurrentHashMap<>();

    public ConfirmationListener(AptMc plugin, AptCommand commandExecutor) {
        this.plugin = plugin;
        this.commandExecutor = commandExecutor;
    }

    public void addPending(CommandSender sender, File file) {
        String key = getKey(sender);
        pending.put(key, file);
        
        // Timeout task
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(key) != null) {
                sender.sendMessage(plugin.getMessage("errors.import-timeout"));
            }
        }, 45 * 20L); // 45 seconds
    }

    private String getKey(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) return "CONSOLE";
        if (sender instanceof org.bukkit.entity.Player) return ((org.bukkit.entity.Player) sender).getUniqueId().toString();
        return sender.getName();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        handleInput(event.getPlayer(), event.getMessage(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerCommand(ServerCommandEvent event) {
        handleInput(event.getSender(), event.getCommand(), event);
    }

    private void handleInput(CommandSender sender, String message, Cancellable event) {
        String key = getKey(sender);
        if (pending.containsKey(key)) {
            event.setCancelled(true);
            File file = pending.remove(key);
            
            if (message.trim().equalsIgnoreCase("y") || message.trim().equalsIgnoreCase("yes")) {
                commandExecutor.performImport(sender, file);
            } else {
                sender.sendMessage(plugin.getMessage("errors.import-cancelled"));
            }
        }
    }
}
