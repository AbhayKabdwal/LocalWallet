package com.ivy.sms

import kotlinx.serialization.Serializable

/**
 * A single pattern for recognising one bank's message format.
 *
 * Rules are data, not code, so a contributor can add support for a bank by
 * editing a JSON file in a pull request - no Kotlin and no build knowledge -
 * and a user whose bank changes its wording can patch it locally without
 * waiting for a release. Bank coverage is permanent maintenance, so it is worth
 * making it the cheapest possible kind.
 */
@Serializable
data class SmsRule(
    val id: String,
    val senderPattern: String? = null,
    /**
     * Regex with named groups. Recognised groups:
     * `amount`, `currency`, `account`, `payee`, `ref`.
     */
    val bodyPattern: String,
    val direction: String,
    val currency: String? = null,
    val confidence: Double = 0.7,
)

@Serializable
data class SmsRuleSet(
    val version: Int,
    val rules: List<SmsRule>,
)

object DefaultRules {

    /**
     * Shipped rule set. Ordered most-specific first; the generic rules at the
     * end are what let an unlisted bank still work.
     */
    val ruleSet: SmsRuleSet = SmsRuleSet(
        version = 1,
        rules = listOf(
            SmsRule(
                id = "in.generic.upi.debit",
                bodyPattern = """(?<currency>rs\.?|inr|₹)\s?(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?:has been\s+)?debited\s+from\s+(?:a/c|ac|account)\s*(?<account>[x*\d]+).{0,40}?(?:to|towards)\s+(?<payee>[^.\n]{2,40})""",
                direction = "expense",
                currency = "INR",
                confidence = 0.95,
            ),
            SmsRule(
                id = "in.generic.upi.credit",
                bodyPattern = """(?<currency>rs\.?|inr|₹)\s?(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?:has been\s+)?credited\s+(?:to\s+)?(?:a/c|ac|account)\s*(?<account>[x*\d]+).{0,40}?(?:from|by)\s+(?<payee>[^.\n]{2,40})""",
                direction = "income",
                currency = "INR",
                confidence = 0.95,
            ),
            // Verb-first wordings, where the account comes first and the amount
            // follows the verb: SBI ("A/C X1234 debited by 150.0", no currency
            // token at all) and ICICI ("Acct XX123 is debited with INR 500.00").
            // Between them these two banks cover a large share of the messages
            // this feature exists to read, and nothing matched them before.
            SmsRule(
                id = "in.account.verbfirst.debit",
                bodyPattern = ACCOUNT + """[\s-]*(?:is\s+|has\s+been\s+)?debited""" +
                    """\s+(?:by|with|for)\s*""" + OPTIONAL_CURRENCY + AMOUNT +
                    """(?:.{0,40}?(?:trf\s+to|transfer\s+to|to)\s+(?<payee>[^.\n]{2,40}))?""",
                direction = "expense",
                confidence = 0.93,
            ),
            SmsRule(
                id = "in.account.verbfirst.credit",
                bodyPattern = ACCOUNT + """[\s-]*(?:is\s+|has\s+been\s+)?credited""" +
                    """\s+(?:by|with|for)\s*""" + OPTIONAL_CURRENCY + AMOUNT +
                    """(?:.{0,40}?(?:transfer\s+from|trf\s+from|from)\s+""" +
                    """(?<payee>[^.\n]{2,40}))?""",
                direction = "income",
                confidence = 0.93,
            ),
            // HDFC's UPI alert: "Sent Rs.49.00 From HDFC Bank A/C x1234 To X".
            SmsRule(
                id = "in.upi.sent",
                bodyPattern = """\bsent\s+""" + CURRENCY + AMOUNT +
                    """\s+from\s+.{0,40}?""" + ACCOUNT +
                    """\s+to\s+(?<payee>[^.\n]{2,40})""",
                direction = "expense",
                currency = "INR",
                confidence = 0.93,
            ),
            SmsRule(
                id = "in.card.spend",
                bodyPattern = """(?<currency>rs\.?|inr|₹)\s?(?<amount>[\d,]+(?:\.\d{1,2})?)\s+spent\s+(?:on|using)\s+.{0,30}?card\s*(?:ending\s*)?(?<account>[x*\d]+).{0,30}?(?:at|on)\s+(?<payee>[^.\n]{2,40})""",
                direction = "expense",
                currency = "INR",
                confidence = 0.92,
            ),
            // Card purchase receipts that name no movement verb:
            // "Credit Card ending 4321 for Rs.1,250.00 at BLUE TOKAI".
            SmsRule(
                id = "in.card.usage",
                bodyPattern = """card\s+(?:ending\s+(?:with\s+)?|no\.?\s*|xx)?""" +
                    """(?<account>[x*\d]{3,})\s+(?:for|with)\s+""" + CURRENCY + AMOUNT +
                    """(?:\s+at\s+(?<payee>[^.\n]{2,40}))?""",
                direction = "expense",
                confidence = 0.9,
            ),
            SmsRule(
                id = "in.atm.withdrawal",
                bodyPattern = """(?<currency>rs\.?|inr|₹)\s?(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?:withdrawn|w/d)\s+from\s+.{0,20}?(?:a/c|ac|account)\s*(?<account>[x*\d]+)""",
                direction = "expense",
                currency = "INR",
                confidence = 0.9,
            ),
            // Axis-style header line: "Debit INR 500.00 A/c no. XX1234 ...".
            // `\bdebit\s` cannot swallow "debited", which the rules above own.
            SmsRule(
                id = "in.header.debit",
                bodyPattern = """\bdebit\s+""" + CURRENCY + AMOUNT + """\s+""" + ACCOUNT,
                direction = "expense",
                confidence = 0.9,
            ),
            SmsRule(
                id = "in.header.credit",
                bodyPattern = """\bcredit\s+""" + CURRENCY + AMOUNT + """\s+""" + ACCOUNT,
                direction = "income",
                confidence = 0.9,
            ),
            // "A debit of Rs.2,000.00 has been made from your a/c XX1234".
            SmsRule(
                id = "in.debit.of",
                bodyPattern = """\bdebit\s+of\s+""" + OPTIONAL_CURRENCY + AMOUNT +
                    """.{0,60}?""" + ACCOUNT,
                direction = "expense",
                confidence = 0.88,
            ),
            SmsRule(
                id = "in.credit.of",
                bodyPattern = """\bcredit\s+of\s+""" + OPTIONAL_CURRENCY + AMOUNT +
                    """.{0,60}?""" + ACCOUNT,
                direction = "income",
                confidence = 0.88,
            ),
            SmsRule(
                id = "intl.card.spend",
                bodyPattern = """(?<currency>\$|€|£|usd|eur|gbp)\s?(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?:was\s+)?(?:spent|charged|debited)\s+.{0,30}?(?:card|account)\s*(?:ending\s*)?(?<account>[x*\d]+)?.{0,30}?(?:at|to)\s+(?<payee>[^.\n]{2,40})""",
                direction = "expense",
                confidence = 0.85,
            ),
            // Fall backs: no payee, lower confidence, so the review screen asks.
            SmsRule(
                id = "generic.debit",
                bodyPattern = """(?<currency>rs\.?|inr|₹|\$|€|£)\s?(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?:has been\s+)?(?:debited|spent|withdrawn|paid|deducted)""",
                direction = "expense",
                confidence = 0.6,
            ),
            SmsRule(
                id = "generic.credit",
                bodyPattern = """(?<currency>rs\.?|inr|₹|\$|€|£)\s?(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?:has been\s+)?(?:credited|received|deposited)""",
                direction = "income",
                confidence = 0.6,
            ),
            // Last resort for a bank that writes the verb first and that none of
            // the shapes above fit. The currency is optional here too, so a
            // message with a bare amount still reaches the review screen rather
            // than vanishing.
            SmsRule(
                id = "generic.verbfirst.debit",
                bodyPattern = """(?:debited|withdrawn|spent|paid|deducted)""" +
                    """\s+(?:by|with|for|of)\s*""" + OPTIONAL_CURRENCY + AMOUNT,
                direction = "expense",
                confidence = 0.55,
            ),
            SmsRule(
                id = "generic.verbfirst.credit",
                bodyPattern = """(?:credited|received|deposited)""" +
                    """\s+(?:by|with|for)\s*""" + OPTIONAL_CURRENCY + AMOUNT,
                direction = "income",
                confidence = 0.55,
            ),
        ),
    )

    /**
     * Fragments shared by the rules above.
     *
     * Bank wordings differ in the order of the same handful of parts, so the
     * parts are named once here: a rule then reads as the shape it recognises
     * rather than as a wall of escapes.
     */
    private const val ACCOUNT =
        """\b(?:a/c|acct|account|ac)\.?\s*(?:no\.?\s*)?(?<account>[x*\d]{3,})"""

    private const val CURRENCY = """(?<currency>rs\.?|inr|₹|\$|€|£)\s?"""

    private const val OPTIONAL_CURRENCY = """(?:(?<currency>rs\.?|inr|₹|\$|€|£)\s?)?"""

    private const val AMOUNT = """(?<amount>[\d,]+(?:\.\d{1,2})?)"""
}
