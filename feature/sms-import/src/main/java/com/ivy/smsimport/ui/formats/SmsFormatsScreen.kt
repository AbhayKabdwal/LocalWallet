package com.ivy.smsimport.ui.formats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.navigation.SmsTeachFormatScreen
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import com.ivy.sms.SmsTemplate

@Composable
fun SmsFormatsScreenImpl(
    viewModel: SmsFormatsViewModel = screenScopedViewModel(),
) {
    SmsFormatsUi(state = viewModel.uiState(), onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmsFormatsUi(
    state: SmsFormatsState,
    onEvent: (SmsFormatsEvent) -> Unit,
) {
    val nav = navigation()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Message formats", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.onBackPressed() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Every bank words its messages differently. If yours are " +
                            "being ignored, show the app one of them and it will read " +
                            "the rest.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!state.counting && state.unrecognisedCount > 0) {
                        Text(
                            text = if (state.unrecognisedCount == 1) {
                                "1 bank message on this phone cannot be read yet."
                            } else {
                                "${state.unrecognisedCount} bank messages on this " +
                                    "phone cannot be read yet."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { nav.navigateTo(SmsTeachFormatScreen()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Teach a new format") }
                }
            }

            if (state.formats.isEmpty()) {
                item {
                    Text(
                        text = "No formats of your own yet. The built-in ones still apply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.formats, key = { it.id }) { format ->
                FormatCard(
                    format = format,
                    onOpen = { nav.navigateTo(SmsTeachFormatScreen(format.id)) },
                    onEvent = onEvent,
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun FormatCard(
    format: FormatRow,
    onOpen: () -> Unit,
    onEvent: (SmsFormatsEvent) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = format.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = listOfNotNull(
                            format.sender?.let { "from $it" } ?: "any sender",
                            if (format.direction == SmsTemplate.DIRECTION_INCOME) {
                                "money in"
                            } else {
                                "money out"
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = format.enabled,
                    onCheckedChange = { onEvent(SmsFormatsEvent.SetEnabled(format.id, it)) },
                )
                IconButton(onClick = { onEvent(SmsFormatsEvent.Delete(format.id)) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete format")
                }
            }

            Text(
                text = format.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // A format that stopped compiling is shown with its reason rather
            // than silently ignored: the user needs to know it is not working.
            format.problem?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
