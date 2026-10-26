package io.github.Earth1283.aptMc.commands.sub

import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.graph.GraphOptions
import io.github.Earth1283.aptMc.graph.GraphRenderer
import io.github.Earth1283.aptMc.graph.GraphMessages
import io.github.Earth1283.aptMc.graph.GraphException
import io.github.Earth1283.aptMc.graph.text
import io.github.Earth1283.aptMc.graph.PluginRuntimeState
import io.github.Earth1283.aptMc.graph.pluginKey
import io.github.Earth1283.aptMc.managers.AsyncExecutor
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.logging.Level

class GraphCommand(plugin: AptMc, packageManager: PackageManager) : SubCommand(plugin, packageManager) {
    private val messages = GraphMessages { key, arguments ->
        plugin.getMessage("graph.$key", *arguments.map { (name, value) -> Placeholder.component(name, value) }.toTypedArray())
    }
    // Capture settings at registration, on the server thread.
    private val renderer = GraphRenderer(
        messages,
        plugin.config.getInt("graph.page-size", 14).coerceIn(5, 40),
        plugin.config.getInt("graph.max-depth", 8).coerceIn(1, 20)
    )
    private val flags = listOf("--required-only", "--reverse", "--refresh", "--page")

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        val options = try {
            GraphOptions.parse(args)
        } catch (e: GraphException) {
            reply(sender, listOf(messages.text(e.detail)))
            return
        }
        // This command is read-only. --dry-run is equivalent to a normal inspection.
        packageManager.graphCache.get(options.refresh).thenCompose { graph ->
            val runtime = CompletableFuture<Map<String, PluginRuntimeState>>()
            plugin.server.scheduler.runTask(plugin, Runnable {
                try {
                    runtime.complete(plugin.server.pluginManager.plugins.associate {
                        pluginKey(it.name) to PluginRuntimeState(it.pluginMeta.version, it.isEnabled)
                    })
                } catch (e: Exception) {
                    runtime.completeExceptionally(e)
                }
            })
            runtime.thenApplyAsync({ states -> renderer.render(graph, states, options, sender is Player) }, AsyncExecutor.POOL)
        }.whenComplete { lines, failure ->
            if (failure == null) {
                reply(sender, lines)
            } else {
                val cause = if (failure is CompletionException) failure.cause ?: failure else failure
                if (cause !is GraphException) plugin.logger.log(Level.WARNING, "Failed to inspect plugin graph", cause)
                reply(sender, listOf(if (cause is GraphException) messages.text(cause.detail)
                    else messages.text("errors.failed", "error" to (cause.message ?: cause.javaClass.simpleName))))
            }
        }
    }

    private fun reply(sender: CommandSender, lines: List<Component>) {
        // Graphs always go to chat; action bars cannot hold a dependency tree.
        if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable { lines.forEach { sender.sendMessage(it) } })
    }

    override fun onTabComplete(sender: CommandSender, args: List<String>): List<String> {
        val current = args.lastOrNull().orEmpty()
        if (args.size >= 2 && args[args.lastIndex - 1] == "--page") return listOf("1", "2", "3").filter { it.startsWith(current) }
        // Never scan or wait on disk on the tab-completion thread.
        val names = packageManager.graphCache.peek()?.plugins?.values?.map { it.name }.orEmpty()
        return (flags + names).filter { it.startsWith(current, ignoreCase = true) }.sorted()
    }
}
