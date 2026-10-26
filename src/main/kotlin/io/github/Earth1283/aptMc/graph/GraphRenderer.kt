package io.github.Earth1283.aptMc.graph

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor

data class PluginRuntimeState(val version: String, val enabled: Boolean)

data class GraphOptions(
    val query: String? = null,
    val requiredOnly: Boolean = false,
    val reverse: Boolean = false,
    val refresh: Boolean = false,
    val page: Int = 1
) {
    fun command(page: Int = this.page): String = buildString {
        append("/apt graph")
        query?.let { append(" ").append(it) }
        if (requiredOnly) append(" --required-only")
        if (reverse) append(" --reverse")
        append(" --page ").append(page)
    }

    companion object {
        fun parse(args: List<String>): GraphOptions {
            var query: String? = null
            var required = false
            var reverse = false
            var refresh = false
            var page = 1
            var index = 0
            while (index < args.size) {
                when (val arg = args[index++]) {
                    "--required-only" -> required = true
                    "--reverse" -> reverse = true
                    "--refresh" -> refresh = true
                    "--page" -> {
                        page = args.getOrNull(index++)?.toIntOrNull()
                            ?: throw GraphException(GraphMessage("errors.page-number"))
                        if (page <= 0) throw GraphException(GraphMessage("errors.page-number"))
                    }
                    else -> {
                        if (arg.startsWith("--")) throw GraphException(GraphMessage("errors.option", mapOf("option" to arg)))
                        if (query != null) throw GraphException(GraphMessage("errors.one-plugin"))
                        query = arg
                    }
                }
            }
            return GraphOptions(query, required, reverse, refresh, page)
        }
    }
}

