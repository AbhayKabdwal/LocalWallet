package com.ivy.sms

/**
 * A format the user taught the app by marking up one real message.
 *
 * Bank coverage cannot be finished by anyone but the user: there are hundreds
 * of banks, each free to reword its alerts, and a format that is missing is
 * indistinguishable from a broken app. A template turns "my bank's messages are
 * ignored" from a bug report with a release-cycle turnaround into something the
 * person holding the phone can fix in a minute, using the message they are
 * looking at.
 *
 * [template] is the message with its variable parts replaced by tokens -
 * `Rs.{amount} transferred from A/c {account} ... Avlbl Amt:Rs.{balance}` - and
 * is compiled to an [SmsRule] by [SmsTemplateCompiler].
 */
data class SmsTemplate(
    val id: String,
    val label: String,
    /**
     * A fragment that must appear in the normalised sender (`BOB`, `HDFC`).
     *
     * Null means any sender, which is what a template for a bank that texts
     * from a plain phone number needs.
     */
    val senderFragment: String?,
    val template: String,
    val direction: String,
    val currency: String?,
    val enabled: Boolean = true,
    val createdAtMillis: Long = 0L,
) {
    companion object {
        const val DIRECTION_EXPENSE = "expense"
        const val DIRECTION_INCOME = "income"
    }
}

/**
 * The placeholders a template can use.
 *
 * Each token carries aliases because the name a user reaches for is the word
 * their bank used - "deducted", "amount left" - not the one the code happens to
 * prefer. Rejecting `{deducted}` because the canonical name is `{amount}` would
 * be a pointless way to fail.
 */
enum class TemplateToken(
    val canonical: String,
    val aliases: Set<String>,
    val description: String,
) {
    Amount(
        canonical = "amount",
        aliases = setOf(
            "amt", "deducted", "debited", "credited", "spent", "paid",
            "value", "txn_amount", "transaction_amount", "money",
        ),
        description = "The amount that moved",
    ),
    Balance(
        canonical = "balance",
        aliases = setOf(
            "amount_left", "left", "avl", "avl_bal", "avlbl", "avbl",
            "available", "available_balance", "bal", "remaining",
            "closing_balance", "total_balance",
        ),
        description = "The balance the bank states afterwards",
    ),
    Account(
        canonical = "account",
        aliases = setOf(
            "acct", "ac", "acc", "last4", "last_4", "card",
            "account_number", "account_no", "account_last4",
        ),
        description = "The masked account or card number",
    ),
    Payee(
        canonical = "payee",
        aliases = setOf("merchant", "counterparty", "name", "payee_name", "vpa", "upi_id"),
        description = "Who the money went to or came from",
    ),
    Reference(
        canonical = "ref",
        aliases = setOf("reference", "refno", "ref_no", "txn", "txn_id", "utr", "rrn"),
        description = "The bank's reference number",
    ),
    Date(
        canonical = "date",
        aliases = setOf("time", "datetime", "timestamp", "on_date", "date_time"),
        description = "A date or time, ignored",
    ),
    Anything(
        canonical = "any",
        aliases = setOf("*", "anything", "something", "rest", "x", "whatever"),
        description = "Any text that changes between messages",
    ),
    ;

    companion object {
        fun fromName(raw: String): TemplateToken? {
            val key = raw.trim().lowercase()
                .replace(Regex("""[\s\-]+"""), "_")
                .trim('_')
            if (key.isEmpty()) return null
            return entries.firstOrNull { it.canonical == key || key in it.aliases }
        }
    }
}

sealed interface TemplateCompileResult {
    data class Ok(val rule: SmsRule) : TemplateCompileResult
    data class Error(val message: String) : TemplateCompileResult
}

/**
 * Turns a marked-up message into a parse rule.
 *
 * The literal text between tokens is matched exactly, with two deliberate
 * relaxations: runs of whitespace match any whitespace, and **runs of digits
 * match any digits**. The second one matters more than it looks - an example
 * message is full of numbers the user did not think to mark (a date, a phone
 * number, a UPI reference buried in `UPI/P2M/533012345678/SHOP`), and baking
 * those in would produce a template that matches exactly one message, forever.
 */
object SmsTemplateCompiler {

    /**
     * Both bracket styles are accepted: a user told to "mark the amount" is as
     * likely to type `(amount)` as `{amount}`. A bracketed word that is not a
     * known token - `(UPI Ref no 123)` - stays literal text, so real
     * parentheses in a bank message do not need escaping.
     */
    private val tokenPattern = Regex("""\{([^{}\n]{1,40})\}|\(([^(){}\n]{1,40})\)""")

    private const val AMOUNT = """[\d,]+(?:\.\d{1,2})?"""
    private const val ACCOUNT = """(?:[x*.]\s?){0,8}\d{2,}"""
    private const val REFERENCE = """[A-Za-z0-9]{4,24}"""

    /** Date and time shapes the Indian banks mix freely, plus ISO. */
    private const val DATE =
        """(?:\d{1,2}[-/.][A-Za-z0-9]{2,5}[-/.]\d{2,4}|\d{1,2}[A-Za-z]{3}\d{2,4}|""" +
            """\d{4}[-/]\d{1,2}[-/]\d{1,2})(?:[ ,T]+\d{1,2}:\d{2}(?::\d{2})?)?"""

