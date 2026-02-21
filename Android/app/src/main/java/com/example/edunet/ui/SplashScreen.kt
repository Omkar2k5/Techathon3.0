package com.example.edunet.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edunet.data.SessionManager
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: (isLoggedIn: Boolean) -> Unit) {
    val context = LocalContext.current
    val session = remember { SessionManager(context) }

    var startAnimation by remember { mutableStateOf(false) }

    val rotationY by animateFloatAsState(
        targetValue = if (startAnimation) 360f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "3D Rotation"
    )

    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, delayMillis = 800),
        label = "Alpha Fade"
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(2500)
        onSplashFinished(session.isLoggedIn())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "EDUNET",
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 4.sp,
                modifier = Modifier.graphicsLayer {
                    this.rotationY = rotationY
                    this.cameraDistance = 8 * density
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Campus Offline Knowledge\nSharing System",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { this.alpha = alpha }
            )
        }
    }
}
