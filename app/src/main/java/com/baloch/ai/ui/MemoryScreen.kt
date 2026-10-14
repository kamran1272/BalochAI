package com.baloch.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baloch.ai.memory.room.EntityRecord
import com.baloch.ai.memory.room.FactEntity
import com.baloch.ai.memory.room.Tier
import com.baloch.ai.ui.components.BackChevron
import com.baloch.ai.ui.theme.BalochAmber
import com.baloch.ai.ui.theme.BalochBlack
import com.baloch.ai.ui.theme.BalochCyan
import com.baloch.ai.ui.theme.BalochCyanDim
import com.baloch.ai.ui.theme.BalochPanel
import com.baloch.ai.ui.theme.BalochPanelBorder
import com.baloch.ai.ui.theme.BalochRed
import com.baloch.ai.ui.theme.BalochText
import com.baloch.ai.ui.theme.BalochTextDim
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * TOTAL MEMORY screen — holographic cyan. Browse / search / delete facts and
 * known people, with tier icons (📌 Permanent vs 🕐 Temporary + "expires in
 * X days"), a PIN-gated "Forget everything" danger zone, and encrypted
 * backup export/import.
 *
 * STATE CONTRACT (parent wires): the parent owns the data (facts/entities
 * from repo.factsFlow()/repo.entities() collected in its ViewModel), the
 * search query, and all callbacks. This composable owns only UI chrome state
 * (selected tab, tier filter, confirm dialogs). Backup buttons expose
 * [onExport]/[onImport] — the parent hooks ActivityResultContracts
 * CreateDocument/OpenDocument to those and calls MemoryBackup.exportToFile /
 * importFromFile with the returned Uri.
 */

/**
 * Seam for the "Forget everything" PIN gate. The parent wires the real
 * PinManager here (voice/PIN module). Document: verifyPin() must return true
 * only after the user passes PIN verification; on false the wipe is aborted.
 */
interface PinGate {
    suspend fun verifyPin(): Boolean
}

private val CATEGORIES = listOf("All", "personal", "preference", "people", "work", "health", "other")

private enum class TierFilter { ALL, PINNED, TEMP }

@Composable
fun MemoryScreen(
    facts: List<FactEntity>,
    entities: List<EntityRecord>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onDeleteFact: (Long) -> Unit,
    onDeleteEntity: (Long) -> Unit,
    onForgetAll: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    pinGate: PinGate,
    /**
     * Optional back navigation. When null (default) no back affordance is
     * shown — the parent is expected to host this screen where the system
     * back gesture suffices. Never a dead button: no callback, no chrome.
     */
    onBack: (() -> Unit)? = null,
) {
    var category by remember { mutableStateOf("All") }
    var tierFilter by remember { mutableStateOf(TierFilter.ALL) }
    var confirmStage by remember { mutableStateOf(0) } // 0 none, 1 first confirm, 2 PIN passed→final
    var pinFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val query = searchQuery.trim().lowercase()
    val visibleFacts = facts.filter { f ->
        (category == "All" || f.category == category) &&
            (tierFilter == TierFilter.ALL ||
                (tierFilter == TierFilter.PINNED && f.tier == Tier.PERMANENT) ||
                (tierFilter == TierFilter.TEMP && f.tier == Tier.TEMPORARY)) &&
            (query.isEmpty() ||
                f.key.lowercase().contains(query) || f.value.lowercase().contains(query) ||
                f.category.lowercase().contains(query))
    }
    val visibleEntities = entities.filter { e ->
        query.isEmpty() || e.name.lowercase().contains(query) || e.notes.lowercase().contains(query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BalochBlack)
            .imePadding()
            .padding(16.dp)
    ) {
        if (onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackChevron(onBack = onBack)
                Spacer(Modifier.width(8.dp))
                Text(
                    "TOTAL MEMORY",
                    color = BalochCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                "TOTAL MEMORY",
                color = BalochCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "${facts.count { it.tier == Tier.PERMANENT }} 📌 pinned · " +
                "${facts.count { it.tier == Tier.TEMPORARY }} 🕐 temporary · " +
                "${entities.size} people",
            color = BalochTextDim,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search memory…", color = BalochTextDim) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = BalochText,
                unfocusedTextColor = BalochText,
                focusedBorderColor = BalochCyan,
                unfocusedBorderColor = BalochPanelBorder,
                cursorColor = BalochCyan,
            ),
        )
        Spacer(Modifier.height(8.dp))

        // Category tabs
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CATEGORIES) { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(cat, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BalochCyanDim,
                        selectedLabelColor = BalochText,
                        containerColor = BalochPanel,
                        labelColor = BalochTextDim,
                    ),
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // Tier filter — LazyRow: the three chips overflow a 320dp screen
        // as a fixed Row (esp. at 1.3x font scale).
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TierFilter.entries) { tf ->
                val label = when (tf) {
                    TierFilter.ALL -> "All tiers"
                    TierFilter.PINNED -> "📌 Pinned"
                    TierFilter.TEMP -> "🕐 Temporary"
                }
                FilterChip(
                    selected = tierFilter == tf,
                    onClick = { tierFilter = tf },
                    label = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BalochCyanDim,
                        selectedLabelColor = BalochText,
                        containerColor = BalochPanel,
                        labelColor = BalochTextDim,
                    ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visibleFacts, key = { "f${it.id}" }) { fact ->
                FactRow(fact = fact, onDelete = { onDeleteFact(fact.id) })
            }
            if (visibleEntities.isNotEmpty()) {
                item {
                    Text(
                        "PEOPLE & PLACES",
                        color = BalochCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(visibleEntities, key = { "e${it.id}" }) { entity ->
                EntityRow(entity = entity, onDelete = { onDeleteEntity(entity.id) })
            }
            if (visibleFacts.isEmpty() && visibleEntities.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet, sir. Tell me about yourself — I'll remember. 📝",
                        color = BalochTextDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BalochCyan),
            ) { Text("⬆ Backup", fontFamily = FontFamily.Monospace) }
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BalochCyan),
            ) { Text("⬇ Restore", fontFamily = FontFamily.Monospace) }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { confirmStage = 1; pinFailed = false },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = BalochRed.copy(alpha = 0.15f),
                contentColor = BalochRed,
            ),
        ) { Text("🗑 Forget everything", fontFamily = FontFamily.Monospace) }
    }

    // ---- Double-confirm + PIN gate ----
    if (confirmStage == 1) {
        AlertDialog(
            onDismissRequest = { confirmStage = 0 },
            title = { Text("Forget EVERYTHING?", color = BalochRed, fontFamily = FontFamily.Monospace) },
            text = {
                Text(
                    "This wipes all ${facts.size} facts, all conversations and all people — permanently. No undo.",
                    color = BalochText,
                    fontFamily = FontFamily.Monospace,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // Stage 2: PIN verification via the parent's PinGate seam.
                    scope.launch {
                        val ok = try {
                            pinGate.verifyPin()
                        } catch (_: Exception) {
                            false
                        }
                        if (ok) {
                            confirmStage = 2
                        } else {
                            pinFailed = true
                            confirmStage = 0
                        }
                    }
                }) { Text("Verify PIN", color = BalochRed, fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStage = 0 }) {
                    Text("Cancel", color = BalochTextDim, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = BalochPanel,
        )
    }
    if (pinFailed) {
        AlertDialog(
            onDismissRequest = { pinFailed = false },
            title = { Text("PIN failed", color = BalochAmber, fontFamily = FontFamily.Monospace) },
            text = {
                Text(
                    "Memory wipe aborted — your memories are safe.",
                    color = BalochText,
                    fontFamily = FontFamily.Monospace,
                )
            },
            confirmButton = {
                TextButton(onClick = { pinFailed = false }) {
                    Text("OK", color = BalochCyan, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = BalochPanel,
        )
    }
    if (confirmStage == 2) {
        AlertDialog(
            onDismissRequest = { confirmStage = 0 },
            title = { Text("Last chance, sir", color = BalochRed, fontFamily = FontFamily.Monospace) },
            text = {
                Text(
                    "PIN accepted. Tap WIPE to permanently erase all memory. This cannot be undone.",
                    color = BalochText,
                    fontFamily = FontFamily.Monospace,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStage = 0
                    onForgetAll()
                }) { Text("WIPE", color = BalochRed, fontFamily = FontFamily.Monospace) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStage = 0 }) {
                    Text("Keep my memories", color = BalochCyan, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = BalochPanel,
        )
    }
}

@Composable
private fun FactRow(fact: FactEntity, onDelete: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }
    val tierLine = if (fact.tier == Tier.PERMANENT) {
        "📌 Permanent · ${dateFmt.format(Date(fact.createdAt))}"
    } else {
        val days = fact.expiresAt?.let {
            maxOf(0, TimeUnit.MILLISECONDS.toDays(it - System.currentTimeMillis()))
        }
        "🕐 Temporary · expires in ${days ?: "?"} days"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BalochPanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, BalochPanelBorder),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${fact.key}: ${fact.value}",
                    color = BalochText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tierLine,
                    color = if (fact.tier == Tier.PERMANENT) BalochCyan else BalochTextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
                Text(
                    "“${fact.sourceQuote.take(60)}”",
                    color = BalochTextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                )
            }
            IconButton(onClick = onDelete) {
                Text("✕", color = BalochRed, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun EntityRow(entity: EntityRecord, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BalochPanel),
        border = androidx.compose.foundation.BorderStroke(1.dp, BalochPanelBorder),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "👤 ${entity.name} · ${entity.type}",
                    color = BalochText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                )
                if (entity.notes.isNotBlank()) {
                    Text(
                        entity.notes.take(80),
                        color = BalochTextDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Text("✕", color = BalochRed, fontSize = 16.sp)
            }
        }
    }
}
