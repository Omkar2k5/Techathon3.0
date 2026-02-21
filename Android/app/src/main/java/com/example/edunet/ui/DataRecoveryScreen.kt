package com.example.edunet.ui

import android.os.Environment
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edunet.data.local.SessionHistoryStore
import com.example.edunet.data.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val BgPage  = Color(0xFF000000)
private val CardBg  = Color(0xFF111111)
private val TextPri = Color(0xFFFFFFFF)
private val TextSec = Color(0xFF888888)

sealed class RecoveryState {
    object Idle      : RecoveryState()
    object Searching : RecoveryState()
    object Found     : RecoveryState()
    object Syncing   : RecoveryState()
    data class Done(val count: Int)    : RecoveryState()
    data class Error(val msg: String)  : RecoveryState()
}

// Extension to get date list without duplication
fun SessionHistoryStore.getDates(subjectCode: String): List<String> =
    getRecords(subjectCode).map { it.dateKey }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataRecoveryScreen(
    subjectCode: String,
    subjectName: String,
    onBack: () -> Unit
) {
    val context    = LocalContext.current
    val scope      = rememberCoroutineScope()
    val store      = remember { SessionHistoryStore(context) }
    val displayFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val parseFmt   = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    var state          by remember { mutableStateOf<RecoveryState>(RecoveryState.Idle) }
    var peerOffer      by remember { mutableStateOf<PeerOffer?>(null) }
    var statusMsg      by remember { mutableStateOf("") }
    var progressMax    by remember { mutableStateOf(0) }
    var progressCur    by remember { mutableStateOf(0) }
    var broadcaster    by remember { mutableStateOf<PeerRequestBroadcaster?>(null) }

    DisposableEffect(Unit) {
        onDispose { broadcaster?.stop() }
    }

    // Auto-navigate back when done (after short delay so user sees success)
    LaunchedEffect(state) {
        if (state is RecoveryState.Done) {
            kotlinx.coroutines.delay(2000)
            onBack()
        }
    }

    Scaffold(containerColor = BgPage) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // ── Header ─────────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF111111))
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { broadcaster?.stop(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPri)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text("Recover Notes", fontSize = 12.sp, color = TextSec)
                        Text(subjectName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPri)
                    }
                }
            }

            // ── Body ─────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                when (val s = state) {

                    // ── IDLE ──────────────────────────────────────────────────
                    RecoveryState.Idle -> {
                        Text("📥", fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Recover Missing Notes", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPri)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Find a classmate who attended the session and sync their notes to your device.\n\n" +
                            "Both devices must be on the same WiFi or hotspot. Ask your classmate to open this subject's History screen — they will see a \"Share\" prompt automatically.",
                            fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center, lineHeight = 22.sp
                        )
                        Spacer(Modifier.height(32.dp))

                        // Show my existing dates
                        val myDates = store.getDates(subjectCode)
                        if (myDates.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = CardBg)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Notes I already have:", fontWeight = FontWeight.SemiBold, color = TextPri, fontSize = 14.sp)
                                    Spacer(Modifier.height(8.dp))
                                    myDates.forEach { d ->
                                        val label = try { displayFmt.format(parseFmt.parse(d)!!) } catch (_: Exception) { d }
                                        Text("  ✅ $label", color = TextSec, fontSize = 13.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    state = RecoveryState.Searching
                                    statusMsg = "Broadcasting request on local network…"
                                    val myExisting = store.getDates(subjectCode)
                                    val bc = PeerRequestBroadcaster(subjectCode, myExisting)
                                    bc.start(); broadcaster = bc
                                    statusMsg = "Waiting for a classmate to respond (up to 30 s)…"
                                    val offer = withContext(Dispatchers.IO) { listenForOffer(subjectCode, 30_000) }
                                    bc.stop(); broadcaster = null
                                    if (offer == null) {
                                        state = RecoveryState.Error(
                                            "No classmate found within 30 seconds.\n\nMake sure your classmate has opened the History screen for this subject."
                                        )
                                    } else {
                                        peerOffer = offer
                                        state = RecoveryState.Found
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(10.dp))
                            Text("Find a Classmate", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    // ── SEARCHING ─────────────────────────────────────────────
                    RecoveryState.Searching -> {
                        Spacer(Modifier.height(40.dp))
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(64.dp), strokeWidth = 4.dp)
                        Spacer(Modifier.height(24.dp))
                        Text("Searching…", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPri)
                        Spacer(Modifier.height(8.dp))
                        Text(statusMsg, fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(onClick = { broadcaster?.stop(); state = RecoveryState.Idle }) {
                            Text("Cancel", color = TextSec)
                        }
                    }

                    // ── FOUND ─────────────────────────────────────────────────
                    RecoveryState.Found -> {
                        val offer = peerOffer!!
                        Text("🎉", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Classmate Found!", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPri)
                        Spacer(Modifier.height(8.dp))
                        Text("Available sessions:", fontSize = 13.sp, color = TextSec)
                        Spacer(Modifier.height(8.dp))
                        offer.availableDates.forEach { d ->
                            val label = try { displayFmt.format(parseFmt.parse(d)!!) } catch (_: Exception) { d }
                            Text("  📅 $label", color = TextPri, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    state = RecoveryState.Syncing
                                    try {
                                        val catalog = withContext(Dispatchers.IO) { PeerSyncClient.getCatalog(offer.url) }
                                        val myDates = store.getDates(subjectCode).toSet()
                                        val toSync  = catalog.filter { it.dateKey !in myDates }
                                        progressMax = toSync.sumOf { it.files.size }.coerceAtLeast(1)
                                        progressCur = 0
                                        val destDir = File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                            "EduNet/$subjectCode"
                                        )
                                        toSync.forEach { dayRec ->
                                            dayRec.files.forEach { fi ->
                                                statusMsg = "Downloading ${fi.name}…"
                                                val saved = withContext(Dispatchers.IO) {
                                                    PeerSyncClient.downloadFile(offer.url, dayRec.dateKey, fi, destDir)
                                                }
                                                store.saveFile(subjectCode, subjectName, saved)
                                                progressCur++
                                            }
                                        }
                                        state = RecoveryState.Done(progressCur)
                                    } catch (e: Exception) {
                                        state = RecoveryState.Error("Sync failed: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Sync All Notes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { state = RecoveryState.Idle }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancel", color = TextSec)
                        }
                    }

                    // ── SYNCING ───────────────────────────────────────────────
                    RecoveryState.Syncing -> {
                        Spacer(Modifier.height(40.dp))
                        CircularProgressIndicator(
                            progress = { if (progressMax > 0) progressCur.toFloat() / progressMax else 0f },
                            modifier = Modifier.size(80.dp), strokeWidth = 6.dp,
                            color = Color.White,
                            trackColor = Color(0xFF2A2A2A)
                        )
                        Spacer(Modifier.height(20.dp))
                        Text("Downloading…", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPri)
                        Spacer(Modifier.height(8.dp))
                        Text(statusMsg, fontSize = 13.sp, color = TextSec, textAlign = TextAlign.Center)
                        Text("$progressCur / $progressMax files", fontSize = 13.sp, color = TextSec)
                    }

                    // ── DONE ─────────────────────────────────────────────────
                    is RecoveryState.Done -> {
                        Spacer(Modifier.height(40.dp))
                        Text("✅", fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Sync Complete!", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPri)
                        Spacer(Modifier.height(8.dp))
                        Text("${s.count} files recovered. Returning to history…", fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center)
                    }

                    // ── ERROR ─────────────────────────────────────────────────
                    is RecoveryState.Error -> {
                        Spacer(Modifier.height(24.dp))
                        Text("❌", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Not Found", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPri)
                        Spacer(Modifier.height(8.dp))
                        Text(s.msg, fontSize = 14.sp, color = TextSec, textAlign = TextAlign.Center, lineHeight = 22.sp)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { state = RecoveryState.Idle },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))
                        ) { Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}
