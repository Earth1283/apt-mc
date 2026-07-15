package io.github.Earth1283.aptMc.api

import com.google.gson.JsonObject
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

object HangarAPI {
    private const val BASE_URL = "https://hangar.papermc.io/api/v1"

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun search(query: String, limit: Int, offset: Int): JsonObject {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val url = String.format("%s/projects?q=%s&limit=%d&offset=%d", BASE_URL, encodedQuery, limit, offset)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .GET()
            .build()

        val response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IOException("Hangar API search failed: " + response.statusCode())
        }

        return HttpClientProvider.GSON.fromJson(response.body(), JsonObject::class.java)
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun getProject(slug: String): JsonObject? {
        val url = String.format("%s/projects/%s", BASE_URL, slug)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .GET()
            .build()

        val response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) return null
        if (response.statusCode() != 200) {
            throw IOException("Hangar API getProject failed: " + response.statusCode())
        }

        return HttpClientProvider.GSON.fromJson(response.body(), JsonObject::class.java)
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun getLatestVersion(slug: String, channel: String?): JsonObject? {
        var url = String.format("%s/projects/%s/versions?limit=1", BASE_URL, slug)
        if (channel != null) {
            url += "&channel=" + URLEncoder.encode(channel, StandardCharsets.UTF_8)
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .GET()
            .build()

        val response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IOException("Hangar API getVersions failed: " + response.statusCode())
        }

        val json = HttpClientProvider.GSON.fromJson(response.body(), JsonObject::class.java)
        val results = json.getAsJsonArray("result")
        if (results.size() > 0) {
            return results.get(0).asJsonObject
        }
        return null
    }

    @JvmStatic
    fun getDownloadUrl(versionObj: JsonObject, platform: String): String? {
        if (versionObj.has("downloads")) {
            val downloads = versionObj.getAsJsonObject("downloads")
            if (downloads.has(platform)) {
                return downloads.getAsJsonObject(platform).get("downloadUrl").asString
            }
            if (downloads.has("PAPER")) {
                return downloads.getAsJsonObject("PAPER").get("downloadUrl").asString
            }
        }
        return null
    }

    @JvmStatic
    fun getFileName(versionObj: JsonObject, platform: String): String? {
        if (versionObj.has("downloads")) {
            val downloads = versionObj.getAsJsonObject("downloads")
            if (downloads.has(platform)) {
                return downloads.getAsJsonObject(platform).getAsJsonObject("fileInfo").get("name").asString
            }
            if (downloads.has("PAPER")) {
                return downloads.getAsJsonObject("PAPER").getAsJsonObject("fileInfo").get("name").asString
            }
        }
        return null
    }
}
