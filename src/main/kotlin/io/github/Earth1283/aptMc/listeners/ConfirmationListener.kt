package io.github.Earth1283.aptMc.listeners

import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.commands.AptCommand
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerCommandEvent
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ConfirmationListener(
    private val plugin: AptMc,
    private val commandExecutor: AptCommand
) : Listener {
    private val pending: MutableMap<String, File> = ConcurrentHashMap()

    fun addPending(sender: CommandSender, file: File) {
        val key = getKey(sender)
        pending[key] = file

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (pending.remove(key) != null) {
                sender.sendMessage(plugin.getMessage("errors.import-timeout"))
            }
        }, 45 * 20L)
    }

    private fun getKey(sender: CommandSender): String {
        if (sender is ConsoleCommandSender) return "CONSOLE"
        if (sender is Player) return sender.uniqueId.toString()
        return sender.name
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        handleInput(event.player, PlainTextComponentSerializer.plainText().serialize(event.message()), event)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onServerCommand(event: ServerCommandEvent) {
        handleInput(event.sender, event.command, event)
    }

    private fun handleInput(sender: CommandSender, message: String, event: Cancellable) {
        val key = getKey(sender)
        if (pending.containsKey(key)) {
            event.isCancelled = true
            val file = pending.remove(key)

            if (message.trim().equals("y", ignoreCase = true) || message.trim().equals("yes", ignoreCase = true)) {
                commandExecutor.performImport(sender, file!!)
            } else {
                sender.sendMessage(plugin.getMessage("errors.import-cancelled"))
            }
        }
    }
}
