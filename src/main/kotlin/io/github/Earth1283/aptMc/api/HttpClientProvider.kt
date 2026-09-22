package io.github.Earth1283.aptMc.api

import com.google.gson.Gson
import java.net.http.HttpClient
import java.time.Duration

object HttpClientProvider {
    @JvmField val CLIENT: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()
    @JvmField val GSON: Gson = Gson()
    const val USER_AGENT: String = "apt-mc/1.5 (parody-cli)"

    /** Timeout for short metadata/API calls (search, project/version lookups). */
    @JvmField val API_TIMEOUT: Duration = Duration.ofSeconds(20)

    /** Timeout for file downloads, which can legitimately take much longer. */
    @JvmField val DOWNLOAD_TIMEOUT: Duration = Duration.ofMinutes(10)
}
