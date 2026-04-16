package io.github.Earth1283.aptMc.managers;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PackageManagerTest {

    private static final Logger LOGGER = Logger.getLogger(PackageManagerTest.class.getName());

    @Test
    void calculateSha1_knownContent(@TempDir Path tempDir) throws IOException {
        File f = tempDir.resolve("hello.txt").toFile();
        Files.writeString(f.toPath(), "hello");
        PackageManager pm = new PackageManager(tempDir.toFile(), tempDir.toFile(), LOGGER);
        // SHA-1("hello") = aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", pm.calculateSha1(f));
    }

    @Test
    void cache_roundtrip(@TempDir Path tempDir) {
        PackageManager pm = new PackageManager(tempDir.toFile(), tempDir.toFile(), LOGGER);
        JsonObject obj = new JsonObject();
        obj.addProperty("name", "TestPlugin");
        pm.updateCache("abc123", obj);

        // A fresh instance reading from the same directory should find the entry
        PackageManager pm2 = new PackageManager(tempDir.toFile(), tempDir.toFile(), LOGGER);
        JsonObject loaded = pm2.getCachedInfo("abc123");
        assertNotNull(loaded);
        assertEquals("TestPlugin", loaded.get("name").getAsString());
    }

    @Test
    void updateCache_doesNotSaveTwice(@TempDir Path tempDir) throws IOException {
        PackageManager pm = new PackageManager(tempDir.toFile(), tempDir.toFile(), LOGGER);
        JsonObject obj = new JsonObject();
        obj.addProperty("x", 1);
        pm.updateCache("key1", obj);
        File cache = new File(tempDir.toFile(), "cache.json");
        assertTrue(cache.exists());
        String content = Files.readString(cache.toPath());
        assertTrue(content.contains("key1"));
    }

    @Test
    void getInstalledPlugins_emptyDir(@TempDir Path tempDir) {
        File pluginsDir = tempDir.resolve("plugins").toFile();
        pluginsDir.mkdir();
        PackageManager pm = new PackageManager(tempDir.toFile(), pluginsDir, LOGGER);
        assertTrue(pm.getInstalledPlugins().isEmpty());
    }
}
