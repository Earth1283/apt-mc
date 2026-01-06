package io.github.Earth1283.aptMc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModrinthAPI {
    private static final String BASE_URL = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "apt-mc/1.2 (parody-cli)";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    public static JsonArray search(String query, int limit) throws IOException, InterruptedException {
        String facets = "[[\"project_type:plugin\"], [\"categories:spigot\", \"categories:paper\", \"categories:purpur\", \"categories:bukkit\"]]";
        // Encode facets and query properly in a real app, but for now simple string concat if query is simple
        // Better to use URI builder logic or simple replacement
        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String encodedFacets = java.net.URLEncoder.encode(facets, java.nio.charset.StandardCharsets.UTF_8);

        String url = String.format("%s/search?query=%s&facets=%s&limit=%d", BASE_URL, encodedQuery, encodedFacets, limit);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("API request failed with status " + response.statusCode());
        }

        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        return json.getAsJsonArray("hits");
    }

    public static JsonObject getProject(String idOrSlug) throws IOException, InterruptedException {
        String url = String.format("%s/project/%s", BASE_URL, idOrSlug);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) {
            throw new IOException("API request failed with status " + response.statusCode());
        }

        return gson.fromJson(response.body(), JsonObject.class);
    }

    public static JsonArray getVersions(String projectId, List<String> loaders) throws IOException, InterruptedException {
        String loadersJson = gson.toJson(loaders);
        String encodedLoaders = java.net.URLEncoder.encode(loadersJson, java.nio.charset.StandardCharsets.UTF_8);
        String url = String.format("%s/project/%s/version?loaders=%s", BASE_URL, projectId, encodedLoaders);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("API request failed with status " + response.statusCode());
        }

        return gson.fromJson(response.body(), JsonArray.class);
    }

    public static JsonObject getVersionsByHashes(List<String> hashes) throws IOException, InterruptedException {
        if (hashes.isEmpty()) return new JsonObject();

        JsonObject body = new JsonObject();
        JsonArray hashesArray = new JsonArray();
        for (String hash : hashes) hashesArray.add(hash);
        body.add("hashes", hashesArray);
        body.addProperty("algorithm", "sha1");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/version_files"))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("API request failed with status " + response.statusCode());
        }

        return gson.fromJson(response.body(), JsonObject.class);
    }

    public static JsonArray getMembers(String idOrSlug) throws IOException, InterruptedException {
        String url = String.format("%s/project/%s/members", BASE_URL, idOrSlug);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("API request failed with status " + response.statusCode());
        }

        return gson.fromJson(response.body(), JsonArray.class);
    }
}
