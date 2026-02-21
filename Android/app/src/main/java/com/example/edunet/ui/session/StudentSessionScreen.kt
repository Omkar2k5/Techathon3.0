package com.example.edunet.ui.session

import android.content.Intent
import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.edunet.data.network.RemoteFile
import com.example.edunet.data.network.SessionClient
import com.example.edunet.data.network.SessionInfo
import com.example.edunet.data.network.discoverSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private val BgDark   = Color(0xFF0F0F1A)
private val CardDark = Color(0xFF1C1C2E)
private val Accent   = Color(0xFF6C63FF)
private val TextPri  = Color(0xFFFFFFFF)
private val TextSec  = Color(0xFFB0B0C8)

/**
 * Student session screen — auto-discovers the teacher's server for [subjectCode]
 * via UDP broadcast. No QR scanning, no manual URL entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSessionScreen(
    subjectCode: String,
    subjectName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // States
    var searching    by remember { mutableStateOf(true) }
    var connected    by remember { mutableStateOf(false) }
    var errorMsg     by remember { mutableStateOf("") }
    var baseUrl      by remember { mutableStateOf("") }
    var sessionInfo  by remember { mutableStateOf<SessionInfo?>(null) }
    var files        by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var lastPoll     by remember { mutableStateOf(0L) }
    var downloading  by remember { mutableStateOf<String?>(null) }

    // Step 1: Discover teacher's server via UDP broadcast
    LaunchedEffect(Unit) {
        searching = true
        errorMsg  = ""
        val url = discoverSession(subjectCode, timeoutMs = 15_000)
        if (url == null) {
            searching = false
            errorMsg  = "No active session found for $subjectCode. Make sure you are on the same WiFi/hotspot as your teacher."
            return@LaunchedEffect
        }
        // Step 2: Connect and load existing files
        try {
            val info = SessionClient.getSessionInfo(url)
            baseUrl     = url
            sessionInfo = info
            files       = SessionClient.getFiles(url)
            lastPoll    = if (files.isNotEmpty()) files.maxOf { it.addedAt } else 0L
            connected   = true
        } catch (e: Exception) {
            errorMsg = "Found session but could not connect: ${e.message}"
        }
        searching = false
    }

    // Step 3: Poll for new files every 3 seconds while connected
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        while (isActive) {
            delay(3000)
            try {
                val newFiles = SessionClient.getFiles(baseUrl, lastPoll)
                if (newFiles.isNotEmpty()) {
                    files    = files + newFiles
                    lastPoll = newFiles.maxOf { it.addedAt }
                }
            } catch (_: Exception) {}
        }
    }

    fun downloadAndOpen(file: RemoteFile) {
        scope.launch {
            downloading = file.id
            try {
                val bytes = SessionClient.downloadFileBytes(baseUrl, file.id)
                val dir   = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "EduNet"
                ).also { it.mkdirs() }
                val dest  = File(dir, file.name)
                FileOutputStream(dest).use { it.write(bytes) }
                val uri   = FileProvider.getUriForFile(context, "${context.packageName}.provider", dest)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, file.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                errorMsg = "Download failed: ${e.message}"
            }
            downloading = null
        }
    }

    // ─── UI ──────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(BgDark)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Accent, Color(0xFF00D4FF))))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Live Session", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                            Text(subjectName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    if (connected) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("🟢 Connected", Color(0xFF4CAF50))
                            sessionInfo?.let { Chip("👩‍🏫 ${it.teacherName}", Color.White.copy(alpha = 0.6f)) }
                        }
                    }
                }
            }

            when {
                // ── Searching ────────────────────────────────────────────────
                searching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Accent, modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
                            Spacer(Modifier.height(24.dp))
                            Text("Looking for session…", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPri)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Searching for your teacher's $subjectCode session on the local network.",
                                fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Make sure you are on the same WiFi or hotspot as your teacher.",
                                fontSize = 12.sp, color = TextSec.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp))
                        }
                    }
                }

                // ── Error ────────────────────────────────────────────────────
                errorMsg.isNotEmpty() && !connected -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text("📡", fontSize = 56.sp)
                            Spacer(Modifier.height(16.dp))
                            Text("Session Not Found", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPri)
                            Spacer(Modifier.height(8.dp))
                            Text(errorMsg, fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        searching = true; errorMsg = ""
                                        val url = discoverSession(subjectCode, 15_000)
                                        if (url != null) {
                                            try {
                                                sessionInfo = SessionClient.getSessionInfo(url)
                                                baseUrl = url
                                                files   = SessionClient.getFiles(url)
                                                lastPoll = if (files.isNotEmpty()) files.maxOf { it.addedAt } else 0L
                                                connected = true
                                            } catch (e: Exception) { errorMsg = e.message ?: "Connection failed" }
                                        } else { errorMsg = "No active session found. Try again." }
                                        searching = false
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Try Again")
                            }
                        }
                    }
                }

                // ── File Feed ─────────────────────────────────────────────────
                else -> {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Received Files", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPri)
                            Surface(shape = RoundedCornerShape(50), color = Accent) {
                                Text("${files.size}", color = Color.White, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Auto-updates every 3s • Tap a file to download & open",
                            fontSize = 12.sp, color = TextSec)
                        Spacer(Modifier.height(16.dp))

                        if (files.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("⏳", fontSize = 48.sp)
                                    Spacer(Modifier.height(12.dp))
                                    Text("Waiting for files…", color = TextSec, fontSize = 16.sp)
                                    Text("Teacher hasn't shared anything yet.", color = TextSec.copy(alpha = 0.6f), fontSize = 13.sp)
                                }
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(files.reversed()) { f ->
                                    ReceivedFileCard(f, isDownloading = downloading == f.id, onClick = { downloadAndOpen(f) })
                                }
                            }
                        }
                        if (errorMsg.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, bg: Color) {
    Surface(shape = RoundedCornerShape(50), color = bg.copy(alpha = 0.2f)) {
        Text(label, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun ReceivedFileCard(file: RemoteFile, isDownloading: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(fileEmoji(file.mimeType), fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPri, maxLines = 2)
                Spacer(Modifier.height(3.dp))
                Text(formatSize(file.sizeBytes), fontSize = 11.sp, color = TextSec)
            }
            Spacer(Modifier.width(8.dp))
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Accent)
            } else {
                Text("⬇", color = Accent, fontSize = 20.sp)
            }
        }
    }
}

private fun fileEmoji(mime: String) = when {
    mime.startsWith("image") -> "🖼️"
    mime.contains("pdf")     -> "📄"
    mime.startsWith("video") -> "🎥"
    mime.startsWith("audio") -> "🎵"
    else                     -> "📎"
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024        -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else                -> "${"%.1f".format(bytes / 1024.0 / 1024.0)} MB"
}
