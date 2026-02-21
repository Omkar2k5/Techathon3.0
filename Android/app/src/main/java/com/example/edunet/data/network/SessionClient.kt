package com.example.edunet.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SessionInfo(
    val teacherName: String,
    val subjectName: String,
    val subjectCode: String,
    val fileCount: Int
)

data class RemoteFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val addedAt: Long
)

object SessionClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun get(url: String): String {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body?.string() ?: ""
        }
    }

    suspend fun getSessionInfo(baseUrl: String): SessionInfo = withContext(Dispatchers.IO) {
        val json = JSONObject(get("$baseUrl/info"))
        SessionInfo(
            teacherName = json.optString("teacher_name"),
            subjectName = json.optString("subject_name"),
            subjectCode = json.optString("subject_code"),
            fileCount   = json.optInt("file_count", 0)
        )
    }

    suspend fun getFiles(baseUrl: String, since: Long = 0L): List<RemoteFile> =
        withContext(Dispatchers.IO) {
            val arr = JSONArray(get("$baseUrl/files?since=$since"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RemoteFile(
                    id        = o.optString("id"),
                    name      = o.optString("name"),
                    sizeBytes = o.optLong("size"),
                    mimeType  = o.optString("mime", "application/octet-stream"),
                    addedAt   = o.optLong("added_at")
                )
            }
        }

    suspend fun downloadFileBytes(baseUrl: String, fileId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("$baseUrl/files/$fileId").build()
            client.newCall(req).execute().use { resp ->
                resp.body?.bytes() ?: throw Exception("Empty response")
            }
        }

    suspend fun ping(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        try { get("$baseUrl/ping") == "pong" } catch (_: Exception) { false }
    }
}
