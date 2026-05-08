package com.offchat.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offchat.bluetooth.BluetoothPermissions
import com.offchat.bluetooth.BluetoothService
import com.offchat.bluetooth.MsgPayload
import com.offchat.bluetooth.AttPayload
import com.offchat.data.db.*
import com.offchat.data.model.*
import com.offchat.data.repository.SessionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class AppScreen {
    object Login : AppScreen()
    object Permissions : AppScreen()
    object Home : AppScreen()
    data class GlobalChat(val channel: String = "global") : AppScreen()
    data class DirectChat(val peerRoll: String) : AppScreen()
    object Attendance : AppScreen()
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Error(val msg: String) : LoginState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = OffChatDatabase.get(app)
    private val session = SessionRepository(app)

    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Login)
    val screen: StateFlow<AppScreen> = _screen

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Loading)
    val loginState: StateFlow<LoginState> = _loginState

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    val peers: StateFlow<Map<String, Peer>> = BluetoothService.peers

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _attendanceRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val attendanceRecords: StateFlow<List<AttendanceRecord>> = _attendanceRecords

    private val _subjects = MutableStateFlow(listOf("General","Data Structures","Networks","Mathematics","Physics"))
    val subjects: StateFlow<List<String>> = _subjects

    private val _activeChannel = MutableStateFlow("global")
    val activeChannel: StateFlow<String> = _activeChannel

    // Delivery ACKs from mesh — msgId of successfully delivered messages
    val deliveryAcks: StateFlow<String?> = BluetoothService.deliveryAck

    private val today get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    init {
        restoreSession()
        observeIncomingMesh()
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private fun restoreSession() {
        viewModelScope.launch {
            try {
                val roll = session.roll.firstOrNull()
                if (!roll.isNullOrBlank()) {
                    val u = db.userDao().get(roll)?.toModel()
                    if (u != null) { setLoggedIn(u); return@launch }
                }
            } catch (e: Exception) { Log.e("VM", "Restore: ${e.message}") }
            _loginState.value = LoginState.Idle
        }
    }

    fun login(roll: String, password: String) {
        if (roll.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Please fill all fields")
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val u = db.userDao().login(roll.uppercase().trim(), password.trim())?.toModel()
                if (u != null) {
                    session.save(u.rollNumber)
                    setLoggedIn(u)
                } else {
                    _loginState.value = LoginState.Error("Invalid credentials\nDefault password = roll number")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("Error: ${e.message}")
            }
        }
    }

    private fun setLoggedIn(u: User) {
        _currentUser.value = u
        _loginState.value = LoginState.Idle
        _screen.value = if (BluetoothPermissions.allGranted(getApplication()))
            AppScreen.Home else AppScreen.Permissions
        loadMessages("global")
        if (u.role == UserRole.STUDENT) loadStudentAttendance(u.rollNumber)
    }

    fun logout() {
        viewModelScope.launch {
            session.clear()
            stopService()
            _currentUser.value = null
            _screen.value = AppScreen.Login
            _loginState.value = LoginState.Idle
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    fun goHome() { _screen.value = AppScreen.Home }

    fun openGlobalChat(channel: String = "global") {
        _activeChannel.value = channel
        _screen.value = AppScreen.GlobalChat(channel)
        loadMessages(channel)
    }

    fun openDirectChat(peerRoll: String) {
        _screen.value = AppScreen.DirectChat(peerRoll)
        loadDirectMessages(peerRoll)
    }

    fun openAttendance() { _screen.value = AppScreen.Attendance }

    fun onPermissionsGranted() {
        _screen.value = AppScreen.Home
        startService()
    }

    // ── Bluetooth Service ─────────────────────────────────────────────────────

    fun startService() {
        val u = _currentUser.value ?: return
        BluetoothService.myRoll = u.rollNumber
        BluetoothService.myName = u.name
        BluetoothService.myRole = u.role
        BluetoothService.myYear = u.year
        BluetoothService.myBranch = u.branch
        BluetoothService.myDept = u.department
        val intent = Intent(getApplication(), BluetoothService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            getApplication<Application>().startForegroundService(intent)
        else getApplication<Application>().startService(intent)
    }

    private fun stopService() {
        getApplication<Application>().stopService(Intent(getApplication(), BluetoothService::class.java))
    }

    // ── Messaging ──────────────────────────────────────────────────────────────

    private fun loadMessages(channel: String) {
        viewModelScope.launch {
            db.messageDao().getByChannel(channel).collect { list ->
                _messages.value = list.map { it.toModel() }
            }
        }
    }

    private fun loadDirectMessages(peerRoll: String) {
        val myRoll = _currentUser.value?.rollNumber ?: return
        viewModelScope.launch {
            db.messageDao().getDirect(myRoll, peerRoll).collect { list ->
                _messages.value = list.map { it.toModel() }
            }
        }
    }

    fun sendGlobalMessage(content: String, channel: String = "global") {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val msg = Message(
                id = UUID.randomUUID().toString(),
                senderRoll = u.rollNumber,
                senderName = u.name,
                receiverRoll = channel,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            db.messageDao().insert(msg.toEntity())
            BluetoothService.sendMsg(MsgPayload(msg.id, u.rollNumber, u.name, channel, content, msg.timestamp))
        }
    }

    fun sendDirectMessage(peerRoll: String, content: String) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val msg = Message(
                id = UUID.randomUUID().toString(),
                senderRoll = u.rollNumber,
                senderName = u.name,
                receiverRoll = peerRoll,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            db.messageDao().insert(msg.toEntity())
            BluetoothService.sendMsg(MsgPayload(msg.id, u.rollNumber, u.name, peerRoll, content, msg.timestamp))
        }
    }

    // ── Incoming mesh messages ─────────────────────────────────────────────────

    private fun observeIncomingMesh() {
        viewModelScope.launch {
            BluetoothService.incomingMsg.collect { payload ->
                payload ?: return@collect
                val msg = Message(payload.id, payload.senderRoll, payload.senderName,
                    payload.channel, payload.content, payload.ts)
                db.messageDao().insert(msg.toEntity())
            }
        }
        viewModelScope.launch {
            BluetoothService.incomingAtt.collect { payload ->
                payload ?: return@collect
                val myRoll = _currentUser.value?.rollNumber ?: return@collect
                if (payload.studentRoll == myRoll) {
                    val rec = AttendanceRecord(payload.date, payload.studentRoll,
                        payload.studentName, payload.subject, payload.teacherRoll, payload.present)
                    db.attendanceDao().insert(rec.toEntity())
                }
            }
        }
    }

    // ── Attendance ────────────────────────────────────────────────────────────

    private fun loadStudentAttendance(roll: String) {
        viewModelScope.launch {
            db.attendanceDao().forStudent(roll).collect { list ->
                _attendanceRecords.value = list.map { it.toModel() }
            }
        }
    }

    fun loadTeacherAttendance(subject: String) {
        viewModelScope.launch {
            db.attendanceDao().forTeacher(today, subject).collect { list ->
                _attendanceRecords.value = list.map { it.toModel() }
            }
        }
    }

    fun markAttendance(studentRoll: String, studentName: String, subject: String, present: Boolean) {
        val u = _currentUser.value ?: return
        viewModelScope.launch {
            val rec = AttendanceRecord(today, studentRoll, studentName, subject, u.rollNumber, present)
            db.attendanceDao().insert(rec.toEntity())
            BluetoothService.sendAtt(AttPayload(studentRoll, studentName, subject, today, present, u.rollNumber))
            if (!_subjects.value.contains(subject))
                _subjects.value = (_subjects.value + subject).sorted()
            loadTeacherAttendance(subject)
        }
    }

    fun addSubject(name: String) {
        if (name.isNotBlank() && !_subjects.value.contains(name))
            _subjects.value = (_subjects.value + name.trim()).sorted()
    }
}
