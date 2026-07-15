package io.github.Earth1283.aptMc.api

import com.google.gson.Gson
import java.net.http.HttpClient

object HttpClientProvider {
    @JvmField val CLIENT: HttpClient = HttpClient.newHttpClient()
    @JvmField val GSON: Gson = Gson()
    const val USER_AGENT: String = "apt-mc/1.2 (parody-cli)"
}