/** Builds only a bounded number of rows, off the server thread. All prose comes from messages.yml. */
class GraphRenderer(
    private val messages: GraphMessages,
    private val pageSize: Int = 14,
    private val maxDepth: Int = 8,
    private val maxLines: Int = 2000
) {
    fun render(graph: PluginGraph, runtime: Map<String, PluginRuntimeState>, options: GraphOptions, interactive: Boolean = true): List<Component> {
        val rows = mutableListOf<Component>()
        val expanded = hashSetOf<String>()
        var truncated = false
        val edges = if (options.reverse) graph.incoming else graph.outgoing
        fun visible(key: String) = edges[key].orEmpty().filter { !options.requiredOnly || it.dependency.kind == DependencyKind.REQUIRED }

        fun visit(key: String, prefix: String, branch: String, path: Set<String>, edge: GraphEdge?, depth: Int) {
            if (rows.size >= maxLines) { truncated = true; return }
            val cycle = key in path
            val repeated = !cycle && key in expanded
            val children = visible(key)
            val limited = depth >= maxDepth && children.isNotEmpty()
            val suffix = when {
                cycle -> messages.text("cycle")
                repeated -> messages.text("repeated")
                limited -> messages.text("depth-limit")
                else -> Component.empty()
            }
            rows.add(Component.text(prefix + branch, NamedTextColor.DARK_GRAY)
                .append(node(graph, runtime, key, edge, options.reverse)).append(suffix))
            if (cycle || repeated || limited) return
            expanded.add(key)
            val childPrefix = prefix + when (branch) {
                "├── " -> "│   "
                "└── " -> "    "
                else -> ""
            }
            for ((index, child) in children.withIndex()) {
                visit(if (options.reverse) child.owner else child.target, childPrefix,
                    if (index == children.lastIndex) "└── " else "├── ", path + key, child, depth + 1)
                if (truncated) break
            }
        }

        if (options.query != null) {
            visit(findRoot(graph, options.query, options.reverse), "", "", emptySet(), null, 0)
        } else {
            val keys = graph.plugins.keys.sorted()
            // Natural roots first; a second pass includes isolated plugins and entirely cyclic components.
            val parentEdges = if (options.reverse) graph.outgoing else graph.incoming
            val roots = keys.filter { parentEdges[it].orEmpty().none { edge -> !options.requiredOnly || edge.dependency.kind == DependencyKind.REQUIRED } }
            for (key in (roots + keys).distinct()) {
                if (key !in expanded) visit(key, "", "", emptySet(), null, 0)
                if (truncated) break
            }
        }
        if (rows.isEmpty()) rows.add(messages.text("empty"))
        if (truncated) rows.add(messages.text("truncated", "count" to maxLines.toString()))
        graph.warnings.take(50).forEach { rows.add(messages.text(it)) }
        if (graph.warnings.size > 50) rows.add(messages.text("more-warnings", "count" to (graph.warnings.size - 50).toString()))

        val pages = (rows.size + pageSize - 1) / pageSize
        if (options.page > pages) throw GraphException(GraphMessage("errors.page-range", mapOf("page" to options.page.toString(), "pages" to pages.toString())))
        val title = messages.text(if (options.reverse) "title-reverse" else "title")
        val result = mutableListOf(messages.render("header", mapOf("title" to title, "page" to Component.text(options.page), "pages" to Component.text(pages))))
        result.addAll(rows.drop((options.page - 1) * pageSize).take(pageSize))
        var footer = messages.text(if (interactive) "footer" else "footer-console")
        if (options.page > 1) footer = footer.append(messages.text("previous").clickEvent(ClickEvent.suggestCommand(options.command(options.page - 1))))
        if (options.page < pages) footer = footer.append(messages.text("next").clickEvent(ClickEvent.suggestCommand(options.command(options.page + 1))))
        result.add(footer)
        return result
    }

    private fun findRoot(graph: PluginGraph, query: String, reverse: Boolean): String {
        val key = graph.resolveKey(query)
        if (key in graph.plugins || (reverse && key in graph.incoming)) return key
        val matches = graph.plugins.filterValues {
            it.name.contains(query, ignoreCase = true) || it.filename.equals(query, ignoreCase = true)
        }
        if (matches.isEmpty()) throw GraphException(GraphMessage("errors.not-found", mapOf("query" to query)))
        if (matches.size != 1) throw GraphException(GraphMessage("errors.ambiguous", mapOf("plugins" to matches.values.joinToString { it.name })))
        return matches.keys.first()
    }

    private fun node(graph: PluginGraph, runtime: Map<String, PluginRuntimeState>, key: String, edge: GraphEdge?, reverse: Boolean): Component {
        val descriptor = graph.plugins[key]
        val name = descriptor?.name ?: edge?.dependency?.name ?: key
        val state = runtime[key]
        val optional = edge?.dependency?.kind?.let { it == DependencyKind.OPTIONAL }
            ?: graph.incoming[key].orEmpty().none { it.dependency.kind == DependencyKind.REQUIRED }
        val statusKey = when {
            descriptor == null && state != null -> "jar-absent"
            descriptor == null -> if (optional) "absent" else "missing"
            state == null -> "not-loaded"
            state.version != descriptor.version -> "version-mismatch"
            state.enabled -> "enabled"
            else -> "disabled"
        }
        val status = messages.text("state.$statusKey")
        var label = messages.text("node", "name" to name)
        descriptor?.let { label = label.append(messages.text("version", "version" to it.version)) }
        edge?.let {
            label = label.append(messages.render("relationship-label", mapOf("kind" to messages.text("kind.${pluginKey(it.dependency.kind.name)}"))))
            if (it.dependency.phase != "server") label = label.append(messages.render("phase-label", mapOf("phase" to messages.text("phase.${it.dependency.phase}"))))
            if (!reverse && pluginKey(it.dependency.name) != key) label = label.append(messages.text("alias-label", "alias" to it.dependency.name))
        }
        label = label.append(messages.render("status-label", mapOf("status" to status)))
        var tooltip = messages.text("tooltip.title", "name" to name, "version" to (descriptor?.version ?: ""))
        fun line(key: String, vararg args: Pair<String, String>) {
            tooltip = tooltip.append(Component.newline()).append(messages.text(key, *args))
        }
        fun componentLine(key: String, args: Map<String, Component>) {
            tooltip = tooltip.append(Component.newline()).append(messages.render(key, args))
        }
        componentLine("tooltip.status", mapOf("status" to status))
        state?.let {
            componentLine("tooltip.runtime", mapOf("version" to Component.text(it.version), "status" to messages.text(if (it.enabled) "state.enabled" else "state.disabled")))
        }
        edge?.let {
            val owner = graph.plugins[it.owner]?.name ?: it.owner
            val target = graph.plugins[it.target]?.name ?: it.dependency.name
            componentLine(if (reverse) "tooltip.relationship-reverse" else "tooltip.relationship", mapOf(
                "plugin" to Component.text(if (reverse) target else owner), "kind" to messages.text("kind.${pluginKey(it.dependency.kind.name)}")
            ))
            componentLine("tooltip.phase", mapOf("phase" to messages.text("phase.${it.dependency.phase}")))
            componentLine("tooltip.load", mapOf("order" to messages.text("load.${pluginKey(it.dependency.load)}")))
        }
        descriptor?.let {
            line("tooltip.jar", "file" to it.filename)
            line("tooltip.descriptor", "file" to it.descriptorFile)
            if (it.description.isNotBlank()) line("tooltip.description", "description" to it.description.take(500))
            if (it.authors.isNotEmpty()) line("tooltip.authors", "authors" to it.authors.joinToString().take(300))
            if (it.provides.isNotEmpty()) line("tooltip.provides", "plugins" to it.provides.joinToString().take(300))
            if (it.loadBefore.isNotEmpty()) line("tooltip.load-before", "plugins" to it.loadBefore.joinToString().take(300))
            val requiredBy = graph.incoming[key].orEmpty().filter { dependency -> dependency.dependency.kind == DependencyKind.REQUIRED }.map { dependency -> dependency.owner }.distinct().size
            line("tooltip.required-by", "count" to requiredBy.toString())
            line("tooltip.inspect", "command" to "/apt graph ${it.name}")
        }
        if (descriptor == null && optional) line("tooltip.optional-absent")
        var component = label.hoverEvent(HoverEvent.showText(tooltip))
        if (descriptor != null) component = component.clickEvent(ClickEvent.suggestCommand("/apt graph ${descriptor.name}"))
        return component
    }
}
