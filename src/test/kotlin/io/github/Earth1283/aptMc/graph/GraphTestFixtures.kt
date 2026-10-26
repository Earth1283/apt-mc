package io.github.Earth1283.aptMc.graph

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

internal fun writePluginJar(directory: File, filename: String = "Example.jar", vararg descriptors: Pair<String, String>): File {
    return File(directory, filename).also { file ->
        JarOutputStream(file.outputStream()).use { jar ->
            for ((name, contents) in descriptors) {
                jar.putNextEntry(JarEntry(name))
                jar.write(contents.toByteArray())
                jar.closeEntry()
            }
        }
    }
}

internal fun descriptor(name: String, vararg dependencies: PluginDependency, provides: List<String> = emptyList()): PluginDescriptor =
    PluginDescriptor(name, "1.0", "$name.jar", "Description <red>literal", listOf("An author"), dependencies.toList(), provides, emptyList(), "plugin.yml")

internal fun plain(component: Component): String =
    (if (component is TextComponent) component.content() else "") + component.children().joinToString("") { plain(it) }

internal fun descendants(component: Component): List<Component> = listOf(component) + component.children().flatMap { descendants(it) }

internal class TestGraphMessages : GraphMessages {
    val yaml = YamlConfiguration().apply {
        loadFromString(TestGraphMessages::class.java.getResourceAsStream("/messages.yml")!!.bufferedReader().use { it.readText() })
    }

    override fun render(key: String, arguments: Map<String, Component>): Component {
        val template = yaml.getString("graph.$key") ?: error("Missing graph message: $key")
        return MiniMessage.miniMessage().deserialize(template, *arguments.map { (name, value) -> Placeholder.component(name, value) }.toTypedArray())
    }
}
