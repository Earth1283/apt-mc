package io.github.Earth1283.aptMc.graph

import java.util.Locale

internal fun pluginKey(name: String): String = name.lowercase(Locale.ROOT)

enum class DependencyKind {
    REQUIRED, OPTIONAL
}

data class PluginDependency(
    val name: String,
    val kind: DependencyKind,
    val phase: String = "server",
    val load: String = "OMIT"
)

data class PluginDescriptor(
    val name: String,
    val version: String,
    val filename: String,
    val description: String,
    val authors: List<String>,
    val dependencies: List<PluginDependency>,
    val provides: List<String>,
    val loadBefore: List<String>,
    val descriptorFile: String
)

data class GraphEdge(val owner: String, val target: String, val dependency: PluginDependency)

/** Published once and never mutated; the renderer can safely share it between requests. */
class PluginGraph(descriptors: List<PluginDescriptor>, scanWarnings: List<GraphMessage>) {
    val plugins: Map<String, PluginDescriptor>
    val warnings: List<GraphMessage>
    private val aliases: Map<String, String>
    val outgoing: Map<String, List<GraphEdge>>
    val incoming: Map<String, List<GraphEdge>>

    init {
        val issues = scanWarnings.toMutableList()
        val byName = linkedMapOf<String, PluginDescriptor>()
        for (descriptor in descriptors.sortedBy { it.filename }) {
            val key = pluginKey(descriptor.name)
            val existing = byName.putIfAbsent(key, descriptor)
            if (existing != null) {
                issues.add(GraphMessage("warnings.duplicate", mapOf("plugin" to descriptor.name, "first" to existing.filename, "second" to descriptor.filename)))
            }
        }
        plugins = java.util.Map.copyOf(byName)
        val provided = linkedMapOf<String, String>()
        for ((key, descriptor) in byName) {
            for (alias in descriptor.provides) {
                val aliasKey = pluginKey(alias)
                if (aliasKey in byName) continue
                val previous = provided.putIfAbsent(aliasKey, key)
                if (previous != null && previous != key) {
                    issues.add(GraphMessage("warnings.alias", mapOf("alias" to alias, "plugin" to byName.getValue(previous).name)))
                }
            }
        }
        aliases = java.util.Map.copyOf(provided)
        val edges = byName.flatMap { (key, descriptor) ->
            descriptor.dependencies.map { GraphEdge(key, resolveKey(it.name), it) }
        }
        outgoing = immutableGroups(edges.groupBy { it.owner })
        incoming = immutableGroups(edges.groupBy { it.target })
        warnings = java.util.List.copyOf(issues)
    }

    fun resolveKey(name: String): String {
        val key = pluginKey(name)
        return aliases[key] ?: key
    }

    private fun immutableGroups(groups: Map<String, List<GraphEdge>>): Map<String, List<GraphEdge>> =
        java.util.Map.copyOf(groups.mapValues { (_, edges) ->
            java.util.List.copyOf(edges.sortedWith(compareBy({ it.dependency.kind }, { it.dependency.name }, { it.dependency.phase })))
        })
}
