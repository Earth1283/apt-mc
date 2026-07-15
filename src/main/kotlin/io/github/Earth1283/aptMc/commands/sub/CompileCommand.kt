package io.github.Earth1283.aptMc.commands.sub

import io.github.Earth1283.aptMc.AptMc
import io.github.Earth1283.aptMc.commands.SubCommand
import io.github.Earth1283.aptMc.commands.sub.compile.BuildRunner
import io.github.Earth1283.aptMc.commands.sub.compile.BuildRunner.BuildSystem
import io.github.Earth1283.aptMc.commands.sub.compile.GitOperations
import io.github.Earth1283.aptMc.managers.PackageManager
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.eclipse.jgit.api.errors.GitAPIException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.logging.Level

class CompileCommand(plugin: AptMc, packageManager: PackageManager) : SubCommand(plugin, packageManager) {

    private val pendingInputs = ConcurrentHashMap<Player, CompletableFuture<String>>()

    override fun execute(sender: CommandSender, args: List<String>, dryRun: Boolean) {
        if (args.isEmpty()) {
            sender.sendMessage(plugin.getMessage("errors.compile-no-url"))
            return
        }

        val gitUrl = args[0]
        var branch = "main"
        var i = 1
        while (i < args.size) {
            if (args[i].equals("--branch", ignoreCase = true) && i + 1 < args.size) {
                i++
                branch = args[i]
            }
            i++
        }

        if (!GitOperations.isValidGitUrl(gitUrl)) {
            sender.sendMessage(plugin.getMessage("errors.compile-invalid-url", Placeholder.unparsed("arg", gitUrl)))
            return
        }

        if (dryRun) {
            sender.sendMessage(plugin.getMessage("status.dry-run",
                Placeholder.unparsed("arg", "Compile from $gitUrl (branch: $branch)")))
            return
        }

        compileFromGit(sender, gitUrl, branch, sender is Player)
    }

