package com.example.edunet.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.edunet.data.SubjectCache
import com.example.edunet.data.SessionManager
import com.example.edunet.data.repository.JoinResult
import com.example.edunet.data.repository.MongoRepository
import com.example.edunet.data.repository.SubjectItem
import com.example.edunet.data.repository.SubjectResult
import kotlinx.coroutines.launch

// ─── B&W Colours ─────────────────────────────────────────────────────────────
private val BgDark    = Color(0xFF000000)
private val CardDark  = Color(0xFF111111)
private val Accent    = Color(0xFF1A1A1A)   // dark button/FAB bg → white text on it
private val AccentAlt = Color(0xFF2A2A2A)
private val GreenOk   = Color(0xFF1A1A1A)   // same dark for teacher elements
private val TextPri   = Color(0xFFFFFFFF)
private val TextSec   = Color(0xFF888888)
private val OnAccent  = Color(0xFFFFFFFF)   // text/icon ON Accent/GreenOk buttons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onStartSession: (teacherName: String, subjectName: String, subjectCode: String) -> Unit = { _, _, _ -> },
    onJoinSession: (subjectCode: String, subjectName: String) -> Unit = { _, _ -> },
    onOpenHistory: (subjectCode: String, subjectName: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val session = remember { SessionManager(context) }
    val role    = session.getUserRole()

    if (role == "teacher") {
        TeacherHomeScreen(session = session, onLogout = onLogout, onStartSession = { sn, sc ->
            onStartSession(session.getUserName(), sn, sc)
        })
    } else {
        StudentHomeScreen(session = session, onLogout = onLogout, onJoinSession = onJoinSession, onOpenHistory = onOpenHistory)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  STUDENT HOME
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentHomeScreen(
    session: SessionManager,
    onLogout: () -> Unit,
    onJoinSession: (subjectCode: String, subjectName: String) -> Unit = { _, _ -> },
    onOpenHistory: (subjectCode: String, subjectName: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val cache   = remember { SubjectCache(context) }
    val scope   = rememberCoroutineScope()
    var subjects  by remember { mutableStateOf<List<SubjectItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf("") }
    var isOffline by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    // Offline-first: load cache immediately, then try network
    LaunchedEffect(Unit) {
        isLoading = true
        val uid = session.getUserId()
        // 1. Show cached data instantly (if any)
        cache.loadStudentSubjects(uid)?.let { cached ->
            subjects = cached
        }
        // 2. Try fetching fresh data from server
        if (cache.isOnline()) {
            isOffline = false
            when (val r = MongoRepository.getStudentSubjects(uid)) {
                is SubjectResult.Success -> {
                    subjects = r.subjects
                    cache.saveStudentSubjects(uid, r.subjects)
                }
                is SubjectResult.Error -> {
                    if (subjects.isEmpty()) errorMsg = r.message
                }
            }
        } else {
            isOffline = true
            if (subjects.isEmpty()) errorMsg = "No internet. No cached data available."
        }
        isLoading = false
    }

    Scaffold(
        containerColor = BgDark,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { showJoinDialog = true },
                icon           = { Icon(Icons.Default.Add, contentDescription = null) },
                text           = { Text("Join Class") },
                containerColor = Color(0xFF222222),
                contentColor   = Color.White
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Welcome back 👋", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                                Text(session.getUserName(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        IconButton(onClick = { session.clearSession(); onLogout() }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                            Text(
                                "📚 Student",
                                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                        if (isOffline) {
                            Surface(shape = RoundedCornerShape(50), color = Color(0xFFFF9800).copy(alpha = 0.9f)) {
                                Text(
                                    "📶 Offline – cached",
                                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Body ─────────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Subjects", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPri)
                Surface(shape = RoundedCornerShape(50), color = Color(0xFF2A2A2A)) {
                    Text(
                        "${subjects.size} enrolled",
                        color = TextPri, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
                errorMsg.isNotEmpty() -> {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp))
                }
                subjects.isEmpty() -> {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        EmptySubjectsCard(isStudent = true)
                    }
                }
                else -> {
                    // LazyColumn fills remaining space; Scaffold innerPadding already
                    // adds bottom space equal to FAB height so nothing is obscured
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(subjects) { subj ->
                            StudentSubjectCard(
                                subj,
                                onJoinSession = { onJoinSession(subj.subjectCode, subj.subjectName) },
                                onOpenHistory = { onOpenHistory(subj.subjectCode, subj.subjectName) }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Join Dialog ───────────────────────────────────────────────────────────
    if (showJoinDialog) {
        JoinClassDialog(
            studentId    = session.getUserId(),
            studentEmail = session.getUserEmail(),
            onDismiss = { showJoinDialog = false },
            onJoined  = { newSubj ->
                subjects = subjects + newSubj
                showJoinDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TEACHER HOME
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherHomeScreen(
    session: SessionManager,
    onLogout: () -> Unit,
    onStartSession: (subjectName: String, subjectCode: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val cache   = remember { SubjectCache(context) }
    var subjects by remember { mutableStateOf<List<SubjectItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg  by remember { mutableStateOf("") }
    var isOffline by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        val uid = session.getUserId()
        // 1. Show cached data instantly
        cache.loadTeacherSubjects(uid)?.let { cached -> subjects = cached }
        // 2. Try fresh from network
        if (cache.isOnline()) {
            isOffline = false
            when (val r = MongoRepository.getTeacherSubjects(uid)) {
                is SubjectResult.Success -> {
                    subjects = r.subjects
                    cache.saveTeacherSubjects(uid, r.subjects)
                }
                is SubjectResult.Error -> {
                    if (subjects.isEmpty()) errorMsg = r.message
                }
            }
        } else {
            isOffline = true
            if (subjects.isEmpty()) errorMsg = "No internet. No cached data available."
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFF111111)
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Welcome back 👋", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                                Text(session.getUserName(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        IconButton(onClick = { session.clearSession(); onLogout() }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "🎓 Teacher",
                                color = Color.White, fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                        if (isOffline) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFFF9800).copy(alpha = 0.9f)
                            ) {
                                Text(
                                    "📶 Offline – cached",
                                    color = Color.White, fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Body ─────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("My Subjects", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPri)
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFF2A2A2A)) {
                        Text(
                            "${subjects.size} subjects",
                            color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GreenOk)
                        }
                    }
                    errorMsg.isNotEmpty() -> Text(errorMsg, color = MaterialTheme.colorScheme.error)
                    subjects.isEmpty()    -> EmptySubjectsCard(isStudent = false)
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(subjects) { subj ->
                                TeacherSubjectCard(subj, onStartSession = { onStartSession(subj.subjectName, subj.subjectCode) })
                            }
                        }
                    }
                }
            }
        }

        // ── FAB: Create Subject ───────────────────────────────────────────────
        ExtendedFloatingActionButton(
            onClick = { showCreateDialog = true },
            icon    = { Icon(Icons.Default.Add, contentDescription = null) },
            text    = { Text("Create Subject") },
            containerColor = Color(0xFF222222),
            contentColor   = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        )
    }

    if (showCreateDialog) {
        CreateSubjectDialog(
            teacherId    = session.getUserId(),
            teacherEmail = session.getUserEmail(),
            onDismiss = { showCreateDialog = false },
            onCreated = { newSubj ->
                subjects = subjects + newSubj
                showCreateDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  CARDS & DIALOG
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StudentSubjectCard(
    subj: SubjectItem,
    onJoinSession: () -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenHistory() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Code badge
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2A2A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        subj.subjectCode.take(4),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(subj.subjectName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPri)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("by ${subj.teacherName}", fontSize = 12.sp, color = TextSec)
                    Spacer(modifier = Modifier.height(3.dp))
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFF2A2A2A)) {
                        Text(
                            subj.subjectCode, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

            }
            Spacer(Modifier.height(10.dp))
            // Action row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenHistory,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Text("📂 History", fontSize = 12.sp, color = TextSec)
                }
                Button(
                    onClick = onJoinSession,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("📡 Join Session", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun TeacherSubjectCard(subj: SubjectItem, onStartSession: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    subj.subjectCode.take(4),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(subj.subjectName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPri)
                Spacer(modifier = Modifier.height(3.dp))
                Text("Code: ${subj.subjectCode}", fontSize = 12.sp, color = TextSec)
                Spacer(modifier = Modifier.height(3.dp))
                Text("👥 ${subj.studentCount} students", fontSize = 11.sp, color = TextSec)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onStartSession,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp),
                    tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text("Start", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun EmptySubjectsCard(isStudent: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (isStudent) "📭" else "📋", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                if (isStudent) "No subjects yet" else "No subjects created",
                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPri
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                if (isStudent) "Tap the + button to join your first class" else "Subjects you create will appear here",
                fontSize = 13.sp, color = TextSec, textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Join Class Dialog ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinClassDialog(
    studentId: String,
    studentEmail: String,
    onDismiss: () -> Unit,
    onJoined: (SubjectItem) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg  by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Join a Class", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPri)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Enter the subject code your teacher shared", fontSize = 13.sp, color = TextSec)
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase(); errorMsg = "" },
                    label = { Text("Subject Code", color = TextSec) },
                    placeholder = { Text("e.g. CS301", color = TextSec.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Accent,
                        unfocusedBorderColor = TextSec.copy(alpha = 0.3f),
                        focusedTextColor     = TextPri,
                        unfocusedTextColor   = TextPri,
                        cursorColor          = Accent,
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                AnimatedVisibility(errorMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        Text("Cancel", color = TextSec)
                    }
                    Button(
                        onClick = {
                            if (code.isBlank()) {
                                errorMsg = "Please enter a class code"
                                return@Button
                            }
                            scope.launch {
                                isLoading = true
                                when (val r = MongoRepository.joinClass(studentId, studentEmail, code)) {
                                    is JoinResult.Success -> onJoined(r.subject)
                                    is JoinResult.Error -> {
                                        errorMsg = r.message
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Join", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Create Subject Dialog (Teacher) ──────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSubjectDialog(
    teacherId: String,
    teacherEmail: String,
    onDismiss: () -> Unit,
    onCreated: (SubjectItem) -> Unit
) {
    var subjectName by remember { mutableStateOf("") }
    var subjectCode by remember { mutableStateOf("") }
    var isLoading   by remember { mutableStateOf(false) }
    var errorMsg    by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Create Subject", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPri)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Students will use the subject code to join", fontSize = 13.sp, color = TextSec)
                Spacer(modifier = Modifier.height(20.dp))

                // Subject Name
                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { subjectName = it; errorMsg = "" },
                    label = { Text("Subject Name", color = TextSec) },
                    placeholder = { Text("e.g. Data Structures", color = TextSec.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color.White,
                        unfocusedBorderColor = TextSec.copy(alpha = 0.3f),
                        focusedTextColor     = TextPri,
                        unfocusedTextColor   = TextPri,
                        cursorColor          = Color.White,
                        focusedContainerColor   = Color(0xFF1A1A1A),
                        unfocusedContainerColor = Color(0xFF1A1A1A),
                        focusedLabelColor    = TextSec,
                        unfocusedLabelColor  = TextSec
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Subject Code
                OutlinedTextField(
                    value = subjectCode,
                    onValueChange = { subjectCode = it.uppercase(); errorMsg = "" },
                    label = { Text("Subject Code", color = TextSec) },
                    placeholder = { Text("e.g. CS501", color = TextSec.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color.White,
                        unfocusedBorderColor = TextSec.copy(alpha = 0.3f),
                        focusedTextColor     = TextPri,
                        unfocusedTextColor   = TextPri,
                        cursorColor          = Color.White,
                        focusedContainerColor   = Color(0xFF1A1A1A),
                        unfocusedContainerColor = Color(0xFF1A1A1A),
                        focusedLabelColor    = TextSec,
                        unfocusedLabelColor  = TextSec
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                AnimatedVisibility(errorMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        Text("Cancel", color = TextSec)
                    }
                    Button(
                        onClick = {
                            when {
                                subjectName.isBlank() -> { errorMsg = "Subject name is required"; return@Button }
                                subjectCode.isBlank() -> { errorMsg = "Subject code is required"; return@Button }
                            }
                            scope.launch {
                                isLoading = true
                                when (val r = MongoRepository.createSubject(teacherId, teacherEmail, subjectName, subjectCode)) {
                                    is JoinResult.Success -> onCreated(r.subject)
                                    is JoinResult.Error   -> { errorMsg = r.message; isLoading = false }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        enabled = !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
