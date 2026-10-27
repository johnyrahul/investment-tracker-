package com.johnyrahul.investmenttracker.core.sync

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

/** Minimal GET helper; the app only talks to a handful of fixed HTTPS endpoints. */
internal fun <T> httpGet(url: String, headers: Map<String, String> = emptyMap(), read: (InputStream) -> T): T {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) InvestmentTracker")
        headers.forEach(connection::setRequestProperty)
        val code = connection.responseCode
        if (code !in 200..299) throw HttpStatusException(code, url.substringBefore('?'))
        return connection.inputStream.use(read)
    } finally {
        connection.disconnect()
    }
}
