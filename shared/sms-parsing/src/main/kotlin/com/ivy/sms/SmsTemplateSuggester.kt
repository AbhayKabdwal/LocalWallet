package com.ivy.sms

/**
 * Guesses a template from one real message, so the user confirms a draft
 * instead of writing a pattern.
 *
 * Asking someone to mark up an SMS by hand is asking them to notice every part
 * that varies - the greeting, a reference buried in a UPI string, the date -
 * and missing one produces a template that matches exactly the message they
 * pasted and nothing else afterwards. The guess is what makes the feature
 * usable: it is nearly always right about the amount and the account, and the
 * user's job shrinks to glancing at the preview and fixing the payee.
 */
object SmsTemplateSuggester {

    private const val AMOUNT = """\d[\d,]*(?:\.\d{1,2})?"""

    private val currencyAndAmount = Regex(
        """(?<cur>rs\.?|inr|₹|\$|€|£)?\s?(?<num>$AMOUNT)""",
        setOf(RegexOption.IGNORE_CASE),
    )

    private val accountPattern = Regex(
        """\b(?:a/c|ac|acct|acc|account|card)\.?""" +
            """(?:\s*(?:no\.?|number|ending(?:\s*with)?|xx))?""" +
            """\s*[:#]?\s*((?:[x*.]\s?){0,8}\d{2,})""",
        setOf(RegexOption.IGNORE_CASE),
    )

    private val referencePattern = Regex(
        """(?:ref(?:erence)?(?:\s*no\.?)?|txn(?:\s*id)?|utr|rrn)[:\s#/]*([A-Za-z0-9]{4,24})""",
        setOf(RegexOption.IGNORE_CASE),
    )

    private val datePattern = Regex(
        """(?:\d{1,2}[-/.][A-Za-z0-9]{2,5}[-/.]\d{2,4}|\d{1,2}[A-Za-z]{3}\d{2,4}|""" +
            """\d{4}[-/]\d{1,2}[-/]\d{1,2})(?:[ ,T]+\d{1,2}:\d{2}(?::\d{2})?)?""",
        setOf(RegexOption.IGNORE_CASE),
    )

    /**
     * Greetings vary within one bank's own alerts - the same sender writes
     * "Dear UPI user" on one message and "Dear Customer," on the next - so the
     * greeting is replaced rather than kept as literal text.
     */
    private val greetingPattern = Regex(
        """^(?:dear|hi|hello)\s+(?:[A-Za-z]+\s+){0,3}?""" +
            """(?:customer|user|sir|madam|client|cardholder)\b[,:]?""",
        setOf(RegexOption.IGNORE_CASE),
    )

    private val vpaPattern = Regex("""[A-Za-z0-9][A-Za-z0-9.\-_]{1,}@[A-Za-z]{2,}""")

    /** Phrases after which a bank tends to name the other party. */
    private val payeePrefixes = listOf(
        "trf to", "transferred to", "transfer to", "trf from", "transferred from",
        "transfer from", "sent to", "paid to", "received from", "credited to",
        "credited by", "debited to", " to:", " at ", " to ", " from ", " by ",
    )

    /** Where a payee name stops, whatever the bank puts next. */
    private val payeeStops = listOf(
        ".", "\n", " on ", " ref", " upi", " not ", " if ", " avl", " tot",
        " bal", " a/c", " call", "(", " - ", " dt ", " date",
    )

    private val payeeBlocklist = setOf(
        "a/c", "ac", "acct", "acc", "account", "card", "upi", "bank", "your",
        "the", "you", "customer", "user",
    )

    private val expenseVerbs = listOf(
        "transferred from", "trf from", "transfer from", "debited", "debit",
        "withdrawn", "spent", "deducted", "paid", "sent", "w/d", "purchase",
    )

    private val incomeVerbs = listOf(
        "transferred to", "trf to", "credited", "credit", "received", "deposited",
    )

    private val balanceMarkers = listOf(
        "bal", "avl", "avbl", "avlbl", "available", "closing", "amt", "limit",
    )

    /**
     * Sender headers end in a varying category fragment - the same bank texts
     * from `BOBTXN`, `BOBSMS` and `BOBIND` - so the suggested fragment is the
     * stem. Matching on `BOBTXN` would quietly ignore two thirds of the bank's
     * own messages.
     */
    private val senderSuffixes = listOf(
        "ALERT", "ALRT", "TXN", "SMS", "INB", "UPI", "CRD", "BNK", "IND",
        "INFO", "MSG", "PAY", "BK", "B", "T", "S", "I",
    )

