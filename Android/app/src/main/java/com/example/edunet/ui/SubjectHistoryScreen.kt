package com.example.edunet.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.edunet.data.local.SavedFile
import com.example.edunet.data.local.SessionHistoryStore
import com.example.edunet.data.local.SessionRecord
import com.example.edunet.data.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val BgPage  = Color(0xFF000000)
private val CardBg  = Color(0xFF111111)
private val CardAlt = Color(0xFF1A1A1A)
private val TextPri = Color(0xFFFFFFFF)
private val TextSec = Color(0xFF888888)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectHistoryScreen(
    subjectCode: String,
    subjectName: String,
    onBack: () -> Unit,
    onRecover: () -> Unit
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val store      = remember { SessionHistoryStore(context) }
    val parseFmt   = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var records by remember { mutableStateOf(store.getRecords(subjectCode)) }
    var filter  by remember { mutableStateOf("All") }

    // ── Auto-refresh history every 2 s (catches files saved after recovery sync) ─
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2000)
            records = store.getRecords(subjectCode)
        }
    }

    // ── Background peer-request listener (offerer mode) ───────────────────────
    // Runs silently; shows a dialog when a classmate requests data for this subject.
    var incomingRequest by remember { mutableStateOf<PeerRequest?>(null) }
    var offerStatus     by remember { mutableStateOf("") }
    var peerServer      by remember { mutableStateOf<PeerSyncServer?>(null) }
    var offerBc         by remember { mutableStateOf<PeerOfferBroadcaster?>(null) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val req = withContext(Dispatchers.IO) {
                listenForRequest(setOf(subjectCode), timeoutMs = 10_000)
            }
            if (req != null && incomingRequest == null && peerServer == null) {
                incomingRequest = req
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { peerServer?.stop(); offerBc?.stop() }
    }

    // Filtered list
    val filtered = remember(filter, records) {
        val now = Calendar.getInstance()
        records.filter { r ->
            when (filter) {
                "This Week"  -> {
                    val d = try { parseFmt.parse(r.dateKey)!! } catch (_: Exception) { return@filter true }
                    (now.timeInMillis - d.time) / 86_400_000 <= 7
                }
                "This Month" -> r.dateKey.startsWith(
                    "%04d-%02d".format(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
                )
                else -> true
            }
        }
    }

    // ── Incoming share dialog ─────────────────────────────────────────────────
    incomingRequest?.let { req ->
        AlertDialog(
            onDismissRequest = { incomingRequest = null },
            containerColor   = CardBg,
            title  = { Text("📨 Classmate Needs Notes", color = TextPri, fontWeight = FontWeight.Bold) },
            text   = {
                val dates = req.missingDates.joinToString(", ") { d ->
                    try { displayFmt.format(parseFmt.parse(d)!!) } catch (_: Exception) { d }
                }
                Text("A classmate is requesting data for:\n$dates", color = TextSec, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val recs = store.getRecords(req.subjectCode)
                                .filter { req.missingDates.isEmpty() || it.dateKey in req.missingDates }
                                .associate { r -> r.dateKey to r.files }
                            val srv = PeerSyncServer(req.subjectCode, recs)
                            srv.start()
                            peerServer = srv
                            val ip = runCatching {
                                java.net.NetworkInterface.getNetworkInterfaces()
                                    .asSequence().flatMap { it.inetAddresses.asSequence() }
                                    .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                                    ?.hostAddress
                            }.getOrNull() ?: "192.168.43.1"
                            val bc = PeerOfferBroadcaster(req.subjectCode, recs.keys.toList(), "http://$ip:$PEER_HTTP_PORT")
                            bc.start(); offerBc = bc
                            withContext(Dispatchers.Main) {
                                offerStatus = "Sharing… peer is downloading your notes."
                                incomingRequest = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A))
                ) { Text("📤 Share Notes", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { incomingRequest = null }) { Text("Decline", color = TextSec) }
            }
        )
    }

    Scaffold(
        containerColor = BgPage,
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Share Notes FAB (visible when no peer server running)
                if (peerServer == null) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val recs = store.getRecords(subjectCode)
                                    .associate { r -> r.dateKey to r.files }
                                val srv = PeerSyncServer(subjectCode, recs)
                                srv.start(); peerServer = srv
                                val ip = runCatching {
                                    java.net.NetworkInterface.getNetworkInterfaces()
                                        .asSequence().flatMap { it.inetAddresses.asSequence() }
                                        .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                                        ?.hostAddress
                                }.getOrNull() ?: "192.168.43.1"
                                val bc = PeerOfferBroadcaster(subjectCode, recs.keys.toList(), "http://$ip:$PEER_HTTP_PORT")
                                bc.start(); offerBc = bc
                                withContext(Dispatchers.Main) { offerStatus = "Ready to share — waiting for classmate to request…" }
                            }
                        },
                        containerColor = Color(0xFF1A1A1A),
                        contentColor   = Color.White
                    ) { Icon(Icons.Default.Share, contentDescription = "Share Notes") }
                }
                // Recover Notes FAB
                ExtendedFloatingActionButton(
                    onClick         = onRecover,
                    icon            = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    text            = { Text("Recover Notes") },
                    containerColor  = Color(0xFF1A1A1A),
                    contentColor    = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Header ─────────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF111111))
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { peerServer?.stop(); offerBc?.stop(); onBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPri)
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Session History", fontSize = 12.sp, color = TextSec)
                            Text(subjectName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPri)
                        }
                        if (peerServer != null) {
                            Surface(shape = RoundedCornerShape(50), color = Color(0xFF2A2A2A)) {
                                Text("📤 Sharing", color = Color.White, fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                    if (offerStatus.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(offerStatus, fontSize = 12.sp, color = TextSec)
                    }
                    Spacer(Modifier.height(10.dp))
                    // Filter chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("All", "This Week", "This Month")) { label ->
                            FilterChip(
                                selected = filter == label,
                                onClick  = { filter = label },
                                label    = { Text(label, fontSize = 12.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF2A2A2A),
                                    selectedLabelColor     = TextPri,
                                    containerColor         = Color.Transparent,
                                    labelColor             = TextSec
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true, selected = filter == label,
                                    borderColor = Color(0xFF2A2A2A), selectedBorderColor = Color(0xFF444444)
                                )
                            )
                        }
                    }
                }
            }

            // ── Content ────────────────────────────────────────────────────────
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text("📂", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("No sessions yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPri)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (filter == "All") "Join a live session to start building your history,\nor tap 'Recover Notes' to get files from a classmate."
                            else "No sessions in this time range.",
                            fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.dateKey }) { record ->
                        val label = try { displayFmt.format(parseFmt.parse(record.dateKey)!!) }
                                    catch (_: Exception) { record.dateKey }
                        SessionDayCard(record, label, context)
                    }
                }
            }
        }
    }
}

