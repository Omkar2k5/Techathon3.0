package com.example.edunet.data.network

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

data class SharedFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val file: File,
    val addedAt: Long = System.currentTimeMillis()
)

class SessionServer(
    port: Int = 8080,
    private val teacherName: String,
    private val subjectName: String,
    private val subjectCode: String
) : NanoHTTPD(port) {

    private val sharedFiles = mutableListOf<SharedFile>()
    private val connectedClients = mutableSetOf<String>()

    val fileCount: Int get() = sharedFiles.size
    val clientCount: Int get() = connectedClients.size

    fun addFile(shared: SharedFile) {
        synchronized(sharedFiles) { sharedFiles.add(shared) }
        Log.d("SessionServer", "File added: ${shared.name}")
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val clientIp = session.headers["http-client-ip"]
            ?: session.headers["x-forwarded-for"]
            ?: "unknown"
        connectedClients.add(clientIp)

        return when {
            uri == "/info" -> serveInfo()
            uri == "/files" -> serveFileList(session)
            uri.startsWith("/files/") -> serveFileDownload(uri.removePrefix("/files/"))
            uri == "/ping" -> newFixedLengthResponse(Response.Status.OK, "text/plain", "pong")
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun serveInfo(): Response {
        val json = JSONObject().apply {
            put("teacher_name", teacherName)
            put("subject_name", subjectName)
            put("subject_code", subjectCode)
            put("file_count", sharedFiles.size)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
    }

    private fun serveFileList(session: IHTTPSession): Response {
        val since = session.parameters["since"]?.firstOrNull()?.toLongOrNull() ?: 0L
        val arr = JSONArray()
        synchronized(sharedFiles) {
            sharedFiles.filter { it.addedAt > since }.forEach { f ->
                arr.put(JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("size", f.sizeBytes)
                    put("mime", f.mimeType)
                    put("added_at", f.addedAt)
                })
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", arr.toString())
    }

    private fun serveFileDownload(fileId: String): Response {
        val shared = synchronized(sharedFiles) { sharedFiles.find { it.id == fileId } }
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")
        return newChunkedResponse(
            Response.Status.OK,
            shared.mimeType,
            FileInputStream(shared.file)
        ).also {
            it.addHeader("Content-Disposition", "attachment; filename=\"${shared.name}\"")
            it.addHeader("Content-Length", shared.sizeBytes.toString())
        }
    }
}
