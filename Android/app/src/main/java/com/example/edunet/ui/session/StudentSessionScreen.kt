package com.example.edunet.ui.session

import android.content.Intent
import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.edunet.data.network.RemoteFile
import com.example.edunet.data.network.SessionClient
import com.example.edunet.data.network.SessionInfo
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSessionScreen(
    initialUrl: String = "",   // pass from QR scan
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var baseUrl      by remember { mutableStateOf(initialUrl) }
    var urlInput     by remember { mutableStateOf(initialUrl) }
    var sessionInfo  by remember { mutableStateOf<SessionInfo?>(null) }
    var connected    by remember { mutableStateOf(false) }
    var errorMsg     by remember { mutableStateOf("") }
    var files        by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var lastPoll     by remember { mutableStateOf(0L) }
    var downloading  by remember { mutableStateOf<String?>(null) }  // file id being downloaded

    // Auto-poll for new files every 3 seconds while connected
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        while (isActive) {
            try {
                val newFiles = SessionClient.getFiles(baseUrl, lastPoll)
                if (newFiles.isNotEmpty()) {
                    files = files + newFiles
                    lastPoll = newFiles.maxOf { it.addedAt }
                }
            } catch (_: Exception) {}
            delay(3000)
        }
    }

    fun connect(url: String) {
        val trimmed = url.trim().trimEnd('/')
        scope.launch {
            try {
                errorMsg = ""
                val info = SessionClient.getSessionInfo(trimmed)
                baseUrl     = trimmed
                sessionInfo = info
                connected   = true
                // Load existing files immediately
                files     = SessionClient.getFiles(trimmed)
                lastPoll  = if (files.isNotEmpty()) files.maxOf { it.addedAt } else 0L
            } catch (e: Exception) {
                errorMsg = "Cannot connect: ${e.message}"
            }
        }
    }

    // Auto-connect if url provided
    LaunchedEffect(Unit) {
        if (initialUrl.isNotBlank()) connect(initialUrl)
    }

    fun downloadAndOpen(file: RemoteFile) {
        scope.launch {
            downloading = file.id
            try {
                val bytes = SessionClient.downloadFileBytes(baseUrl, file.id)
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "EduNet"
                ).also { it.mkdirs() }
                val dest = File(dir, file.name)
                FileOutputStream(dest).use { it.write(bytes) }
                // Open the file
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", dest)
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
                            Text("Class Session", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                            Text(
                                if (connected) sessionInfo?.subjectName ?: "Connected" else "Join Session",
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White
                            )
                        }
                    }
                    if (connected) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                                Text("🟢 Connected", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                            sessionInfo?.let { info ->
                                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                                    Text("👩‍🏫 ${info.teacherName}", fontSize = 12.sp, color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (!connected) {
                // ── Connect UI ────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📡", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Join a Session", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPri)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan the teacher's QR code, or enter the session URL manually",
                        fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(32.dp))

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Session URL", color = TextSec) },
                        placeholder = { Text("http://192.168.x.x:8080", color = TextSec.copy(alpha = 0.4f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { connect(urlInput) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Accent,
                            unfocusedBorderColor = TextSec.copy(alpha = 0.3f),
                            focusedTextColor     = TextPri,
                            unfocusedTextColor   = TextPri,
                            cursorColor          = Accent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (errorMsg.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { connect(urlInput) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Connect", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }
                }
            } else {
                // ── File Feed ─────────────────────────────────────────────────
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
                    Spacer(Modifier.height(8.dp))
                    Text("Auto-updating every 3 seconds • Tap a file to download and open",
                        fontSize = 12.sp, color = TextSec)
                    Spacer(Modifier.height(16.dp))

                    if (files.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⏳", fontSize = 48.sp)
                                Spacer(Modifier.height(12.dp))
                                Text("Waiting for files...", color = TextSec, fontSize = 16.sp)
                                Text("The teacher hasn't shared anything yet", color = TextSec.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(files.reversed()) { f ->
                                ReceivedFileCard(
                                    file        = f,
                                    isDownloading = downloading == f.id,
                                    onClick     = { downloadAndOpen(f) }
                                )
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

@Composable
private fun ReceivedFileCard(file: RemoteFile, isDownloading: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent.copy(alpha = 0.15f)),
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
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Accent
                )
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
