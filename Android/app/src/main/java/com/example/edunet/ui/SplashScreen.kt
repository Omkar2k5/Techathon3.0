package com.example.edunet.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }

    // 3D Rotation Animation
    val rotationY by animateFloatAsState(
        targetValue = if (startAnimation) 360f else 0f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing
        ),
        label = "3D Rotation"
    )

    // Fade-in for Subtitle
    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1000,
            delayMillis = 800
        ),
        label = "Alpha Fade"
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(2500) // Wait for animation + a little pause
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2C)), // Deep dark blue/purple modern background
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 3D Animated Logo Text
            Text(
                text = "EDUNET",
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 4.sp,
                modifier = Modifier.graphicsLayer {
                    this.rotationY = rotationY
                    // Add slight perspective/depth
                    this.cameraDistance = 8 * density
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Subtitle fading in
            Text(
                text = "Campus Offline Knowledge\nSharing System",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    this.alpha = alpha
                }
            )
        }
    }
}
