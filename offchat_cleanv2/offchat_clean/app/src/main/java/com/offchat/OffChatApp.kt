package com.offchat

import android.app.Application
import android.util.Log
import com.offchat.data.db.AdminSeeder
import com.offchat.data.db.OffChatDatabase
import kotlinx.coroutines.*

class OffChatApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try {
                val db = OffChatDatabase.get(applicationContext)
                if (db.userDao().count() == 0) {
                    db.userDao().insertAll(AdminSeeder.users())
                    Log.d("OffChatApp", "DB seeded")
                }
            } catch (e: Exception) {
                Log.e("OffChatApp", "Seed failed: ${e.message}")
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        scope.cancel()
    }
}
