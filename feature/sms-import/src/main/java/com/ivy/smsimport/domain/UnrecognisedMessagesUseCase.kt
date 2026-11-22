package com.ivy.smsimport.domain

import com.ivy.sms.MessageClassifier
import com.ivy.sms.ParseResult
import com.ivy.sms.RawMessage
import com.ivy.sms.SenderFilter
import com.ivy.sms.TransactionSmsParser
import com.ivy.smsimport.data.SmsImportDao
import com.ivy.smsimport.data.SmsImportSettings
import com.ivy.smsimport.data.SmsInboxSource
import com.ivy.smsimport.data.SmsTemplateRepository
import java.time.Duration
import javax.inject.Inject

/**
 * Bank messages that no rule could read.
 *
 * This is the list a user needs to see in order to teach a format, and asking
 * them to find and copy the message out of their inbox instead would be the
 * step at which most people stop. It is also the honest answer to "why did it
 * find my SBI messages but not my Bank of Baroda ones" - here are the ones it
 * could not read, pick one.
 */
class UnrecognisedMessagesUseCase @Inject constructor(
    private val inbox: SmsInboxSource,
    private val dao: SmsImportDao,
    private val settings: SmsImportSettings,
    private val templates: SmsTemplateRepository,
) {
    data class Candidate(
        val sourceId: String,
        val sender: String,
        val body: String,
        val receivedAtMillis: Long,
    )

    suspend fun find(limit: Int = DEFAULT_LIMIT): List<Candidate> {
        if (!inbox.hasPermission()) return emptyList()

        val since = System.currentTimeMillis() -
            Duration.ofDays(settings.scanWindowDays().coerceAtLeast(LOOK_BACK_DAYS)).toMillis()
        val parser = TransactionSmsParser(userRules = templates.activeRules())
        val blocked = dao.blockedSenders().toSet()

        return inbox.fetchRecent(since)
            .asSequence()
            .filter { SenderFilter.normalize(it.sender) !in blocked }
            .filter { looksLikeABankMessage(it) }
            .filter { parser.parse(it) is ParseResult.Rejected }
            // One message per sender and shape is enough: a month of a busy
            // account is forty near-identical alerts, and a list of forty rows
            // that all need the same template is a wall, not a choice.
            .distinctBy { SenderFilter.normalize(it.sender) to shapeOf(it.body) }
            .take(limit)
            .map {
                Candidate(
                    sourceId = it.sourceId,
                    sender = it.sender,
                    body = it.body,
                    receivedAtMillis = it.receivedAtMillis,
                )
            }
            .toList()
    }

    /**
     * Worth offering, and safe to show.
     *
     * [MessageClassifier.hardReject] filters out one-time passwords before
     * anything is listed: a screen of OTPs would be both useless and the worst
     * possible thing to put message text on.
     */
    @Suppress("ReturnCount")
    private fun looksLikeABankMessage(message: RawMessage): Boolean {
        if (MessageClassifier.hardReject(message.body) != null) return false
        if (SenderFilter.isKnownBank(message.sender)) return true

        val text = message.body.lowercase()
        return amountPattern.containsMatchIn(text) && accountMarkers.any { it in text }
    }

    /**
     * A crude fingerprint of wording: the letters only, so two alerts that
     * differ in amount, date and reference collapse to the same shape.
     */
    private fun shapeOf(body: String): String = body
        .lowercase()
        .filter { it.isLetter() || it.isWhitespace() }
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(SHAPE_LENGTH)

    companion object {
        private const val DEFAULT_LIMIT = 25
        private const val LOOK_BACK_DAYS = 60L
        private const val SHAPE_LENGTH = 60

        private val amountPattern = Regex(
            """(?:rs\.?|inr|₹|\$|€|£)\s?[\d,]+(?:\.\d{1,2})?|\b\d+\.\d{2}\b""",
            RegexOption.IGNORE_CASE,
        )

        private val accountMarkers = listOf(
            "a/c", "acct", "account", "card", "upi", "bank", "balance", "bal",
        )
    }
}
