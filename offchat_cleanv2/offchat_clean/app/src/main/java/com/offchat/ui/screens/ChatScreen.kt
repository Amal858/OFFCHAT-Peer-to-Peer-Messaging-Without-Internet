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
fun GlobalChatScreen(vm: MainViewModel, channel: String) {
    val messages by vm.messages.collectAsState()
    val user by vm.currentUser.collectAsState()
    val ackedMsgIds by vm.deliveryAcks.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    // Track all acked IDs in a set for the session
    val ackedSet = remember { mutableSetOf<String>() }
    LaunchedEffect(ackedMsgIds) { ackedMsgIds?.let { ackedSet.add(it) } }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).background(Accent, CircleShape))
                        Text("#$channel", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        bottomBar = { BottomBar(vm, selected = 1) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(BgDark)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val grouped = messages.groupBy { dateStr(it.timestamp) }
                grouped.forEach { (date, msgs) ->
                    item {
                        DateDivider(date)
                    }
                    items(msgs, key = { it.id }) { msg ->
                        val isMe = msg.senderRoll == user?.rollNumber
                        MessageBubble(msg = msg, isMe = isMe, isAcked = msg.id in ackedSet)
                    }
                }
            }
            // Input bar
            ChatInputBar(
                input = input,
                onInput = { input = it },
                onSend = {
                    if (input.isNotBlank()) {
                        vm.sendGlobalMessage(input, channel)
                        input = ""
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectChatScreen(vm: MainViewModel, peerRoll: String) {
    val messages by vm.messages.collectAsState()
    val user by vm.currentUser.collectAsState()
    val peers by vm.peers.collectAsState()
    val ackedMsgIds by vm.deliveryAcks.collectAsState()
    val ackedSet = remember { mutableSetOf<String>() }
    LaunchedEffect(ackedMsgIds) { ackedMsgIds?.let { ackedSet.add(it) } }
    val peer = peers[peerRoll]
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val initials = peer?.name?.split(" ")?.mapNotNull { it.firstOrNull()?.toString() }?.take(2)?.joinToString("") ?: peerRoll.take(2)
                        val roleColor = if (peer?.role == UserRole.TEACHER) TeacherClr else Accent
                        Box(Modifier.size(34.dp).clip(CircleShape).background(roleColor.copy(0.2f)), contentAlignment = Alignment.Center) {
                            Text(initials, color = roleColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Column {
                            Text(peer?.name ?: peerRoll, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${peerRoll} · direct mesh", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(BgDark)) {
            // E2E notice
            Row(
                Modifier.fillMaxWidth().background(MsgBg.copy(alpha = 0.6f)).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Shield, null, tint = Accent, modifier = Modifier.size(14.dp))
                Text("Encrypted mesh connection", color = TextMuted, fontSize = 11.sp)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val grouped = messages.groupBy { dateStr(it.timestamp) }
                grouped.forEach { (date, msgs) ->
                    item { DateDivider(date) }
                    items(msgs, key = { it.id }) { msg ->
                        MessageBubble(msg = msg, isMe = msg.senderRoll == user?.rollNumber, isAcked = msg.id in ackedSet)
                    }
                }
            }
            ChatInputBar(input = input, onInput = { input = it }, onSend = {
                if (input.isNotBlank()) {
                    vm.sendDirectMessage(peerRoll, input)
                    input = ""
                }
            })
        }
    }
}

@Composable
fun MessageBubble(msg: Message, isMe: Boolean, isAcked: Boolean = false) {
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        if (!isMe) {
            val initials = msg.senderName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Accent.copy(0.2f)).padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) { Text(initials, color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(6.dp))
        }
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            if (!isMe) Text(msg.senderName, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 2.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(
                        topStart = if (isMe) 12.dp else 0.dp,
                        topEnd = if (isMe) 0.dp else 12.dp,
                        bottomStart = 12.dp, bottomEnd = 12.dp
                    ))
                    .background(if (isMe) MsgBg else Surface)
                    .run { if (isMe) this else this }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(msg.content, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
                Text(timeStr, color = TextMuted, fontSize = 10.sp)
                if (isMe) Icon(
                    if (isAcked) Icons.Default.DoneAll else Icons.Default.Done,
                    null,
                    tint = if (isAcked) Accent else TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun DateDivider(date: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = Border)
        Text("  $date  ", color = TextMuted, fontSize = 11.sp)
        HorizontalDivider(Modifier.weight(1f), color = Border)
    }
}

@Composable
fun ChatInputBar(input: String, onInput: (String) -> Unit, onSend: () -> Unit) {
    Surface(color = Surface, tonalElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding().imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                placeholder = { Text("Message...", color = TextMuted, fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent, unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    cursorColor = Accent, focusedContainerColor = Surface2, unfocusedContainerColor = Surface2
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            FloatingActionButton(
                onClick = onSend,
                modifier = Modifier.size(46.dp),
                containerColor = Accent, contentColor = Color.Black,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Send, "Send", modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun dateStr(ts: Long): String =
    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ts))
