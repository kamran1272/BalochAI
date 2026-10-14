package com.baloch.ai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Hosts [MemoryScreen], wiring it to [BalochViewModel]'s memory flows.
 *
 * Honest limits of this host: export rides the viewModel's document-picker
 * request flow (the launchers live in MainScreen) and has no direct trigger
 * here, so the export button is a no-op until that is wired; the PIN gate
 * fails closed (verifyPin returns false) until a real PIN prompt exists —
 * "forget everything" cannot run from this host yet.
 */
@Composable
fun MemoryScreenHost(viewModel: BalochViewModel) {
    val facts by viewModel.memoryFacts.collectAsState()
    val entities by viewModel.memoryEntities.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val pinGate = remember {
        object : PinGate {
            override suspend fun verifyPin(): Boolean = false
        }
    }
    MemoryScreen(
        facts = facts,
        entities = entities,
        searchQuery = searchQuery,
        onSearchChange = { searchQuery = it },
        onDeleteFact = viewModel::deleteFact,
        onDeleteEntity = viewModel::deleteEntity,
        onForgetAll = viewModel::forgetAllMemories,
        onExport = { },
        onImport = viewModel::requestImport,
        pinGate = pinGate,
        onBack = viewModel::goBack,
    )
}
