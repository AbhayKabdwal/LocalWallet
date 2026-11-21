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
            SmsRule(
                id = "in.card.spend",
                bodyPattern = """(?<currency>rs\.?|inr|₹)\s?(?<amount>[\d,]+(?:\.\d{1,2})?)\s+spent\s+(?:on|using)\s+.{0,30}?card\s*(?:ending\s*)?(?<account>[x*\d]+).{0,30}?(?:at|on)\s+(?<payee>[^.\n]{2,40})""",
                direction = "expense",
                currency = "INR",
                confidence = 0.92,
            ),
            SmsRule(
                id = "in.atm.withdrawal",
                bodyPattern = """(?<currency>rs\.?|inr|₹)\s?(?<amount>[\d,]+(?:\.\d{1,2})?)\s+(?:withdrawn|w/d)\s+from\s+.{0,20}?(?:a/c|ac|account)\s*(?<account>[x*\d]+)""",
                direction = "expense",
                currency = "INR",
                confidence = 0.9,
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
        ),
    )
}
