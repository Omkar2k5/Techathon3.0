package com.example.edunet.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.edunet.data.repository.SubjectItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences-backed cache for subject lists.
 * Keys:
 *   student_subjects_{userId}  -> JSON array
 *   teacher_subjects_{userId}  -> JSON array
 */
class SubjectCache(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("subject_cache", Context.MODE_PRIVATE)

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // ─── Connectivity ─────────────────────────────────────────────────────────
    fun isOnline(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps    = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // ─── Student subjects ──────────────────────────────────────────────────────
    fun saveStudentSubjects(userId: String, subjects: List<SubjectItem>) {
        val arr = JSONArray()
        subjects.forEach { s ->
            arr.put(JSONObject().apply {
                put("subject_id",   s.subjectId)
                put("subject_name", s.subjectName)
                put("subject_code", s.subjectCode)
                put("teacher_name", s.teacherName)
                put("enrolled_at",  s.enrolledAt)
            })
        }
        prefs.edit().putString("student_$userId", arr.toString()).apply()
    }

    fun loadStudentSubjects(userId: String): List<SubjectItem>? {
        val raw = prefs.getString("student_$userId", null) ?: return null
        return parseSubjectArray(raw)
    }

    // ─── Teacher subjects ──────────────────────────────────────────────────────
    fun saveTeacherSubjects(userId: String, subjects: List<SubjectItem>) {
        val arr = JSONArray()
        subjects.forEach { s ->
            arr.put(JSONObject().apply {
                put("subject_id",     s.subjectId)
                put("subject_name",   s.subjectName)
                put("subject_code",   s.subjectCode)
                put("student_count",  s.studentCount)
            })
        }
        prefs.edit().putString("teacher_$userId", arr.toString()).apply()
    }

    fun loadTeacherSubjects(userId: String): List<SubjectItem>? {
        val raw = prefs.getString("teacher_$userId", null) ?: return null
        return parseSubjectArrayTeacher(raw)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private fun parseSubjectArray(json: String): List<SubjectItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SubjectItem(
                subjectId   = o.optString("subject_id"),
                subjectName = o.optString("subject_name"),
                subjectCode = o.optString("subject_code"),
                teacherName = o.optString("teacher_name"),
                enrolledAt  = o.optString("enrolled_at"),
            )
        }
    }

    private fun parseSubjectArrayTeacher(json: String): List<SubjectItem> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SubjectItem(
                subjectId    = o.optString("subject_id"),
                subjectName  = o.optString("subject_name"),
                subjectCode  = o.optString("subject_code"),
                studentCount = o.optInt("student_count", 0),
            )
        }
    }

    fun clearAll() = prefs.edit().clear().apply()
}
