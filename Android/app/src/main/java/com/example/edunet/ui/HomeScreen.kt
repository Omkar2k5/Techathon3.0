package com.example.edunet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edunet.data.SessionManager

@Composable
fun HomeScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val session = remember { SessionManager(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2C)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "👋 Welcome back,",
                fontSize = 18.sp,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = session.getUserName(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = if (session.getUserRole() == "teacher") Color(0xFF4CAF50) else Color(0xFF2196F3)
            ) {
                Text(
                    text = if (session.getUserRole() == "teacher") "🎓 Staff" else "📚 Student",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "EDUNET",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Campus Offline Knowledge Sharing",
                fontSize = 13.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(60.dp))

            OutlinedButton(
                onClick = {
                    session.clearSession()
                    onLogout()
                },
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Logout", color = Color.LightGray)
            }
        }
    }
}
