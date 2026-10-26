package io.github.Earth1283.aptMc.graph

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PluginDescriptorReaderTest {
    @TempDir lateinit var directory: File

    @Test
    fun `reads Bukkit dependencies aliases and load hints separately`() {
        val file = writePluginJar(directory, "Shop.jar", "plugin.yml" to """
            name: Shop
            version: '2.1'
            author: Alice
            authors: [Bob]
            depend: [Core]
            softdepend: [Placeholders]
            loadbefore: [AnotherShop]
            provides: [OldShop]
        """.trimIndent())
        val plugin = PluginDescriptorReader.read(file)
        assertEquals("Shop", plugin.name)
        assertEquals("2.1", plugin.version)
        assertEquals(listOf("Bob", "Alice"), plugin.authors)
        assertEquals(listOf(DependencyKind.REQUIRED, DependencyKind.OPTIONAL), plugin.dependencies.map { it.kind })
        assertEquals(listOf("AnotherShop"), plugin.loadBefore)
        assertEquals(listOf("OldShop"), plugin.provides)
        assertFalse(plugin.dependencies.any { it.name == "AnotherShop" })
    }

    @Test
    fun `Paper metadata takes precedence and preserves phases dotted names and defaults`() {
        val file = writePluginJar(directory, "Paper.jar", "plugin.yml" to "name: Legacy\nversion: 0\ndepend: [Wrong]",
            "paper-plugin.yml" to """
                name: Modern
                version: '3'
                dependencies:
                  bootstrap:
                    Registry.Plugin:
                      load: BEFORE
                  server:
                    Registry.Plugin:
                      required: false
                      load: AFTER
                    Another:
                      join-classpath: false
            """.trimIndent())
        val plugin = PluginDescriptorReader.read(file)
        assertEquals("Modern", plugin.name)
        assertEquals("paper-plugin.yml", plugin.descriptorFile)
        assertEquals(listOf(
            PluginDependency("Registry.Plugin", DependencyKind.REQUIRED, "bootstrap", "BEFORE"),
            PluginDependency("Registry.Plugin", DependencyKind.OPTIONAL, "server", "AFTER"),
            PluginDependency("Another", DependencyKind.REQUIRED)
        ), plugin.dependencies)
    }

    @Test
    fun `malformed or absent descriptors fail explicitly`() {
        val absent = writePluginJar(directory, "Absent.jar", "other.yml" to "nothing: here")
        assertEquals("errors.no-descriptor", assertThrows(GraphException::class.java) { PluginDescriptorReader.read(absent) }.detail.key)
        val malformed = writePluginJar(directory, "Bad.jar", "plugin.yml" to "name: [broken")
        assertThrows(Exception::class.java) { PluginDescriptorReader.read(malformed) }
        val invalid = writePluginJar(directory, "Invalid.jar", "plugin.yml" to "name: 'Bad name'\nversion: 1")
        assertEquals("errors.invalid-name", assertThrows(GraphException::class.java) { PluginDescriptorReader.read(invalid) }.detail.key)
    }

    @Test
    fun `descriptor reads are size bounded even for compressed entries`() {
        val file = writePluginJar(directory, "Huge.jar", "plugin.yml" to "name: Example\nversion: 1\ndescription: ${"x".repeat(1_048_576)}")
        assertEquals("errors.large-descriptor", assertThrows(GraphException::class.java) { PluginDescriptorReader.read(file) }.detail.key)
    }
}
