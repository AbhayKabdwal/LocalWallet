package com.ivy.smsimport.ui.formats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.navigation.SmsTeachFormatScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.sms.SmsTemplate
import com.ivy.sms.TemplateToken

@Composable
fun SmsTeachFormatScreenImpl(
    screen: SmsTeachFormatScreen,
    viewModel: TeachFormatViewModel = screenScopedViewModel(),
) {
    LaunchedEffect(screen.templateId) {
        viewModel.onEvent(TeachFormatEvent.Start(screen.templateId))
    }
    TeachFormatUi(state = viewModel.uiState(), onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeachFormatUi(
    state: TeachFormatState,
    onEvent: (TeachFormatEvent) -> Unit,
) {
    val nav = navigation()
    LaunchedEffect(state.saved) {
        if (state.saved) nav.onBackPressed()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (state.editing) "Describe the format" else "Pick a message",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.editing) {
                                onEvent(TeachFormatEvent.BackToMessages)
                            } else {
                                nav.onBackPressed()
                            }
                        },
                    ) {
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
                state.loading -> Loading()
                state.editing -> Editor(state = state, onEvent = onEvent)
                else -> MessagePicker(state = state, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * The messages the app could not read.
 *
 * Starting from a real message rather than an empty text field is the whole
 * difference in effort: the draft that comes back is usually already correct,
 * and the user is reviewing rather than authoring.
 */
@Composable
private fun MessagePicker(
    state: TeachFormatState,
    onEvent: (TeachFormatEvent) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                text = if (state.candidates.isEmpty()) {
                    "No unreadable bank messages found on this phone."
                } else {
                    "These bank messages could not be read. Pick one that is a " +
                        "payment or a deposit, and the app will work out the format."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        items(state.candidates, key = { it.sourceId }) { candidate ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEvent(TeachFormatEvent.Pick(candidate.sourceId)) },
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row {
                        Text(
                            text = candidate.sender,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = candidate.received,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(text = candidate.body, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Column {
                TextButton(
                    onClick = { onEvent(TeachFormatEvent.WriteItMyself) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Paste a message instead") }
                if (state.candidates.isNotEmpty()) {
                    TextButton(
                        onClick = { onEvent(TeachFormatEvent.Refresh) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Look again") }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Suppress("LongMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editor(
    state: TeachFormatState,
    onEvent: (TeachFormatEvent) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                text = "Below is your bank's message with the parts that change " +
                    "replaced by markers. Fix any that are wrong - the rest of the " +
                    "wording is what identifies this format.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item {
            OutlinedTextField(
                value = state.template,
                onValueChange = { onEvent(TeachFormatEvent.TemplateChanged(it)) },
                label = { Text("Format") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { TokenLegend(onEvent = onEvent) }

        item { PreviewCard(state.preview) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.direction == SmsTemplate.DIRECTION_EXPENSE,
                    onClick = {
                        onEvent(
                            TeachFormatEvent.DirectionChanged(SmsTemplate.DIRECTION_EXPENSE),
                        )
                    },
                    label = { Text("Money out") },
                )
                FilterChip(
                    selected = state.direction == SmsTemplate.DIRECTION_INCOME,
                    onClick = {
                        onEvent(
                            TeachFormatEvent.DirectionChanged(SmsTemplate.DIRECTION_INCOME),
                        )
                    },
                    label = { Text("Money in") },
                )
            }
        }

        item {
            OutlinedTextField(
                value = state.example,
                onValueChange = { onEvent(TeachFormatEvent.ExampleChanged(it)) },
                label = { Text("Example message") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedTextField(
                value = state.senderFragment,
                onValueChange = { onEvent(TeachFormatEvent.SenderChanged(it)) },
                label = { Text("Sender contains") },
                singleLine = true,
                supportingText = {
                    Text(
                        "Part of the sender's name, such as BOB. Leave empty to " +
                            "accept any sender.",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedTextField(
                value = state.label,
                onValueChange = { onEvent(TeachFormatEvent.LabelChanged(it)) },
                label = { Text("Name this format") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(TeachFormatEvent.Save) },
                    enabled = state.saveable,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save format and scan again") }

                if (!state.saveable) {
                    Text(
                        text = "The format has to read the example before it can " +
                            "be saved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedButton(
                    onClick = { onEvent(TeachFormatEvent.BackToMessages) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Pick another message") }

                state.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokenLegend(onEvent: (TeachFormatEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Tap to add a marker at the end, then move it into place:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TemplateToken.entries.forEach { token ->
                AssistChip(
                    onClick = { onEvent(TeachFormatEvent.InsertToken(token.canonical)) },
                    label = { Text("{${token.canonical}}") },
                )
            }
        }
        Text(
            text = TemplateToken.entries.joinToString("\n") {
                "{${it.canonical}} - ${it.description}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewCard(preview: FormatPreview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "What this reads",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            when (preview) {
                FormatPreview.NeedsExample -> Text(
                    text = "Paste one of the bank's messages into the example box " +
                        "to check the format against it.",
                    style = MaterialTheme.typography.bodySmall,
                )

                is FormatPreview.Problem -> Text(
                    text = preview.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                is FormatPreview.Reads -> Column {
                    PreviewLine("Amount", preview.amount)
                    PreviewLine("Direction", preview.direction)
                    PreviewLine("Account ending", preview.account)
                    PreviewLine("Balance afterwards", preview.balance)
                    PreviewLine("Paid to / from", preview.payee)
                    PreviewLine("Reference", preview.reference)
                }
            }
        }
    }
}

@Composable
private fun PreviewLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
