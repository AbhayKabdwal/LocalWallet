package com.ivy.smsimport.ui.formats

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class MessageCandidate(
    val sourceId: String,
    val sender: String,
    val body: String,
    val received: String,
)

/**
 * What the format currently reads out of the example message.
 *
 * The preview is the whole safety net of this screen: a template is a pattern,
 * and nobody should be asked to trust one they have not seen work. Showing the
 * extracted amount and account next to the message they came from is the
 * difference between confirming a format and hoping.
 */
@Immutable
sealed interface FormatPreview {
    data object NeedsExample : FormatPreview

    data class Problem(val message: String) : FormatPreview

    data class Reads(
        val amount: String,
        val direction: String,
        val account: String?,
        val balance: String?,
        val payee: String?,
        val reference: String?,
    ) : FormatPreview
}

@Immutable
data class TeachFormatState(
    val loading: Boolean,
    val editing: Boolean,
    val candidates: ImmutableList<MessageCandidate>,
    val permissionGranted: Boolean,
    val label: String,
    val senderFragment: String,
    val direction: String,
    val template: String,
    val example: String,
    val preview: FormatPreview,
    val saveable: Boolean,
    val saved: Boolean,
    val error: String?,
)

sealed interface TeachFormatEvent {
    /** Sent once by the screen, carrying the format it was opened for. */
    data class Start(val templateId: String?) : TeachFormatEvent

    data object Refresh : TeachFormatEvent
    data class Pick(val sourceId: String) : TeachFormatEvent
    data object WriteItMyself : TeachFormatEvent
    data object BackToMessages : TeachFormatEvent
    data class LabelChanged(val value: String) : TeachFormatEvent
    data class SenderChanged(val value: String) : TeachFormatEvent
    data class TemplateChanged(val value: String) : TeachFormatEvent
    data class ExampleChanged(val value: String) : TeachFormatEvent
    data class DirectionChanged(val value: String) : TeachFormatEvent
    data class InsertToken(val token: String) : TeachFormatEvent
    data object Save : TeachFormatEvent
    data object DismissError : TeachFormatEvent
}
