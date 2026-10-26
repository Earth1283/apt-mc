package io.github.Earth1283.aptMc.graph

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GraphRendererTest {
    private val messages = TestGraphMessages()
    private fun render(graph: PluginGraph, options: GraphOptions = GraphOptions()): List<Component> =
        GraphRenderer(messages, pageSize = 40).render(graph, emptyMap(), options)

    @Test
    fun `optional dependencies are visible by default with absence distinct from missing requirements`() {
        val graph = PluginGraph(listOf(descriptor("Shop",
            PluginDependency("Core", DependencyKind.REQUIRED),
            PluginDependency("Optional", DependencyKind.OPTIONAL))), emptyList())
        val output = render(graph).joinToString("\n") { plain(it) }
        assertTrue(output.contains("Core [required] [missing!]"), output)
        assertTrue(output.contains("Optional [optional] [absent]"), output)
        assertTrue(output.contains("Shop 1.0 [not loaded]"), output)
        val required = render(graph, GraphOptions(requiredOnly = true)).joinToString("\n") { plain(it) }
        assertFalse(required.contains("Optional"))
        assertTrue(required.contains("Core"))
    }

    @Test
    fun `aliases resolve to installed providers and reverse traversal finds their dependents`() {
        val graph = PluginGraph(listOf(descriptor("Consumer", PluginDependency("Alias", DependencyKind.REQUIRED)),
            descriptor("Provider", provides = listOf("Alias"))), emptyList())
        assertEquals("provider", graph.outgoing.getValue("consumer").single().target)
        val output = render(graph, GraphOptions("Consumer")).joinToString("\n") { plain(it) }
        assertTrue(output.contains("Provider 1.0 [required] [provides Alias] [not loaded]"), output)
        assertFalse(output.contains("missing!"))
        val reverse = render(graph, GraphOptions("Alias", reverse = true)).joinToString("\n") { plain(it) }
        assertTrue(reverse.contains("Consumer 1.0 [required]"), reverse)
        assertTrue(reverse.startsWith("Plugin dependents"), reverse)
    }

    @Test
    fun `cycles shared subgraphs and isolated plugins all render without unbounded recursion`() {
        val graph = PluginGraph(listOf(
            descriptor("A", PluginDependency("B", DependencyKind.REQUIRED)),
            descriptor("B", PluginDependency("A", DependencyKind.OPTIONAL)),
            descriptor("C", PluginDependency("B", DependencyKind.REQUIRED)),
            descriptor("Isolated")
        ), emptyList())
        val lines = render(graph)
        val output = lines.joinToString("\n") { plain(it) }
        assertTrue(output.contains("[cycle]"), output)
        assertTrue(output.contains("Isolated"), output)
        assertTrue(lines.size < 15)
        val shared = PluginGraph(listOf(descriptor("A", PluginDependency("Library", DependencyKind.REQUIRED)),
            descriptor("B", PluginDependency("Library", DependencyKind.OPTIONAL)), descriptor("Library")), emptyList())
        assertTrue(render(shared).any { plain(it).contains("[shown earlier]") })
    }

    @Test
    fun `tooltips contain literal metadata and click events only suggest graph commands`() {
        val graph = PluginGraph(listOf(descriptor("Example")), emptyList())
        val lines = GraphRenderer(messages).render(graph, mapOf("example" to PluginRuntimeState("1.0", true)), GraphOptions())
        val node = lines.flatMap { descendants(it) }.first { it.hoverEvent() != null }
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, node.clickEvent()!!.action())
        assertEquals("/apt graph Example", node.clickEvent()!!.value())
        assertEquals(HoverEvent.Action.SHOW_TEXT, node.hoverEvent()!!.action())
        val tooltip = node.hoverEvent()!!.value() as Component
        val text = plain(tooltip)
        assertTrue(text.contains("Description <red>literal"), text)
        assertTrue(text.contains("Example.jar"), text)
        assertTrue(text.contains("Runtime: 1.0 (enabled)"), text)
        assertTrue(text.contains("An author"), text)
        assertFalse(descendants(tooltip).any { it.color() == NamedTextColor.RED }, "Plugin description must not inject MiniMessage formatting")
    }

    @Test
    fun `visible output and tooltip labels use the message catalog`() {
        messages.yaml.set("graph.state.not-loaded", "NON_CHARGE")
        messages.yaml.set("graph.kind.optional", "FACULTATIF")
        messages.yaml.set("graph.tooltip.description", "DETAILS: <description>")
        messages.yaml.set("graph.title", "DEPENDANCES")
        val graph = PluginGraph(listOf(descriptor("Example", PluginDependency("Other", DependencyKind.OPTIONAL))), emptyList())
        val lines = render(graph)
        val output = lines.joinToString("\n") { plain(it) }
        assertTrue(output.contains("NON_CHARGE"), output)
        assertTrue(output.contains("FACULTATIF"), output)
        assertTrue(output.contains("DEPENDANCES"), output)
        val tooltip = lines.flatMap { descendants(it) }.first { it.hoverEvent() != null }.hoverEvent()!!.value() as Component
        assertTrue(plain(tooltip).contains("DETAILS: Description"))
    }

    @Test
    fun `pagination preserves filters and bounds output`() {
        val graph = PluginGraph((1..12).map { descriptor("Plugin$it") }, emptyList())
        val options = GraphOptions(requiredOnly = true, reverse = true)
        val lines = GraphRenderer(messages, pageSize = 5).render(graph, emptyMap(), options)
        assertEquals(7, lines.size)
        assertTrue(plain(lines.first()).contains("page 1/3"))
        val next = lines.last().children().first { it.clickEvent() != null }
        assertEquals("/apt graph --required-only --reverse --page 2", next.clickEvent()!!.value())
        val last = GraphRenderer(messages, pageSize = 5).render(graph, emptyMap(), options.copy(page = 3))
        assertEquals(4, last.size)
        assertEquals("errors.page-range", assertThrows(GraphException::class.java) {
            GraphRenderer(messages, pageSize = 5).render(graph, emptyMap(), options.copy(page = 4))
        }.detail.key)
        val console = GraphRenderer(messages).render(graph, emptyMap(), options, interactive = false)
        assertFalse(plain(console.last()).contains("Hover"))
    }

    @Test
    fun `depth and total row limits protect chat on large graphs`() {
        val graph = PluginGraph((1..20).map { descriptor("P$it", PluginDependency("P${it + 1}", DependencyKind.REQUIRED)) }, emptyList())
        val shallow = GraphRenderer(messages, maxDepth = 2).render(graph, emptyMap(), GraphOptions("P1"))
        assertTrue(shallow.any { plain(it).contains("[depth limit]") })
        val limited = GraphRenderer(messages, pageSize = 40, maxDepth = 20, maxLines = 5).render(graph, emptyMap(), GraphOptions("P1"))
        assertTrue(limited.any { plain(it).contains("limited to 5 rows") })
        assertEquals(8, limited.size)
    }

    @Test
    fun `runtime disabled and changed versions are not mislabeled enabled`() {
        val graph = PluginGraph(listOf(descriptor("Disabled"), descriptor("Changed")), emptyList())
        val lines = GraphRenderer(messages).render(graph, mapOf(
            "disabled" to PluginRuntimeState("1.0", false), "changed" to PluginRuntimeState("0.9", true)
        ), GraphOptions())
        val output = lines.joinToString("\n") { plain(it) }
        assertTrue(output.contains("Disabled 1.0 [disabled]"), output)
        assertTrue(output.contains("Changed 1.0 [disk version differs from runtime]"), output)
    }

    @Test
    fun `removed but still loaded dependency is distinct from a missing dependency`() {
        val graph = PluginGraph(listOf(descriptor("Consumer", PluginDependency("Removed", DependencyKind.REQUIRED))), emptyList())
        val lines = GraphRenderer(messages).render(graph, mapOf("removed" to PluginRuntimeState("1.0", true)), GraphOptions())
        assertTrue(lines.any { plain(it).contains("Removed [required] [loaded; JAR absent]") })
    }

    @Test
    fun `reverse inspection of an absent optional dependency does not imply a missing requirement`() {
        val graph = PluginGraph(listOf(descriptor("Consumer", PluginDependency("Absent", DependencyKind.OPTIONAL))), emptyList())
        val output = render(graph, GraphOptions("Absent", reverse = true)).joinToString("\n") { plain(it) }
        assertTrue(output.contains("[absent]"), output)
        assertFalse(output.contains("missing!"), output)
    }

    @Test
    fun `duplicate metadata and ambiguous matches are surfaced with translatable messages`() {
        val graph = PluginGraph(listOf(descriptor("Duplicate").copy(filename = "A.jar"), descriptor("Duplicate").copy(filename = "B.jar")), emptyList())
        assertEquals("warnings.duplicate", graph.warnings.single().key)
        assertTrue(render(graph).any { plain(it).contains("A.jar, B.jar") })
        val ambiguous = PluginGraph(listOf(descriptor("PluginOne"), descriptor("PluginTwo")), emptyList())
        assertEquals("errors.ambiguous", assertThrows(GraphException::class.java) { render(ambiguous, GraphOptions("Plugin")) }.detail.key)
        assertEquals("errors.not-found", assertThrows(GraphException::class.java) { render(ambiguous, GraphOptions("Unknown")) }.detail.key)
    }

    @Test
    fun `invalid arguments are rejected and flags may surround the plugin name`() {
        assertEquals(GraphOptions("Example", true, true, true, 2), GraphOptions.parse(listOf("--refresh", "Example", "--reverse", "--page", "2", "--required-only")))
        for (args in listOf(listOf("--page"), listOf("--page", "0"), listOf("--page", "-1"), listOf("--page", "oops"), listOf("A", "B"), listOf("--typo"))) {
            val error = assertThrows(GraphException::class.java) { GraphOptions.parse(args) }
            messages.text(error.detail) // Every validation error must have a catalog entry.
        }
    }
}
