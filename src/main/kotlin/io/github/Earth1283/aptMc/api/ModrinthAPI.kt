package io.github.Earth1283.aptMc.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

object ModrinthAPI {
    private const val BASE_URL = "https://api.modrinth.com/v2"

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun search(query: String, limit: Int): JsonArray {
        val facets = "[[\"project_type:plugin\"], [\"categories:spigot\", \"categories:paper\", \"categories:purpur\", \"categories:bukkit\"]]"
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val encodedFacets = URLEncoder.encode(facets, StandardCharsets.UTF_8)

        val url = String.format("%s/search?query=%s&facets=%s&limit=%d", BASE_URL, encodedQuery, encodedFacets, limit)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .GET()
            .build()

        val response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IOException("API request failed with status " + response.statusCode())
        }

        val json = HttpClientProvider.GSON.fromJson(response.body(), JsonObject::class.java)
        return json.getAsJsonArray("hits")
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun getProject(idOrSlug: String): JsonObject? {
        val url = String.format("%s/project/%s", BASE_URL, idOrSlug)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .GET()
            .build()

        val response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) return null
        if (response.statusCode() != 200) {
            throw IOException("API request failed with status " + response.statusCode())
        }

        return HttpClientProvider.GSON.fromJson(response.body(), JsonObject::class.java)
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun getVersions(projectId: String, loaders: List<String>): JsonArray {
        val loadersJson = HttpClientProvider.GSON.toJson(loaders)
        val encodedLoaders = URLEncoder.encode(loadersJson, StandardCharsets.UTF_8)
        val url = String.format("%s/project/%s/version?loaders=%s", BASE_URL, projectId, encodedLoaders)

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .GET()
            .build()

        val response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IOException("API request failed with status " + response.statusCode())
        }

        return HttpClientProvider.GSON.fromJson(response.body(), JsonArray::class.java)
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun getVersionsByHashes(hashes: List<String>): JsonObject {
        if (hashes.isEmpty()) return JsonObject()

        val body = JsonObject()
        val hashesArray = JsonArray()
        for (hash in hashes) hashesArray.add(hash)
        body.add("hashes", hashesArray)
        body.addProperty("algorithm", "sha1")

        val request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/version_files"))
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(HttpClientProvider.GSON.toJson(body)))
            .build()

        val response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IOException("API request failed with status " + response.statusCode())
        }

        return HttpClientProvider.GSON.fromJson(response.body(), JsonObject::class.java)
    }

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun getMembers(idOrSlug: String): JsonArray {
        val url = String.format("%s/project/%s/members", BASE_URL, idOrSlug)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .GET()
            .build()

        val response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw IOException("API request failed with status " + response.statusCode())
        }

        return HttpClientProvider.GSON.fromJson(response.body(), JsonArray::class.java)
    }
}
