package com.baloch.ai.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.baloch.ai.callerid.PrefixTables
import com.baloch.ai.callerid.SpamShield
import com.baloch.ai.ui.components.BackChevron
import com.baloch.ai.ui.theme.BalochAmber
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochRed
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standalone Caller ID screen — holographic cyan on deep black.
 * Shows recent UNKNOWN incoming callers with their offline detection
 * (network / city / spam badge) plus Block / Report-as-spam actions and
 * the "set as call screener" row.
 *
 * The screen is deliberately dumb: the parent loads [entries] (off the
 * main thread, via [loadRecentUnknownCallers]) and wires every action
 * through [CallLogCallbacks].
 */
data class UnknownCallerEntry(
    val number: String,
    val displayNumber: String,
    val dateMs: Long,
    val network: String?,
    val city: String?,
    val isSpam: Boolean,
    val isBlocked: Boolean,
)

interface CallLogCallbacks {
    fun onBlock(number: String)
    fun onUnblock(number: String)
    fun onReportSpam(number: String)
    fun onDeepLookup(number: String)
    fun onBack()

    /**
     * Parent wiring (Activity):
     * ```
     * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
     *     val rm = getSystemService(RoleManager::class.java)
     *     if (!rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
     *         rm.requestRole(
     *             RoleManager.ROLE_CALL_SCREENING, this, mainExecutor,
     *             java.util.function.Consumer { held -> /* refresh isScreenerActive */ },
     *         )
     *     }
     * }
     * // else: API 26–28 has no ROLE_CALL_SCREENING — Baloch silently uses
     * // the no-role Path B ring watcher instead (see CallerIdService KDoc).
     * ```
     */
    fun onRequestScreenerRole()
}

/**
 * Loads the last [limit] incoming calls, keeps only unknown callers
 * (not in contacts), deduped to the latest call per number, each annotated
 * with [PrefixTables]/[SpamShield] results. Returns emptyList() without
 * READ_CALL_LOG — the parent shows the permission prompt instead.
 *
 * Call off the main thread.
 */
fun loadRecentUnknownCallers(
    context: Context,
    limit: Int = 20,
): List<UnknownCallerEntry> {
    if (ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALL_LOG
        ) != PackageManager.PERMISSION_GRANTED
    ) return emptyList()
    val hasContacts = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    val out = LinkedHashMap<String, UnknownCallerEntry>()
    val cursor = context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.TYPE),
        "${CallLog.Calls.TYPE} = ?",
        arrayOf(CallLog.Calls.INCOMING_TYPE.toString()),
        "${CallLog.Calls.DATE} DESC",
    ) ?: return emptyList()
    cursor.use {
        val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
        val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
        while (it.moveToNext() && out.size < limit) {
            val raw = it.getString(numIdx) ?: continue
            val normalized = PrefixTables.normalize(raw)
            if (normalized.length < 7 || out.containsKey(normalized)) continue
            if (hasContacts && isKnownContact(context, normalized)) continue
            out[normalized] = UnknownCallerEntry(
                number = normalized,
                displayNumber = raw,
                dateMs = it.getLong(dateIdx),
                network = PrefixTables.networkOf(normalized),
                city = PrefixTables.cityOf(normalized),
                isSpam = SpamShield.isSpam(normalized),
                isBlocked = SpamShield.isBlocked(context, normalized),
            )
        }
    }
    return out.values.toList()
}

private fun isKnownContact(context: Context, normalized: String): Boolean {
    val uri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(normalized),
    )
    val c = context.contentResolver.query(
        uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null
    ) ?: return false
    c.use { return it.moveToFirst() }
}

// ----------------------------------------------------------------------

@Composable
fun CallLogScreen(
    entries: List<UnknownCallerEntry>,
    isScreenerActive: Boolean,
    hasCallLogPermission: Boolean,
    callbacks: CallLogCallbacks,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BalochBlack)
            .padding(16.dp)
    ) {
        // Header — 48dp RTL-mirrored back chevron (see components/NavChrome).
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackChevron(onBack = callbacks::onBack)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "◈ CALLER ID",
                color = BalochCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Unknown callers, identified offline — network, city, spam.",
            color = BalochTextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))

        // Screener role row
        ScreenerRow(
            isScreenerActive = isScreenerActive,
            onRequest = callbacks::onRequestScreenerRole,
        )
        Spacer(Modifier.height(12.dp))

        if (!hasCallLogPermission) {
            Text(
                text = "Call-log permission is off — grant it to see unknown callers here, sir.",
                color = BalochAmber,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            return
        }
        if (entries.isEmpty()) {
            Text(
                text = "No unknown incoming calls in the recent log. Clean, sir.",
                color = BalochTextDim,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries, key = { it.number }) { entry ->
                CallerCard(entry = entry, callbacks = callbacks)
            }
        }
    }
}

@Composable
private fun ScreenerRow(
    isScreenerActive: Boolean,
    onRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BalochPanel)
            .border(1.dp, BalochPanelBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Call screener",
                    color = BalochText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isScreenerActive)
                        "Active — Baloch announces every call."
                    else
                        "Off — grant the role for full number detection.",
                    color = BalochTextDim,
                    fontSize = 12.sp,
                )
            }
            if (!isScreenerActive) {
                Text(
                    text = "ENABLE",
                    color = BalochBlack,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BalochCyan)
                        .clickable(onClick = onRequest)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                Text(text = "●", color = BalochCyan, fontSize = 16.sp)
            }
        }
        Text(
            // Honest note: the role only exists on API 29+.
            text = "Android 9+: system role grant. Android 8.x: no role exists — " +
                "Baloch uses the no-role ring watcher automatically.",
            color = BalochTextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun CallerCard(
    entry: UnknownCallerEntry,
    callbacks: CallLogCallbacks,
) {
    val dateFmt = SimpleDateFormat("d MMM, h:mm a", Locale.US)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BalochPanel)
            .border(1.dp, BalochPanelBorder, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayNumber,
                    color = BalochText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dateFmt.format(Date(entry.dateMs)),
                    color = BalochTextDim,
                    fontSize = 12.sp,
                )
            }
            if (entry.isSpam) Badge("SPAM", BalochRed)
            if (entry.isBlocked) {
                Spacer(Modifier.width(6.dp))
                Badge("BLOCKED", BalochAmber)
            }
        }
        Spacer(Modifier.height(8.dp))
        // Detection line
        val detail = listOfNotNull(
            entry.network?.let { "$it mobile" },
            entry.city?.let { "$it landline" },
        ).joinToString(" · ").ifEmpty { "Could not place this number" }
        Text(
            text = detail,
            color = BalochTextDim,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionLink(
                label = if (entry.isBlocked) "Unblock" else "Block",
                onClick = {
                    if (entry.isBlocked) callbacks.onUnblock(entry.number)
                    else callbacks.onBlock(entry.number)
                },
            )
            if (!entry.isSpam) {
                ActionLink(
                    label = "Report spam",
                    onClick = { callbacks.onReportSpam(entry.number) },
                )
            }
            ActionLink(
                label = "Look up",
                onClick = { callbacks.onDeepLookup(entry.number) },
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun ActionLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            color = BalochCyan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