    /**
     * Enough of the bank's own wording has to survive that the rule cannot
     * match a message it was never meant for. A template of `{amount}` alone
     * would happily turn every OTP into a transaction.
     */
    private const val MIN_LITERAL_CHARS = 8

    private const val MAX_PAYEE = 40
    private const val MAX_ANY = 80

    @Suppress("ReturnCount")
    fun compile(template: SmsTemplate): TemplateCompileResult {
        val text = template.template.trim()
        if (text.isBlank()) {
            return TemplateCompileResult.Error("Paste one of the bank's messages first.")
        }

        val hits = tokensIn(text)
        if (hits.isEmpty()) {
            return TemplateCompileResult.Error(
                "Mark the amount by replacing it with {amount}.",
            )
        }
        if (hits.none { it.token == TemplateToken.Amount }) {
            return TemplateCompileResult.Error(
                "A format needs {amount} so the app knows how much moved.",
            )
        }

        val pattern = StringBuilder()
        val captured = mutableSetOf<TemplateToken>()
        var literalChars = 0
        var cursor = 0

        for ((index, hit) in hits.withIndex()) {
            val literal = text.substring(cursor, hit.range.first)
            literalChars += literal.count { !it.isWhitespace() }
            pattern.append(literalToPattern(literal))
            pattern.append(
                hit.token.toPattern(
                    capture = captured.add(hit.token),
                    trailing = index == hits.lastIndex &&
                        text.substring(hit.range.last + 1).isBlank(),
                ),
            )
            cursor = hit.range.last + 1
        }
        val tail = text.substring(cursor)
        literalChars += tail.count { !it.isWhitespace() }
        pattern.append(literalToPattern(tail))

        if (literalChars < MIN_LITERAL_CHARS) {
            return TemplateCompileResult.Error(
                "Keep more of the bank's own wording, so this format cannot " +
                    "match unrelated messages.",
            )
        }

        val body = pattern.toString()
        runCatching { Regex(body) }.onFailure {
            return TemplateCompileResult.Error("That format could not be read.")
        }

        return TemplateCompileResult.Ok(
            SmsRule(
                id = "user.${template.id}",
                senderPattern = template.senderFragment?.trim()?.takeIf { it.isNotBlank() },
                bodyPattern = body,
                direction = template.direction,
                currency = template.currency?.takeIf { it.isNotBlank() },
                // A format the user wrote by hand beats every shipped guess,
                // and arrives pre-filled rather than flagged for a second look.
                confidence = USER_CONFIDENCE,
            ),
        )
    }

    const val USER_CONFIDENCE = 0.99

    private data class Hit(val range: IntRange, val token: TemplateToken)

    private fun tokensIn(text: String): List<Hit> = tokenPattern.findAll(text)
        .mapNotNull { match ->
            val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
            TemplateToken.fromName(raw)?.let { Hit(match.range, it) }
        }
        .toList()

    /**
     * Literal text, with whitespace and digit runs relaxed.
     *
     * [Regex.escape] is avoided on purpose: it wraps text in `\Q...\E`, and a
     * template's literal chunks get spliced together with group patterns, where
     * an unbalanced quote block is a confusing failure mode.
     */
    private fun literalToPattern(literal: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < literal.length) {
            val char = literal[i]
            when {
                char.isWhitespace() -> {
                    while (i < literal.length && literal[i].isWhitespace()) i++
                    out.append("""\s+""")
                }

                char.isDigit() -> {
                    while (i < literal.length && literal[i].isDigit()) i++
                    out.append("""\d+""")
                }

                else -> {
                    if (char in REGEX_METACHARACTERS) out.append('\\')
                    out.append(char)
                    i++
                }
            }
        }
        return out.toString()
    }

    private const val REGEX_METACHARACTERS = """\^$.|?*+()[]{}"""

    /**
     * [trailing] decides whether an open-ended token is greedy. A lazy group at
     * the very end of the pattern would match the minimum - two characters of a
     * payee name - because there is no following literal to force it onwards.
     */
    private fun TemplateToken.toPattern(capture: Boolean, trailing: Boolean): String =
        when (this) {
            TemplateToken.Amount -> group("amount", AMOUNT, capture)
            TemplateToken.Balance -> group("balance", AMOUNT, capture)
            TemplateToken.Account -> group("account", ACCOUNT, capture)
            TemplateToken.Payee -> group(
                name = "payee",
                pattern = if (trailing) """[^.\n]{2,$MAX_PAYEE}""" else """[^\n]{2,$MAX_PAYEE}?""",
                capture = capture,
            )

            TemplateToken.Reference -> group("ref", REFERENCE, capture)
            // Never read, so never captured - which also means a template may
            // use them as often as it likes without colliding on group names.
            TemplateToken.Date -> "(?:$DATE)"
            TemplateToken.Anything ->
                if (trailing) """(?:[^\n]{0,$MAX_ANY})""" else """(?:[^\n]{0,$MAX_ANY}?)"""
        }

    /**
     * Only the first use of a token captures; a repeat matches the same shape
     * without a name. Java's regex rejects a duplicated group name, and a
     * message that states its balance twice ("Tot Bal ... Avlbl Amt ...") is
     * common enough that erroring on it would be the wrong call.
     */
    private fun group(name: String, pattern: String, capture: Boolean): String =
        if (capture) "(?<$name>$pattern)" else "(?:$pattern)"
}
