package com.example.edunet.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.edunet.data.SessionManager
import com.example.edunet.data.repository.AuthResult
import com.example.edunet.data.repository.MongoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val role: String, val name: String, val email: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val session = SessionManager(application.applicationContext)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        // Auto-restore session if user was previously logged in
        if (session.isLoggedIn()) {
            _uiState.value = AuthUiState.Success(
                role  = session.getUserRole(),
                name  = session.getUserName(),
                email = session.getUserEmail()
            )
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all fields")
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = MongoRepository.login(email.trim(), password)
            if (result is AuthResult.Success) {
                session.saveSession(result.userId, result.name, result.email, result.role)
                _uiState.value = AuthUiState.Success(result.role, result.name, result.email)
            } else {
                _uiState.value = AuthUiState.Error((result as AuthResult.Error).message)
            }
        }
    }

    fun signUp(name: String, email: String, password: String, role: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please fill in all fields")
            return
        }
        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters")
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            val result = MongoRepository.signUp(name.trim(), email.trim(), password, role)
            if (result is AuthResult.Success) {
                session.saveSession(result.userId, result.name, result.email, result.role)
                _uiState.value = AuthUiState.Success(result.role, result.name, result.email)
            } else {
                _uiState.value = AuthUiState.Error((result as AuthResult.Error).message)
            }
        }
    }

    fun logout() {
        session.clearSession()
        _uiState.value = AuthUiState.Idle
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
