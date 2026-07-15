package io.github.Earth1283.aptMc.listeners

import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.commands.sub.CompileCommand
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class CompileInputListener(plugin: AptMc, private val compileCommand: CompileCommand) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncChatEvent) {
        val player = event.player

        if (compileCommand.hasPendingInput(player)) {
            event.isCancelled = true
            val message = PlainTextComponentSerializer.plainText().serialize(event.message())
            compileCommand.handlePlayerChat(player, message)
        }
    }
}
