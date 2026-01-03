package io.github.Earth1283.aptMc;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class PackageManager {
    private final File dataFolder;
    private final File pluginsDir;
    private final File cacheFile;
    private Map<String, JsonObject> cache;
    private static final String USER_AGENT = "apt-mc/1.0 (parody-cli)";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    public PackageManager(File dataFolder, File pluginsDir) {
        this.dataFolder = dataFolder;
        this.pluginsDir = pluginsDir;
        this.cacheFile = new File(dataFolder, "cache.json");
        this.cache = new HashMap<>();
        loadCache();
    }

    private void loadCache() {
        if (cacheFile.exists()) {
            try (FileReader reader = new FileReader(cacheFile)) {
                Type type = new TypeToken<Map<String, JsonObject>>(){}.getType();
                Map<String, JsonObject> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    this.cache = loaded;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveCache() {
        if (!dataFolder.exists()) dataFolder.mkdirs();
        try (FileWriter writer = new FileWriter(cacheFile)) {
            gson.toJson(cache, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public JsonObject getCachedInfo(String sha1) {
        return cache.get(sha1);
    }

    public void updateCache(String sha1, JsonObject info) {
        cache.put(sha1, info);
        saveCache(); // Save on every update or batch? simple to save on update for now or let caller trigger.
        // Actually, saving on every put might be slow if batching. 
        // I'll make this just put, and let caller or shutdown save? 
        // Or just save. It's not high frequency.
        // Let's just save.
        saveCache();
    }
    
    // Batch update
    public void updateCache(Map<String, JsonObject> newEntries) {
        cache.putAll(newEntries);
        saveCache();
    }

    public void ensureDir() {
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs();
        }
    }

    public File getUpdateDir() {
        File updateDir = new File(pluginsDir, "update");
        if (!updateDir.exists()) {
            updateDir.mkdirs();
        }
        return updateDir;
    }

    public void downloadFile(String url, File destDir, String filename, long size, Consumer<Double> progressCallback) throws IOException, InterruptedException {
        if (!destDir.exists()) destDir.mkdirs();
        File destPath = new File(destDir, filename);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Download failed with status " + response.statusCode());
        }

        try (java.io.InputStream in = response.body();
             FileOutputStream out = new FileOutputStream(destPath)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (progressCallback != null && size > 0) {
                    progressCallback.accept((double) totalRead / size);
                }
            }
        }
    }

    public String calculateSha1(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[8192]; // 8KB buffer
                int count;
                while ((count = bis.read(buffer)) > 0) {
                    digest.update(buffer, 0, count);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate SHA1", e);
        }
    }

    public Map<String, String> getInstalledPlugins() {
        Map<String, String> plugins = new HashMap<>();
        if (!pluginsDir.exists()) return plugins;

        File[] files = pluginsDir.listFiles();
        if (files == null) return plugins;

        for (File f : files) {
            if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
                try {
                    plugins.put(f.getName(), calculateSha1(f));
                } catch (IOException e) {
                    // Ignore read errors
                }
            }
        }
        return plugins;
    }

    public File getPluginsDir() {
        return pluginsDir;
    }
}
