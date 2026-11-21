package com.ivy.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.design.system.colors.PaletteResolver
import com.ivy.domain.theming.ColorHex
import com.ivy.domain.theming.Emphasis
import com.ivy.domain.theming.Palette
import com.ivy.domain.theming.ThemeMode
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel

@Composable
fun AppearanceScreenImpl(
    viewModel: AppearanceViewModel = screenScopedViewModel(),
) {
    AppearanceUi(
        state = viewModel.uiState(),
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceUi(
    state: AppearanceState,
    onEvent: (AppearanceEvent) -> Unit,
) {
    val nav = navigation()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Appearance", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { Preview(state) }
            item { ModeSection(state, onEvent) }
            if (state.supportsWallpaperColors) {
                item { WallpaperSection(state, onEvent) }
            }
            item { PresetSection(state, onEvent) }
            item { AccentSection(state, onEvent) }
            item { EmphasisSection(state, onEvent) }
            item { ShareSection(state, onEvent) }
            item {
                OutlinedButton(
                    onClick = { onEvent(AppearanceEvent.Reset) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Reset to default") }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    state.shareCode?.let { code ->
        ShareCodeDialog(
            code = code,
            onImport = { onEvent(AppearanceEvent.ImportPalette(it)) },
            onDismiss = { onEvent(AppearanceEvent.DismissShareCode) },
        )
    }
}

/**
 * A miniature of the Home balance card, rendered with the palette currently
 * being edited.
 *
 * Without this the user is picking colours blind - it is the most useful
 * element on the screen.
 */
@Composable
private fun Preview(state: AppearanceState) {
    val colors = remember(state.theme) {
        PaletteResolver.resolve(state.theme, systemInDark = false)
    }
    SectionLabel("Preview")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(16.dp),
        ) {
            Text(
                text = "Balance",
                color = colors.gray,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "12,480.50",
                color = colors.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Income  +2,100", colors.income.primary, colors.onIncome)
                Pill("Expenses  -840", colors.expense.primary, colors.onExpense)
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(colors.primary.primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Add transaction",
                    color = colors.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Pill(text: String, background: Color, content: Color) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text = text, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ModeSection(state: AppearanceState, onEvent: (AppearanceEvent) -> Unit) {
    SectionLabel("Theme")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ThemeMode.entries.toList()) { mode ->
            FilterChip(
                selected = state.theme.mode == mode,
                onClick = { onEvent(AppearanceEvent.SetMode(mode)) },
                label = { Text(mode.label()) },
            )
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.Light -> "Light"
    ThemeMode.Dark -> "Dark"
    ThemeMode.Amoled -> "AMOLED"
    ThemeMode.SystemAuto -> "Follow system"
}

@Composable
private fun WallpaperSection(state: AppearanceState, onEvent: (AppearanceEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("Use wallpaper colours", fontWeight = FontWeight.Bold)
            Text(
                text = "Material You. Overrides the accent colours below.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.fillMaxWidth(0.1f))
        Switch(
            checked = state.theme.useWallpaperColors,
            onCheckedChange = { onEvent(AppearanceEvent.SetWallpaperColors(it)) },
        )
    }
}

@Composable
private fun PresetSection(state: AppearanceState, onEvent: (AppearanceEvent) -> Unit) {
    SectionLabel("Presets")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(state.presets) { preset ->
            PresetSwatch(
                palette = preset,
                selected = state.theme.palette.id == preset.id,
                onClick = { onEvent(AppearanceEvent.SetPreset(preset)) },
            )
        }
    }
}

@Composable
private fun PresetSwatch(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(palette.primary.argb.toInt()), CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(palette.name, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AccentSection(state: AppearanceState, onEvent: (AppearanceEvent) -> Unit) {
    SectionLabel("Colours")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ColorRow("Primary", state.theme.palette.primary) {
            onEvent(AppearanceEvent.SetPrimary(it))
        }
        ColorRow("Secondary", state.theme.palette.secondary) {
            onEvent(AppearanceEvent.SetSecondary(it))
        }
        ColorRow("Income", state.theme.palette.income) {
            onEvent(AppearanceEvent.SetIncome(it))
        }
        ColorRow("Expense", state.theme.palette.expense) {
            onEvent(AppearanceEvent.SetExpense(it))
        }
    }
}

/**
 * Hex entry plus a swatch. Accepts `RRGGBB` or `AARRGGBB`, with or without `#`,
 * and only commits once the value parses - so a half-typed colour never
 * repaints the app.
 */
@Composable
private fun ColorRow(label: String, value: ColorHex, onChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.argb.toHexString()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(value.argb.toInt()), RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw
                parseHexColor(raw)?.let(onChange)
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Long.toHexString(): String =
    "#" + java.lang.Long.toHexString(this).padStart(8, '0').uppercase()

internal fun parseHexColor(raw: String): Long? {
    val cleaned = raw.removePrefix("#").trim()
    if (cleaned.length != 6 && cleaned.length != 8) return null
    val parsed = cleaned.toLongOrNull(radix = 16) ?: return null
    // 6 digits means "fully opaque"
    return if (cleaned.length == 6) parsed or 0xFF000000L else parsed
}

@Composable
private fun EmphasisSection(state: AppearanceState, onEvent: (AppearanceEvent) -> Unit) {
    SectionLabel("Depth")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Emphasis.entries.toList()) { emphasis ->
            FilterChip(
                selected = state.theme.emphasis == emphasis,
                onClick = { onEvent(AppearanceEvent.SetEmphasis(emphasis)) },
                label = { Text(emphasis.name) },
            )
        }
    }
    Text(
        text = state.theme.emphasis.describe(),
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun Emphasis.describe(): String = when (this) {
    Emphasis.Flat -> "No coloured glow, flat fills. The calmest option."
    Emphasis.Subtle -> "Softened glow and shorter gradients."
    Emphasis.Vivid -> "Full glow and gradients, as the original app."
}

@Composable
private fun ShareSection(state: AppearanceState, onEvent: (AppearanceEvent) -> Unit) {
    OutlinedButton(
        onClick = { onEvent(AppearanceEvent.ShowShareCode) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Export / import theme") }
}

@Composable
private fun ShareCodeDialog(
    code: String,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(code) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share theme") },
        text = {
            Column {
                Text(
                    text = "Copy this to share your palette, or paste someone " +
                        "else's and apply it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onImport(input)
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
