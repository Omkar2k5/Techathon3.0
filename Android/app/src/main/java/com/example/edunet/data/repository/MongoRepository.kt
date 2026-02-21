package com.example.edunet.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class AuthResult {
    data class Success(val role: String, val name: String, val email: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

object MongoRepository {

    // ─── PC's local IP — phone must be on the same Wi-Fi network ────────────────
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

    /** Login – password is sent as plain text; the backend hashes it */
    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            val resp = post("/login", body)
            AuthResult.Success(
                role  = resp.optString("role", "student"),
                name  = resp.optString("name", ""),
                email = resp.optString("email", email)
            )
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Login failed")
        }
    }

    /** Sign Up – password is sent as plain text; the backend hashes it */
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
                    role  = resp.optString("role", role),
                    name  = resp.optString("name", name),
                    email = resp.optString("email", email)
                )
            } catch (e: Exception) {
                AuthResult.Error(e.message ?: "Sign up failed")
            }
        }
}
