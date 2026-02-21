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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignUpSuccess: (role: String) -> Unit,
    onNavigateToLogin: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    var isStaffSelected by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var emailOrId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val uiState by authViewModel.uiState.collectAsState()

    // Navigate on success
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onSignUpSuccess((uiState as AuthUiState.Success).role)
            authViewModel.resetState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F6FA)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Join EDUNET",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E1E2C)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Create your account to start sharing",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Role Selector
            TabRow(
                selectedTabIndex = if (isStaffSelected) 1 else 0,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF1E1E2C),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Tab(
                    selected = !isStaffSelected,
                    onClick = { isStaffSelected = false },
                    text = { Text("Student", fontWeight = if (!isStaffSelected) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = isStaffSelected,
                    onClick = { isStaffSelected = true },
                    text = { Text("Staff", fontWeight = if (isStaffSelected) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1E1E2C),
                    unfocusedTextColor = Color(0xFF1E1E2C),
                    focusedIndicatorColor = Color(0xFF1E1E2C),
                    unfocusedIndicatorColor = Color.LightGray,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = Color(0xFF1E1E2C)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = emailOrId,
                onValueChange = { emailOrId = it },
                label = { Text(if (isStaffSelected) "Staff Email" else "Student Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1E1E2C),
                    unfocusedTextColor = Color(0xFF1E1E2C),
                    focusedIndicatorColor = Color(0xFF1E1E2C),
                    unfocusedIndicatorColor = Color.LightGray,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = Color(0xFF1E1E2C)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (min. 6 characters)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1E1E2C),
                    unfocusedTextColor = Color(0xFF1E1E2C),
                    focusedIndicatorColor = Color(0xFF1E1E2C),
                    unfocusedIndicatorColor = Color.LightGray,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = Color(0xFF1E1E2C)
                )
            )

            // Error message
            if (uiState is AuthUiState.Error) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = (uiState as AuthUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    authViewModel.signUp(
                        name, emailOrId, password,
                        role = if (isStaffSelected) "teacher" else "student"
                    )
                },
                enabled = uiState !is AuthUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2C))
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(text = "Sign Up", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { onNavigateToLogin() }) {
                Text("Already have an account? Login", color = Color(0xFF1E1E2C))
            }
        }
    }
}