    private fun compileFromGit(sender: CommandSender, gitUrl: String, branch: String, interactive: Boolean) {
        var cacheDir = plugin.getConfig().getString("compile.cache-directory", "~/plugins/apt-mc/build-caches")!!
        if (cacheDir.startsWith("~")) {
            val serverRoot = plugin.server.worldContainer.absoluteFile
            cacheDir = cacheDir.replace("~", serverRoot.absolutePath)
        }

        val cacheRoot = File(cacheDir)
        if (!cacheRoot.exists()) cacheRoot.mkdirs()

        val repoName = GitOperations.extractRepoName(gitUrl)
        val repoDir = File(cacheRoot, repoName + "_" + System.currentTimeMillis())

        try {
            sendStatus(sender, plugin.getMessage("status.compile-cloning", Placeholder.unparsed("arg", gitUrl)))
            val timeout = plugin.getConfig().getInt("compile.clone-timeout", 300)
            val depth = plugin.getConfig().getInt("compile.clone-depth", 1)
            GitOperations.cloneRepository(gitUrl, branch, repoDir, timeout, depth)
            sendStatus(sender, plugin.getMessage("status.compile-cloned"))

            sendStatus(sender, plugin.getMessage("status.compile-detecting"))
            val buildSystem = BuildRunner.detect(repoDir)
            if (buildSystem == BuildSystem.UNKNOWN) {
                sender.sendMessage(plugin.getMessage("errors.compile-no-build-system"))
                cleanup(repoDir)
                return
            }

            sendStatus(sender, if (buildSystem == BuildSystem.GRADLE)
                plugin.getMessage("status.compile-detected-gradle")
            else
                plugin.getMessage("status.compile-detected-maven"))

            val buildTask: String
            if (interactive) {
                val prompted = promptForBuildTask(sender as Player, buildSystem)
                if (prompted == null) {
                    sender.sendMessage(plugin.getMessage("status.compile-cancelled"))
                    cleanup(repoDir)
                    return
                }
                buildTask = prompted
            } else {
                buildTask = getDefaultBuildTask(buildSystem)
                sender.sendMessage(plugin.getMessage("status.compile-using-default-task",
                    Placeholder.unparsed("task", buildTask)))
            }

            sendStatus(sender, plugin.getMessage("status.compile-building", Placeholder.unparsed("arg", buildTask)))
            val buildTimeout = plugin.getConfig().getInt("compile.build-timeout", 600)
            val ok = BuildRunner.run(repoDir, buildSystem, buildTask, buildTimeout) { line ->
                sender.sendMessage(plugin.getMessage("status.compile-build-log",
                    Placeholder.unparsed("line", line)))
            }
            if (!ok) {
                cleanup(repoDir)
                return
            }
            sendStatus(sender, plugin.getMessage("status.compile-build-success"))

            sendStatus(sender, plugin.getMessage("status.compile-finding-jars"))
            val jars = BuildRunner.findJars(repoDir, buildSystem)
            if (jars.isEmpty()) {
                sender.sendMessage(plugin.getMessage("errors.compile-no-jars"))
                cleanup(repoDir)
                return
            }

            val selectedJar: File
            if (jars.size == 1) {
                selectedJar = jars[0]
            } else if (interactive) {
                val selected = selectJar(sender as Player, jars)
                if (selected == null) {
                    sender.sendMessage(plugin.getMessage("status.compile-cancelled"))
                    cleanup(repoDir)
                    return
                }
                selectedJar = selected
            } else {
                selectedJar = jars[0]
                sender.sendMessage(plugin.getMessage("status.compile-auto-selected-jar",
                    Placeholder.unparsed("name", selectedJar.name),
                    Placeholder.unparsed("size", formatFileSize(selectedJar.length()))))
            }

            sendStatus(sender, plugin.getMessage("status.compile-copying",
                Placeholder.unparsed("arg", selectedJar.name)))
            Files.copy(selectedJar.toPath(),
                File(packageManager.getPluginsDir(), selectedJar.name).toPath(),
                StandardCopyOption.REPLACE_EXISTING)
            sender.sendMessage(plugin.getMessage("status.compile-complete"))

            if (!plugin.getConfig().getBoolean("compile.keep-repos-by-default", true)) {
                cleanup(repoDir)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            sender.sendMessage(plugin.getMessage("errors.compile-clone-failed",
                Placeholder.unparsed("error", "Interrupted")))
            plugin.logger.log(Level.WARNING, "Compile interrupted for $gitUrl", e)
            cleanup(repoDir)
        } catch (e: Exception) {
            if (e !is GitAPIException && e !is IOException) throw e
            sender.sendMessage(plugin.getMessage("errors.compile-clone-failed",
                Placeholder.unparsed("error", e.message ?: "")))
            plugin.logger.log(Level.SEVERE, "Compile failed for $gitUrl", e)
            cleanup(repoDir)
        }
    }

    private fun promptForBuildTask(player: Player, buildSystem: BuildSystem): String? {
        try {
            val options = if (buildSystem == BuildSystem.GRADLE)
                plugin.getConfig().getStringList("compile.default-gradle-tasks")
            else
                listOf(plugin.getConfig().getString("compile.default-maven-goal", "package")!!,
                    "clean package", "install")

            player.sendMessage(plugin.getMessage("status.compile-prompt-task"))
            for (idx in options.indices) {
                player.sendMessage(plugin.getMessage("status.compile-task-option",
                    Placeholder.unparsed("index", (idx + 1).toString()),
                    Placeholder.unparsed("task", options[idx])))
            }
            player.sendMessage(plugin.getMessage("status.compile-task-custom",
                Placeholder.unparsed("custom_index", (options.size + 1).toString())))
            player.sendMessage(plugin.getMessage("status.compile-task-cancel",
                Placeholder.unparsed("cancel_index", (options.size + 2).toString())))

            val response = waitForPlayerInput(player, 30) ?: return null

            try {
                val sel = response.trim().toInt()
                if (sel in 1..options.size) return options[sel - 1]
                if (sel == options.size + 1) {
                    player.sendMessage(plugin.getMessage("status.compile-prompt-custom-task"))
                    val custom = waitForPlayerInput(player, 30)
                    return if (custom != null && custom.trim().isNotEmpty()) custom.trim() else null
                }
            } catch (e: NumberFormatException) {
                player.sendMessage(plugin.getMessage("errors.compile-invalid-selection"))
            }
            return null
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error prompting player for build task", e)
            return null
        }
    }

    private fun getDefaultBuildTask(buildSystem: BuildSystem): String {
        if (buildSystem == BuildSystem.GRADLE) {
            val tasks = plugin.getConfig().getStringList("compile.default-gradle-tasks")
            return if (tasks.isEmpty()) "build" else tasks[0]
        }
        return plugin.getConfig().getString("compile.default-maven-goal", "package")!!
    }

    private fun selectJar(player: Player, jars: List<File>): File? {
        try {
            player.sendMessage(plugin.getMessage("status.compile-select-jar"))
            for (idx in jars.indices) {
                player.sendMessage(plugin.getMessage("status.compile-jar-option",
                    Placeholder.unparsed("index", (idx + 1).toString()),
                    Placeholder.unparsed("name", jars[idx].name),
                    Placeholder.unparsed("size", formatFileSize(jars[idx].length()))))
            }
            val response = waitForPlayerInput(player, 30) ?: return null
            try {
                val sel = response.trim().toInt()
                if (sel in 1..jars.size) return jars[sel - 1]
            } catch (e: NumberFormatException) {
                player.sendMessage(plugin.getMessage("errors.compile-invalid-selection"))
            }
            return null
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error prompting player for JAR selection", e)
            return null
        }
    }

    private fun waitForPlayerInput(player: Player, timeoutSeconds: Int): String? {
        val future = CompletableFuture<String>()
        pendingInputs[player] = future
        return try {
            future.get(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            pendingInputs.remove(player)
            null
        } catch (e: Exception) {
            pendingInputs.remove(player)
            plugin.logger.log(Level.WARNING, "Interrupted waiting for player input", e)
            null
        }
    }

    fun handlePlayerChat(player: Player, message: String) {
        val future = pendingInputs.remove(player)
        future?.complete(message)
    }

    fun hasPendingInput(player: Player): Boolean {
        return pendingInputs.containsKey(player)
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
    }

    private fun cleanup(directory: File?) {
        if (directory != null && directory.exists()) {
            deleteRecursively(directory)
        }
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files != null) {
                for (child in files) deleteRecursively(child)
            }
        }
        file.delete()
    }
}
