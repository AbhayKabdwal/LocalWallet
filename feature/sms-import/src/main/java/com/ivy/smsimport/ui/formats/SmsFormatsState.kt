package com.ivy.smsimport.ui.formats

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class FormatRow(
    val id: String,
    val label: String,
    val sender: String?,
    val direction: String,
    val enabled: Boolean,
    /** Why this format cannot be used, if it cannot - shown rather than hidden. */
    val problem: String?,
    val preview: String,
)

@Immutable
data class SmsFormatsState(
    val formats: ImmutableList<FormatRow>,
    val unrecognisedCount: Int,
    val counting: Boolean,
)

sealed interface SmsFormatsEvent {
    data class SetEnabled(val id: String, val enabled: Boolean) : SmsFormatsEvent
    data class Delete(val id: String) : SmsFormatsEvent
    data object Refresh : SmsFormatsEvent
}
