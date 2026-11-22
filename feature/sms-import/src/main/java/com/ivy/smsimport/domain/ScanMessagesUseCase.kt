package com.ivy.smsimport.domain

import com.ivy.sms.MessageFacts
import com.ivy.sms.ParseResult
import com.ivy.sms.ParsedTransaction
import com.ivy.sms.RawMessage
import com.ivy.sms.SenderFilter
import com.ivy.sms.TransactionSmsParser
import com.ivy.sms.TxnDirection
import com.ivy.smsimport.data.SmsBalanceSnapshotEntity
import com.ivy.smsimport.data.SmsImportDao
import com.ivy.smsimport.data.SmsImportSettings
import com.ivy.smsimport.data.SmsInboxSource
import com.ivy.smsimport.data.SmsPendingEntity
import com.ivy.smsimport.data.SmsTemplateRepository
import timber.log.Timber
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Duration
import javax.inject.Inject

/**
 * Scans for new bank messages and queues what it finds for review.
 *
 * Runs entirely on-device: nothing here touches the network, and message bodies
 * are only persisted in the pending queue until the user decides, at which
 * point they are discarded.
 */
class ScanMessagesUseCase @Inject constructor(
    private val inbox: SmsInboxSource,
    private val dao: SmsImportDao,
    private val settings: SmsImportSettings,
    private val templates: SmsTemplateRepository,
) {
    data class Result(val found: Int, val skipped: Int)

    /**
     * [rescan] looks at the whole window again instead of resuming from the
     * watermark.
     *
     * Needed because a scan advances the watermark past every message it
     * examined, including the ones it rejected. Teaching the app a new format
     * would otherwise change nothing visible - the messages that format exists
     * to read are all behind the mark.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    suspend fun scan(rescan: Boolean = false): Result {
        if (!inbox.hasPermission()) return Result(found = 0, skipped = 0)

        val since = if (rescan) windowStart() else settings.lastScannedAt()
        val messages = inbox.fetchSince(since)
        if (messages.isEmpty()) return Result(found = 0, skipped = 0)

        val alreadyDecided = dao.allDecidedIds().toSet()
        val blocked = dao.blockedSenders().toSet()
        val queued = dao.pendingIds().toSet()
        val parser = TransactionSmsParser(userRules = templates.activeRules())

        val pending = mutableListOf<SmsPendingEntity>()
        var skipped = 0
        var newestLogged = since

        for (message in messages) {
            // Hard dedupe: a message we have already decided on is never re-offered.
            if (message.sourceId in alreadyDecided || message.sourceId in queued) {
                newestLogged = maxOf(newestLogged, message.receivedAtMillis)
                continue
            }
            if (SenderFilter.normalize(message.sender) in blocked) {
                skipped++
                newestLogged = maxOf(newestLogged, message.receivedAtMillis)
                continue
            }

            when (val result = parser.parse(message)) {
                is ParseResult.Matched -> {
                    val fingerprint = fingerprintOf(result.transaction, message)
                    pending += result.transaction.toPending(
                        message = message,
                        fingerprint = fingerprint,
                        // Soft dedupe: surface as "possible duplicate" rather than
                        // dropping it, because a silent drop looks like a bug.
                        possibleDuplicate = dao.fingerprintSeen(fingerprint),
                    )
                    recordBalance(message, result.transaction.balance)
                }

                is ParseResult.Rejected -> {
                    skipped++
                    // A message that is not a transaction can still state the
                    // balance - a plain balance alert is the clearest example -
                    // and that figure is what reconciliation needs.
                    if (SenderFilter.isKnownBank(message.sender)) {
                        recordBalance(message, balance = null)
                    }
                    Timber.v("Message skipped: %s", result.reason)
                }
            }
            newestLogged = maxOf(newestLogged, message.receivedAtMillis)
        }

        if (pending.isNotEmpty()) dao.addPending(pending)
        settings.setLastScannedAt(newestLogged)

        return Result(found = pending.size, skipped = skipped)
    }

    private suspend fun windowStart(): Long =
        System.currentTimeMillis() - Duration.ofDays(settings.scanWindowDays()).toMillis()

    /**
     * Keeps the latest balance the bank stated for an account.
     *
     * Only the number is stored, never the message - the point is to answer
     * "has the wallet drifted from the real account", not to keep a copy of
     * the inbox. An older message never overwrites a newer reading.
     */
    @Suppress("ReturnCount")
    private suspend fun recordBalance(message: RawMessage, balance: BigDecimal?) {
        val hint = MessageFacts.accountHint(message.body) ?: return
        val reading = MessageFacts.balance(message.body)
        val amount = balance ?: reading?.amount ?: return

        val existing = dao.balanceSnapshot(hint)
        if (existing != null && existing.observedAtMillis >= message.receivedAtMillis) return

        dao.saveBalanceSnapshot(
            SmsBalanceSnapshotEntity(
                accountHint = hint,
                balance = amount.toPlainString(),
                currency = reading?.currency,
                observedAtMillis = message.receivedAtMillis,
                settledBalance = existing?.settledBalance,
            ),
        )
    }

    private fun ParsedTransaction.toPending(
        message: RawMessage,
        fingerprint: String,
        possibleDuplicate: Boolean,
    ): SmsPendingEntity = SmsPendingEntity(
        sourceId = message.sourceId,
        sender = message.sender,
        body = message.body,
        amount = amount.toPlainString(),
        direction = when (direction) {
            TxnDirection.Expense -> DIRECTION_EXPENSE
            TxnDirection.Income -> DIRECTION_INCOME
        },
        currency = currency,
        accountHint = accountHint,
        merchant = merchant,
        reference = reference,
        confidence = confidence,
        ruleId = ruleId,
        receivedAtMillis = message.receivedAtMillis,
        fingerprint = fingerprint,
        possibleDuplicate = possibleDuplicate,
    )

    /**
     * Identifies the same real-world transaction across re-scans and restores:
     * amount, direction, a two-minute time bucket and the bank reference.
     */
    private fun fingerprintOf(txn: ParsedTransaction, message: RawMessage): String {
        val bucket = message.receivedAtMillis / TWO_MINUTES_MILLIS
        val raw = listOf(
            txn.amount.toPlainString(),
            txn.direction.name,
            txn.accountHint.orEmpty(),
            bucket.toString(),
            txn.reference.orEmpty(),
        ).joinToString("|")

        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DIRECTION_EXPENSE = "expense"
        const val DIRECTION_INCOME = "income"
        private const val TWO_MINUTES_MILLIS = 120_000L

        /** Items at or above this confidence are safe to pre-fill and bulk accept. */
        const val HIGH_CONFIDENCE = 0.9
    }
}
