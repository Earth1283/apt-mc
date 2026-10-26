package io.github.Earth1283.aptMc.graph

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/** Single-flight async refresh. No filesystem work is performed by get(), peek(), or invalidate(). */
class PluginGraphCache(
    private val directory: File,
    private val executor: Executor,
    private val ttlMillis: Long = 30_000,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val reader: (File) -> PluginDescriptor = PluginDescriptorReader::read
) {
    private data class Fingerprint(val size: Long, val modified: FileTime)
    private data class Entry(val fingerprint: Fingerprint, val descriptor: PluginDescriptor?, val warning: GraphMessage?)
    private var entries: Map<String, Entry> = emptyMap()
    @Volatile private var snapshot: PluginGraph? = null
    private var refreshedAt = 0L
    private var generation = 0L
    private var cachedGeneration = -1L
    private var inFlight: CompletableFuture<PluginGraph>? = null

    fun peek(): PluginGraph? = snapshot

    @Synchronized
    fun invalidate() {
        generation++
    }

    @Synchronized
    fun get(force: Boolean = false): CompletableFuture<PluginGraph> {
        inFlight?.let { return it }
        val current = snapshot
        if (!force && current != null && cachedGeneration == generation && clock() - refreshedAt < ttlMillis) {
            return CompletableFuture.completedFuture(current)
        }
        val future = CompletableFuture<PluginGraph>()
        inFlight = future
        val startedGeneration = generation
        try {
            executor.execute {
                try {
                    val next = scan(force)
                    val graph = PluginGraph(next.values.mapNotNull { it.descriptor }, next.values.mapNotNull { it.warning })
                    synchronized(this) {
                        entries = next
                        snapshot = graph
                        refreshedAt = clock()
                        cachedGeneration = startedGeneration
                        inFlight = null
                    }
                    future.complete(graph)
                } catch (e: Exception) {
                    synchronized(this) { inFlight = null }
                    future.completeExceptionally(e)
                }
            }
        } catch (e: Exception) {
            inFlight = null
            future.completeExceptionally(e)
        }
        return future
    }

    private fun scan(force: Boolean): Map<String, Entry> {
        if (!directory.exists()) return emptyMap()
        val files = directory.listFiles() ?: throw GraphException(GraphMessage("errors.directory"))
        val next = linkedMapOf<String, Entry>()
        for (file in files.sortedBy { it.name }) {
            if (!file.isFile || !file.name.endsWith(".jar", ignoreCase = true)) continue
            val path = file.absolutePath
            try {
                val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                val fingerprint = Fingerprint(attrs.size(), attrs.lastModifiedTime())
                val cached = entries[path]
                next[path] = if (!force && cached?.fingerprint == fingerprint) cached else {
                    try {
                        Entry(fingerprint, reader(file), null)
                    } catch (e: Exception) {
                        val detail = (e as? GraphException)?.detail
                        Entry(fingerprint, null, if (detail != null) {
                            GraphMessage("scan-error.${detail.key.removePrefix("errors.")}", detail.arguments + ("file" to file.name))
                        } else {
                            GraphMessage("warnings.unreadable", mapOf("file" to file.name, "error" to (e.message ?: e.javaClass.simpleName)))
                        })
                    }
                }
            } catch (e: IOException) {
                // Retry unreadable/disappearing files on the next refresh.
                next[path] = Entry(Fingerprint(-1, FileTime.fromMillis(0)), null,
                    GraphMessage("warnings.unreadable", mapOf("file" to file.name, "error" to (e.message ?: e.javaClass.simpleName))))
            }
        }
        return next
    }
}
