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

    init {
        loadCache()
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
    fun downloadFile(url: String, destDir: File, filename: String, size: Long, progressCallback: Consumer<Double>?) {
        if (!destDir.exists()) destDir.mkdirs()
        val destPath = File(destDir, filename)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", HttpClientProvider.USER_AGENT)
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

    fun getInstalledPlugins(): Map<String, String> {
        val plugins: MutableMap<String, String> = HashMap()
        if (!pluginsDir.exists()) return plugins

        val files = pluginsDir.listFiles() ?: return plugins

        for (f in files) {
            if (f.isFile && f.name.lowercase().endsWith(".jar")) {
                try {
                    plugins[f.name] = calculateSha1(f)
                } catch (e: IOException) {
                    logger.log(Level.WARNING, "Could not read plugin file: " + f.name, e)
                }
            }
        }
        return plugins
    }

    fun getPluginsDir(): File {
        return pluginsDir
    }
}
