package com.example.edunet.ui

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.edunet.data.AttendanceStore
import com.example.edunet.data.repository.AttendanceRecord
import com.example.edunet.data.repository.EnrolledStudent
import com.example.edunet.data.SessionManager
import com.example.edunet.data.repository.MongoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private val BgDark = Color(0xFF000000)
private val CardDark = Color(0xFF111111)
private val TextPri = Color(0xFFFFFFFF)
private val TextSec = Color(0xFF888888)

// Status colors
private val PresentColor = Color(0xFF4CAF50)
private val LateColor = Color(0xFFFF9800)
private val AbsentColor = Color(0xFFF44336)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    subjectCode: String,
    subjectName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { SessionManager(context) }
    val store = remember { AttendanceStore(context) }

    val formatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    var selectedDate by remember { mutableStateOf(formatter.format(Date())) }

    var students by remember { mutableStateOf<List<EnrolledStudent>>(emptyList()) }
    var attendanceMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // studentId -> status
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var pendingUpdates by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    // Auto-sync loop
    LaunchedEffect(Unit) {
        while (isActive) {
            val pendingKeys = store.getPendingSyncKeys()
            pendingUpdates = pendingKeys.size
            if (pendingKeys.isNotEmpty() && MongoRepository.getTeacherSubjects(session.getUserId()) is com.example.edunet.data.repository.SubjectResult.Success) {
                // we have internet
                for (key in pendingKeys) {
                    val parts = key.split("_")
                    if (parts.size >= 4) {
                        val c = parts[2]
                        val d = parts[3]
                        val recs = store.getRecordsByKey(key)
                        if (recs != null) {
                            val success = MongoRepository.submitAttendance(c, d, session.getUserId(), recs)
                            if (success) {
                                store.markSynced(key)
                            }
                        } else {
                            store.markSynced(key) // corrupted, remove it
                        }
                    }
                }
            }
            kotlinx.coroutines.delay(5000)
        }
    }

    fun loadData() {
        scope.launch {
            isLoading = true
            message = null
            
            // Fetch enrolled students
            val fetchedStudents = MongoRepository.getEnrolledStudents(subjectCode)
            if (fetchedStudents.isNotEmpty() || students.isEmpty()) {
                students = fetchedStudents.sortedBy { it.studentName }
            }
            
            // Initialize map with default "present" or offline cache
            val newMap = mutableMapOf<String, String>()
            
            // 1. Try checking local store first
            val localRecords = store.loadRecords(subjectCode, selectedDate)
            if (localRecords != null && localRecords.isNotEmpty()) {
                localRecords.forEach { newMap[it.studentId] = it.status }
            } else {
                // 2. Try fetching from network
                val remoteRecords = MongoRepository.getAttendanceForDate(subjectCode, selectedDate)
                if (remoteRecords.isNotEmpty()) {
                    remoteRecords.forEach { newMap[it.studentId] = it.status }
                    // Update local cache
                    store.saveRecords(subjectCode, selectedDate, remoteRecords, pendingSync = false)
                }
            }
            
            // Ensure all students have a default if not found
            fetchedStudents.forEach { s ->
                if (!newMap.containsKey(s.studentId)) {
                    newMap[s.studentId] = "present" // Default to present
                }
            }
            
            attendanceMap = newMap
            isLoading = false
        }
    }

    LaunchedEffect(selectedDate) {
        loadData()
    }

    val datePickerDialog = remember {
        val calendar = Calendar.getInstance()
        calendar.time = formatter.parse(selectedDate) ?: Date()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance()
                c.set(year, month, dayOfMonth)
                selectedDate = formatter.format(c.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Scaffold(
        containerColor = BgDark,
        floatingActionButton = {
            if (students.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isSaving) return@ExtendedFloatingActionButton
                        scope.launch {
                            isSaving = true
                            val records = students.map { s ->
                                AttendanceRecord(
                                    studentId = s.studentId,
                                    studentName = s.studentName,
                                    status = attendanceMap[s.studentId] ?: "present",
                                    markedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
                                )
                            }
                            // Always save locally first with pending=true
                            store.saveRecords(subjectCode, selectedDate, records, pendingSync = true)
                            
                            // Try to push immediately
                            val success = MongoRepository.submitAttendance(subjectCode, selectedDate, session.getUserId(), records)
                            if (success) {
                                store.markSynced("sync_pending_${subjectCode}_${selectedDate}")
                                message = "Saved & Synced!"
                            } else {
                                message = "Saved offline. Auto-sync will run later."
                            }
                            isSaving = false
                            // Refresh un-synced count
                            pendingUpdates = store.getPendingSyncKeys().size
                        }
                    },
                    icon = {
                        if (isSaving) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Done, contentDescription = null, tint = Color.Black)
                    },
                    text = { Text("Save Attendance", color = Color.Black, fontWeight = FontWeight.Bold) },
                    containerColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Attendance", color = TextSec, fontSize = 13.sp)
                    Text(subjectName, color = TextPri, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                if (pendingUpdates > 0) {
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFFFF9800).copy(alpha = 0.2f)) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("$pendingUpdates pending", color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Date selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clickable { datePickerDialog.show() }
                    .background(CardDark, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text(selectedDate, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("Change", color = Color(0xFF2196F3), fontSize = 14.sp)
            }

            if (message != null) {
                Text(
                    message!!,
                    color = if (message!!.contains("offline", true)) LateColor else PresentColor,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                students.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No students enrolled yet.\nWait for students to join your class.", color = TextSec, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp) // space for FAB
                    ) {
                        items(students) { student ->
                            AttendanceRow(
                                student = student,
                                currentStatus = attendanceMap[student.studentId] ?: "present",
                                onStatusChange = { newStatus ->
                                    val map = attendanceMap.toMutableMap()
                                    map[student.studentId] = newStatus
                                    attendanceMap = map
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceRow(
    student: EnrolledStudent,
    currentStatus: String,
    onStatusChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(student.studentName, color = TextPri, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(student.studentEmail, color = TextSec, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(label = "Present", status = "present", currentStatus = currentStatus, color = PresentColor, onClick = { onStatusChange("present") }, modifier = Modifier.weight(1f))
                StatusChip(label = "Late", status = "late", currentStatus = currentStatus, color = LateColor, onClick = { onStatusChange("late") }, modifier = Modifier.weight(1f))
                StatusChip(label = "Absent", status = "absent", currentStatus = currentStatus, color = AbsentColor, onClick = { onStatusChange("absent") }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    status: String,
    currentStatus: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = currentStatus == status
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) color.copy(alpha = 0.2f) else Color(0xFF1E1E1E),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, color) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (isSelected) color else TextSec,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
