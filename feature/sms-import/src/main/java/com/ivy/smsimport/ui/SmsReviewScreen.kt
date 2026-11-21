package com.ivy.smsimport.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.smsimport.domain.ScanMessagesUseCase
import java.util.UUID

@Composable
fun SmsReviewScreenImpl(
    viewModel: SmsReviewViewModel = screenScopedViewModel(),
) {
    SmsReviewUi(
        state = viewModel.uiState(),
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsReviewUi(
    state: SmsReviewState,
    onEvent: (SmsReviewEvent) -> Unit,
) {
    val nav = navigation()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (state.current != null) {
                            "${state.position} of ${state.total}"
                        } else {
                            "From messages"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.onBackPressed() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            when {
                !state.permissionGranted -> PermissionRationale(onEvent)
                state.scanning && state.current == null -> Loading()
                state.current == null -> Empty(onEvent)
                else -> ReviewCard(state = state, onEvent = onEvent)
            }
        }
    }
}

/**
 * Shown before the system dialog, so the user knows exactly what is read and
 * that nothing leaves the phone. The permission is only requested when they
 * actually open this screen.
 */
@Composable
private fun PermissionRationale(onEvent: (SmsReviewEvent) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onEvent(SmsReviewEvent.PermissionGranted)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Import from your messages", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Ivy can read the bank messages already on this phone and " +
                "suggest transactions from them, one at a time.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "• Messages are read and parsed entirely on this device\n" +
                "• Nothing is uploaded, and the app has no internet permission\n" +
                "• Message text is discarded as soon as you decide\n" +
                "• One-time passwords are never read as transactions",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { launcher.launch(Manifest.permission.READ_SMS) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Allow reading messages") }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Empty(onEvent: (SmsReviewEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing to review", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "New bank messages will show up here.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { onEvent(SmsReviewEvent.Scan) }) { Text("Scan now") }
    }
}

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewCard(
    state: SmsReviewState,
    onEvent: (SmsReviewEvent) -> Unit,
) {
    val pending = state.current ?: return

    var amount by remember(pending.sourceId) { mutableStateOf(pending.amount) }
    var title by remember(pending.sourceId) { mutableStateOf(pending.merchant.orEmpty()) }
    var accountId by remember(pending.sourceId) { mutableStateOf<UUID?>(null) }
    var categoryId by remember(pending.sourceId) { mutableStateOf<UUID?>(null) }
    var showRaw by remember(pending.sourceId) { mutableStateOf(false) }

    LaunchedEffect(state.suggestedAccountId, pending.sourceId) {
        if (accountId == null) accountId = state.suggestedAccountId
    }
    LaunchedEffect(state.suggestedCategoryId, pending.sourceId) {
        if (categoryId == null) categoryId = state.suggestedCategoryId
    }

    val isIncome = pending.direction == ScanMessagesUseCase.DIRECTION_INCOME
    val lowConfidence = pending.confidence < ScanMessagesUseCase.HIGH_CONFIDENCE

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isIncome) "INCOME" else "EXPENSE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.fillMaxWidth(0.6f))
                    if (lowConfidence) {
                        AssistChip(onClick = {}, label = { Text("Check this one") })
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount${pending.currency?.let { " ($it)" }.orEmpty()}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Picker(
                    label = "Account",
                    options = state.accounts.map { it.id to it.name },
                    selectedId = accountId,
                    onSelect = { accountId = it },
                )

                Picker(
                    label = "Category (optional)",
                    options = state.categories.map { it.id to it.name },
                    selectedId = categoryId,
                    onSelect = { categoryId = it },
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                pending.accountHint?.let {
                    Text(
                        text = "Message mentions account ending $it",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (pending.possibleDuplicate) {
                    Text(
                        text = "This looks like a transaction you already have. " +
                            "Check before adding it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                // Showing the original message is what turns the parser from a
                // black box into something the user can actually verify.
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Hide original message" else "Show original message")
                }
                if (showRaw) {
                    Text(
                        text = pending.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(
            onClick = {
                accountId?.let { id ->
                    onEvent(
                        SmsReviewEvent.Add(
                            accountId = id,
                            categoryId = categoryId,
                            amount = amount,
                            title = title.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            },
            enabled = accountId != null && amount.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add") }

        OutlinedButton(
            onClick = { onEvent(SmsReviewEvent.Skip) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Skip") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onEvent(SmsReviewEvent.IgnoreSender) }) {
                Text("Ignore this sender")
            }
            TextButton(onClick = { onEvent(SmsReviewEvent.NotATransaction) }) {
                Text("Not a transaction")
            }
        }

        if (state.remaining > 1) {
            TextButton(
                onClick = { onEvent(SmsReviewEvent.AddAllHighConfidence) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add all I'm confident about") }
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(
    label: String,
    options: List<Pair<UUID, String>>,
    selectedId: UUID?,
    onSelect: (UUID) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.first == selectedId }?.second.orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}
