package io.github.Earth1283.aptMc.commands.sub;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.commands.SubCommand;
import io.github.Earth1283.aptMc.managers.PackageManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RemoveCommand extends SubCommand {

    public RemoveCommand(AptMc plugin, PackageManager packageManager) {
        super(plugin, packageManager);
    }

    @Override
    public void execute(CommandSender sender, List<String> args) {
        if (args.isEmpty()) {
             sender.sendMessage(plugin.getMessage("usage.remove"));
            return;
        }
        String pkg = args.get(0);
        sender.sendMessage(plugin.getMessage("status.update-reading"));
        sender.sendMessage(plugin.getMessage("status.update-building"));

        File[] files = packageManager.getPluginsDir().listFiles();
        List<File> candidates = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".jar") && f.getName().toLowerCase().contains(pkg.toLowerCase())) {
                    candidates.add(f);
                }
            }
        }
        
        if (candidates.isEmpty()) {
            sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", pkg)));
            return;
        }
        
        if (candidates.size() > 1) {
            sender.sendMessage(plugin.getMessage("errors.execution-error", Placeholder.unparsed("arg", "Multiple candidates found for " + pkg)));
            return;
        }
        
        File target = candidates.get(0);
        if (target.delete()) {
            sender.sendMessage(plugin.getMessage("status.removing", Placeholder.unparsed("arg", pkg + " (" + target.getName() + ")")));
            sender.sendMessage(plugin.getMessage("status.removal-complete"));
        } else {
            sender.sendMessage(plugin.getMessage("errors.remove-failed", Placeholder.unparsed("arg", target.getName())));
        }
    }
}
