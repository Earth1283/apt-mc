package io.github.Earth1283.aptMc.managers

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

class PackageManagerTest {

    @Test
    fun calculateSha1_knownContent(@TempDir tempDir: Path) {
        val f = tempDir.resolve("hello.txt").toFile()
        Files.writeString(f.toPath(), "hello", StandardCharsets.UTF_8)
        val pm = PackageManager(tempDir.toFile(), tempDir.toFile(), LOGGER)
        // SHA-1("hello") = aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", pm.calculateSha1(f))
    }

    @Test
    fun cache_roundtrip(@TempDir tempDir: Path) {
        val pm = PackageManager(tempDir.toFile(), tempDir.toFile(), LOGGER)
        val obj = JsonObject()
        obj.addProperty("name", "TestPlugin")
        pm.updateCache("abc123", obj)

        // A fresh instance reading from the same directory should find the entry
        val pm2 = PackageManager(tempDir.toFile(), tempDir.toFile(), LOGGER)
        val loaded = pm2.getCachedInfo("abc123")
        assertNotNull(loaded)
        assertEquals("TestPlugin", loaded!!.get("name").asString)
    }

    @Test
    fun updateCache_persistsToFile(@TempDir tempDir: Path) {
        val pm = PackageManager(tempDir.toFile(), tempDir.toFile(), LOGGER)
        val obj = JsonObject()
        obj.addProperty("x", 1)
        pm.updateCache("key1", obj)
        val cache = tempDir.resolve("cache.json").toFile()
        assertTrue(cache.exists())
        val content = Files.readString(cache.toPath())
        assertTrue(content.contains("key1"))
    }

    @Test
    fun updateCache_batchUpdatesArePersisted(@TempDir tempDir: Path) {
        val pm = PackageManager(tempDir.toFile(), tempDir.toFile(), LOGGER)
        val a = JsonObject(); a.addProperty("name", "PluginA")
        val b = JsonObject(); b.addProperty("name", "PluginB")
        val entries = mapOf("hash1" to a, "hash2" to b)
        pm.updateCache(entries)

        val pm2 = PackageManager(tempDir.toFile(), tempDir.toFile(), LOGGER)
        assertNotNull(pm2.getCachedInfo("hash1"))
        assertNotNull(pm2.getCachedInfo("hash2"))
    }

    @Test
    fun getInstalledPlugins_emptyDir(@TempDir tempDir: Path) {
        val pluginsDir = tempDir.resolve("plugins").toFile()
        pluginsDir.mkdir()
        val pm = PackageManager(tempDir.toFile(), pluginsDir, LOGGER)
        assertTrue(pm.getInstalledPlugins().isEmpty())
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(PackageManagerTest::class.java.name)
    }
}
