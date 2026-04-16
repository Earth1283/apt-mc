package io.github.Earth1283.aptMc.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class GitHubAPI {
    private static final String BASE_URL = "https://api.github.com/repos";

    public static JsonObject getLatestRelease(String ownerRepo) throws IOException, InterruptedException {
        String url = String.format("%s/%s/releases/latest", BASE_URL, ownerRepo);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .header("Accept", "application/vnd.github.v3+json")
                .GET()
                .build();

        HttpResponse<String> response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) {
            throw new IOException("GitHub API request failed with status " + response.statusCode());
        }

        return HttpClientProvider.GSON.fromJson(response.body(), JsonObject.class);
    }

    public static JsonObject getPrimaryJarAsset(JsonObject release) {
        if (!release.has("assets")) return null;
        JsonArray assets = release.getAsJsonArray("assets");
        
        // Prefer assets containing "-all.jar" or just ".jar"
        JsonObject bestMatch = null;
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = asset.get("name").getAsString().toLowerCase();
            if (name.endsWith(".jar")) {
                if (name.contains("-all") || name.contains("-shaded")) {
                    return asset; // High priority match
                }
                if (bestMatch == null) {
                    bestMatch = asset;
                }
            }
        }
        return bestMatch;
    }
}
