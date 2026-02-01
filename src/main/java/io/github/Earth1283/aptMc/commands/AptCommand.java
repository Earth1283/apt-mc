package io.github.Earth1283.aptMc.commands;

import io.github.Earth1283.aptMc.AptMc;
import io.github.Earth1283.aptMc.commands.sub.*;
import io.github.Earth1283.aptMc.listeners.ConfirmationListener;
import io.github.Earth1283.aptMc.managers.PackageManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.File;
import java.util.*;

public class AptCommand implements CommandExecutor, TabCompleter {
    private final AptMc plugin;
    private final PackageManager packageManager;
    private ConfirmationListener confirmationListener;
    private CompileCommand compileCommand;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public AptCommand(AptMc plugin) {
        this.plugin = plugin;
        this.packageManager = new PackageManager(plugin.getDataFolder(), plugin.getDataFolder().getParentFile());
        registerCommands();
    }

    public void setConfirmationListener(ConfirmationListener confirmationListener) {
        this.confirmationListener = confirmationListener;
        // Re-register install command as it depends on confirmationListener
        subCommands.put("install", new InstallCommand(plugin, packageManager, confirmationListener));
    }

    private void registerCommands() {
        subCommands.put("list", new ListCommand(plugin, packageManager));
        subCommands.put("info", new InfoCommand(plugin, packageManager));
        subCommands.put("update", new UpdateCommand(plugin, packageManager));
        subCommands.put("search", new SearchCommand(plugin, packageManager));
        subCommands.put("upgrade", new UpgradeCommand(plugin, packageManager));
        subCommands.put("remove", new RemoveCommand(plugin, packageManager));
        subCommands.put("export", new ExportCommand(plugin, packageManager));
        subCommands.put("import", new ImportCommand(plugin, packageManager));
        compileCommand = new CompileCommand(plugin, packageManager);
        subCommands.put("compile", compileCommand);
        // Install is initially null or not registered until listener is set?
        // Or we register a temporary one / null check?
        // Better to wait for listener or update logic.
        // For now, if user runs install before listener set (onEnable), it might error
        // or be missing.
        // It's fine, listener is set in onEnable immediately after.
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        SubCommand cmd = subCommands.get(sub);

        if (cmd == null) {
            sender.sendMessage(plugin.getMessage("errors.unknown-command", Placeholder.unparsed("arg", sub)));
            return true;
        }

        List<String> subArgs = new ArrayList<>(Arrays.asList(args).subList(1, args.length));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                cmd.execute(sender, subArgs);
            } catch (Exception e) {
                sender.sendMessage(
                        plugin.getMessage("errors.execution-error", Placeholder.unparsed("arg", e.getMessage())));
                e.printStackTrace();
            }
        });

        return true;
    }

    public void performImport(CommandSender sender, File file) {
        ImportCommand cmd = (ImportCommand) subCommands.get("import");
        if (cmd != null) {
            cmd.performImport(sender, file);
        }
    }

    public CompileCommand getCompileCommand() {
        return compileCommand;
    }

    private void sendHelp(CommandSender sender) {
        if (plugin.getConfig().getBoolean("apt-song-references")) {
            sender.sendMessage(plugin.getMessage("usage.song-ref"));
        }
        sender.sendMessage(plugin.getMessage("usage.header"));
        sender.sendMessage(plugin.getMessage("usage.install"));
        sender.sendMessage(plugin.getMessage("usage.remove"));
        sender.sendMessage(plugin.getMessage("usage.upgrade"));
        sender.sendMessage(plugin.getMessage("usage.search"));
        sender.sendMessage(plugin.getMessage("usage.info"));
        sender.sendMessage(plugin.getMessage("usage.list"));
        sender.sendMessage(plugin.getMessage("usage.update"));
        sender.sendMessage(plugin.getMessage("usage.export"));
        sender.sendMessage(plugin.getMessage("usage.import"));
        sender.sendMessage(plugin.getMessage("usage.compile"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(subCommands.keySet());
            options.add("help");
            return options;
        }
        // Delegate to subcommands?
        // Current implementation only did autocomplete for first arg.
        // Refactoring opportunity: check subCommands for autocomplete.
        if (args.length > 1) {
            String sub = args[0].toLowerCase();
            SubCommand cmd = subCommands.get(sub);
            if (cmd != null) {
                List<String> subArgs = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
                return cmd.onTabComplete(sender, subArgs);
            }
        }
        return Collections.emptyList();
    }
}