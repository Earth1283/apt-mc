package io.github.Earth1283.aptMc

import io.github.Earth1283.aptMc.commands.AptCommand
import io.github.Earth1283.aptMc.listeners.CompileInputListener
import io.github.Earth1283.aptMc.listeners.ConfirmationListener
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.logging.Level

class AptMc : JavaPlugin() {

    override fun onEnable() {
        updateConfig("config.yml")
        updateConfig("messages.yml")

        val aptCommand = AptCommand(this)
        val confirmationListener = ConfirmationListener(this, aptCommand)
        aptCommand.setConfirmationListener(confirmationListener)
        server.pluginManager.registerEvents(confirmationListener, this)

        val compileInputListener = CompileInputListener(this, aptCommand.getCompileCommand())
        server.pluginManager.registerEvents(compileInputListener, this)

        getCommand("apt")!!.setExecutor(aptCommand)
    }

    override fun onDisable() {
    }

    fun getMessage(key: String, vararg placeholders: TagResolver): Component {
        val messagesFile = File(dataFolder, "messages.yml")
        val messages = YamlConfiguration.loadConfiguration(messagesFile)
        val raw = messages.getString(key) ?: return Component.text("Missing message: $key")
        return MiniMessage.miniMessage().deserialize(raw, *placeholders)
    }

    private fun updateConfig(fileName: String) {
        val file = File(dataFolder, fileName)
        if (!file.exists()) {
            saveResource(fileName, false)
            return
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val defConfigStream = getResource(fileName) ?: return

        val defConfig = YamlConfiguration.loadConfiguration(InputStreamReader(defConfigStream, StandardCharsets.UTF_8))
        var changesMade = false

        for (key in defConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defConfig.get(key))
                changesMade = true
            }
        }

        if (changesMade) {
            try {
                config.save(file)
                logger.info("Updated $fileName with missing values.")
            } catch (e: IOException) {
                logger.log(Level.SEVERE, "Could not save updated $fileName", e)
            }
        }
    }
}
