package com.example.edunet.data.network

import android.util.Log
import com.example.edunet.data.local.SavedFile
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG  = "PeerSyncServer"
const val PEER_HTTP_PORT = 8081   // different from session server port 8080

/**
 * NanoHTTPD server that serves a student's local history files to a peer.
 * Routes:
 *   GET /catalog?subject=XX       → JSON array of {dateKey, files:[{name,size,mimeType}]}
 *   GET /file?date=YYYY-MM-DD&name=foo.pdf → raw file bytes
 */
class PeerSyncServer(
    private val subjectCode: String,
    private val records: Map<String, List<SavedFile>>    // dateKey → files
) : NanoHTTPD(PEER_HTTP_PORT) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.uri == "/catalog" -> serveCatalog()
                session.uri == "/file"    -> serveFile(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "serve error: ${e.message}")
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
    }

    private fun serveCatalog(): Response {
        val arr = JSONArray()
        records.forEach { (dateKey, files) ->
            arr.put(JSONObject().apply {
                put("dateKey", dateKey)
                put("files", JSONArray().also { fa ->
                    files.forEach { f ->
                        fa.put(JSONObject().apply {
                            put("name",      f.name)
                            put("size",      f.sizeBytes)
                            put("mimeType",  f.mimeType)
                            put("receivedAt",f.receivedAt)
                        })
                    }
                })
            })
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", arr.toString())
    }

    private fun serveFile(session: IHTTPSession): Response {
        val params   = session.parameters
        val dateKey  = params["date"]?.firstOrNull() ?: return badRequest("missing date")
        val name     = params["name"]?.firstOrNull() ?: return badRequest("missing name")
        val file     = records[dateKey]?.firstOrNull { it.name == name }
            ?: return badRequest("file not found")
        val f = File(file.localPath)
        if (!f.exists()) return badRequest("file missing on disk")
        return newChunkedResponse(Response.Status.OK, file.mimeType, f.inputStream())
    }

    private fun badRequest(msg: String) =
        newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, msg)
}
