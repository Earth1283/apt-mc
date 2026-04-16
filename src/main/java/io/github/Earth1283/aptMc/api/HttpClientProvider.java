package io.github.Earth1283.aptMc.api;

import com.google.gson.Gson;
import java.net.http.HttpClient;

public final class HttpClientProvider {
    public static final HttpClient CLIENT = HttpClient.newHttpClient();
    public static final Gson GSON = new Gson();
    public static final String USER_AGENT = "apt-mc/1.2 (parody-cli)";

    private HttpClientProvider() {}
}
