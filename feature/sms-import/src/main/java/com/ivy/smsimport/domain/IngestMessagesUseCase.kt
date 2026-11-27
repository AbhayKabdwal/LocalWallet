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
import com.ivy.smsimport.data.SmsPendingEntity
import com.ivy.smsimport.data.SmsTemplateRepository
import timber.log.Timber
import java.math.BigDecimal
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Turns messages into review-queue entries, whatever brought them in.
 *
 * Split out of the SMS scan so that a bank notification goes through exactly
 * the same parser, the same taught formats, the same duplicate checks and the
 * same balance capture. Two ingest paths that drifted apart would mean a
 * format the user taught working over SMS and silently not over
 * notifications, which is the kind of difference nobody can diagnose from the
 * outside.
 */
class IngestMessagesUseCase @Inject constructor(
    private val dao: SmsImportDao,
    private val templates: SmsTemplateRepository,
) {
    data class Result(val found: Int, val skipped: Int)

    suspend fun ingest(messages: List<RawMessage>): Result {
        if (messages.isEmpty()) return Result(found = 0, skipped = 0)

        val alreadyDecided = dao.allDecidedIds().toSet()
        val blocked = dao.blockedSenders().toSet()
        val queued = dao.pendingIds().toSet()
        val parser = TransactionSmsParser(userRules = templates.activeRules())

        val pending = mutableListOf<SmsPendingEntity>()
        var skipped = 0

        for (message in messages) {
            // Hard dedupe: a message already decided on is never re-offered.
            if (message.sourceId in alreadyDecided || message.sourceId in queued) continue
            if (SenderFilter.normalize(message.sender) in blocked) {
                skipped++
                continue
            }

            when (val result = parser.parse(message)) {
                is ParseResult.Matched -> {
                    val fingerprint = fingerprintOf(result.transaction, message)
                    pending += result.transaction.toPending(
                        message = message,
                        fingerprint = fingerprint,
                        // Soft dedupe: surfaced as "possible duplicate" rather
                        // than dropped, because a silent drop looks like a bug.
                        possibleDuplicate = dao.fingerprintSeen(fingerprint),
                    )
                    recordBalance(message, result.transaction.balance)
                }

                is ParseResult.Rejected -> {
                    skipped++
                    if (SenderFilter.isKnownBank(message.sender)) {
                        recordBalance(message, balance = null)
                    }
                    Timber.v("Message skipped: %s", result.reason)
                }
            }
        }

        if (pending.isNotEmpty()) dao.addPending(pending)
        return Result(found = pending.size, skipped = skipped)
    }

    /**
     * Keeps the latest balance the bank stated for an account.
     *
     * Only the number is stored, never the message. An older reading never
     * overwrites a newer one.
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
            TxnDirection.Expense -> ScanMessagesUseCase.DIRECTION_EXPENSE
            TxnDirection.Income -> ScanMessagesUseCase.DIRECTION_INCOME
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
     * Identifies the same real-world transaction across sources and re-scans.
     *
     * This is what stops a payment being queued twice when the bank sends an
     * SMS *and* its app posts a notification for it: same amount, direction,
     * account, reference and two-minute bucket collapse to one fingerprint,
     * regardless of which path carried it in.
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

    private companion object {
        const val TWO_MINUTES_MILLIS = 120_000L
    }
}
