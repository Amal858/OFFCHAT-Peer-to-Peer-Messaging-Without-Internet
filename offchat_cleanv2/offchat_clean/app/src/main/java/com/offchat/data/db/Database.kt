package com.offchat.data.db

import android.content.Context
import androidx.room.*
import com.offchat.data.model.*
import kotlinx.coroutines.flow.Flow

// ─── Entities ─────────────────────────────────────────────────────────────────

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val rollNumber: String,
    val name: String,
    val password: String,
    val role: String,
    val year: String,
    val branch: String,
    val department: String
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderRoll: String,
    val senderName: String,
    val receiverRoll: String,
    val content: String,
    val timestamp: Long,
    val isAttendance: Boolean = false
)

@Entity(tableName = "attendance", primaryKeys = ["date", "studentRoll", "subject"])
data class AttendanceEntity(
    val date: String,
    val studentRoll: String,
    val studentName: String,
    val subject: String,
    val markedByRoll: String,
    val present: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE rollNumber=:r AND password=:p LIMIT 1")
    suspend fun login(r: String, p: String): UserEntity?

    @Query("SELECT * FROM users WHERE rollNumber=:r LIMIT 1")
    suspend fun get(r: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(users: List<UserEntity>)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE receiverRoll=:channel ORDER BY timestamp ASC")
    fun getByChannel(channel: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE (senderRoll=:a AND receiverRoll=:b) OR (senderRoll=:b AND receiverRoll=:a) ORDER BY timestamp ASC")
    fun getDirect(a: String, b: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(msg: MessageEntity)
}

@Dao
interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: AttendanceEntity)

    @Query("SELECT * FROM attendance WHERE studentRoll=:roll ORDER BY date DESC, timestamp DESC")
    fun forStudent(roll: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE date=:date AND subject=:subject ORDER BY studentName ASC")
    fun forTeacher(date: String, subject: String): Flow<List<AttendanceEntity>>

    @Query("SELECT DISTINCT subject FROM attendance ORDER BY subject ASC")
    suspend fun subjects(): List<String>
}

// ─── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [UserEntity::class, MessageEntity::class, AttendanceEntity::class],
    version = 1, exportSchema = false
)
abstract class OffChatDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile private var INSTANCE: OffChatDatabase? = null
        fun get(ctx: Context) = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(ctx.applicationContext, OffChatDatabase::class.java, "offchat.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}

// ─── Mappers ──────────────────────────────────────────────────────────────────

fun UserEntity.toModel() = User(rollNumber, name, password,
    UserRole.valueOf(role), year, branch, department)

fun MessageEntity.toModel() = Message(id, senderRoll, senderName,
    receiverRoll, content, timestamp, isAttendance)

fun AttendanceEntity.toModel() = AttendanceRecord(
    date, studentRoll, studentName, subject, markedByRoll, present, timestamp)

fun Message.toEntity() = MessageEntity(id, senderRoll, senderName,
    receiverRoll, content, timestamp, isAttendance)

fun AttendanceRecord.toEntity() = AttendanceEntity(
    date, studentRoll, studentName, subject, markedByRoll, present, timestamp)

// ─── Seeder ───────────────────────────────────────────────────────────────────

object AdminSeeder {
    fun users(): List<UserEntity> = listOf(
        // Students
        UserEntity("22CS001","Arjun Kumar","22CS001","STUDENT","2022","CSE","Computer Science"),
        UserEntity("22CS002","Priya Nair","22CS002","STUDENT","2022","CSE","Computer Science"),
        UserEntity("22CS003","Rahul Menon","22CS003","STUDENT","2022","CSE","Computer Science"),
        UserEntity("22CS004","Anjali Das","22CS004","STUDENT","2022","CSE","Computer Science"),
        UserEntity("22CS005","Vishnu Pillai","22CS005","STUDENT","2022","CSE","Computer Science"),
        UserEntity("22EC001","Sreejith Varma","22EC001","STUDENT","2022","ECE","Electronics"),
        UserEntity("22EC002","Meera Thomas","22EC002","STUDENT","2022","ECE","Electronics"),
        UserEntity("22EC003","Arun Krishnan","22EC003","STUDENT","2022","ECE","Electronics"),
        UserEntity("22ME001","Deepak Raj","22ME001","STUDENT","2022","MECH","Mechanical"),
        UserEntity("22ME002","Lakshmi Priya","22ME002","STUDENT","2022","MECH","Mechanical"),
        // Teachers
        UserEntity("TCH001","Dr. Rajan Mathew","TCH001","TEACHER","","CSE","Computer Science"),
        UserEntity("TCH002","Prof. Sunitha Rao","TCH002","TEACHER","","ECE","Electronics"),
        UserEntity("TCH003","Dr. Anoop George","TCH003","TEACHER","","MECH","Mechanical")
    )
}
