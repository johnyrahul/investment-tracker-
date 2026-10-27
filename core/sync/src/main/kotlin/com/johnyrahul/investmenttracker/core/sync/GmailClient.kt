package com.johnyrahul.investmenttracker.core.sync

import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

/** The three Gmail REST calls needed to find CAS emails and download their PDF attachments. */
internal class GmailClient(private val accessToken: String) {

    data class Attachment(val filename: String, val bytes: ByteArray)

    private val auth get() = mapOf("Authorization" to "Bearer $accessToken")

    fun searchMessageIds(query: String, limit: Int): List<String> {
        val ids = mutableListOf<String>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$BASE/messages?maxResults=100&q=").append(URLEncoder.encode(query, "UTF-8"))
                pageToken?.let { append("&pageToken=").append(it) }
            }
            val json = getJson(url)
            val messages = json.optJSONArray("messages")
            if (messages != null) for (i in 0 until messages.length()) ids += messages.getJSONObject(i).getString("id")
            pageToken = json.optString("nextPageToken").ifEmpty { null }
        } while (pageToken != null && ids.size < limit)
        return ids.take(limit)
    }

    fun pdfAttachments(messageId: String): List<Attachment> {
        val message = getJson("$BASE/messages/$messageId?format=full")
        val result = mutableListOf<Attachment>()
        fun walk(part: JSONObject) {
            val filename = part.optString("filename")
            val isPdf = filename.endsWith(".pdf", ignoreCase = true) || part.optString("mimeType") == "application/pdf"
            if (isPdf) {
                val body = part.optJSONObject("body")
                val inline = body?.optString("data").orEmpty()
                val attachmentId = body?.optString("attachmentId").orEmpty()
                val data = when {
                    inline.isNotEmpty() -> inline
                    attachmentId.isNotEmpty() -> getJson("$BASE/messages/$messageId/attachments/$attachmentId").getString("data")
                    else -> ""
                }
                if (data.isNotEmpty()) result += Attachment(filename, Base64.getUrlDecoder().decode(data))
            }
            val parts = part.optJSONArray("parts") ?: return
            for (i in 0 until parts.length()) walk(parts.getJSONObject(i))
        }
        message.optJSONObject("payload")?.let(::walk)
        return result
    }

    private fun getJson(url: String): JSONObject =
        httpGet(url, auth) { JSONObject(it.bufferedReader().readText()) }

    private companion object {
        const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
    }
}
