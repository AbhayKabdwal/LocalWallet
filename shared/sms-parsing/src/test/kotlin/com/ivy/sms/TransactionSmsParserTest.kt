package com.ivy.sms

import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import java.math.BigDecimal

class TransactionSmsParserTest {

    private val parser = TransactionSmsParser()

    private fun msg(body: String, sender: String = "AD-HDFCBK-S") = RawMessage(
        sourceId = "sms:1",
        sender = sender,
        body = body,
        receivedAtMillis = 0L,
    )

    private fun parseMatched(body: String, sender: String = "AD-HDFCBK-S"): ParsedTransaction {
        val result = parser.parse(msg(body, sender))
        result.shouldBeInstanceOf<ParseResult.Matched>()
        return result.transaction
    }

    private fun assertRejected(body: String, sender: String = "AD-HDFCBK-S") {
        parser.parse(msg(body, sender)).shouldBeInstanceOf<ParseResult.Rejected>()
    }

    // ---------------------------------------------------------------- positives

    @Test
    fun `parses a UPI debit`() {
        val txn = parseMatched(
            "Rs.1,249.00 has been debited from a/c XX1234 on 21-11-26 to SWIGGY. Ref No 123456789."
        )
        txn.direction shouldBe TxnDirection.Expense
        txn.amount shouldBe BigDecimal("1249.00")
        txn.currency shouldBe "INR"
        txn.accountHint shouldBe "1234"
        txn.merchant.shouldNotBeNull()
        txn.reference shouldBe "123456789"
    }

    @Test
    fun `parses a UPI credit`() {
        val txn = parseMatched(
            "INR 5,000.00 credited to a/c XX9876 on 20-11-26 from ACME PAYROLL. Ref 99887766."
        )
        txn.direction shouldBe TxnDirection.Income
        txn.amount shouldBe BigDecimal("5000.00")
        txn.accountHint shouldBe "9876"
    }

    @Test
    fun `parses a card spend`() {
        val txn = parseMatched(
            "Rs 340.50 spent on your HDFC Bank card ending 4321 at BLUE TOKAI on 19-11-26."
        )
        txn.direction shouldBe TxnDirection.Expense
        txn.amount shouldBe BigDecimal("340.50")
        txn.accountHint shouldBe "4321"
    }

    @Test
    fun `parses an ATM withdrawal`() {
        val txn = parseMatched("Rs.2000 withdrawn from a/c XX1234 at ATM on 18-11-26.")
        txn.direction shouldBe TxnDirection.Expense
        txn.amount shouldBe BigDecimal("2000")
    }

    @Test
    fun `falls back to a generic rule for an unlisted bank`() {
        val txn = parseMatched(
            body = "INR 150.00 debited from your account for UPI txn. Ref 4455.",
            sender = "VK-NEWBNK-S",
        )
        txn.direction shouldBe TxnDirection.Expense
        // Generic rules are low confidence so the review screen asks the user.
        (txn.confidence < 0.7) shouldBe true
    }

    // ---------------------------------------------------------------- negatives
    // A fabricated transaction silently corrupts the user's balance, while a
    // missed one is a minor annoyance. These cases matter more than the above.

    @Test
    fun `never parses an OTP message`() {
        assertRejected("123456 is your OTP for a transaction of Rs.4,999 at AMAZON. Do not share.")
        assertRejected("Your one time password is 889900. Never share this with anyone.")
        assertRejected("Use verification code 445566 to authorise Rs 2,000 debit.")
    }

    @Test
    fun `rejects promotional messages`() {
        assertRejected("Get cashback upto Rs.5,000! Apply now for a pre-approved loan. T&C apply.")
        assertRejected("Congratulations! You are eligible for Rs 2,00,000 instant loan. Click here.")
    }

    @Test
    fun `rejects balance enquiries`() {
        assertRejected("Available balance in a/c XX1234 is Rs.12,480.50 as on 21-11-26.")
    }

    @Test
    fun `rejects scheduled and bill-due notices`() {
        assertRejected("Rs.1,500 will be debited from a/c XX1234 on 25-11-26 towards your SIP.")
        assertRejected("Your credit card bill of Rs.8,420 is due on 28-11-26. Minimum due Rs.500.")
    }

    @Test
    fun `rejects failed and reversed transactions`() {
        assertRejected("Transaction of Rs.999 on card ending 4321 was declined.")
        assertRejected("Rs.250 debited on 18-11-26 has been reversed to a/c XX1234.")
    }

    @Test
    fun `rejects UPI collect requests`() {
        assertRejected("JOHN has requested Rs.500 via UPI. Approve in your app.")
    }

    // ---------------------------------------------------------------- amounts

    @Test
    fun `parses indian grouping`() {
        TransactionSmsParser.parseAmount("1,23,456.78") shouldBe BigDecimal("123456.78")
    }

    @Test
    fun `parses european grouping`() {
        TransactionSmsParser.parseAmount("1.234,56") shouldBe BigDecimal("1234.56")
    }

    @Test
    fun `rejects zero and malformed amounts`() {
        TransactionSmsParser.parseAmount("0") shouldBe null
        TransactionSmsParser.parseAmount("abc") shouldBe null
    }

    // ---------------------------------------------------------------- senders

    @Test
    fun `normalises indian sender headers`() {
        SenderFilter.normalize("AD-HDFCBK-S") shouldBe "HDFCBK"
        SenderFilter.normalize("VM-ICICIT") shouldBe "ICICIT"
        SenderFilter.normalize("JD-SBIINB-T") shouldBe "SBIINB"
    }

    @Test
    fun `recognises known banks`() {
        SenderFilter.isKnownBank("AD-HDFCBK-S") shouldBe true
        SenderFilter.isKnownBank("AX-KOTAKB-T") shouldBe true
        SenderFilter.isKnownBank("VM-PIZZAHUT") shouldBe false
    }
}