    private const val MIN_SENDER_FRAGMENT = 3
    private const val MAX_PAYEE_LENGTH = 40
    private const val MIN_PAYEE_LENGTH = 2
    private const val MIN_LETTERS_IN_PAYEE = 2

    /** How far back to look for the word that gives an amount its meaning. */
    private const val CONTEXT_WINDOW = 30
    private const val MAX_BARE_AMOUNT_DIGITS = 9
    private const val MAX_TAIL = 56

    private data class Claim(val range: IntRange, val token: TemplateToken)

    fun suggest(message: RawMessage, id: String = ""): SmsTemplate {
        val body = message.body
        val lower = body.lowercase()
        val claims = mutableListOf<Claim>()

        greetingPattern.find(body)?.let {
            claims.add(Claim(it.range, TemplateToken.Anything))
        }
        accountPattern.findAll(body).forEach { match ->
            match.groups[1]?.range?.let { claims.addIfFree(it, TemplateToken.Account) }
        }
        referencePattern.findAll(body).forEach { match ->
            match.groups[1]?.range?.let { claims.addIfFree(it, TemplateToken.Reference) }
        }
        datePattern.findAll(body).forEach { claims.addIfFree(it.range, TemplateToken.Date) }

        val amounts = amountOccurrences(body, lower, claims)
        val balances = amounts.filter { it.isBalance }
        val transacted = pickTransacted(amounts, lower)

        balances.forEach { claims.addIfFree(it.range, TemplateToken.Balance) }
        transacted?.let { claims.addIfFree(it.range, TemplateToken.Amount) }

        findPayee(body, lower, claims)?.let { claims.addIfFree(it, TemplateToken.Payee) }

        val senderFragment = senderFragment(message.sender)
        val direction = direction(lower, transacted?.range)

        return SmsTemplate(
            id = id,
            label = label(senderFragment, direction),
            senderFragment = senderFragment,
            template = render(body, claims),
            direction = direction,
            currency = transacted?.currency,
        )
    }

    /**
     * A sender fragment to match on, or null for "any sender" when the bank
     * texts from a plain phone number and there is no header to key off.
     */
    @Suppress("ReturnCount")
    fun senderFragment(rawSender: String): String? {
        val normalized = SenderFilter.normalize(rawSender).filter { it.isLetterOrDigit() }
        if (normalized.length < MIN_SENDER_FRAGMENT) return null
        if (normalized.none { it.isLetter() }) return null

        val suffix = senderSuffixes.firstOrNull { normalized.endsWith(it) }
        val stem = suffix?.let { normalized.dropLast(it.length) } ?: normalized
        return if (stem.length >= MIN_SENDER_FRAGMENT) stem else normalized
    }

    private data class AmountHit(
        val range: IntRange,
        val currency: String?,
        val isBalance: Boolean,
    )

    private fun amountOccurrences(
        body: String,
        lower: String,
        claims: List<Claim>,
    ): List<AmountHit> = currencyAndAmount.findAll(body).mapNotNull { match ->
        val number = match.groups["num"] ?: return@mapNotNull null
        if (claims.any { it.range overlaps number.range }) return@mapNotNull null

        val currency = match.groups["cur"]?.value?.takeIf { it.isNotBlank() }
        val before = lower.substring(
            (number.range.first - CONTEXT_WINDOW).coerceAtLeast(0),
            number.range.first,
        )
        // A bare number is only an amount when a movement verb is right in
        // front of it. Without that, every phone number in the footer and
        // every digit of a date would look like money.
        val plausible = currency != null ||
            (
                (expenseVerbs + incomeVerbs).any { verb -> verb in before } &&
                    number.value.count { it.isDigit() } <= MAX_BARE_AMOUNT_DIGITS
                )
        if (!plausible) return@mapNotNull null

        AmountHit(
            range = number.range,
            currency = currency?.let(TransactionSmsParser::normalizeCurrency),
            isBalance = balanceMarkers.any { it in before },
        )
    }.toList()

    /**
     * The transacted amount is the one nearest a movement verb; a message that
     * states only a balance has none, and gets no `{amount}`.
     */
    @Suppress("ReturnCount")
    private fun pickTransacted(amounts: List<AmountHit>, lower: String): AmountHit? {
        val candidates = amounts.filterNot { it.isBalance }.ifEmpty { return null }
        val verbPositions = (expenseVerbs + incomeVerbs).flatMap { occurrences(lower, it) }
        if (verbPositions.isEmpty()) return candidates.first()

        return candidates.minByOrNull { hit ->
            verbPositions.minOf { position -> kotlin.math.abs(position - hit.range.first) }
        }
    }

