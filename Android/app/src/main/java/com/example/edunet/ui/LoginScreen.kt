package com.example.edunet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

// ─── B&W palette (local) ─────────────────────────────
private val BgPage   = Color(0xFF000000)
private val BgCard   = Color(0xFF111111)
private val Border   = Color(0xFF333333)
private val TextPri  = Color(0xFFFFFFFF)
private val TextSec  = Color(0xFF999999)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (role: String) -> Unit,
    onNavigateToSignUp: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var isStaffSelected by remember { mutableStateOf(false) }
    var emailOrId by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    val uiState   by authViewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onLoginSuccess((uiState as AuthUiState.Success).role)
            authViewModel.resetState()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(BgPage),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo mark
            Text("EDUNET", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold,
                color = TextPri, letterSpacing = 3.sp)
            Spacer(Modifier.height(6.dp))
            Text("Offline Knowledge Sharing", fontSize = 13.sp, color = TextSec,
                letterSpacing = 1.sp)

            Spacer(Modifier.height(40.dp))

            // Card container
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BgCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {

                    // Role tabs
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("Student" to false, "Staff" to true).forEach { (label, isStaff) ->
                            val selected = isStaffSelected == isStaff
                            Surface(
                                onClick = { isStaffSelected = isStaff },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) TextPri else Color.Transparent,
                                contentColor = if (selected) BgPage else TextSec
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Email field
                    OutlinedTextField(
                        value = emailOrId,
                        onValueChange = { emailOrId = it },
                        label = { Text(if (isStaffSelected) "Staff Email" else "Student Email",
                            color = TextSec, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor     = TextPri,
                            unfocusedTextColor   = TextPri,
                            focusedBorderColor   = TextPri,
                            unfocusedBorderColor = Border,
                            cursorColor          = TextPri,
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        )
                    )

                    Spacer(Modifier.height(14.dp))

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", color = TextSec, fontSize = 13.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor     = TextPri,
                            unfocusedTextColor   = TextPri,
                            focusedBorderColor   = TextPri,
                            unfocusedBorderColor = Border,
                            cursorColor          = TextPri,
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        )
                    )

                    if (uiState is AuthUiState.Error) {
                        Spacer(Modifier.height(10.dp))
                        Text((uiState as AuthUiState.Error).message,
                            color = Color(0xFFFF4444), fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(24.dp))

                    // Login button
                    Button(
                        onClick = { authViewModel.login(emailOrId, password) },
                        enabled = uiState !is AuthUiState.Loading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TextPri)
                    ) {
                        if (uiState is AuthUiState.Loading) {
                            CircularProgressIndicator(color = BgPage, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Login", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BgPage)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onNavigateToSignUp) {
                Text("Don't have an account? ", color = TextSec, fontSize = 14.sp)
                Text("Sign Up", color = TextPri, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}
