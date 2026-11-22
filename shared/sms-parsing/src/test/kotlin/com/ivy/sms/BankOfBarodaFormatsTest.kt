package com.ivy.sms

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import java.math.BigDecimal

/**
 * Bank of Baroda's wording, which the shipped rules dropped completely.
 *
 * Two independent reasons: BOB words a UPI payment as a transfer *out of* the
 * account rather than a debit, and it masks the account with dots (`A/c
 * ...1234`) where the account pattern only allowed `x`, `*` and digits. Either
 * one alone was enough to make the feature look broken for this bank while
 * working for SBI on the same phone.
 */
class BankOfBarodaFormatsTest {

    private val parser = TransactionSmsParser()

    private fun parseMatched(body: String, sender: String = "AD-BOBTXN-S"): ParsedTransaction {
        val result = parser.parse(
            RawMessage(sourceId = "sms:1", sender = sender, body = body, receivedAtMillis = 0L),
        )
        result.shouldBeInstanceOf<ParseResult.Matched>()
        return result.transaction
    }

    @Test
    fun `UPI payment worded as a transfer out of the account`() {
        val txn = parseMatched(
            "Rs.149.00 transferred from A/c ...1234 to:UPI/533012345678. " +
                "Total Bal:Rs.5000.00CR. Avlbl Amt:Rs.5000.00(22-11-2025 11:00:00) " +
                "- Bank of Baroda",
        )
        txn.direction shouldBe TxnDirection.Expense
        txn.amount shouldBe BigDecimal("149.00")
        txn.accountHint shouldBe "1234"
        txn.balance shouldBe BigDecimal("5000.00")
    }

    @Test
    fun `credit into a dot-masked account`() {
        val txn = parseMatched(
            "Rs.500.00 Credited to A/c ...1234 thru UPI/CR/533012345678/JOHN by 1234. " +
                "Tot Bal:Rs.12345.67CR. Avlbl Amt:Rs.12345.67(22-11-2025 10:30:00) " +
                "- Bank of Baroda",
        )
        txn.direction shouldBe TxnDirection.Income
        txn.amount shouldBe BigDecimal("500.00")
        txn.accountHint shouldBe "1234"
        txn.balance shouldBe BigDecimal("12345.67")
    }

    @Test
    fun `a reference number is never used as the title`() {
        parseMatched(
            "Rs.500.00 Credited to A/c ...1234 thru UPI/CR/533012345678/JOHN by 1234. " +
                "Tot Bal:Rs.12345.67CR - Bank of Baroda",
        ).merchant shouldBe null
    }

    @Test
    fun `the ledger balance is preferred over the available one`() {
        // Holds are not transactions, so the available figure can sit below the
        // balance the wallet's own transactions add up to.
        MessageFacts.balance(
            "Rs.149.00 debited. Total Bal:Rs.5000.00CR. Avlbl Amt:Rs.4500.00",
        )?.amount shouldBe BigDecimal("5000.00")
    }

    @Test
    fun `a balance of zero is still a balance`() {
        MessageFacts.balance("A/c ...1234 debited by Rs.500. Avl Bal: Rs.0.00")
            ?.amount shouldBe BigDecimal("0.00")
    }

    @Test
    fun `an account masked with dots yields the last four digits`() {
        MessageFacts.accountHint("Your A/c ...1234 has been credited") shouldBe "1234"
        MessageFacts.accountHint("Acct XX5678 is debited") shouldBe "5678"
        MessageFacts.accountHint("Card ending 4321 was used") shouldBe "4321"
    }

    @Test
    fun `a one-time password is still never a transaction`() {
        val result = parser.parse(
            RawMessage(
                sourceId = "sms:2",
                sender = "AD-BOBTXN-S",
                body = "123456 is your OTP to transfer Rs.5000.00 from A/c ...1234. " +
                    "Do not share it with anyone.",
                receivedAtMillis = 0L,
            ),
        )
        result shouldBe ParseResult.Rejected(RejectReason.LooksLikeOtp)
    }
}
