package com.offchat.data.model

enum class UserRole { STUDENT, TEACHER }

data class User(
    val rollNumber: String,
    val name: String,
    val password: String,
    val role: UserRole,
    val year: String,
    val branch: String,
    val department: String
)

data class Peer(
    val rollNumber: String,
    val name: String,
    val role: UserRole,
    val year: String,
    val branch: String,
    val department: String,
    val deviceAddress: String = ""
)

data class Message(
    val id: String,
    val senderRoll: String,
    val senderName: String,
    val receiverRoll: String,  // "ALL" = global, "CLASS_X" = channel, else direct
    val content: String,
    val timestamp: Long,
    val isAttendance: Boolean = false
)

data class AttendanceRecord(
    val date: String,
    val studentRoll: String,
    val studentName: String,
    val subject: String,
    val markedByRoll: String,
    val present: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
