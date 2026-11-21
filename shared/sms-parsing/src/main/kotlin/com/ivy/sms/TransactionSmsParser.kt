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
) {
    private val compiled: List<Pair<SmsRule, Regex>> = ruleSet.rules.map { rule ->
        rule to Regex(
            rule.bodyPattern,
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    }

    fun parse(message: RawMessage): ParseResult {
        if (!SenderFilter.accepts(message.sender, message.body)) {
            return ParseResult.Rejected(RejectReason.NotFromKnownSender)
        }
        MessageClassifier.classify(message.body)?.let {
            return ParseResult.Rejected(it)
        }

        val body = message.body
        for ((rule, regex) in compiled) {
            if (rule.senderPattern != null &&
                !SenderFilter.normalize(message.sender).contains(rule.senderPattern.uppercase())
            ) {
                continue
            }
            val match = regex.find(body) ?: continue
            val amount = match.groupOrNull("amount")?.let(::parseAmount) ?: continue

            return ParseResult.Matched(
                ParsedTransaction(
                    direction = if (rule.direction.equals("income", ignoreCase = true)) {
                        TxnDirection.Income
                    } else {
                        TxnDirection.Expense
                    },
                    amount = amount,
                    currency = rule.currency
                        ?: match.groupOrNull("currency")?.let(::normalizeCurrency),
                    accountHint = match.groupOrNull("account")?.let(::normalizeAccount),
                    merchant = match.groupOrNull("payee")?.let(::cleanMerchant),
                    reference = extractReference(body),
                    confidence = rule.confidence,
                    ruleId = rule.id,
                ),
            )
        }
        return ParseResult.Rejected(RejectReason.NoRuleMatched)
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

        fun extractReference(body: String): String? =
            referencePattern.find(body)?.groupValues?.getOrNull(1)
    }
}
