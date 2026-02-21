package com.example.edunet.ui.session

import android.content.Intent
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.example.edunet.data.network.discoverSession
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private val BgDark   = Color(0xFF000000)
private val CardDark = Color(0xFF111111)
private val Accent   = Color(0xFFFFFFFF)
private val TextPri  = Color(0xFFFFFFFF)
private val TextSec  = Color(0xFF888888)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSessionScreen(
    subjectCode: String,
    subjectName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Discovery states
    var searching   by remember { mutableStateOf(true) }
    var showFallback by remember { mutableStateOf(false) }   // show after UDP fails
    var connected   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf("") }

    // Session data
    var baseUrl     by remember { mutableStateOf("") }
    var urlInput    by remember { mutableStateOf("") }
    var sessionInfo by remember { mutableStateOf<SessionInfo?>(null) }
    var files       by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var lastPoll    by remember { mutableStateOf(0L) }
    var downloading by remember { mutableStateOf<String?>(null) }

    // ── QR scanner launcher ───────────────────────────────────────────────────
    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents ?: return@rememberLauncherForActivityResult
        // QR contains the session URL directly
        scope.launch { connectTo(scanned, onSuccess = { info, url, initial ->
            sessionInfo = info; baseUrl = url; files = initial
            lastPoll = if (initial.isNotEmpty()) initial.maxOf { it.addedAt } else 0L
            connected = true; errorMsg = ""; showFallback = false
        }, onError = { errorMsg = it })
        }
    }

    // ── Automatic UDP discovery on open ───────────────────────────────────────
    LaunchedEffect(Unit) {
        searching = true; errorMsg = ""
        val url = discoverSession(subjectCode, timeoutMs = 12_000)
        if (url != null) {
            connectTo(url,
                onSuccess = { info, u, initial ->
                    sessionInfo = info; baseUrl = u; files = initial
                    lastPoll = if (initial.isNotEmpty()) initial.maxOf { it.addedAt } else 0L
                    connected = true
                },
                onError = { errorMsg = it; showFallback = true }
            )
        } else {
            errorMsg = "No active session detected automatically."
            showFallback = true
        }
        searching = false
    }

    // ── Poll for new files every 3 seconds when connected ─────────────────────
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

    // ── File download ─────────────────────────────────────────────────────────
    fun downloadAndOpen(file: RemoteFile) {
        scope.launch {
            downloading = file.id
            try {
                val bytes = SessionClient.downloadFileBytes(baseUrl, file.id)
                val dir   = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "EduNet"
                ).also { it.mkdirs() }
                val dest  = File(dir, file.name)
                FileOutputStream(dest).use { it.write(bytes) }
                val uri   = FileProvider.getUriForFile(context, "${context.packageName}.provider", dest)
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, file.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (e: Exception) { errorMsg = "Download failed: ${e.message}" }
            downloading = null
        }
    }

    // ── Manual connect trigger ────────────────────────────────────────────────
    fun tryManualConnect() {
        val raw = urlInput.trim().trimEnd('/')
        if (raw.isBlank()) return
        scope.launch {
            searching = true; errorMsg = ""
            connectTo(raw,
                onSuccess = { info, url, initial ->
                    sessionInfo = info; baseUrl = url; files = initial
                    lastPoll = if (initial.isNotEmpty()) initial.maxOf { it.addedAt } else 0L
                    connected = true; showFallback = false
                },
                onError = { errorMsg = it }
            )
            searching = false
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(BgDark)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF111111), Color(0xFF1A1A1A))))
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
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SessionChip("🟢 Connected")
                            sessionInfo?.let { SessionChip("👩‍🏫 ${it.teacherName}") }
                        }
                    }
                }
            }

            when {
                // ── Searching spinner ─────────────────────────────────────────
                searching -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            CircularProgressIndicator(color = Accent, modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
                            Spacer(Modifier.height(24.dp))
                            Text("Looking for session…", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPri)
                            Spacer(Modifier.height(8.dp))
                            Text("Scanning the network for $subjectCode", fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center)
                        }
                    }
                }

                // ── File Feed (connected) ─────────────────────────────────────
                connected -> {
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
                        Text("Auto-updates every 3s • Tap a file to download & open", fontSize = 12.sp, color = TextSec)
                        Spacer(Modifier.height(16.dp))
                        if (files.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

                // ── Fallback: QR / Manual IP ──────────────────────────────────
                showFallback -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text("📡", fontSize = 56.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Auto-Discovery Failed", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPri)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Couldn't detect the teacher's session automatically. Join manually using a QR code or the session URL.",
                            fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center
                        )

                        if (errorMsg.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }

                        Spacer(Modifier.height(28.dp))

                        // ── QR Scan button ────────────────────────────────────
                        Button(
                            onClick = {
                                qrLauncher.launch(ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt("Scan the teacher's session QR code")
                                    setBeepEnabled(false)
                                    setOrientationLocked(false)
                                })
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF))
                        ) {
                            Text("📷  Scan QR Code", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        }

                        Spacer(Modifier.height(20.dp))

                        // ── Divider ───────────────────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Divider(modifier = Modifier.weight(1f), color = TextSec.copy(alpha = 0.3f))
                            Text("  or enter IP manually  ", color = TextSec, fontSize = 13.sp)
                            Divider(modifier = Modifier.weight(1f), color = TextSec.copy(alpha = 0.3f))
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Manual URL field ──────────────────────────────────
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("Session URL / IP", color = TextSec) },
                            placeholder = { Text("http://192.168.x.x:8080", color = TextSec.copy(alpha = 0.4f)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions  = KeyboardActions(onGo = { tryManualConnect() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Accent,
                                unfocusedBorderColor = TextSec.copy(alpha = 0.3f),
                                focusedTextColor     = TextPri,
                                unfocusedTextColor   = TextPri,
                                cursorColor          = Accent
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { tryManualConnect() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            enabled = urlInput.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Connect", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Retry auto-discovery ──────────────────────────────
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    searching = true; errorMsg = ""; showFallback = false
                                    val url = discoverSession(subjectCode, 12_000)
                                    if (url != null) {
                                        connectTo(url,
                                            onSuccess = { info, u, initial ->
                                                sessionInfo = info; baseUrl = u; files = initial
                                                lastPoll = if (initial.isNotEmpty()) initial.maxOf { it.addedAt } else 0L
                                                connected = true
                                            },
                                            onError = { errorMsg = it; showFallback = true }
                                        )
                                    } else {
                                        errorMsg = "No active session detected."
                                        showFallback = true
                                    }
                                    searching = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Accent)
                            Spacer(Modifier.width(8.dp))
                            Text("Retry Auto-Discovery", color = Accent)
                        }
                    }
                }
            }
        }
    }
}

// ─── Shared connection helper ─────────────────────────────────────────────────

private suspend fun connectTo(
    url: String,
    onSuccess: (SessionInfo, String, List<RemoteFile>) -> Unit,
    onError: (String) -> Unit
) {
    try {
        val info    = SessionClient.getSessionInfo(url)
        val initial = SessionClient.getFiles(url)
        onSuccess(info, url, initial)
    } catch (e: Exception) {
        onError("Connection failed: ${e.message}")
    }
}

// ─── Small composables ────────────────────────────────────────────────────────

@Composable
private fun SessionChip(label: String) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
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
            ) { Text(fileEmoji(file.mimeType), fontSize = 22.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPri, maxLines = 2)
                Spacer(Modifier.height(3.dp))
                Text(formatSize(file.sizeBytes), fontSize = 11.sp, color = TextSec)
            }
            Spacer(Modifier.width(8.dp))
            if (isDownloading)
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Accent)
            else
                Text("⬇", color = Accent, fontSize = 20.sp)
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
