package io.github.Earth1283.aptMc.graph

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginGraphCacheTest {
    @TempDir lateinit var directory: File

    @Test
    fun `TTL hits avoid scans while refresh reparses only changed jars and prunes deletions`() {
        var now = 0L
        var reads = 0
        val reader: (File) -> PluginDescriptor = { file -> reads++; descriptor(file.nameWithoutExtension).copy(version = file.readText()) }
        val first = File(directory, "First.jar").apply { writeText("1") }
        val second = File(directory, "Second.jar").apply { writeText("1") }
        val cache = PluginGraphCache(directory, Executor { it.run() }, 30_000, { now }, reader)
        val original = cache.get().join()
        assertEquals(2, reads)
        first.writeText("2.0")
        assertSame(original, cache.get().join())
        assertEquals(2, reads)
        now = 30_000
        val changed = cache.get().join()
        assertEquals("2.0", changed.plugins.getValue("first").version)
        assertEquals(3, reads)
        assertTrue(second.delete())
        File(directory, "Third.jar").writeText("3")
        cache.invalidate()
        val refreshed = cache.get().join()
        assertEquals(setOf("first", "third"), refreshed.plugins.keys)
        assertEquals(4, reads)
        assertEquals(setOf("first", "second"), original.plugins.keys, "Published snapshots must not be mutated")
    }

    @Test
    fun `forced refresh rereads even when file size and timestamp are unchanged`() {
        val file = File(directory, "First.jar").apply { writeText("1") }
        val modified = Files.getLastModifiedTime(file.toPath())
        var reads = 0
        val cache = PluginGraphCache(directory, Executor { it.run() }, reader = { reads++; descriptor("First").copy(version = it.readText()) })
        cache.get().join()
        file.writeText("2")
        Files.setLastModifiedTime(file.toPath(), modified)
        assertEquals("2", cache.get(force = true).join().plugins.getValue("first").version)
        assertEquals(2, reads)
    }

    @Test
    fun `concurrent requests share one worker and invalidation during scan survives publication`() {
        File(directory, "First.jar").writeText("1")
        val executor = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reads = AtomicInteger()
        val callerThread = Thread.currentThread()
        val cache = PluginGraphCache(directory, executor, reader = {
            assertNotSame(callerThread, Thread.currentThread())
            reads.incrementAndGet()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            descriptor("First")
        })
        try {
            val first = cache.get()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            repeat(20) { assertSame(first, cache.get()) }
            cache.invalidate()
            release.countDown()
            val snapshot = first.get(5, TimeUnit.SECONDS)
            val refreshed = cache.get().get(5, TimeUnit.SECONDS)
            assertNotSame(snapshot, refreshed)
            assertEquals(1, reads.get(), "Invalidation should recheck the directory but reuse unchanged metadata")
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `broken jars yield cached warnings and do not hide healthy plugins`() {
        writePluginJar(directory, "Healthy.jar", "plugin.yml" to "name: Healthy\nversion: 1")
        val broken = File(directory, "Broken.jar").apply { writeText("not a jar") }
        var reads = 0
        val cache = PluginGraphCache(directory, Executor { it.run() }, 0, reader = { reads++; PluginDescriptorReader.read(it) })
        val graph = cache.get().join()
        assertEquals(setOf("healthy"), graph.plugins.keys)
        assertEquals("Broken.jar", graph.warnings.single().arguments["file"])
        cache.get().join()
        assertEquals(2, reads, "Invalid descriptors should also be cached")
        writePluginJar(directory, "Broken.jar", "plugin.yml" to "name: Repaired\nversion: 1")
        Files.setLastModifiedTime(broken.toPath(), FileTime.fromMillis(System.currentTimeMillis() + 1000))
        assertTrue(cache.get().join().warnings.isEmpty())
        assertEquals(3, reads)
    }

    @Test
    fun `a failed directory scan can be retried`() {
        val notDirectory = File(directory, "not-a-directory").apply { writeText("x") }
        val cache = PluginGraphCache(notDirectory, Executor { it.run() })
        assertThrows(java.util.concurrent.CompletionException::class.java) { cache.get().join() }
        assertTrue(notDirectory.delete())
        assertTrue(notDirectory.mkdir())
        assertTrue(cache.get().join().plugins.isEmpty())
    }
}
