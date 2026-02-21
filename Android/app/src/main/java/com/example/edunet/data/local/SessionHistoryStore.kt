package com.example.edunet.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class SessionHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("session_history", Context.MODE_PRIVATE)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun todayKey(): String = dateFmt.format(Date())

    // ── Write ──────────────────────────────────────────────────────────────────

    fun saveFile(subjectCode: String, subjectName: String, file: SavedFile) {
        val dateKey = dateFmt.format(Date(file.receivedAt))
        val key     = recordKey(subjectCode, dateKey)
        val arr     = loadRawFiles(key)
        arr.put(fileToJson(file))
        // store back
        prefs.edit()
            .putString(key, arr.toString())
            .putString(nameKey(subjectCode), subjectName)     // remember display name
            .putString(datesKey(subjectCode), addDate(subjectCode, dateKey))
            .apply()
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    /** All session records for a subject, newest date first. */
    fun getRecords(subjectCode: String): List<SessionRecord> {
        val name = prefs.getString(nameKey(subjectCode), subjectCode) ?: subjectCode
        return getDates(subjectCode).map { dateKey ->
            val files = loadRawFiles(recordKey(subjectCode, dateKey)).let { arr ->
                (0 until arr.length()).map { jsonToFile(arr.getJSONObject(it)) }
            }
            SessionRecord(subjectCode, name, dateKey, files)
        }.sortedByDescending { it.dateKey }
    }

    /** Every subject code that has at least one record. */
    fun getAllSubjectCodes(): List<String> =
        prefs.getString("all_subjects", null)
            ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    fun hasDate(subjectCode: String, dateKey: String): Boolean =
        getDates(subjectCode).contains(dateKey)

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun recordKey(code: String, date: String) = "files_${code}_$date"
    private fun nameKey(code: String)  = "name_$code"
    private fun datesKey(code: String) = "dates_$code"

    private fun loadRawFiles(key: String): JSONArray =
        JSONArray(prefs.getString(key, "[]") ?: "[]")

    private fun getDates(code: String): List<String> =
        prefs.getString(datesKey(code), null)
            ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    private fun addDate(code: String, dateKey: String): String {
        val existing = getDates(code).toMutableList()
        if (!existing.contains(dateKey)) {
            existing.add(dateKey)
            // Track this subject globally
            val all = getAllSubjectCodes().toMutableList()
            if (!all.contains(code)) {
                all.add(code)
                prefs.edit().putString("all_subjects", all.joinToString(",")).apply()
            }
        }
        return existing.joinToString(",")
    }

    private fun fileToJson(f: SavedFile) = JSONObject().apply {
        put("name",       f.name)
        put("localPath",  f.localPath)
        put("mimeType",   f.mimeType)
        put("sizeBytes",  f.sizeBytes)
        put("receivedAt", f.receivedAt)
    }

    private fun jsonToFile(o: JSONObject) = SavedFile(
        name       = o.optString("name"),
        localPath  = o.optString("localPath"),
        mimeType   = o.optString("mimeType"),
        sizeBytes  = o.optLong("sizeBytes"),
        receivedAt = o.optLong("receivedAt")
    )
}
