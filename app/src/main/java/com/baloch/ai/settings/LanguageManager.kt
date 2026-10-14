package com.baloch.ai.settings

import android.content.Context
import android.content.SharedPreferences
import com.baloch.ai.brain.BalochLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the user's language choice (Urdu / Hindi / English).
 * Simple SharedPreferences — no extra dependencies.
 */
class LanguageManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(
        BalochLanguage.fromCode(prefs.getString(KEY_LANGUAGE, BalochLanguage.ENGLISH.code)!!)
    )
    val language: StateFlow<BalochLanguage> = _language.asStateFlow()

    fun current(): BalochLanguage = _language.value

    fun setLanguage(lang: BalochLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, lang.code).apply()
        _language.value = lang
    }

    companion object {
        private const val PREFS_NAME = "baloch_prefs"
        private const val KEY_LANGUAGE = "language"
    }
}
