package com.offchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.offchat.data.model.*
import com.offchat.ui.theme.*
import com.offchat.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(vm: MainViewModel) {
    val user by vm.currentUser.collectAsState()
    val records by vm.attendanceRecords.collectAsState()
    val subjects by vm.subjects.collectAsState()
    val peers by vm.peers.collectAsState()

    val isTeacher = user?.role == UserRole.TEACHER
    var selectedSubject by remember { mutableStateOf(subjects.firstOrNull() ?: "General") }
    var showAddSubject by remember { mutableStateOf(false) }
    var newSubjectName by remember { mutableStateOf("") }
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    LaunchedEffect(selectedSubject) {
        if (isTeacher) vm.loadTeacherAttendance(selectedSubject)
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { vm.goHome() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = Accent)
                    }
                },
                title = { Text(if (isTeacher) "Mark Attendance" else "My Attendance", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
                actions = {
                    Surface(shape = RoundedCornerShape(6.dp), color = if (isTeacher) TeacherClr.copy(.15f) else Accent.copy(.15f)) {
                        Text(
                            if (isTeacher) "Teacher" else "Student",
                            color = if (isTeacher) TeacherClr else Accent,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    if (isTeacher) {
                        IconButton(onClick = { showAddSubject = true }) {
                            Icon(Icons.Default.Add, "Add subject", tint = Accent)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
            )
        },
        bottomBar = { BottomBar(vm, selected = 2) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(BgDark)) {

            // Subject chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subjects) { subj ->
                    FilterChip(
                        selected = selectedSubject == subj,
                        onClick = {
                            selectedSubject = subj
                            if (isTeacher) vm.loadTeacherAttendance(subj)
                        },
                        label = { Text(subj, fontSize = 12.sp, fontWeight = if (selectedSubject == subj) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent,
                            selectedLabelColor = Color.Black,
                            containerColor = Surface,
                            labelColor = TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, selected = selectedSubject == subj,
                            selectedBorderColor = Accent, borderColor = Border
                        )
                    )
                }
            }

            Text("  Date: $today", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))

            if (isTeacher) {
                TeacherAttendanceView(vm, peers, records, selectedSubject)
            } else {
                StudentAttendanceView(records)
            }
        }
    }

    if (showAddSubject) {
        AlertDialog(
            onDismissRequest = { showAddSubject = false },
            containerColor = Surface,
            title = { Text("New Subject", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newSubjectName, onValueChange = { newSubjectName = it },
                    label = { Text("Subject name", color = TextMuted) },
                    colors = fieldColors(), singleLine = true, shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addSubject(newSubjectName)
                    newSubjectName = ""; showAddSubject = false
                }) { Text("Add", color = Accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubject = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

@Composable
fun TeacherAttendanceView(
    vm: MainViewModel,
    peers: Map<String, Peer>,
    records: List<AttendanceRecord>,
    subject: String
) {
    if (peers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.BluetoothSearching, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                Text("No students nearby on mesh", color = TextMuted)
                Text("Students must have OffChat open with Bluetooth on", color = TextMuted.copy(0.6f), fontSize = 12.sp)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(peers.values.filter { it.role == UserRole.STUDENT }.toList(), key = { it.rollNumber }) { peer ->
            val marked = records.find { it.studentRoll == peer.rollNumber && it.subject == subject }
            StudentAttendanceCard(peer = peer, markedRecord = marked,
                onPresent = { vm.markAttendance(peer.rollNumber, peer.name, subject, true) },
                onAbsent  = { vm.markAttendance(peer.rollNumber, peer.name, subject, false) }
            )
        }
    }
}

@Composable
fun StudentAttendanceCard(peer: Peer, markedRecord: AttendanceRecord?, onPresent: () -> Unit, onAbsent: () -> Unit) {
    val initials = peer.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Accent.copy(.2f)), contentAlignment = Alignment.Center) {
                    Text(initials, color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(peer.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("${peer.rollNumber} · ${peer.branch}", color = TextMuted, fontSize = 12.sp)
                }
                if (markedRecord != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (markedRecord.present) Accent else ErrorClr))
                        Text(
                            if (markedRecord.present) "Present" else "Absent",
                            color = if (markedRecord.present) Accent else ErrorClr,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                    }
                }
            }
            if (markedRecord == null) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAbsent, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorClr),
                        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                    ) { Text("Absent") }
                    Button(
                        onClick = onPresent, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Present", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun StudentAttendanceView(records: List<AttendanceRecord>) {
    if (records.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.EventNote, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                Text("No attendance records yet", color = TextMuted)
            }
        }
        return
    }

    val present = records.count { it.present }
    val total = records.size
    val pct = if (total > 0) (present * 100) / total else 0

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary stats
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("$pct%", "Overall", Accent, Modifier.weight(1f))
                StatCard("$present", "Present", Accent, Modifier.weight(1f))
                StatCard("${total - present}", "Absent", ErrorClr, Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { Text("RECORDS", color = TextMuted, fontSize = 11.sp, letterSpacing = 1.sp) }
        items(records, key = { "${it.date}_${it.subject}" }) { rec ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(rec.subject, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("${rec.date} · by ${rec.markedByRoll}", color = TextMuted, fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (rec.present) Accent else ErrorClr))
                        Text(if (rec.present) "Present" else "Absent",
                            color = if (rec.present) Accent else ErrorClr,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(value: String, label: String, valueColor: Color, modifier: Modifier) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Surface), modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = valueColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextMuted, fontSize = 11.sp)
        }
    }
}
