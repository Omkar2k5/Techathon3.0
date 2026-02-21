package com.example.edunet.ui.session

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.edunet.data.network.QrUtils
import com.example.edunet.data.network.SessionBroadcaster
import com.example.edunet.data.network.SessionServer
import com.example.edunet.data.network.SharedFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private val BgDark   = Color(0xFF0F0F1A)
private val CardDark = Color(0xFF1C1C2E)
private val GreenOk  = Color(0xFF4CAF50)
private val TextPri  = Color(0xFFFFFFFF)
private val TextSec  = Color(0xFFB0B0C8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherSessionScreen(
    teacherName: String,
    subjectName: String,
    subjectCode: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Server + broadcaster state
    var server      by remember { mutableStateOf<SessionServer?>(null) }
    var broadcaster by remember { mutableStateOf<SessionBroadcaster?>(null) }
    var isRunning   by remember { mutableStateOf(false) }
    var sessionUrl  by remember { mutableStateOf<String?>(null) }
    var qrBitmap    by remember { mutableStateOf<Bitmap?>(null) }
    var sharedFiles by remember { mutableStateOf<List<SharedFile>>(emptyList()) }
    var clients     by remember { mutableStateOf(0) }
    var errorMsg    by remember { mutableStateOf("") }

    // Refresh client count every 3s when running
    LaunchedEffect(isRunning) {
        while (isActive && isRunning) {
            delay(3000)
            clients = server?.clientCount ?: 0
        }
    }

    // Clean up on exit
    DisposableEffect(Unit) {
        onDispose { server?.stop(); broadcaster?.stop() }
    }

    fun startSession() {
        val url = QrUtils.sessionUrl()
        if (url == null) {
            errorMsg = "No WiFi/hotspot IP found. Enable your hotspot or connect to WiFi first."
            return
        }
        val srv = SessionServer(teacherName = teacherName, subjectName = subjectName, subjectCode = subjectCode)
        srv.start()
        val bc = SessionBroadcaster(context = context, subjectCode = subjectCode, url = url)
        bc.start()
        server      = srv
        broadcaster = bc
        sessionUrl  = url
        qrBitmap    = QrUtils.generateQrBitmap(url)
        isRunning   = true
        errorMsg    = ""
    }

    fun stopSession() {
        server?.stop();      server      = null
        broadcaster?.stop(); broadcaster = null
        isRunning  = false
        sessionUrl = null
        qrBitmap   = null
    }

    // File picker
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null || server == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val cr = context.contentResolver
                val mime = cr.getType(uri) ?: "application/octet-stream"
                val ext  = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
                val raw  = uri.lastPathSegment?.substringAfterLast("/")?.substringAfterLast(":") ?: "file.$ext"
                val name = if (raw.contains('.')) raw else "$raw.$ext"
                val id   = UUID.randomUUID().toString()
                val dest = File(context.cacheDir, "$id.$ext")
                cr.openInputStream(uri)!!.use { ins -> FileOutputStream(dest).use { it.write(ins.readBytes()) } }
                val shared = SharedFile(id = id, name = name, sizeBytes = dest.length(), mimeType = mime, file = dest)
                server!!.addFile(shared)
                sharedFiles = sharedFiles + shared
            } catch (e: Exception) {
                errorMsg = "Failed to add file: ${e.message}"
            }
        }
    }

    fun shareQr() {
        val bmp = qrBitmap ?: return
        val file = File(context.cacheDir, "session_qr.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Join my EduNet class session:\n$sessionUrl\n\nScan the QR code or open the link on your device.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share QR Code via"))
    }

    // ─── UI ──────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().background(BgDark)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF1B5E20), GreenOk)))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { stopSession(); onBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Session Host", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                            Text(subjectName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    if (isRunning) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatusChip("🟢 LIVE", GreenOk)
                            StatusChip("👥 $clients connected", Color(0xFF2196F3))
                            StatusChip("📁 ${sharedFiles.size} files", Color(0xFFFF9800))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (errorMsg.isNotEmpty()) {
                Snack(errorMsg, MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
            }

            if (!isRunning) {
                // Start session card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📡", fontSize = 56.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Start a Session", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPri)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Enable your hotspot or connect to WiFi, then tap Start. Students will join via QR code.",
                            fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = { startSession() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenOk),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Start Session", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        }
                    }
                }
            } else {
                // QR Code card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Share to let students join", fontSize = 14.sp, color = TextSec)
                        Spacer(Modifier.height(12.dp))
                        qrBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Session QR Code",
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(sessionUrl ?: "", fontSize = 12.sp, color = GreenOk, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { shareQr() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = GreenOk, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share QR", color = GreenOk)
                            }
                            OutlinedButton(
                                onClick = { stopSession() },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stop", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Broadcast button
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Broadcast Material", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPri)
                            Text("Share files/notes with all students", fontSize = 12.sp, color = TextSec)
                        }
                        Button(
                            onClick = { filePicker.launch("*/*") },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenOk)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add File")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Shared files list
                if (sharedFiles.isNotEmpty()) {
                    Text(
                        "  Shared (${sharedFiles.size})",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSec,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    sharedFiles.reversed().forEach { f ->
                        SharedFileRow(f)
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.2f)) {
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun Snack(msg: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(msg, color = color, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun SharedFileRow(f: SharedFile) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(GreenOk.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(fileEmoji(f.mimeType), fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPri, maxLines = 1)
                Text(formatSize(f.sizeBytes), fontSize = 11.sp, color = TextSec)
            }
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenOk, modifier = Modifier.size(20.dp))
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
