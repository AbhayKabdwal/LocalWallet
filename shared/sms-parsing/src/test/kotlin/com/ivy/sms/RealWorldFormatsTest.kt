package com.ivy.sms

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import java.math.BigDecimal

/**
 * Message shapes taken from the wording the large Indian banks and UPI apps
 * actually send.
 *
 * The original rule set only matched `<currency><amount> ... <verb>`, so every
 * bank that writes the verb first ("debited by 150.0", "is debited with INR
 * 500") or omits the currency altogether (SBI) was silently dropped. Those are
 * the formats that made the feature look completely broken, so they are pinned
 * here.
 */
class RealWorldFormatsTest {

    private val parser = TransactionSmsParser()

    private fun parseMatched(body: String, sender: String): ParsedTransaction {
        val result = parser.parse(
            RawMessage(sourceId = "sms:1", sender = sender, body = body, receivedAtMillis = 0L),
        )
        result.shouldBeInstanceOf<ParseResult.Matched>()
        return result.transaction
    }

    @Test
    fun `SBI UPI debit without any currency token`() {
        val txn = parseMatched(
            body = "Dear UPI user A/C X1234 debited by 150.0 on date 21Nov25 trf to " +
                "SWIGGY Refno 533012345678. If not u? call 1800111109. -SBI",
            sender = "JD-SBIUPI-S",
        )
        txn.direction shouldBe TxnDirection.Expense
        txn.amount shouldBe BigDecimal("150.0")
        txn.accountHint shouldBe "1234"
    }

    @Test
    fun `SBI credit written verb-first`() {
        val txn = parseMatched(
            body = "Dear Customer, Your A/c XX1234-credited by Rs.5000 on 21Nov25 " +
                "transfer from JOHN DOE Ref No 123456 -SBI",
            sender = "JD-SBIINB-S",
        )
        txn.direction shouldBe TxnDirection.Income
        txn.amount shouldBe BigDecimal("5000")
        txn.accountHint shouldBe "1234"
    }

    @Test
    fun `ICICI debit with the amount after the verb`() {
        val txn = parseMatched(
            body = "Dear Customer, Acct XX123 is debited with INR 500.00 on 21-Nov-25 " +
                "and MERCHANT STORE is credited (UPI Ref no 533012345678).",
            sender = "VM-ICICIT-S",
        )
        txn.direction shouldBe TxnDirection.Expense
        txn.amount shouldBe BigDecimal("500.00")
    }

    @Test
    fun `HDFC UPI sent format`() {
        val txn = parseMatched(
            body = "Sent Rs.49.00 From HDFC Bank A/C x1234 To MERCHANT STORE On 21/11/25 " +
                "Ref 533012345678 Not You? Call 18002586161",
            sender = "AD-HDFCBK-S",
        )
        txn.direction shouldBe TxnDirection.Expense
        txn.amount shouldBe BigDecimal("49.00")
        txn.accountHint shouldBe "1234"
    }

    @Test
    fun `Axis debit header format`() {
        val txn = parseMatched(
            body = "Debit INR 500.00 A/c no. XX1234 21-11-25 11:30:00 " +
                "UPI/P2M/533012345678/MERCHANT",
            sender = "AD-AXISBK-S",
        )
        txn.direction shouldBe TxnDirection.Expense
        txn.amount shouldBe BigDecimal("500.00")
        txn.accountHint shouldBe "1234"
    }

    @Test
    fun `credit card purchase worded as use of the card`() {
        val txn = parseMatched(
            body = "Thank you for using your HDFC Bank Credit Card ending 4321 for " +
                "Rs.1,250.00 at BLUE TOKAI on 21-11-25.",
            sender = "AD-HDFCBK-S",
        )
        txn.direction shouldBe TxnDirection.Expense
        txn.amount shouldBe BigDecimal("1250.00")
        txn.accountHint shouldBe "4321"
    }

    @Test
    fun `Kotak debit naming the bank between from and the account`() {
        val txn = parseMatched(
            body = "Rs 500.00 debited from Kotak Bank A/c X1234 to merchant@upi on 21-11-25. " +
                "UPI Ref 533012345678.",
            sender = "AD-KOTAKB-S",
        )
        txn.direction shouldBe TxnDirection.Expense
        txn.amount shouldBe BigDecimal("500.00")
    }

    @Test
    fun `debit of phrasing`() {
        val txn = parseMatched(
            body = "A debit of Rs.2,000.00 has been made from your a/c XX1234 on 21-11-25.",
            sender = "AD-PNBSMS-S",
        )
        txn.direction shouldBe TxnDirection.Expense
        txn.amount shouldBe BigDecimal("2000.00")
    }
}
