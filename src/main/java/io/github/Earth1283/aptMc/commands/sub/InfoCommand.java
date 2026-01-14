package io.github.Earth1283.aptMc.commands.sub;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.api.ModrinthAPI;
import io.github.Earth1283.aptMc.commands.SubCommand;
import io.github.Earth1283.aptMc.managers.PackageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InfoCommand extends SubCommand {

    public InfoCommand(AptMc plugin, PackageManager packageManager) {
        super(plugin, packageManager);
    }

    @Override
    public void execute(CommandSender sender, List<String> args) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("usage.info"));
            return;
        }
        String pkg = args.get(0);

        try {
            JsonObject project = ModrinthAPI.getProject(pkg);
            if (project == null) {
                sender.sendMessage(plugin.getMessage("errors.package-not-found", Placeholder.unparsed("arg", pkg)));
                return;
            }

            Map<String, String> installedPlugins = packageManager.getInstalledPlugins();
            String statusKey = "status.not-installed";

            if (!installedPlugins.isEmpty()) {
                List<String> hashes = new ArrayList<>(installedPlugins.values());
                Map<String, JsonObject> installedVersions = resolveVersions(hashes);
                
                for(JsonObject v : installedVersions.values()) {
                    if (v.get("project_id").getAsString().equals(project.get("id").getAsString())) {
                        statusKey = "status.installed";
                        break;
                    }
                }
            }
            Component statusComp = plugin.getMessage(statusKey);

            String dependencies = "None";
            try {
                JsonArray versions = ModrinthAPI.getVersions(project.get("id").getAsString(), List.of("spigot", "paper", "purpur", "bukkit"));
                if (versions.size() > 0) {
                    JsonObject latest = versions.get(0).getAsJsonObject();
                    JsonArray deps = latest.getAsJsonArray("dependencies");
                    if (deps != null && deps.size() > 0) {
                        List<String> depNames = new ArrayList<>();
                        for (JsonElement d : deps) {
                            JsonObject depObj = d.getAsJsonObject();
                            if (depObj.has("dependency_type") && depObj.get("dependency_type").getAsString().equals("required")) {
                                if (depObj.has("project_id") && !depObj.get("project_id").isJsonNull()) {
                                     depNames.add(depObj.get("project_id").getAsString());
                                }
                            }
                        }
                        if (!depNames.isEmpty()) dependencies = String.join(", ", depNames);
                    }
                }
            } catch (Exception ignored) {}

            sender.sendMessage(plugin.getMessage("status.package-info.name", Placeholder.unparsed("arg", project.get("slug").getAsString())));
            sender.sendMessage(plugin.getMessage("status.package-info.id", Placeholder.unparsed("arg", project.get("id").getAsString())));
            sender.sendMessage(plugin.getMessage("status.package-info.status", Placeholder.component("arg", statusComp)));
            sender.sendMessage(plugin.getMessage("status.package-info.description", Placeholder.unparsed("arg", project.get("description").getAsString())));
            sender.sendMessage(plugin.getMessage("status.package-info.downloads", Placeholder.unparsed("arg", String.valueOf(project.get("downloads").getAsInt()))));
            sender.sendMessage(plugin.getMessage("status.package-info.dependencies", Placeholder.unparsed("arg", dependencies)));

        } catch (Exception e) {
             sender.sendMessage(plugin.getMessage("errors.error-checking-pkg", Placeholder.unparsed("arg", pkg), Placeholder.unparsed("error", e.getMessage())));
        }
    }
}
