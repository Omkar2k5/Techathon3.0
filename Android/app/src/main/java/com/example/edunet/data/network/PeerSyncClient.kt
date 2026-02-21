package com.example.edunet.data.network

import com.example.edunet.data.local.SavedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class PeerDayRecord(
    val dateKey: String,
    val files: List<PeerFileInfo>
)

data class PeerFileInfo(
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val receivedAt: Long
)

object PeerSyncClient {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** Fetch catalog of available dates+files from peer */
    suspend fun getCatalog(baseUrl: String): List<PeerDayRecord> = withContext(Dispatchers.IO) {
        val conn = URL("$baseUrl/catalog").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 10000
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = JSONArray(body)
        (0 until arr.length()).map { i ->
            val obj   = arr.getJSONObject(i)
            val files = obj.getJSONArray("files")
            PeerDayRecord(
                dateKey = obj.getString("dateKey"),
                files   = (0 until files.length()).map { j ->
                    val f = files.getJSONObject(j)
                    PeerFileInfo(f.getString("name"), f.getLong("size"), f.getString("mimeType"), f.optLong("receivedAt"))
                }
            )
        }
    }

    /** Download a single file from peer, save to dest dir. Returns SavedFile on success. */
    suspend fun downloadFile(
        baseUrl: String,
        dateKey: String,
        fileInfo: PeerFileInfo,
        destDir: File
    ): SavedFile = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(fileInfo.name, "UTF-8")
        val conn = URL("$baseUrl/file?date=$dateKey&name=$encoded").openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 60_000
        destDir.mkdirs()
        val dest = File(destDir, fileInfo.name)
        conn.inputStream.use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
        conn.disconnect()
        SavedFile(
            name       = fileInfo.name,
            localPath  = dest.absolutePath,
            mimeType   = fileInfo.mimeType,
            sizeBytes  = dest.length(),
            receivedAt = fileInfo.receivedAt.takeIf { it > 0 }
                ?: dateFmt.parse(dateKey)?.time ?: System.currentTimeMillis()
        )
    }
}
