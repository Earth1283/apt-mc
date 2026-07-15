package io.github.Earth1283.aptMc.commands.sub

import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import java.io.File

class RemoveCommand(plugin: AptMc, packageManager: PackageManager) : SubCommand(plugin, packageManager) {

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("usage.remove"))
            return
        }
        val pkg = args[0]
        sender.sendMessage(plugin.getMessage("status.update-reading"))
        sender.sendMessage(plugin.getMessage("status.update-building"))

        val files = packageManager.getPluginsDir().listFiles()
        val candidates = ArrayList<File>()
        if (files != null) {
            for (f in files) {
                if (f.isFile && f.name.endsWith(".jar") && f.name.lowercase().contains(pkg.lowercase())) {
                    candidates.add(f)
                }
            }
        }

        if (candidates.isEmpty()) {
            sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", pkg)))
            return
        }

        if (candidates.size > 1) {
            sender.sendMessage(plugin.getMessage("errors.execution-error", Placeholder.unparsed("arg", "Multiple candidates found for $pkg")))
            return
        }

        val target = candidates[0]
        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "Remove " + target.name)))
            return
        }

        if (target.delete()) {
            sender.sendMessage(plugin.getMessage("status.removing", Placeholder.unparsed("arg", pkg + " (" + target.name + ")")))
            sender.sendMessage(plugin.getMessage("status.removal-complete"))
        } else {
            sender.sendMessage(plugin.getMessage("errors.remove-failed", Placeholder.unparsed("arg", target.name)))
        }
    }
}
