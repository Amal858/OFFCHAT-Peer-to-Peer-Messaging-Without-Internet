package com.offchat.data.repository

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("offchat_session")

class SessionRepository(private val ctx: Context) {
    private val KEY = stringPreferencesKey("roll")
    val roll: Flow<String?> = ctx.ds.data.map { it[KEY] }
    suspend fun save(roll: String) = ctx.ds.edit { it[KEY] = roll }
    suspend fun clear() = ctx.ds.edit { it.remove(KEY) }
}
