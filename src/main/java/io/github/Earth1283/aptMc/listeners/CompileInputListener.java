package io.github.Earth1283.aptMc.listeners;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.commands.sub.CompileCommand;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class CompileInputListener implements Listener {
    private final CompileCommand compileCommand;

    public CompileInputListener(AptMc plugin, CompileCommand compileCommand) {
        this.compileCommand = compileCommand;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Check if this player has a pending compile input
        if (compileCommand.hasPendingInput(player)) {
            event.setCancelled(true); // Don't broadcast their response
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());
            compileCommand.handlePlayerChat(player, message);
        }
    }
}
