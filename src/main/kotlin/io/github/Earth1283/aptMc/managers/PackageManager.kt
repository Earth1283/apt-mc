package io.github.Earth1283.aptMc.managers

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.github.Earth1283.aptMc.api.HttpClientProvider
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger

class PackageManager(
    private val dataFolder: File,
    private val pluginsDir: File,
    private val logger: Logger
) {
    private val cacheFile: File = File(dataFolder, "cache.json")
    private var cache: MutableMap<String, JsonObject> = HashMap()

    private val fileHashCacheFile: File = File(dataFolder, "filehashes.json")
    private var fileHashCache: MutableMap<String, FileHashEntry> = ConcurrentHashMap()

    private val pinnedFile: File = File(dataFolder, "pinned.json")
    private var pinned: MutableMap<String, JsonObject> = ConcurrentHashMap()

    private data class FileHashEntry(val size: Long, val lastModified: Long, val sha1: String)

    init {
        loadCache()
        loadFileHashCache()
        loadPinned()
    }

    private fun loadCache() {
        if (cacheFile.exists()) {
            try {
                FileReader(cacheFile).use { reader ->
                    val type = object : TypeToken<Map<String, JsonObject>>() {}.type
                    val loaded: MutableMap<String, JsonObject>? = HttpClientProvider.GSON.fromJson(reader, type)
                    if (loaded != null) {
                        this.cache = loaded
                    }
                }
            } catch (e: IOException) {
                logger.log(Level.WARNING, "Failed to load package cache", e)
            }
        }
    }

    fun saveCache() {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        try {
            FileWriter(cacheFile).use { writer ->
                HttpClientProvider.GSON.toJson(cache, writer)
            }
        } catch (e: IOException) {
            logger.log(Level.WARNING, "Failed to save package cache", e)
        }
    }

    fun getCachedInfo(sha1: String): JsonObject? {
        return cache[sha1]
    }

    fun updateCache(sha1: String, info: JsonObject) {
        cache[sha1] = info
        saveCache()
    }

    fun updateCache(newEntries: Map<String, JsonObject>) {
        cache.putAll(newEntries)
        saveCache()
    }

    private fun loadFileHashCache() {
        if (fileHashCacheFile.exists()) {
            try {
                FileReader(fileHashCacheFile).use { reader ->
                    val type = object : TypeToken<Map<String, FileHashEntry>>() {}.type
                    val loaded: MutableMap<String, FileHashEntry>? = HttpClientProvider.GSON.fromJson(reader, type)
                    if (loaded != null) {
                        this.fileHashCache = ConcurrentHashMap(loaded)
                    }
                }
            } catch (e: IOException) {
                logger.log(Level.WARNING, "Failed to load file hash cache", e)
            }
        }
    }

    private fun saveFileHashCache() {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        try {
            FileWriter(fileHashCacheFile).use { writer ->
                HttpClientProvider.GSON.toJson(fileHashCache, writer)
            }
        } catch (e: IOException) {
            logger.log(Level.WARNING, "Failed to save file hash cache", e)
        }
    }

    private fun loadPinned() {
        if (pinnedFile.exists()) {
            try {
                FileReader(pinnedFile).use { reader ->
                    val type = object : TypeToken<Map<String, JsonObject>>() {}.type
                    val loaded: MutableMap<String, JsonObject>? = HttpClientProvider.GSON.fromJson(reader, type)
                    if (loaded != null) {
                        this.pinned = ConcurrentHashMap(loaded)
                    }
                }
            } catch (e: IOException) {
                logger.log(Level.WARNING, "Failed to load pinned packages", e)
            }
        }
    }

    private fun savePinned() {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        try {
            FileWriter(pinnedFile).use { writer ->
                HttpClientProvider.GSON.toJson(pinned, writer)
            }
        } catch (e: IOException) {
            logger.log(Level.WARNING, "Failed to save pinned packages", e)
        }
    }

    fun isPinned(projectId: String): Boolean = pinned.containsKey(projectId)

    fun pin(projectId: String, info: JsonObject) {
        pinned[projectId] = info
        savePinned()
    }

    fun unpin(projectId: String): Boolean {
        val removed = pinned.remove(projectId) != null
        if (removed) savePinned()
        return removed
    }

    fun getPinned(): Map<String, JsonObject> = pinned

    fun ensureDir() {
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }
    }

    fun getUpdateDir(): File {
        val updateDir = File(pluginsDir, "update")
        if (!updateDir.exists()) {
            updateDir.mkdirs()
        }
        return updateDir
    }

    @Throws(IOException::class, InterruptedException::class)
    fun downloadFile(url: String, destDir: File, filename: String, size: Long, progressCallback: Consumer<Double>?, expectedSha1: String? = null) {
        if (!destDir.exists()) destDir.mkdirs()
        val destPath = File(destDir, filename)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .timeout(HttpClientProvider.DOWNLOAD_TIMEOUT)
            .GET()
            .build()

        val response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            throw IOException("Download failed with status " + response.statusCode())
        }

        (response.body() as InputStream).use { input ->
            FileOutputStream(destPath).use { out ->
                val buffer = ByteArray(8192)
                var totalRead: Long = 0
                var bytesRead: Int
                while ((input.read(buffer).also { bytesRead = it }) != -1) {
                    out.write(buffer, 0, bytesRead)
                    totalRead += bytesRead.toLong()
                    if (progressCallback != null && size > 0) {
                        progressCallback.accept(totalRead.toDouble() / size)
                    }
                }
            }
        }

        if (expectedSha1 != null) {
            val actual = calculateSha1(destPath)
            if (!actual.equals(expectedSha1, ignoreCase = true)) {
                destPath.delete()
                throw IOException("Downloaded file hash mismatch for $filename (expected $expectedSha1, got $actual)")
            }
        }
    }

    @Throws(IOException::class)
    fun calculateSha1(file: File): String {
        try {
            val digest = MessageDigest.getInstance("SHA-1")
            BufferedInputStream(FileInputStream(file)).use { bis ->
                val buffer = ByteArray(8192)
                var count: Int
                while ((bis.read(buffer).also { count = it }) > 0) {
                    digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest()
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            return hexString.toString()
        } catch (e: Exception) {
            throw IOException("Failed to calculate SHA1", e)
        }
    }

    /**
     * Returns filename -> sha1 for every installed plugin jar. Hashes are cached by
     * (filename, size, lastModified) and persisted to disk so unchanged jars are not
     * re-hashed on every call.
     */
    fun getInstalledPlugins(): Map<String, String> {
        val plugins: MutableMap<String, String> = HashMap()
        if (!pluginsDir.exists()) return plugins

        val files = pluginsDir.listFiles() ?: return plugins
        var cacheChanged = false

        for (f in files) {
            if (f.isFile && f.name.lowercase().endsWith(".jar")) {
                try {
                    val cached = fileHashCache[f.name]
                    val sha1 = if (cached != null && cached.size == f.length() && cached.lastModified == f.lastModified()) {
                        cached.sha1
                    } else {
                        val computed = calculateSha1(f)
                        fileHashCache[f.name] = FileHashEntry(f.length(), f.lastModified(), computed)
                        cacheChanged = true
                        computed
                    }
                    plugins[f.name] = sha1
                } catch (e: IOException) {
                    logger.log(Level.WARNING, "Could not read plugin file: " + f.name, e)
                }
            }
        }

        if (cacheChanged) saveFileHashCache()
        return plugins
    }

    fun getPluginsDir(): File {
        return pluginsDir
    }
}
