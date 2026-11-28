package com.ultimateiptv.sports.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Small wrapper around OkHttp that always closes the response and maps HTTP errors. */
@Singleton
class HttpFetcher @Inject constructor(val client: OkHttpClient) {

    suspend fun <T> stream(url: String, userAgent: String?, block: (InputStream, Response) -> T): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).apply {
                if (!userAgent.isNullOrBlank()) header("User-Agent", userAgent)
                header("Accept", "*/*")
            }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw HttpStatusException(response.code)
                val body = response.body ?: throw HttpStatusException(response.code, "Empty body")
                body.byteStream().use { block(it, response) }
            }
        }

    suspend fun text(url: String, userAgent: String?): String =
        stream(url, userAgent) { input, _ -> input.readBytes().toString(Charsets.UTF_8) }
}
