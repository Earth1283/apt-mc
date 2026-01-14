package io.github.Earth1283.aptMc.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class HangarAPI {
    private static final String BASE_URL = "https://hangar.papermc.io/api/v1";
    private static final String USER_AGENT = "apt-mc/1.2 (parody-cli)";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    public static JsonObject search(String query, int limit, int offset) throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = String.format("%s/projects?q=%s&limit=%d&offset=%d", BASE_URL, encodedQuery, limit, offset);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Hangar API search failed: " + response.statusCode());
        }

        return gson.fromJson(response.body(), JsonObject.class);
    }

    public static JsonObject getProject(String slug) throws IOException, InterruptedException {
        String url = String.format("%s/projects/%s", BASE_URL, slug);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() != 200) {
            throw new IOException("Hangar API getProject failed: " + response.statusCode());
        }

        return gson.fromJson(response.body(), JsonObject.class);
    }

    public static JsonObject getLatestVersion(String slug, String channel) throws IOException, InterruptedException {
        // Hangar versions endpoint: /projects/{slug}/versions
        // We can filter by channel if needed, but basic implementation first.
        // Hangar doesn't have a simple "latest" endpoint like Modrinth's version list logic exactly, 
        // but getting versions?limit=1 usually works.
        
        String url = String.format("%s/projects/%s/versions?limit=1", BASE_URL, slug);
        if (channel != null) {
            url += "&channel=" + URLEncoder.encode(channel, StandardCharsets.UTF_8);
        }
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Hangar API getVersions failed: " + response.statusCode());
        }

        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        JsonArray results = json.getAsJsonArray("result");
        if (results.size() > 0) {
            return results.get(0).getAsJsonObject();
        }
        return null;
    }
    
    // Helper to get download URL from a version object
    // Hangar version object structure is complex.
    // It has "downloads" map: { "PAPER": { "downloadUrl": "...", "fileInfo": { "name": "..." } } }
    public static String getDownloadUrl(JsonObject versionObj, String platform) {
        if (versionObj.has("downloads")) {
            JsonObject downloads = versionObj.getAsJsonObject("downloads");
            if (downloads.has(platform)) {
                return downloads.getAsJsonObject(platform).get("downloadUrl").getAsString();
            }
            // Fallback to PAPER if requested platform missing?
            if (downloads.has("PAPER")) {
                return downloads.getAsJsonObject("PAPER").get("downloadUrl").getAsString();
            }
        }
        return null;
    }
    
    public static String getFileName(JsonObject versionObj, String platform) {
        if (versionObj.has("downloads")) {
            JsonObject downloads = versionObj.getAsJsonObject("downloads");
            if (downloads.has(platform)) {
                return downloads.getAsJsonObject(platform).getAsJsonObject("fileInfo").get("name").getAsString();
            }
             if (downloads.has("PAPER")) {
                return downloads.getAsJsonObject("PAPER").getAsJsonObject("fileInfo").get("name").getAsString();
            }
        }
        return null;
    }
}
