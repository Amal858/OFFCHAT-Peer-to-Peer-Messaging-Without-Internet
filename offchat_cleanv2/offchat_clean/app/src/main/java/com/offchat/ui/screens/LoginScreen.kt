package com.offchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.offchat.ui.theme.*
import com.offchat.viewmodel.LoginState
import com.offchat.viewmodel.MainViewModel

@Composable
fun LoginScreen(vm: MainViewModel) {
    var roll by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val loginState by vm.loginState.collectAsState()
    val focus = LocalFocusManager.current
    val isLoading = loginState is LoginState.Loading
    val error = (loginState as? LoginState.Error)?.msg

    Box(
        Modifier.fillMaxSize().background(BgDark).imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Logo
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(Surface).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.WifiTethering, null, tint = Accent, modifier = Modifier.size(40.dp))
            }

            Text("OffChat", color = Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Offline Mesh Chat", color = TextMuted, fontSize = 13.sp)

            Spacer(Modifier.height(8.dp))

            // Roll Number field
            OutlinedTextField(
                value = roll,
                onValueChange = { roll = it.uppercase() },
                label = { Text("Roll Number", color = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                leadingIcon = { Icon(Icons.Default.Badge, null, tint = TextMuted) }
            )

            // Password field
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Password", color = TextMuted) },
                singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focus.clearFocus()
                    if (!isLoading) vm.login(roll, pass)
                }),
                trailingIcon = {
                    IconButton(onClick = { showPass = !showPass }) {
                        Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextMuted)
                    }
                },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = TextMuted) }
            )

            // Error
            if (error != null) {
                Card(colors = CardDefaults.cardColors(containerColor = ErrorClr.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(error, color = ErrorClr, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }

            // Login button
            Button(
                onClick = { if (!isLoading) vm.login(roll, pass) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("LOGIN", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
            }

            Text("Default password = roll number", color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Border,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = Accent,
    focusedContainerColor = Surface,
    unfocusedContainerColor = Surface,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextMuted
)
