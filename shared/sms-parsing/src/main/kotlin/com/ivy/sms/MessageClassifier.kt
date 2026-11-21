package com.ivy.sms

/**
 * Decides whether a message is worth parsing at all.
 *
 * This runs before any amount extraction, and it is deliberately strict: a
 * false positive silently corrupts the user's balance, while a missed message
 * is a minor annoyance they can fix by hand. Precision beats recall here.
 */
object MessageClassifier {

    /**
     * OTP messages are the single biggest source of false positives - they
     * contain a bank name, a 6-digit number and often an amount. Never parse one.
     */
    private val otpMarkers = listOf(
        "otp",
        "one time password",
        "one-time password",
        "do not share",
        "don't share",
        "never share",
        "verification code",
        "security code",
        "login code",
        "2fa",
    )

    private val promoMarkers = listOf(
        "cashback upto",
        "cashback up to",
        "apply now",
        "pre-approved",
        "pre approved",
        "offer",
        "discount",
        "sale",
        "coupon",
        "click here",
        "t&c apply",
        "terms apply",
        "unsubscribe",
        "lowest interest",
        "instant loan",
        "congratulations",
        "you are eligible",
        "win ",
    )

    private val scheduledMarkers = listOf(
        "will be debited",
        "will be deducted",
        "due on",
        "due date",
        "bill generated",
        "statement is ready",
        "minimum due",
        "total due",
        "autopay",
        "e-mandate",
        "emandate",
        "scheduled for",
        "reminder",
    )

    private val failedMarkers = listOf(
        "failed",
        "declined",
        "unsuccessful",
        "could not be processed",
        "reversed",
        "refund initiated",
        "cancelled",
        "canceled",
    )

    private val requestMarkers = listOf(
        "collect request",
        "has requested",
        "is requesting",
        "payment request",
        "requesting money",
    )

    private val balanceOnlyMarkers = listOf(
        "available balance",
        "avl bal",
        "avl. bal",
        "available bal",
        "closing balance",
        "a/c balance",
    )

    /** Words that indicate money actually moved. */
    private val directionMarkers = listOf(
        "debited",
        "credited",
        "spent",
        "withdrawn",
        "deposited",
        "received",
        "transferred",
        "paid",
        "purchase",
        "sent to",
        "debit of",
        "credit of",
        // Verb-first wordings. Several large banks lead with the verb and put
        // the amount after it ("Debit INR 500.00 A/c no. XX1234", "Sent Rs.49
        // From ... A/C x1234"), so the earlier markers alone missed them
        // entirely. Each is paired with a currency token to keep "credit card"
        // and similar noun uses from counting as movement.
        "debit rs",
        "debit inr",
        "debit ₹",
        "credit rs",
        "credit inr",
        "credit ₹",
        "sent rs",
        "sent inr",
        "sent ₹",
        "trf to",
        "trf from",
        "transfer to",
        "transfer from",
        // Card purchase receipts ("Thank you for using your Credit Card
        // ending 4321 for Rs.1,250.00 at ...") name no movement verb at all.
        "for using your",
    )

    /** Signs the message concerns an account or card. */
    private val accountMarkers = listOf(
        "a/c",
        "a/c.",
        "acct",
        "account",
        "card ending",
        "card no",
        "credit card",
        "debit card",
        "upi",
        "vpa",
        "imps",
        "neft",
        "rtgs",
    )

    private val amountPattern = Regex(
        """(?:rs\.?|inr|₹|\$|€|£|usd|eur|gbp)\s?[\d,]+(?:\.\d{1,2})?""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * An amount written with no currency token at all, next to a movement verb.
     *
     * SBI - the largest bank in the country this feature was built for - sends
     * "A/C X1234 debited by 150.0 on date 21Nov25", with no "Rs" anywhere in
     * the message. Requiring a currency token rejected every one of those
     * before a single rule ran. The verb has to be adjacent so that a bare
     * number elsewhere in the text cannot stand in for an amount.
     */
    private val verbAdjacentAmountPattern = Regex(
        """(?:debited|credited|withdrawn|spent|paid|deducted|debit|credit|sent)""" +
            """\s*(?:by|with|for|of|to)?\s*(?:rs\.?|inr|₹|\$|€|£)?\s*[\d,]+(?:\.\d{1,2})?""",
        RegexOption.IGNORE_CASE,
    )

    fun classify(body: String): RejectReason? {
        val text = body.lowercase()

        // Order matters: OTP first, since an OTP message can also contain an amount.
        if (otpMarkers.any { it in text }) return RejectReason.LooksLikeOtp
        if (requestMarkers.any { it in text }) return RejectReason.PaymentRequest
        if (failedMarkers.any { it in text }) return RejectReason.FailedOrReversed
        if (scheduledMarkers.any { it in text }) return RejectReason.FutureOrScheduled
        if (promoMarkers.any { it in text }) return RejectReason.LooksPromotional

        val hasDirection = directionMarkers.any { it in text }

        // "Available balance is X" with no movement verb is informational.
        if (!hasDirection && balanceOnlyMarkers.any { it in text }) {
            return RejectReason.BalanceEnquiryOnly
        }
        if (!hasDirection) return RejectReason.NoRuleMatched
        if (!amountPattern.containsMatchIn(text) &&
            !verbAdjacentAmountPattern.containsMatchIn(text)
        ) {
            return RejectReason.NoRuleMatched
        }
        if (accountMarkers.none { it in text }) return RejectReason.NotFromKnownSender

        return null
    }

    /** True when the body looks transactional even if the sender is unknown. */
    fun looksTransactional(body: String): Boolean = classify(body) == null
}