// ── Day card ──────────────────────────────────────────────────────────────────

@Composable
private fun SessionDayCard(record: SessionRecord, dateLabel: String, context: android.content.Context) {
    var expanded by remember { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2A2A2A)),
                        contentAlignment = Alignment.Center
                    ) { Text("📅", fontSize = 18.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(dateLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPri)
                        Text("${record.files.size} file${if (record.files.size != 1) "s" else ""}",
                            fontSize = 12.sp, color = TextSec)
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null, tint = TextSec
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(bottom = 8.dp))
                    record.files.forEach { file ->
                        HistoryFileRow(file, context)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

// ── File row ──────────────────────────────────────────────────────────────────

@Composable
private fun HistoryFileRow(file: SavedFile, context: android.content.Context) {
    val exists = File(file.localPath).exists()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardAlt)
            .clickable(enabled = exists) {
                val f   = File(file.localPath)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, file.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(fileEmoji(file.mimeType), fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPri, maxLines = 1)
            Text(formatSize(file.sizeBytes), fontSize = 11.sp, color = TextSec)
        }
        Icon(
            if (exists) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint   = if (exists) Color(0xFF4CAF50) else Color(0xFFFF9800),
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun fileEmoji(mime: String) = when {
    mime.startsWith("image") -> "🖼️"
    mime.contains("pdf")     -> "📄"
    mime.startsWith("video") -> "🎥"
    mime.startsWith("audio") -> "🎵"
    else -> "📎"
}
private fun formatSize(b: Long) = when {
    b < 1024        -> "$b B"
    b < 1048576     -> "${b / 1024} KB"
    else            -> "${"%.1f".format(b / 1048576.0)} MB"
}
