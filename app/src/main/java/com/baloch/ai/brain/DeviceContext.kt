package com.baloch.ai.brain

/**
 * Snapshot of live device state handed to the brain with every input,
 * so replies like "battery is at 42%" need no extra tool call.
 */
data class DeviceContext(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val timeString: String,
    val dateString: String,
    val language: BalochLanguage,
)
