package com.offchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.offchat.bluetooth.BluetoothPermissions
import com.offchat.ui.screens.*
import com.offchat.ui.theme.OffChatTheme
import com.offchat.viewmodel.AppScreen
import com.offchat.viewmodel.LoginState
import com.offchat.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OffChatTheme {
                val screen by vm.screen.collectAsState()
                val loginState by vm.loginState.collectAsState()

                // While checking session, show loading
                if (loginState is LoginState.Loading && screen is AppScreen.Login) {
                    SplashScreen()
                    return@OffChatTheme
                }

                when (val s = screen) {
                    is AppScreen.Login       -> LoginScreen(vm)
                    is AppScreen.Permissions -> PermissionScreen(vm)
                    is AppScreen.Home        -> HomeScreen(vm)
                    is AppScreen.GlobalChat  -> GlobalChatScreen(vm, s.channel)
                    is AppScreen.DirectChat  -> DirectChatScreen(vm, s.peerRoll)
                    is AppScreen.Attendance  -> AttendanceScreen(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Start service if logged in, permissions granted, and service not running
        val user = vm.currentUser.value
        if (user != null && BluetoothPermissions.allGranted(this) &&
            !com.offchat.bluetooth.BluetoothService.isRunning) {
            vm.startService()
        }
    }
}
