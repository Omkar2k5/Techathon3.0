package com.example.edunet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.edunet.ui.viewmodel.AuthUiState
import com.example.edunet.ui.viewmodel.AuthViewModel

// ── B&W palette (matches the rest of the app) ─────────────────────────────────
private val BgPage  = Color(0xFF000000)
private val BgCard  = Color(0xFF111111)
private val Border  = Color(0xFF333333)
private val TextPri = Color(0xFFFFFFFF)
private val TextSec = Color(0xFF999999)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignUpSuccess: (role: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var isStaffSelected by remember { mutableStateOf(false) }
    var name            by remember { mutableStateOf("") }
    var emailOrId       by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    val uiState         by authViewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onSignUpSuccess((uiState as AuthUiState.Success).role)
            authViewModel.resetState()
        }
    }

    val fieldColors = TextFieldDefaults.colors(
        focusedTextColor        = TextPri,
        unfocusedTextColor      = TextPri,
        focusedContainerColor   = Color(0xFF1A1A1A),
        unfocusedContainerColor = Color(0xFF1A1A1A),
        focusedIndicatorColor   = Color.White,
        unfocusedIndicatorColor = Border,
        cursorColor             = Color.White,
        focusedLabelColor       = TextSec,
        unfocusedLabelColor     = TextSec
    )

    Box(
        modifier = Modifier.fillMaxSize().background(BgPage),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Text("🎓", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("Join EduNet", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPri)
            Spacer(Modifier.height(6.dp))
            Text("Create your account to start sharing", fontSize = 14.sp, color = TextSec)
            Spacer(Modifier.height(28.dp))

            // ── Role selector ──────────────────────────────────────────────────
            Surface(
                shape  = RoundedCornerShape(12.dp),
                color  = Color(0xFF111111),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    listOf("Student" to false, "Staff" to true).forEach { (label, isStaff) ->
                        val selected = isStaffSelected == isStaff
                        Surface(
                            modifier  = Modifier.weight(1f),
                            shape     = RoundedCornerShape(10.dp),
                            color     = if (selected) Color(0xFF2A2A2A) else Color.Transparent,
                            onClick   = { isStaffSelected = isStaff }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    fontSize   = 14.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (selected) TextPri else TextSec
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Card ───────────────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BgCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {

                    OutlinedTextField(
                        value         = name,
                        onValueChange = { name = it },
                        label         = { Text("Full Name") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedTextColor     = TextPri, unfocusedTextColor = TextPri,
                            focusedBorderColor   = Color.White, unfocusedBorderColor = Border,
                            focusedLabelColor    = TextSec, unfocusedLabelColor = TextSec,
                            cursorColor          = Color.White,
                            focusedContainerColor   = Color(0xFF1A1A1A),
                            unfocusedContainerColor = Color(0xFF1A1A1A)
                        )
                    )

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value         = emailOrId,
                        onValueChange = { emailOrId = it },
                        label         = { Text(if (isStaffSelected) "Staff Email" else "Student Email") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedTextColor     = TextPri, unfocusedTextColor = TextPri,
                            focusedBorderColor   = Color.White, unfocusedBorderColor = Border,
                            focusedLabelColor    = TextSec, unfocusedLabelColor = TextSec,
                            cursorColor          = Color.White,
                            focusedContainerColor   = Color(0xFF1A1A1A),
                            unfocusedContainerColor = Color(0xFF1A1A1A)
                        )
                    )

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value                  = password,
                        onValueChange          = { password = it },
                        label                  = { Text("Password (min. 6 chars)") },
                        singleLine             = true,
                        visualTransformation   = PasswordVisualTransformation(),
                        modifier               = Modifier.fillMaxWidth(),
                        shape                  = RoundedCornerShape(12.dp),
                        colors                 = OutlinedTextFieldDefaults.colors(
                            focusedTextColor     = TextPri, unfocusedTextColor = TextPri,
                            focusedBorderColor   = Color.White, unfocusedBorderColor = Border,
                            focusedLabelColor    = TextSec, unfocusedLabelColor = TextSec,
                            cursorColor          = Color.White,
                            focusedContainerColor   = Color(0xFF1A1A1A),
                            unfocusedContainerColor = Color(0xFF1A1A1A)
                        )
                    )

                    if (uiState is AuthUiState.Error) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            (uiState as AuthUiState.Error).message,
                            color = Color(0xFFFF5555), fontSize = 13.sp
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            authViewModel.signUp(
                                name, emailOrId, password,
                                role = if (isStaffSelected) "teacher" else "student"
                            )
                        },
                        enabled  = uiState !is AuthUiState.Loading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        if (uiState is AuthUiState.Loading) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Sign Up", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onNavigateToLogin) {
                Text("Already have an account? ", color = TextSec, fontSize = 14.sp)
                Text("Login", color = TextPri, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