    private fun direction(lower: String, amountAt: IntRange?): String {
        val anchor = amountAt?.first ?: 0
        val nearest = (
            expenseVerbs.map { it to SmsTemplate.DIRECTION_EXPENSE } +
                incomeVerbs.map { it to SmsTemplate.DIRECTION_INCOME }
            )
            .flatMap { (verb, direction) ->
                occurrences(lower, verb).map { position -> position to direction }
            }
            .minByOrNull { (position, _) -> kotlin.math.abs(position - anchor) }
        return nearest?.second ?: SmsTemplate.DIRECTION_EXPENSE
    }

    private fun occurrences(text: String, needle: String): List<Int> {
        val found = mutableListOf<Int>()
        var from = text.indexOf(needle)
        while (from >= 0) {
            found.add(from)
            from = text.indexOf(needle, from + 1)
        }
        return found
    }

    @Suppress("ReturnCount")
    private fun findPayee(body: String, lower: String, claims: List<Claim>): IntRange? {
        vpaPattern.find(body)?.range
            ?.takeIf { range -> claims.none { it.range overlaps range } }
            ?.let { return it }

        for (prefix in payeePrefixes) {
            var from = lower.indexOf(prefix)
            while (from >= 0) {
                val start = from + prefix.length
                val range = payeeRange(body, lower, start)
                if (range != null && claims.none { it.range overlaps range }) return range
                from = lower.indexOf(prefix, from + 1)
            }
        }
        return null
    }

    @Suppress("ReturnCount")
    private fun payeeRange(body: String, lower: String, start: Int): IntRange? {
        if (start >= body.length) return null
        val limit = (start + MAX_PAYEE_LENGTH).coerceAtMost(body.length)
        val window = lower.substring(start, limit)
        val stop = payeeStops
            .mapNotNull { stop -> window.indexOf(stop).takeIf { it > 0 } }
            .minOrNull()
            ?: window.length

        val text = body.substring(start, start + stop)
        val trimmed = text.trim()
        if (trimmed.length < MIN_PAYEE_LENGTH) return null
        if (trimmed.count { it.isLetter() } < MIN_LETTERS_IN_PAYEE) return null
        // A digit-heavy candidate is a reference number, not a name: the text
        // after "to:" is as often `UPI/533012345678` as it is a merchant.
        if (trimmed.count { it.isDigit() } >= trimmed.count { it.isLetter() }) return null
        if (trimmed.lowercase() in payeeBlocklist) return null
        if (trimmed.split(Regex("""\s+""")).first().lowercase() in payeeBlocklist) return null

        val offset = text.indexOf(trimmed)
        return (start + offset)..(start + offset + trimmed.length - 1)
    }

    /**
     * Rebuilds the message with every claim replaced by its token, and drops
     * the boilerplate that follows the last one - the "Not you? Call ..." tail
     * is reworded freely and would only make the template fragile.
     */
    private fun render(body: String, claims: List<Claim>): String {
        val ordered = claims.sortedBy { it.range.first }
        val out = StringBuilder()
        var cursor = 0
        for (claim in ordered) {
            if (claim.range.first < cursor) continue
            out.append(body, cursor, claim.range.first)
            out.append('{').append(claim.token.canonical).append('}')
            cursor = claim.range.last + 1
        }
        out.append(trimTail(body.substring(cursor.coerceAtMost(body.length))))
        return out.toString().trim()
    }

    private fun trimTail(tail: String): String {
        val sentenceEnd = tail.indexOfFirst { it == '.' || it == '\n' }
        val cut = when {
            sentenceEnd in 0..MAX_TAIL -> sentenceEnd
            tail.length > MAX_TAIL -> tail.lastIndexOf(' ', MAX_TAIL).takeIf { it > 0 }
                ?: MAX_TAIL

            else -> tail.length
        }
        return tail.substring(0, cut)
    }

    private fun label(senderFragment: String?, direction: String): String {
        val who = senderFragment ?: "Bank"
        val what = if (direction == SmsTemplate.DIRECTION_INCOME) "money in" else "money out"
        return "$who - $what"
    }

    private fun MutableList<Claim>.addIfFree(range: IntRange, token: TemplateToken) {
        if (none { it.range overlaps range }) add(Claim(range, token))
    }

    private infix fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last
}
