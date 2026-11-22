package com.ivy.sms

import java.math.BigDecimal

/** A balance the bank stated in a message, with the currency it was written in. */
data class BalanceReading(
    val amount: BigDecimal,
    val currency: String?,
)

/**
 * Facts that can be read out of a bank message without matching a whole rule.
 *
 * A rule describes one bank's way of saying "money moved"; these are the parts
 * that appear in almost every message regardless of that wording - the balance
 * footer and the masked account number. They are extracted separately so that a
 * message no rule matched (a balance alert, or a format nobody has taught the
 * app yet) can still say what the account's balance is.
 */
object MessageFacts {

    private const val AMOUNT = """[\d,]+(?:\.\d{1,2})?"""
    private const val CURRENCY = """(?:rs\.?|inr|₹|\$|€|£)"""

    /**
     * Balance markers, most specific first.
     *
     * The order is the point: a message can state two balances ("Tot Bal:
     * Rs.5000.00CR. Avlbl Amt:Rs.4500.00"), and the wallet's own balance is the
     * sum of its transactions - a ledger balance - so the closing/total figure
     * is the one worth comparing against it. An available balance can sit below
     * the ledger one because of holds, which are not transactions.
     */
    private val balanceMarkers = listOf(
        """closing\s*bal(?:ance)?""",
        """clos\s*bal""",
        """tot(?:al)?\s*bal(?:ance)?""",
        """ledger\s*bal(?:ance)?""",
        """a/c\s*bal(?:ance)?""",
        """acct?\s*bal(?:ance)?""",
        """avl?(?:bl)?\.?\s*bal(?:ance)?""",
        """avai?l(?:able)?\.?\s*bal(?:ance)?""",
        """avl?(?:bl)?\.?\s*amt""",
        """avai?l(?:able)?\.?\s*amt""",
        """bal(?:ance)?""",
    )

    private val balancePatterns: List<Regex> = balanceMarkers.map { marker ->
        Regex(
            marker + """\s*(?:is|of|:|-|=)?\s*""" +
                """(?<currency>$CURRENCY)?\s*(?<balance>$AMOUNT)""",
            setOf(RegexOption.IGNORE_CASE),
        )
    }

    /**
     * Masked account or card numbers: `A/c XX1234`, `A/c ...1234`, `Acct X123`,
     * `card ending 4321`.
     *
     * Several banks mask with dots rather than Xs, which the rule set missed
     * entirely - `A/c ...1234` left no account hint, so the account mapping
     * could never be learned for those messages.
     */
    private val accountPattern = Regex(
        """\b(?:a/c|ac|acct|acc|account|card)\.?""" +
            """(?:\s*(?:no\.?|number|ending(?:\s*with)?|xx))?""" +
            """\s*[:#]?\s*((?:[x*.]\s?){0,8}\d{2,})""",
        setOf(RegexOption.IGNORE_CASE),
    )

    /** `A/c ...1234` -> `1234`. The last four digits are what a user recognises. */
    fun accountHint(body: String): String? = accountPattern
        .findAll(body)
        .mapNotNull { TransactionSmsParser.normalizeAccount(it.groupValues[1]) }
        .firstOrNull { it.length >= MIN_HINT_DIGITS }

    /** The balance the message states, if it states one. */
    fun balance(body: String): BalanceReading? = balancePatterns
        .asSequence()
        .mapNotNull { pattern -> pattern.find(body) }
        .mapNotNull { match ->
            match.groups["balance"]?.value?.let(::parseMoney)?.let { amount ->
                BalanceReading(
                    amount = amount,
                    currency = match.groups["currency"]?.value
                        ?.takeIf { it.isNotBlank() }
                        ?.let(TransactionSmsParser::normalizeCurrency),
                )
            }
        }
        .firstOrNull()

    /**
     * Like [TransactionSmsParser.parseAmount] but accepts zero, because an
     * emptied account really can have a balance of `0.00` and rejecting it
     * would make that the one case reconciliation cannot fix.
     */
    private fun parseMoney(raw: String): BigDecimal? {
        val trimmed = raw.trim()
        val lastComma = trimmed.lastIndexOf(',')
        val lastDot = trimmed.lastIndexOf('.')
        val normalized = when {
            lastComma > lastDot -> trimmed.replace(".", "").replace(',', '.')
            else -> trimmed.replace(",", "")
        }
        return normalized.toBigDecimalOrNull()
    }

    private const val MIN_HINT_DIGITS = 3
}
