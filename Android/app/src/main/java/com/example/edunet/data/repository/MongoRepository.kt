package com.example.edunet.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class AuthResult {
    data class Success(
        val userId: String,
        val role: String,
        val name: String,
        val email: String
    ) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

data class SubjectItem(
    val subjectId: String,
    val subjectName: String,
    val subjectCode: String,
    val teacherName: String = "",
    val enrolledAt: String = "",
    val studentCount: Int = 0,
)

sealed class SubjectResult {
    data class Success(val subjects: List<SubjectItem>) : SubjectResult()
    data class Error(val message: String) : SubjectResult()
}

sealed class JoinResult {
    data class Success(val subject: SubjectItem) : JoinResult()
    data class Error(val message: String) : JoinResult()
}

data class AttendanceRecord(
    val studentId: String,
    val studentName: String,
    val status: String, // "present", "absent", "late"
    val markedAt: String
)

data class EnrolledStudent(
    val studentId: String,
    val studentName: String,
    val studentEmail: String
)


object MongoRepository {

    // ─── PC's local IP — phone must be on the same Wi-Fi / hotspot network ───
    private const val BASE_URL = "http://192.168.137.1:8000"

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun post(endpoint: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(bodyStr).optString("detail", "Unknown error")
                }.getOrDefault("Server error ${response.code}")
                throw Exception(detail)
            }
            return JSONObject(bodyStr)
        }
    }

    private fun get(endpoint: String): String {
        val request = Request.Builder()
            .url("$BASE_URL$endpoint")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: "[]"
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(bodyStr).optString("detail", "Unknown error")
                }.getOrDefault("Server error ${response.code}")
                throw Exception(detail)
            }
            return bodyStr
        }
    }

    // ─── Auth ─────────────────────────────────────────────────────────────────
    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            val resp = post("/login", body)
            AuthResult.Success(
                userId = resp.optString("user_id", ""),
                role   = resp.optString("role", "student"),
                name   = resp.optString("name", ""),
                email  = resp.optString("email", email)
            )
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Login failed")
        }
    }

    suspend fun signUp(name: String, email: String, password: String, role: String): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("name", name)
                    put("email", email)
                    put("password", password)
                    put("role", role)
                }
                val resp = post("/signup", body)
                AuthResult.Success(
                    userId = resp.optString("user_id", ""),
                    role   = resp.optString("role", role),
                    name   = resp.optString("name", name),
                    email  = resp.optString("email", email)
                )
            } catch (e: Exception) {
                AuthResult.Error(e.message ?: "Sign up failed")
            }
        }

    // ─── Student: Get enrolled subjects ───────────────────────────────────────
    suspend fun getStudentSubjects(studentId: String): SubjectResult = withContext(Dispatchers.IO) {
        try {
            val body = get("/student/subjects/$studentId")
            val arr = JSONArray(body)
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SubjectItem(
                    subjectId   = o.optString("subject_id"),
                    subjectName = o.optString("subject_name"),
                    subjectCode = o.optString("subject_code"),
                    teacherName = o.optString("teacher_name"),
                    enrolledAt  = o.optString("enrolled_at"),
                )
            }
            SubjectResult.Success(list)
        } catch (e: Exception) {
            SubjectResult.Error(e.message ?: "Failed to load subjects")
        }
    }

    // ─── Student: Join class by code ──────────────────────────────────────────
    suspend fun joinClass(studentId: String, studentEmail: String, subjectCode: String): JoinResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("student_id", studentId)
                    put("student_email", studentEmail)
                    put("subject_code", subjectCode)
                }
                val resp = post("/student/join", body)
                JoinResult.Success(
                    SubjectItem(
                        subjectId   = resp.optString("subject_id"),
                        subjectName = resp.optString("subject_name"),
                        subjectCode = resp.optString("subject_code"),
                        teacherName = resp.optString("teacher_name"),
                    )
                )
            } catch (e: Exception) {
                JoinResult.Error(e.message ?: "Failed to join class")
            }
        }

    // ─── Teacher: Create a new subject ────────────────────────────────────────
    suspend fun createSubject(
        teacherId: String,
        teacherEmail: String,
        subjectName: String,
        subjectCode: String
    ): JoinResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("teacher_id", teacherId)
                put("teacher_email", teacherEmail)
                put("subject_name", subjectName)
                put("subject_code", subjectCode)
            }
            val resp = post("/teacher/subjects", body)
            JoinResult.Success(
                SubjectItem(
                    subjectId    = resp.optString("subject_id"),
                    subjectName  = resp.optString("subject_name"),
                    subjectCode  = resp.optString("subject_code"),
                    studentCount = resp.optInt("student_count", 0),
                )
            )
        } catch (e: Exception) {
            JoinResult.Error(e.message ?: "Failed to create subject")
        }
    }

    // ─── Teacher: Get my subjects ─────────────────────────────────────────────
    suspend fun getTeacherSubjects(teacherId: String): SubjectResult = withContext(Dispatchers.IO) {
        try {
            val body = get("/teacher/subjects/$teacherId")
            val arr = JSONArray(body)
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SubjectItem(
                    subjectId    = o.optString("subject_id"),
                    subjectName  = o.optString("subject_name"),
                    subjectCode  = o.optString("subject_code"),
                    studentCount = o.optInt("student_count", 0),
                )
            }
            SubjectResult.Success(list)
        } catch (e: Exception) {
            SubjectResult.Error(e.message ?: "Failed to load subjects")
        }
    }

    // ─── Attendance ───────────────────────────────────────────────────────────
    suspend fun getEnrolledStudents(subjectCode: String): List<EnrolledStudent> = withContext(Dispatchers.IO) {
        try {
            val body = get("/teacher/subjects/$subjectCode/students")
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                EnrolledStudent(
                    studentId    = o.optString("student_id"),
                    studentName  = o.optString("student_name"),
                    studentEmail = o.optString("student_email")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAttendanceForDate(subjectCode: String, date: String): List<AttendanceRecord> = withContext(Dispatchers.IO) {
        try {
            val body = get("/attendance/$subjectCode/$date")
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AttendanceRecord(
                    studentId   = o.optString("student_id"),
                    studentName = o.optString("student_name"),
                    status      = o.optString("status"),
                    markedAt    = o.optString("marked_at")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun submitAttendance(
        subjectCode: String,
        date: String,
        teacherId: String,
        records: List<AttendanceRecord>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val recArray = JSONArray()
            records.forEach { r ->
                val o = JSONObject()
                o.put("student_id", r.studentId)
                o.put("student_name", r.studentName)
                o.put("status", r.status)
                o.put("marked_at", r.markedAt)
                recArray.put(o)
            }
            val payload = JSONObject().apply {
                put("subject_code", subjectCode)
                put("date", date)
                put("teacher_id", teacherId)
                put("records", recArray)
            }
            val resp = post("/attendance/submit", payload)
            resp.optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }
}
