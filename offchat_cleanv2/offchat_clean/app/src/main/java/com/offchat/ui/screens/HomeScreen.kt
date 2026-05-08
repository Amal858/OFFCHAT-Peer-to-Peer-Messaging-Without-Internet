package com.offchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel) {
    val peers by vm.peers.collectAsState()
    val user by vm.currentUser.collectAsState()

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).background(if (peers.isNotEmpty()) Accent else TextMuted, CircleShape))
                        Text("OffChat", color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (peers.isNotEmpty())
                            Text("${peers.size} nearby", color = TextMuted, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface),
                actions = {
                    IconButton(onClick = { vm.logout() }) {
                        Icon(Icons.Default.Logout, "Logout", tint = TextMuted)
                    }
                }
            )
        },
        bottomBar = { BottomBar(vm, selected = 0) }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().background(BgDark),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Global chat entry
            item {
                Text("CHANNELS", color = TextMuted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp))
                ChannelCard(
                    name = "#global",
                    subtitle = "All nearby peers",
                    icon = Icons.Default.Forum,
                    onClick = { vm.openGlobalChat("global") }
                )
            }

            // Peer list
            if (peers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("PEERS ON MESH", color = TextMuted, fontSize = 11.sp, letterSpacing = 1.sp)
                }
                items(peers.values.toList(), key = { it.rollNumber }) { peer ->
                    PeerCard(peer = peer, onClick = { vm.openDirectChat(peer.rollNumber) })
                }
            } else {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.BluetoothSearching, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Text("Scanning for peers...", color = TextMuted, fontSize = 14.sp)
                            Text("Make sure others have OffChat open\nnearby with Bluetooth on", color = TextMuted.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(name: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(name, color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = TextMuted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
        }
    }
}

@Composable
fun PeerCard(peer: Peer, onClick: () -> Unit) {
    val roleColor = if (peer.role == UserRole.TEACHER) TeacherClr else Accent
    val initials = peer.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Surface2)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Surface2)
                    .padding(1.dp).clip(CircleShape).background(roleColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center) {
                    Text(initials, color = roleColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(peer.name, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("${peer.rollNumber} · ${peer.branch}", color = TextMuted, fontSize = 12.sp)
            }
            Surface(shape = RoundedCornerShape(6.dp), color = roleColor.copy(alpha = 0.15f)) {
                Text(
                    if (peer.role == UserRole.TEACHER) "Teacher" else "Student",
                    color = roleColor, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun BottomBar(vm: MainViewModel, selected: Int) {
    NavigationBar(containerColor = Surface, tonalElevation = 0.dp) {
        NavigationBarItem(selected = selected == 0,
            onClick = { vm.goHome() },
            icon = { Icon(Icons.Default.People, "Peers") },
            label = { Text("Peers", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Accent, selectedTextColor = Accent,
                indicatorColor = Accent.copy(alpha = 0.15f),
                unselectedIconColor = TextMuted, unselectedTextColor = TextMuted
            ))
        NavigationBarItem(selected = selected == 1,
            onClick = { vm.openGlobalChat() },
            icon = { Icon(Icons.Default.Forum, "Chat") },
            label = { Text("Chat", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Accent, selectedTextColor = Accent,
                indicatorColor = Accent.copy(alpha = 0.15f),
                unselectedIconColor = TextMuted, unselectedTextColor = TextMuted
            ))
        NavigationBarItem(selected = selected == 2,
            onClick = { vm.openAttendance() },
            icon = { Icon(Icons.Default.EventNote, "Attendance") },
            label = { Text("Attendance", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Accent, selectedTextColor = Accent,
                indicatorColor = Accent.copy(alpha = 0.15f),
                unselectedIconColor = TextMuted, unselectedTextColor = TextMuted
            ))
    }
}
