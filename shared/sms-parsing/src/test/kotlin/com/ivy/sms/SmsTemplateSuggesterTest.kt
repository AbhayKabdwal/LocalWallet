package com.ivy.sms

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import java.math.BigDecimal

/**
 * The suggester is what makes teaching a format a ten-second job rather than a
 * regex exercise, so the test that matters is end to end: take one message,
 * suggest a template from it, and check the compiled result reads a *different*
 * message of the same shape. A template that only matches the example it came
 * from is worse than none, because it looks like it worked.
 */
class SmsTemplateSuggesterTest {

    private fun suggestionFor(body: String, sender: String): SmsTemplate =
        SmsTemplateSuggester.suggest(
            RawMessage(sourceId = "sms:1", sender = sender, body = body, receivedAtMillis = 0L),
            id = "t1",
        )

    private fun parseWith(template: SmsTemplate, body: String, sender: String): ParseResult {
        val compiled = SmsTemplateCompiler.compile(template)
        compiled.shouldBeInstanceOf<TemplateCompileResult.Ok>()
        return TransactionSmsParser(userRules = listOf(compiled.rule)).parse(
            RawMessage(sourceId = "sms:2", sender = sender, body = body, receivedAtMillis = 0L),
        )
    }

    @Test
    fun `a Bank of Baroda debit suggests a template that reads the next one`() {
        val sender = "AD-BOBTXN-S"
        val suggestion = suggestionFor(
            body = "Rs.149.00 transferred from A/c ...1234 to:UPI/533012345678. " +
                "Total Bal:Rs.5000.00CR. Avlbl Amt:Rs.5000.00(22-11-2025 11:00:00) " +
                "- Bank of Baroda",
            sender = sender,
        )

        suggestion.direction shouldBe SmsTemplate.DIRECTION_EXPENSE
        suggestion.currency shouldBe "INR"
        suggestion.template shouldContain "{amount}"
        suggestion.template shouldContain "{account}"
        suggestion.template shouldContain "{balance}"
        // The values from the example must not survive into the template.
        suggestion.template shouldNotContain "149.00"
        suggestion.template shouldNotContain "1234 to"

        val next = parseWith(
            template = suggestion,
            body = "Rs.2,340.50 transferred from A/c ...1234 to:UPI/987654321012. " +
                "Total Bal:Rs.2659.50CR. Avlbl Amt:Rs.2659.50(23-11-2025 09:05:11) " +
                "- Bank of Baroda",
            sender = sender,
        )
        next.shouldBeInstanceOf<ParseResult.Matched>()
        next.transaction.amount shouldBe BigDecimal("2340.50")
        next.transaction.accountHint shouldBe "1234"
        next.transaction.balance shouldBe BigDecimal("2659.50")
        next.transaction.direction shouldBe TxnDirection.Expense
    }

    @Test
    fun `a credit suggests an income template`() {
        val suggestion = suggestionFor(
            body = "Rs.500.00 Credited to A/c ...9876 thru UPI/CR/533012345678/JOHN by 1234. " +
                "Tot Bal:Rs.12345.67CR. Avlbl Amt:Rs.12345.67(22-11-2025 10:30:00) " +
                "- Bank of Baroda",
            sender = "VM-BOBSMS",
        )
        suggestion.direction shouldBe SmsTemplate.DIRECTION_INCOME

        val next = parseWith(
            template = suggestion,
            body = "Rs.75.00 Credited to A/c ...9876 thru UPI/CR/111222333444/JANE by 9999. " +
                "Tot Bal:Rs.12420.67CR. Avlbl Amt:Rs.12420.67(24-11-2025 08:00:00) " +
                "- Bank of Baroda",
            sender = "VM-BOBSMS",
        )
        next.shouldBeInstanceOf<ParseResult.Matched>()
        next.transaction.direction shouldBe TxnDirection.Income
        next.transaction.amount shouldBe BigDecimal("75.00")
    }

    @Test
    fun `a varying greeting does not pin the template to one message`() {
        // SBI writes "Dear UPI user" on one alert and "Dear Customer," on the
        // next; keeping either as literal text would reject the other.
        val suggestion = suggestionFor(
            body = "Dear UPI user A/C X1234 debited by 150.0 on date 21Nov25 trf to " +
                "SWIGGY Refno 533012345678. If not u? call 1800111109. -SBI",
            sender = "JD-SBIUPI-S",
        )
        suggestion.template shouldContain "{any}"
        suggestion.senderFragment shouldBe "SBI"

        val next = parseWith(
            template = suggestion,
            body = "Dear Customer, A/C X1234 debited by 2500.0 on date 22Nov25 trf to " +
                "AMAZON Refno 111222333444. If not u? call 1800111109. -SBI",
            sender = "JD-SBIINB-S",
        )
        next.shouldBeInstanceOf<ParseResult.Matched>()
        next.transaction.amount shouldBe BigDecimal("2500.0")
        next.transaction.merchant shouldBe "AMAZON"
    }

    @Test
    fun `the suggested sender fragment is the stem the bank texts under`() {
        SmsTemplateSuggester.senderFragment("AD-BOBTXN-S") shouldBe "BOB"
        SmsTemplateSuggester.senderFragment("VM-BOBSMS") shouldBe "BOB"
        SmsTemplateSuggester.senderFragment("AD-HDFCBK-S") shouldBe "HDFC"
        SmsTemplateSuggester.senderFragment("JD-SBIUPI-S") shouldBe "SBI"
        SmsTemplateSuggester.senderFragment("AX-AXISBK") shouldBe "AXIS"
        // A bank that texts from a plain number gives nothing to match on.
        SmsTemplateSuggester.senderFragment("+919812345678") shouldBe null
    }

    @Test
    fun `a message with no amount at all still produces an editable draft`() {
        val suggestion = suggestionFor(
            body = "Namaste. Aapke khate me jama hua hai.",
            sender = "AD-XYZBNK-S",
        )
        // No amount to find, so this cannot compile yet - the user is told
        // what to mark rather than handed a silent failure.
        SmsTemplateCompiler.compile(suggestion)
            .shouldBeInstanceOf<TemplateCompileResult.Error>()
    }
}
