package com.ivy.sms

import java.math.BigDecimal

/** A message handed to the parser, from SMS or any other local source. */
data class RawMessage(
    val sourceId: String,
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
)

enum class TxnDirection { Expense, Income }

/**
 * A transaction the parser believes is described by a message.
 *
 * [confidence] drives how the review screen presents it: high-confidence items
 * are pre-filled and ready to accept, low-confidence ones ask the user to look.
 */
data class ParsedTransaction(
    val direction: TxnDirection,
    val amount: BigDecimal,
    val currency: String?,
    val accountHint: String?,
    val merchant: String?,
    val reference: String?,
    val confidence: Double,
    val ruleId: String,
)

/** Why a message was not turned into a transaction. Useful for debugging rules. */
enum class RejectReason {
    NotFromKnownSender,
    LooksLikeOtp,
    LooksPromotional,
    BalanceEnquiryOnly,
    FutureOrScheduled,
    FailedOrReversed,
    PaymentRequest,
    NoRuleMatched,
}

sealed interface ParseResult {
    data class Matched(val transaction: ParsedTransaction) : ParseResult
    data class Rejected(val reason: RejectReason) : ParseResult
}
