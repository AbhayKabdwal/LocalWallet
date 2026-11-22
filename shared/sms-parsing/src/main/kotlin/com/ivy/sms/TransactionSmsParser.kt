package com.ivy.sms

import java.math.BigDecimal

/**
 * Turns a bank message into a [ParsedTransaction], or explains why it did not.
 *
 * Everything here is pure: no Android, no I/O, no network. That keeps the whole
 * corpus of bank formats testable on the JVM in milliseconds, which matters
 * because this is the part of the feature most likely to need changing.
 */
class TransactionSmsParser(
    private val ruleSet: SmsRuleSet = DefaultRules.ruleSet,
    /**
     * Formats the user taught the app, tried before the shipped ones.
     *
     * They also skip the sender filter and most of [MessageClassifier]: a
     * template exists precisely because the built-in heuristics got this bank's
     * messages wrong, so letting those heuristics veto it would defeat the
     * feature. Only [MessageClassifier.hardReject] still applies.
     */
    userRules: List<SmsRule> = emptyList(),
) {
    private val compiled: List<Pair<SmsRule, Regex>> = ruleSet.rules.map(::compile)
    private val compiledUserRules: List<Pair<SmsRule, Regex>> = userRules.mapNotNull { rule ->
        runCatching { compile(rule) }.getOrNull()
    }

    private fun compile(rule: SmsRule): Pair<SmsRule, Regex> = rule to Regex(
        rule.bodyPattern,
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    @Suppress("ReturnCount")
    fun parse(message: RawMessage): ParseResult {
        MessageClassifier.hardReject(message.body)?.let {
            return ParseResult.Rejected(it)
        }

        firstMatch(compiledUserRules, message)?.let { return ParseResult.Matched(it) }

        if (!SenderFilter.accepts(message.sender, message.body)) {
            return ParseResult.Rejected(RejectReason.NotFromKnownSender)
        }
        MessageClassifier.classify(message.body)?.let {
            return ParseResult.Rejected(it)
        }

        return firstMatch(compiled, message)
            ?.let(ParseResult::Matched)
            ?: ParseResult.Rejected(RejectReason.NoRuleMatched)
    }

    private fun firstMatch(
        rules: List<Pair<SmsRule, Regex>>,
        message: RawMessage,
    ): ParsedTransaction? = rules
        .asSequence()
        .filter { (rule, _) -> rule.appliesTo(message.sender) }
        .mapNotNull { (rule, regex) ->
            regex.find(message.body)?.let { match -> rule.read(match, message.body) }
        }
        .firstOrNull()

    private fun SmsRule.appliesTo(sender: String): Boolean = senderPattern == null ||
        SenderFilter.normalize(sender).contains(senderPattern.uppercase())

    private fun SmsRule.read(match: MatchResult, body: String): ParsedTransaction? {
        val amount = match.groupOrNull("amount")?.let(::parseAmount) ?: return null
        return ParsedTransaction(
            direction = if (direction.equals("income", ignoreCase = true)) {
                TxnDirection.Income
            } else {
                TxnDirection.Expense
            },
            amount = amount,
            currency = currency ?: match.groupOrNull("currency")?.let(::normalizeCurrency),
            accountHint = match.groupOrNull("account")?.let(::normalizeAccount)
                ?: MessageFacts.accountHint(body),
            merchant = match.groupOrNull("payee")?.let(::cleanMerchant),
            reference = extractReference(body),
            confidence = confidence,
            ruleId = id,
            // A rule that names a balance group wins over the generic footer
            // scan, because the rule knows which of two stated balances this
            // bank means.
            balance = match.groupOrNull("balance")?.let(::parseAmount)
                ?: MessageFacts.balance(body)?.amount,
        )
    }

    private fun MatchResult.groupOrNull(name: String): String? = try {
        groups[name]?.value?.takeIf { it.isNotBlank() }
    } catch (e: IllegalArgumentException) {
        // The rule simply does not define this group.
        null
    }

    companion object {
        private val referencePattern = Regex(
            """(?:ref(?:erence)?(?:\s*no\.?)?|txn(?:\s*id)?|utr)[:\s#]*([a-z0-9]{4,24})""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Parses amounts written with either Indian (`1,23,456.78`) or European
         * (`1.234,56`) grouping. Uses [BigDecimal] throughout - money must never
         * go through a Double.
         */
        fun parseAmount(raw: String): BigDecimal? {
            val trimmed = raw.trim()
            val lastComma = trimmed.lastIndexOf(',')
            val lastDot = trimmed.lastIndexOf('.')

            val normalized = when {
                // European style: comma is the decimal separator.
                lastComma > lastDot -> trimmed.replace(".", "").replace(',', '.')
                else -> trimmed.replace(",", "")
            }
            return normalized.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
        }

        fun normalizeCurrency(raw: String): String = when (raw.lowercase().trim().trimEnd('.')) {
            "rs", "inr", "₹" -> "INR"
            "$", "usd" -> "USD"
            "€", "eur" -> "EUR"
            "£", "gbp" -> "GBP"
            else -> raw.uppercase().trim()
        }

        /** `A/c XX1234` / `card ending 1234` -> `1234`. */
        fun normalizeAccount(raw: String): String? =
            raw.filter { it.isDigit() }.takeLast(4).takeIf { it.isNotEmpty() }

        fun cleanMerchant(raw: String): String? = raw
            .trim()
            .removeSuffix(".")
            .replace(Regex("""\s+(?:on|ref|refno|txn|utr)\b.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bupi/\S*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .takeIf { it.length in 2..60 }
            // A payee group can land on a reference number instead of a name
            // ("...credited to A/c ...1234 thru UPI/CR/5330.../ by 1234"). A
            // title of bare digits is worse than no title.
            ?.takeIf { name -> name.any(Char::isLetter) }

        fun extractReference(body: String): String? =
            referencePattern.find(body)?.groupValues?.getOrNull(1)
    }
}
