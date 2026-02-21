package com.example.edunet.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("edunet_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_ID      = "user_id"
        private const val KEY_USER_NAME    = "user_name"
        private const val KEY_USER_EMAIL   = "user_email"
        private const val KEY_USER_ROLE    = "user_role"
    }

    fun saveSession(userId: String, name: String, email: String, role: String) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_ROLE, role)
            .apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun getUserId(): String    = prefs.getString(KEY_USER_ID, "") ?: ""
    fun getUserName(): String  = prefs.getString(KEY_USER_NAME, "") ?: ""
    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""
    fun getUserRole(): String  = prefs.getString(KEY_USER_ROLE, "student") ?: "student"

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
