package io.github.Earth1283.aptMc.managers

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared, bounded thread pool for network fan-out (dependency resolution, upgrade checks,
 * parallel downloads/imports). Using a fixed pool instead of ForkJoinPool.commonPool()
 * caps how many simultaneous requests hit Modrinth/Hangar/GitHub and avoids starving the
 * JVM-wide common pool that other plugins may also rely on.
 */
object AsyncExecutor {
    private const val POOL_SIZE = 6
    private val threadCounter = AtomicInteger(1)
    private val threadFactory = ThreadFactory { r ->
        Thread(r, "apt-mc-worker-${threadCounter.getAndIncrement()}").apply { isDaemon = true }
    }

    @JvmField val POOL: ExecutorService = Executors.newFixedThreadPool(POOL_SIZE, threadFactory)

    fun shutdown() {
        POOL.shutdown()
        try {
            if (!POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                POOL.shutdownNow()
            }
        } catch (e: InterruptedException) {
            POOL.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
