package io.github.Earth1283.aptMc.api

import com.google.gson.JsonObject
import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object GitHubAPI {
    private const val BASE_URL = "https://api.github.com/repos"

    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun getLatestRelease(ownerRepo: String): JsonObject? {
        val url = String.format("%s/%s/releases/latest", BASE_URL, ownerRepo)
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", HttpClientProvider.USER_AGENT)
            .header("Accept", "application/vnd.github.v3+json")
            .GET()
            .build()

        val response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) return null
        if (response.statusCode() != 200) {
            throw IOException("GitHub API request failed with status " + response.statusCode())
        }

        return HttpClientProvider.GSON.fromJson(response.body(), JsonObject::class.java)
    }

    @JvmStatic
    fun getPrimaryJarAsset(release: JsonObject): JsonObject? {
        if (!release.has("assets")) return null
        val assets = release.getAsJsonArray("assets")

        var bestMatch: JsonObject? = null
        for (element in assets) {
            val asset = element.asJsonObject
            val name = asset.get("name").asString.lowercase()
            if (name.endsWith(".jar")) {
                if (name.contains("-all") || name.contains("-shaded")) {
                    return asset
                }
                if (bestMatch == null) {
                    bestMatch = asset
                }
            }
        }
        return bestMatch
    }
}
