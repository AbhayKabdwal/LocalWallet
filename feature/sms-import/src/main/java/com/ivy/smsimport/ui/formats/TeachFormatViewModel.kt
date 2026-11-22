package com.ivy.smsimport.ui.formats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.sms.ParseResult
import com.ivy.sms.RawMessage
import com.ivy.sms.RejectReason
import com.ivy.sms.SmsRuleSet
import com.ivy.sms.SmsTemplate
import com.ivy.sms.SmsTemplateCompiler
import com.ivy.sms.SmsTemplateSuggester
import com.ivy.sms.TemplateCompileResult
import com.ivy.sms.TransactionSmsParser
import com.ivy.sms.TxnDirection
import com.ivy.smsimport.data.SmsInboxSource
import com.ivy.smsimport.data.SmsTemplateRepository
import com.ivy.smsimport.domain.ScanMessagesUseCase
import com.ivy.smsimport.domain.UnrecognisedMessagesUseCase
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@Stable
@HiltViewModel
class TeachFormatViewModel @Inject constructor(
    private val templates: SmsTemplateRepository,
    private val unrecognised: UnrecognisedMessagesUseCase,
    private val scanMessages: ScanMessagesUseCase,
    private val inbox: SmsInboxSource,
) : ComposeViewModel<TeachFormatState, TeachFormatEvent>() {

    private var loading by mutableStateOf(true)
    private var editing by mutableStateOf(false)
    private var candidates by mutableStateOf<ImmutableList<MessageCandidate>>(persistentListOf())

    private var templateId by mutableStateOf("")
    private var createdAt = 0L
    private var label by mutableStateOf("")
    private var senderFragment by mutableStateOf("")
    private var direction by mutableStateOf(SmsTemplate.DIRECTION_EXPENSE)
    private var template by mutableStateOf("")
    private var example by mutableStateOf("")
    private var currency by mutableStateOf<String?>(null)

    /**
     * The sender of the example, kept so the preview is honest: a format is
     * only matched against a sender that contains its fragment, and previewing
     * against a made-up sender would pass where the real one fails.
     */
    private var exampleSender by mutableStateOf("")

    private var saved by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    private var started = false

    @Composable
    override fun uiState(): TeachFormatState = TeachFormatState(
        loading = loading,
        editing = editing,
        candidates = candidates,
        permissionGranted = inbox.hasPermission(),
        label = label,
        senderFragment = senderFragment,
        direction = direction,
        template = template,
        example = example,
        preview = preview(),
        saveable = template.isNotBlank() && preview() is FormatPreview.Reads,
        saved = saved,
        error = error,
    )

    override fun onEvent(event: TeachFormatEvent) {
        when (event) {
            is TeachFormatEvent.Start -> start(event.templateId)
            TeachFormatEvent.Refresh -> loadCandidates()
            is TeachFormatEvent.Pick -> pick(event.sourceId)
            TeachFormatEvent.WriteItMyself -> startBlank()
            TeachFormatEvent.BackToMessages -> editing = false
            is TeachFormatEvent.LabelChanged -> label = event.value
            is TeachFormatEvent.SenderChanged -> senderFragment = event.value
            is TeachFormatEvent.TemplateChanged -> template = event.value
            is TeachFormatEvent.ExampleChanged -> example = event.value
            is TeachFormatEvent.DirectionChanged -> direction = event.value
            is TeachFormatEvent.InsertToken -> template = "$template{${event.token}}"
            TeachFormatEvent.Save -> save()
            TeachFormatEvent.DismissError -> error = null
        }
    }

    /**
     * The screen reports which format it was opened for.
     *
     * Navigation here hands screens their arguments as objects rather than
     * through a saved-state bundle, so the id arrives as an event instead of
     * being read in the constructor.
     */
    private fun start(id: String?) {
        if (started) return
        started = true
        if (id.isNullOrBlank()) loadCandidates() else loadExisting(id)
    }

    private fun loadCandidates() {
        viewModelScope.launch {
            loading = true
            candidates = runCatching {
                unrecognised.find().map { candidate ->
                    MessageCandidate(
                        sourceId = candidate.sourceId,
                        sender = candidate.sender,
                        body = candidate.body,
                        received = dateFormat.format(Date(candidate.receivedAtMillis)),
                    )
                }
            }.getOrDefault(emptyList()).toImmutableList()
            loading = false
        }
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            loading = true
            templates.find(id)?.let { existing ->
                templateId = existing.id
                createdAt = existing.createdAtMillis
                label = existing.label
                senderFragment = existing.senderFragment.orEmpty()
                direction = existing.direction
                template = existing.template
                currency = existing.currency
                editing = true
            }
            loading = false
        }
    }

    /**
     * Turns the picked message into a draft template.
     *
     * The suggestion is the point of this step: the user came here because
     * their bank's messages are being ignored, not to learn a markup language.
     */
    private fun pick(sourceId: String) {
        val candidate = candidates.firstOrNull { it.sourceId == sourceId } ?: return
        val suggestion = SmsTemplateSuggester.suggest(
            RawMessage(
                sourceId = candidate.sourceId,
                sender = candidate.sender,
                body = candidate.body,
                receivedAtMillis = System.currentTimeMillis(),
            ),
        )
        templateId = ""
        createdAt = 0L
        label = suggestion.label
        senderFragment = suggestion.senderFragment.orEmpty()
        direction = suggestion.direction
        template = suggestion.template
        currency = suggestion.currency
        example = candidate.body
        exampleSender = candidate.sender
        editing = true
    }

    private fun startBlank() {
        templateId = ""
        createdAt = 0L
        label = ""
        senderFragment = ""
        direction = SmsTemplate.DIRECTION_EXPENSE
        template = ""
        example = ""
        exampleSender = ""
        currency = null
        editing = true
    }

    private fun draft() = SmsTemplate(
        id = templateId.ifBlank { "draft" },
        label = label.ifBlank { "My bank" },
        senderFragment = senderFragment.trim().takeIf { it.isNotBlank() },
        template = template,
        direction = direction,
        currency = currency,
        enabled = true,
        createdAtMillis = createdAt,
    )

    /**
     * Compiles the draft and runs it against the example, with the shipped
     * rules taken out of the way so the result can only come from the format
     * being written - otherwise a built-in rule matching the example would
     * report success for a template that does nothing.
     */
    @Suppress("ReturnCount")
    private fun preview(): FormatPreview {
        if (template.isBlank()) return FormatPreview.NeedsExample
        val compiled = when (val result = SmsTemplateCompiler.compile(draft())) {
            is TemplateCompileResult.Error -> return FormatPreview.Problem(result.message)
            is TemplateCompileResult.Ok -> result.rule
        }
        if (example.isBlank()) return FormatPreview.NeedsExample

        val parser = TransactionSmsParser(
            ruleSet = SmsRuleSet(version = 0, rules = emptyList()),
            userRules = listOf(compiled),
        )
        val sender = exampleSender.ifBlank { senderFragment }
        val result = parser.parse(
            RawMessage(
                sourceId = "preview",
                sender = sender,
                body = example,
                receivedAtMillis = System.currentTimeMillis(),
            ),
        )
        return when (result) {
            is ParseResult.Matched -> FormatPreview.Reads(
                amount = result.transaction.amount.toPlainString(),
                direction = if (result.transaction.direction == TxnDirection.Income) {
                    "money in"
                } else {
                    "money out"
                },
                account = result.transaction.accountHint,
                balance = result.transaction.balance?.toPlainString(),
                payee = result.transaction.merchant,
                reference = result.transaction.reference,
            )

            is ParseResult.Rejected -> FormatPreview.Problem(result.reason.explain())
        }
    }

    private fun RejectReason.explain(): String = when (this) {
        RejectReason.LooksLikeOtp ->
            "This message looks like a one-time password, which is never " +
                "imported. Pick a different message."

        RejectReason.FailedOrReversed ->
            "This message describes a payment that failed or was reversed, " +
                "so no money moved."

        RejectReason.PaymentRequest ->
            "This is a request for money rather than a payment."

        RejectReason.FutureOrScheduled ->
            "This message is about money that has not moved yet."

        RejectReason.NotFromKnownSender ->
            "The sender fragment does not appear in this message's sender."

        else -> "The format does not match the example yet. Check the " +
            "wording around each marker."
    }

    private fun save() {
        viewModelScope.launch {
            val stored = runCatching { templates.save(draft().copy(id = templateId)) }
                .getOrElse {
                    error = "Could not save this format"
                    return@launch
                }
            templateId = stored.id
            // The messages this format was written for are all behind the scan
            // watermark, so without a full re-scan saving it would appear to
            // do nothing at all.
            runCatching { scanMessages.scan(rescan = true) }
            saved = true
        }
    }

    companion object {
        private val dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    }
}
