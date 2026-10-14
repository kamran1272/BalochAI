package com.baloch.ai.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DUAL MODE — the headline Baloch feature.
 *
 * MANUAL: Baloch is completely passive. No proactive alerts, no background
 * interruptions, no auto-briefings. It ONLY responds when Kamran speaks or
 * types. Battery guardian, proactive summaries and routine auto-triggers
 * are all paused.
 *
 * JARVIS: full autonomous mode. Battery guardian alerts fire, briefings
 * are offered proactively, routines can auto-trigger — Baloch "speaks up"
 * when something needs attention.
 *
 * Explicit user orders (scheduled tasks, direct commands) work identically
 * in BOTH modes — mode only gates *unasked-for* proactivity.
 */
enum class BalochMode { MANUAL, JARVIS }

private val Context.modeDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "baloch_mode")

class ModeManager(private val context: Context) {

    private val key = stringPreferencesKey("mode")

    val modeFlow: Flow<BalochMode> = context.modeDataStore.data.map { prefs ->
        runCatching { BalochMode.valueOf(prefs[key] ?: BalochMode.MANUAL.name) }
            .getOrDefault(BalochMode.MANUAL)
    }

    suspend fun setMode(mode: BalochMode) {
        context.modeDataStore.edit { it[key] = mode.name }
    }

    suspend fun current(): BalochMode = modeFlow.first()

    suspend fun isJarvis(): Boolean = current() == BalochMode.JARVIS
}

/**
 * Suspend gate for background workers (CoroutineWorker / receivers).
 * Returns false in MANUAL mode — the worker should stand down quietly.
 */
suspend fun isJarvisMode(context: Context): Boolean =
    ModeManager(context).isJarvis()
