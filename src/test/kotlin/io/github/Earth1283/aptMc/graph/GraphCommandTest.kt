package io.github.Earth1283.aptMc.graph

import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.commands.sub.GraphCommand
import io.github.Earth1283.aptMc.managers.PackageManager
import io.papermc.paper.plugin.configuration.PluginMeta
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.Server
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Answers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.logging.Logger

class GraphCommandTest {
    @TempDir lateinit var directory: File

    @Test
    fun `runtime inspection and delivery are scheduled on main thread while rendering stays off it`() {
        val mainThread = Thread.currentThread()
        val messages = TestGraphMessages()
        val plugin = mock(AptMc::class.java) { invocation ->
            if (invocation.method.name == "getMessage") {
                // Rendering templates should be evaluated by a graph worker, never the tick thread.
                assertNotSame(mainThread, Thread.currentThread())
                val key = invocation.rawArguments[0] as String
                @Suppress("UNCHECKED_CAST")
                val resolvers = invocation.rawArguments[1] as Array<TagResolver>
                MiniMessage.miniMessage().deserialize(messages.yaml.getString(key)!!, *resolvers)
            } else Answers.RETURNS_DEFAULTS.answer(invocation)
        }
        val server = mock(Server::class.java)
        val manager = mock(PluginManager::class.java)
        val scheduler = mock(GlobalRegionScheduler::class.java)
        val task = mock(ScheduledTask::class.java)
        val sender = mock(CommandSender::class.java)
        val runtime = mock(Plugin::class.java)
        val metadata = mock(PluginMeta::class.java)
        val mainTasks = LinkedBlockingQueue<Runnable>()
        val delivered = mutableListOf<Component>()
        `when`(plugin.config).thenReturn(YamlConfiguration())
        `when`(plugin.server).thenReturn(server)
        `when`(plugin.isEnabled).thenReturn(true)
        `when`(plugin.logger).thenReturn(Logger.getAnonymousLogger())
        `when`(server.pluginManager).thenReturn(manager)
        `when`(server.globalRegionScheduler).thenReturn(scheduler)
        `when`(runtime.name).thenReturn("Example")
        `when`(runtime.pluginMeta).thenReturn(metadata)
        `when`(runtime.isEnabled).thenReturn(true)
        `when`(metadata.version).thenReturn("1.0")
        `when`(manager.plugins).thenAnswer {
            assertSame(mainThread, Thread.currentThread())
            arrayOf(runtime)
        }
        doAnswer { invocation ->
            val consumer = invocation.getArgument<Consumer<ScheduledTask>>(1)
            mainTasks.add(Runnable { consumer.accept(task) })
            task
        }.`when`(scheduler).run(eq(plugin), any<Consumer<ScheduledTask>>())
        doAnswer { invocation ->
            assertSame(mainThread, Thread.currentThread())
            delivered.add(invocation.getArgument(0))
            null
        }.`when`(sender).sendMessage(any(Component::class.java))

        writePluginJar(directory, "Example.jar", "plugin.yml" to "name: Example\nversion: '1.0'")
        val packages = PackageManager(File(directory, "apt-mc"), directory, Logger.getAnonymousLogger())
        val command = GraphCommand(plugin, packages)
        // Cold tab completion must not trigger a scan.
        command.onTabComplete(sender, listOf(""))
        assertNull(packages.graphCache.peek())
        command.execute(sender, emptyList(), false)
        val snapshot = mainTasks.poll(10, TimeUnit.SECONDS)
        assertNotNull(snapshot)
        verify(manager, never()).plugins
        snapshot!!.run()
        val delivery = mainTasks.poll(10, TimeUnit.SECONDS)
        assertNotNull(delivery)
        delivery!!.run()
        assertTrue(delivered.any { plain(it).contains("Example 1.0 [enabled]") }, delivered.joinToString { plain(it) })
        assertTrue(command.onTabComplete(sender, listOf("Ex")).contains("Example"))
        assertTrue(mainTasks.isEmpty())
    }
}
