package com.example.edunet.data

import android.content.Context
import com.example.edunet.data.repository.AttendanceRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AttendanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("edunet_attendance", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveRecords(subjectCode: String, date: String, records: List<AttendanceRecord>, pendingSync: Boolean = true) {
        val key = "attendance_${subjectCode}_${date}"
        prefs.edit().putString(key, gson.toJson(records)).apply()
        
        if (pendingSync) {
            val pendingKey = "sync_pending_${subjectCode}_${date}"
            prefs.edit().putBoolean(pendingKey, true).apply()
        }
    }

    fun loadRecords(subjectCode: String, date: String): List<AttendanceRecord>? {
        val key = "attendance_${subjectCode}_${date}"
        val json = prefs.getString(key, null) ?: return null
        val type = object : TypeToken<List<AttendanceRecord>>() {}.type
        return try {
             gson.fromJson(json, type)
        } catch(e: Exception) {
             null
        }
    }

    fun getPendingSyncKeys(): List<String> {
        return prefs.all.keys.filter { it.startsWith("sync_pending_") && prefs.getBoolean(it, false) as? Boolean == true }
    }

    fun getRecordsByKey(pendingKey: String): List<AttendanceRecord>? {
        val recordKey = pendingKey.replace("sync_pending_", "attendance_")
        val json = prefs.getString(recordKey, null) ?: return null
        val type = object : TypeToken<List<AttendanceRecord>>() {}.type
        return try { gson.fromJson(json, type) } catch(e: Exception) { null }
    }

    fun markSynced(pendingKey: String) {
        prefs.edit().remove(pendingKey).apply()
    }
}
