package io.github.Earth1283.aptMc.commands.sub;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.commands.SubCommand;
import io.github.Earth1283.aptMc.managers.PackageManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListCommand extends SubCommand {

    public ListCommand(AptMc plugin, PackageManager packageManager) {
        super(plugin, packageManager);
    }

    @Override
    public void execute(CommandSender sender, List<String> args, boolean dryRun) {
        packageManager.ensureDir();
        Map<String, String> installedPlugins = packageManager.getInstalledPlugins();

        if (installedPlugins.isEmpty()) {
            sender.sendMessage(plugin.getMessage("status.list-empty"));
            return;
        }

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run", Placeholder.unparsed("arg", "List installed plugins")));
            return;
        }

        sender.sendMessage(plugin.getMessage("status.listing"));

        try {
            Map<String, JsonObject> versionsMap = resolveVersions(new ArrayList<>(installedPlugins.values()));
            
            for (Map.Entry<String, String> entry : installedPlugins.entrySet()) {
                String filename = entry.getKey();
                String sha1 = entry.getValue();
                
                String pkgName = filename;
                String verNum = "unknown";
                
                if (versionsMap.containsKey(sha1)) {
                    JsonObject v = versionsMap.get(sha1);
                    pkgName = v.get("project_id").getAsString();
                    verNum = v.get("version_number").getAsString();
                }
                
                sender.sendMessage(plugin.getMessage("status.list-item", Placeholder.unparsed("arg1", pkgName), Placeholder.unparsed("arg2", verNum)));
            }
        } catch (Exception e) {
            sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", "list"), Placeholder.unparsed("error", e.getMessage())));
        }
    }

}
