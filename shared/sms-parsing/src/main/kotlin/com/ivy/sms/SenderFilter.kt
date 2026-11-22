package com.ivy.sms

/**
 * Decides whether a sender is plausibly a bank.
 *
 * Indian bank SMS arrive from alphanumeric headers with a two-letter operator
 * prefix and a one-letter category suffix (`AD-HDFCBK-S`, `VM-ICICIT`,
 * `JD-SBIINB`), so the raw sender has to be normalised before matching.
 *
 * Matching is two-stage on purpose: a bundled list of known headers, and - for
 * everyone else's bank - a fall back to whether the body itself looks
 * transactional. Without the second stage the feature would only work for banks
 * that happen to be on the list.
 */
object SenderFilter {

    private val operatorPrefix = Regex("^[A-Z]{2}-")
    private val categorySuffix = Regex("-[A-Z]$")

    /** Known bank/payment header fragments. Contributors can extend this freely. */
    private val knownSenders = setOf(
        // India - banks
        "HDFCBK", "HDFCB", "ICICIB", "ICICIT", "ICICI", "SBIINB", "SBIUPI", "SBICRD",
        "ATMSBI", "AXISBK", "AXISB", "KOTAKB", "KOTAK", "YESBNK", "INDUSB", "IDFCFB",
        "PNBSMS", "BOIIND", "CANBNK", "UNIONB", "CBSSBI", "FEDBNK", "RBLBNK", "AUBANK",
        "BANDHN", "CITIBK", "HSBCIN", "SCBANK", "DBSBNK", "EQUTAS", "UCOBNK", "IOBCHN",
        "CENTBK", "MAHABK", "KARBNK", "KVBANK", "TMBANK", "DCBBNK", "ESAFBK", "JANAFS",
        // Bank of Baroda, under every header it sends from. It was missing
        // from this list entirely, so its messages only ever got through on
        // the body heuristic - and that heuristic rejected the wording it
        // actually uses.
        "BOBTXN", "BOBSMS", "BOBIND", "BOBBNK", "BOBCRD", "BOBALERT", "CBSBOB", "BARODA",
        "BOBIBN", "BOBUPI",
        // India - wallets / UPI
        "PAYTM", "PHONPE", "PHONEPE", "GPAY", "AMZNPAY", "MOBIKW", "FREECH", "SLICEIT",
        "CRED", "JUPITR", "FICRED", "ONECRD", "BAJAJF",
        // Generic / international
        "PAYPAL", "WISE", "REVOLUT", "MONZO", "STARLING", "CHASE", "AMEX", "CITI",
        "BOFA", "WELLSF", "BARCLAY", "NATWEST", "LLOYDS", "HALIFAX", "SANTAN",
    )

    fun normalize(rawSender: String): String =
        rawSender.uppercase()
            .trim()
            .replace(operatorPrefix, "")
            .replace(categorySuffix, "")
            .replace("-", "")

    fun isKnownBank(rawSender: String): Boolean {
        val normalized = normalize(rawSender)
        return knownSenders.any { normalized.contains(it) }
    }

    /**
     * Accept if the sender is recognised, or if the body is clearly a
     * transaction notification even though the sender is not on the list.
     */
    fun accepts(rawSender: String, body: String): Boolean =
        isKnownBank(rawSender) || MessageClassifier.looksTransactional(body)
}
