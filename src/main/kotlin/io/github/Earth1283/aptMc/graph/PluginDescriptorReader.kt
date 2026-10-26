package io.github.Earth1283.aptMc.graph

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.jar.JarFile

/** Reads data only: plugin classes are never loaded or executed. Called only by the scan worker. */
object PluginDescriptorReader {
    private const val MAX_DESCRIPTOR_BYTES = 1_048_576
    private val validName = Regex("[A-Za-z0-9_.-]+")

    fun read(file: File): PluginDescriptor = JarFile(file).use { jar ->
        // Paper chooses its descriptor when a JAR ships both formats.
        val entry = jar.getJarEntry("paper-plugin.yml") ?: jar.getJarEntry("plugin.yml")
            ?: throw GraphException(GraphMessage("errors.no-descriptor"))
        val bytes = jar.getInputStream(entry).use { it.readNBytes(MAX_DESCRIPTOR_BYTES + 1) }
        if (bytes.size > MAX_DESCRIPTOR_BYTES) throw GraphException(GraphMessage("errors.large-descriptor"))
        val yaml = YamlConfiguration()
        yaml.options().pathSeparator('/')
        yaml.loadFromString(bytes.toString(Charsets.UTF_8))
        val name = yaml.getString("name") ?: throw GraphException(GraphMessage("errors.no-name"))
        if (!validName.matches(name)) throw GraphException(GraphMessage("errors.invalid-name"))
        val version = yaml.getString("version") ?: throw GraphException(GraphMessage("errors.no-version"))
        val dependencies = mutableListOf<PluginDependency>()
        if (entry.name == "paper-plugin.yml") {
            for (phase in listOf("bootstrap", "server")) {
                val section = yaml.getConfigurationSection("dependencies/$phase") ?: continue
                // Treat dependency names literally (dots in names are legal).
                for ((dependency, value) in section.getValues(false)) {
                    val details = value as? org.bukkit.configuration.ConfigurationSection
                        ?: throw GraphException(GraphMessage("errors.invalid-dependency", mapOf("dependency" to dependency)))
                    val load = details.getString("load", "OMIT")!!.uppercase(java.util.Locale.ROOT)
                    if (load !in setOf("BEFORE", "AFTER", "OMIT")) {
                        throw GraphException(GraphMessage("errors.invalid-dependency", mapOf("dependency" to dependency)))
                    }
                    dependencies.add(PluginDependency(
                        dependency,
                        if (details.getBoolean("required", true)) DependencyKind.REQUIRED else DependencyKind.OPTIONAL,
                        phase,
                        load
                    ))
                }
            }
        } else {
            dependencies.addAll(yaml.getStringList("depend").map { PluginDependency(it, DependencyKind.REQUIRED, load = "BEFORE") })
            dependencies.addAll(yaml.getStringList("softdepend").map { PluginDependency(it, DependencyKind.OPTIONAL, load = "BEFORE") })
        }
        val authors = yaml.getStringList("authors").toMutableList()
        yaml.getString("author")?.let { if (it !in authors) authors.add(it) }
        PluginDescriptor(
            name, version, file.name, yaml.getString("description", "")!!,
            java.util.List.copyOf(authors), java.util.List.copyOf(dependencies.distinct()),
            java.util.List.copyOf(yaml.getStringList("provides")),
            java.util.List.copyOf(if (entry.name == "plugin.yml") yaml.getStringList("loadbefore") else emptyList()),
            entry.name
        )
    }
}
