package io.github.Earth1283.aptMc.commands

import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.commands.sub.CompileCommand
import io.github.Earth1283.aptMc.commands.sub.ExportCommand
import io.github.Earth1283.aptMc.commands.sub.ImportCommand
import io.github.Earth1283.aptMc.commands.sub.InfoCommand
import io.github.Earth1283.aptMc.commands.sub.InstallCommand
import io.github.Earth1283.aptMc.commands.sub.ListCommand
import io.github.Earth1283.aptMc.commands.sub.RemoveCommand
import io.github.Earth1283.aptMc.commands.sub.SearchCommand
import io.github.Earth1283.aptMc.commands.sub.UpdateCommand
import io.github.Earth1283.aptMc.commands.sub.UpgradeCommand
import io.github.Earth1283.aptMc.listeners.ConfirmationListener
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.io.File
import java.util.logging.Level

class AptCommand(private val plugin: AptMc) : CommandExecutor, TabCompleter {
    private val packageManager: PackageManager = PackageManager(plugin.dataFolder, plugin.dataFolder.parentFile, plugin.logger)
    private var confirmationListener: ConfirmationListener? = null
    private lateinit var compileCommand: CompileCommand
    private val subCommands = HashMap<String, SubCommand>()

    init {
        registerCommands()
    }

    fun setConfirmationListener(confirmationListener: ConfirmationListener) {
        this.confirmationListener = confirmationListener
        subCommands["install"] = InstallCommand(plugin, packageManager, confirmationListener)
    }

    private fun registerCommands() {
        subCommands["list"] = ListCommand(plugin, packageManager)
        subCommands["info"] = InfoCommand(plugin, packageManager)
        subCommands["update"] = UpdateCommand(plugin, packageManager)
        subCommands["search"] = SearchCommand(plugin, packageManager)
        subCommands["upgrade"] = UpgradeCommand(plugin, packageManager)
        subCommands["remove"] = RemoveCommand(plugin, packageManager)
        subCommands["export"] = ExportCommand(plugin, packageManager)
        subCommands["import"] = ImportCommand(plugin, packageManager)
        val compile = CompileCommand(plugin, packageManager)
        compileCommand = compile
        subCommands["compile"] = compile
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args[0].equals("help", ignoreCase = true)) {
            sendHelp(sender)
            return true
        }

        val sub = args[0].lowercase()
        val cmd = subCommands[sub]

        if (cmd == null) {
            sender.sendMessage(plugin.getMessage("errors.unknown-command", Placeholder.unparsed("arg", sub)))
            return true
        }

        val subArgs = ArrayList(args.asList().subList(1, args.size))
        val dryRun = subArgs.removeIf { it.equals("--dry-run", ignoreCase = true) }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                cmd.execute(sender, subArgs, dryRun)
            } catch (e: Exception) {
                sender.sendMessage(
                    plugin.getMessage("errors.execution-error", Placeholder.unparsed("arg", e.message ?: "")))
                plugin.logger.log(Level.SEVERE, "Unhandled exception in apt command", e)
            }
        })

        return true
    }

    fun performImport(sender: CommandSender, file: File) {
        val cmd = subCommands["import"] as? ImportCommand
        if (cmd != null) {
            cmd.performImport(sender, file)
        }
    }

    fun getCompileCommand(): CompileCommand {
        return compileCommand
    }

    private fun sendHelp(sender: CommandSender) {
        if (plugin.getConfig().getBoolean("apt-song-references")) {
            sender.sendMessage(plugin.getMessage("usage.song-ref"))
        }
        sender.sendMessage(plugin.getMessage("usage.header"))
        sender.sendMessage(plugin.getMessage("usage.install"))
        sender.sendMessage(plugin.getMessage("usage.remove"))
        sender.sendMessage(plugin.getMessage("usage.upgrade"))
        sender.sendMessage(plugin.getMessage("usage.search"))
        sender.sendMessage(plugin.getMessage("usage.info"))
        sender.sendMessage(plugin.getMessage("usage.list"))
        sender.sendMessage(plugin.getMessage("usage.update"))
        sender.sendMessage(plugin.getMessage("usage.export"))
        sender.sendMessage(plugin.getMessage("usage.import"))
        sender.sendMessage(plugin.getMessage("usage.compile"))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            val options = ArrayList(subCommands.keys)
            options.add("help")
            return options
        }
        if (args.size > 1) {
            val sub = args[0].lowercase()
            val cmd = subCommands[sub]
            if (cmd != null) {
                val subArgs = ArrayList(args.asList().subList(1, args.size))
                return cmd.onTabComplete(sender, subArgs)
            }
        }
        return emptyList()
    }
}
