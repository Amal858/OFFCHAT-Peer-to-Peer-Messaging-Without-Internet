package com.offchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.google.accompanist.permissions.*
import com.offchat.bluetooth.BluetoothPermissions
import com.offchat.ui.theme.*
import com.offchat.viewmodel.MainViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(vm: MainViewModel) {
    val perms = rememberMultiplePermissionsState(BluetoothPermissions.required().toList())

    LaunchedEffect(perms.allPermissionsGranted) {
        if (perms.allPermissionsGranted) vm.onPermissionsGranted()
    }

    Box(Modifier.fillMaxSize().background(BgDark), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(Icons.Default.Bluetooth, null, tint = Accent, modifier = Modifier.size(64.dp))
            Text("Bluetooth Required", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "OffChat uses Bluetooth to create a peer-to-peer mesh network. No internet required.",
                color = TextMuted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Scan for nearby devices",
                        "Advertise your presence",
                        "Connect to peers directly"
                    ).forEach { perm ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(8.dp).background(Accent, RoundedCornerShape(50)))
                            Text(perm, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }
            Button(
                onClick = { perms.launchMultiplePermissionRequest() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Grant Permissions", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
