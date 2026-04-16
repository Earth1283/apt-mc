package io.github.Earth1283.aptMc.managers;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.github.Earth1283.aptMc.api.HttpClientProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PackageManager {
    private final File dataFolder;
    private final File pluginsDir;
    private final File cacheFile;
    private Map<String, JsonObject> cache;
    private final Logger logger;

    public PackageManager(File dataFolder, File pluginsDir, Logger logger) {
        this.dataFolder = dataFolder;
        this.pluginsDir = pluginsDir;
        this.cacheFile = new File(dataFolder, "cache.json");
        this.logger = logger;
        this.cache = new HashMap<>();
        loadCache();
    }

    private void loadCache() {
        if (cacheFile.exists()) {
            try (FileReader reader = new FileReader(cacheFile)) {
                Type type = new TypeToken<Map<String, JsonObject>>(){}.getType();
                Map<String, JsonObject> loaded = HttpClientProvider.GSON.fromJson(reader, type);
                if (loaded != null) {
                    this.cache = loaded;
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load package cache", e);
            }
        }
    }

    public void saveCache() {
        if (!dataFolder.exists()) dataFolder.mkdirs();
        try (FileWriter writer = new FileWriter(cacheFile)) {
            HttpClientProvider.GSON.toJson(cache, writer);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save package cache", e);
        }
    }

    public JsonObject getCachedInfo(String sha1) {
        return cache.get(sha1);
    }

    public void updateCache(String sha1, JsonObject info) {
        cache.put(sha1, info);
        saveCache();
    }

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
                .header("User-Agent", HttpClientProvider.USER_AGENT)
                .GET()
                .build();

        HttpResponse<java.io.InputStream> response = HttpClientProvider.CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
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
                byte[] buffer = new byte[8192];
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
                    logger.log(Level.WARNING, "Could not read plugin file: " + f.getName(), e);
                }
            }
        }
        return plugins;
    }

    public File getPluginsDir() {
        return pluginsDir;
    }
}
