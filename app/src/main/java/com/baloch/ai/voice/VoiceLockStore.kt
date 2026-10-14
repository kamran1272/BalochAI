package com.baloch.ai.voice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The ONE shared DataStore for all voice-lock state ("voice_lock" file:
 * voiceprint, variance, enrolled/enabled flags, PIN hash).
 *
 * CRASH FIX (Oct 14 2026): VoiceLockManager and PinManager each declared
 * their own `preferencesDataStore("voice_lock")` delegate. DataStore 1.1+
 * throws "multiple DataStores active for the same file" the moment both
 * are touched — which is exactly what happens when Voice Lock setup opens
 * (it reads enrolled/enabled via VoiceLockManager, then the PIN via
 * PinManager), killing the app. There must be exactly one delegate.
 */
internal val Context.voiceLockStore: DataStore<Preferences> by
    preferencesDataStore(name = "voice_lock")
