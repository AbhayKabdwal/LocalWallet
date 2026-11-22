package com.ivy.sms

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import java.math.BigDecimal

class SmsTemplateTest {

    private fun template(
        text: String,
        direction: String = SmsTemplate.DIRECTION_EXPENSE,
        senderFragment: String? = null,
        currency: String? = null,
    ) = SmsTemplate(
        id = "t1",
        label = "test",
        senderFragment = senderFragment,
        template = text,
        direction = direction,
        currency = currency,
    )

    private fun rule(text: String, direction: String = SmsTemplate.DIRECTION_EXPENSE): SmsRule {
        val result = SmsTemplateCompiler.compile(template(text, direction))
        result.shouldBeInstanceOf<TemplateCompileResult.Ok>()
        return result.rule
    }

    private fun parseWith(rule: SmsRule, body: String, sender: String = "AD-BOBTXN-S") =
        TransactionSmsParser(userRules = listOf(rule)).parse(
            RawMessage(sourceId = "sms:1", sender = sender, body = body, receivedAtMillis = 0L),
        )

    @Test
    fun `a taught format reads a message the shipped rules reject`() {
        val body = "Pelo A/c 7788 kharch 250.50 Shop Kirana baki 4321.00"
        // Nothing in the shipped rule set speaks this language.
        TransactionSmsParser().parse(
            RawMessage(sourceId = "sms:1", sender = "AD-XYZBNK-S", body = body, receivedAtMillis = 0),
        ).shouldBeInstanceOf<ParseResult.Rejected>()

        val rule = rule("Pelo A/c {account} kharch {amount} Shop {payee} baki {balance}")
        val result = parseWith(rule, body, sender = "AD-XYZBNK-S")

        result.shouldBeInstanceOf<ParseResult.Matched>()
        result.transaction.amount shouldBe BigDecimal("250.50")
        result.transaction.accountHint shouldBe "7788"
        result.transaction.merchant shouldBe "Kirana"
        result.transaction.balance shouldBe BigDecimal("4321.00")
        result.transaction.confidence shouldBe SmsTemplateCompiler.USER_CONFIDENCE
    }

    @Test
    fun `the words the user reaches for are accepted as tokens`() {
        val rule = rule(
            "Rs.(deducted) sent from A/c (last4) to (merchant) on (date). " +
                "Amount left Rs.(amount_left)",
        )
        val result = parseWith(
            rule,
            "Rs.75.25 sent from A/c XX9090 to CHAI POINT on 22-11-2025. " +
                "Amount left Rs.1200.00",
        )
        result.shouldBeInstanceOf<ParseResult.Matched>()
        result.transaction.amount shouldBe BigDecimal("75.25")
        result.transaction.accountHint shouldBe "9090"
        result.transaction.merchant shouldBe "CHAI POINT"
        result.transaction.balance shouldBe BigDecimal("1200.00")
    }

    @Test
    fun `numbers the user did not mark do not get baked in`() {
        // The date and the reference differ on every message, so a template
        // that only marked the amount still has to match the next one.
        val rule = rule("Txn of Rs.{amount} on 21-11-2025 at SHOP ref 998877")
        parseWith(rule, "Txn of Rs.10.00 on 25-12-2026 at SHOP ref 112233")
            .shouldBeInstanceOf<ParseResult.Matched>()
    }

    @Test
    fun `a stated balance may appear twice`() {
        // Java's regex rejects a repeated group name, and banks really do state
        // the balance twice, so the repeat has to compile to a plain match.
        val rule = rule("Rs.{amount} paid. Tot Bal Rs.{balance} Avl Amt Rs.{balance}")
        val result = parseWith(rule, "Rs.20.00 paid. Tot Bal Rs.500.00 Avl Amt Rs.450.00")
        result.shouldBeInstanceOf<ParseResult.Matched>()
        result.transaction.balance shouldBe BigDecimal("500.00")
    }

    @Test
    fun `a template without an amount is refused`() {
        val result = SmsTemplateCompiler.compile(template("A/c {account} was touched"))
        result.shouldBeInstanceOf<TemplateCompileResult.Error>()
        result.message shouldContain "{amount}"
    }

    @Test
    fun `a template with almost no wording of its own is refused`() {
        // `{amount}` alone would match an OTP, a promotional offer and a bill
        // reminder equally well.
        SmsTemplateCompiler.compile(template("Rs {amount}"))
            .shouldBeInstanceOf<TemplateCompileResult.Error>()
    }

    @Test
    fun `a bracketed word that is not a token stays literal text`() {
        val rule = rule("Rs.{amount} debited (UPI Ref no {ref}) from A/c {account}")
        val matched = parseWith(rule, "Rs.5.00 debited (UPI Ref no 533012345678) from A/c XX1111")
        matched.shouldBeInstanceOf<ParseResult.Matched>()
        matched.transaction.ruleId shouldBe "user.t1"

        // Square brackets where the template had round ones: the taught format
        // must not match, which leaves a shipped rule to pick it up.
        val fellBack = parseWith(rule, "Rs.5.00 debited [UPI Ref no 533012345678] from A/c XX1111")
        fellBack.shouldBeInstanceOf<ParseResult.Matched>()
        fellBack.transaction.ruleId shouldNotBe "user.t1"
    }

    @Test
    fun `a taught format only applies to the sender it was taught for`() {
        val compiled = SmsTemplateCompiler.compile(
            template("Pelo A/c {account} kharch {amount} baki here", senderFragment = "BOB"),
        )
        compiled.shouldBeInstanceOf<TemplateCompileResult.Ok>()
        val body = "Pelo A/c 7788 kharch 250.50 baki here"

        parseWith(compiled.rule, body, sender = "AD-BOBTXN-S")
            .shouldBeInstanceOf<ParseResult.Matched>()
        parseWith(compiled.rule, body, sender = "AD-HDFCBK-S")
            .shouldBeInstanceOf<ParseResult.Rejected>()
    }

    @Test
    fun `a taught format cannot turn a one-time password into a transaction`() {
        val rule = rule("Your code is {ref} for the amount of Rs.{amount} transfer")
        parseWith(
            rule,
            "Your code is 998877 for the amount of Rs.5000.00 transfer. Do not share it.",
        ) shouldBe ParseResult.Rejected(RejectReason.LooksLikeOtp)
    }

    @Test
    fun `a taught format cannot import a payment that failed`() {
        val rule = rule("Payment of Rs.{amount} from A/c {account} was declined today")
        parseWith(
            rule,
            "Payment of Rs.500.00 from A/c XX1234 was declined today",
        ) shouldBe ParseResult.Rejected(RejectReason.FailedOrReversed)
    }

    @Test
    fun `direction comes from the template, not from the wording`() {
        val rule = rule(
            text = "Received {amount} into A/c {account} from somewhere",
            direction = SmsTemplate.DIRECTION_INCOME,
        )
        val result = parseWith(rule, "Received 900.00 into A/c XX2222 from somewhere")
        result.shouldBeInstanceOf<ParseResult.Matched>()
        result.transaction.direction shouldBe TxnDirection.Income
    }

    @Test
    fun `token names are matched case and spacing insensitively`() {
        TemplateToken.fromName("Amount Left") shouldBe TemplateToken.Balance
        TemplateToken.fromName("ACCT") shouldBe TemplateToken.Account
        TemplateToken.fromName("*") shouldBe TemplateToken.Anything
        TemplateToken.fromName("not-a-token") shouldBe null
    }

    @Test
    fun `a taught format beats a shipped one for the same message`() {
        val body = "Rs.149.00 transferred from A/c ...1234 to:UPI/533012345678"
        val shipped = TransactionSmsParser().parse(
            RawMessage(sourceId = "s", sender = "AD-BOBTXN-S", body = body, receivedAtMillis = 0),
        )
        shipped.shouldBeInstanceOf<ParseResult.Matched>()

        val taught = parseWith(
            rule("Rs.{amount} transferred from A/c {account} to:UPI/{ref}"),
            body,
        )
        taught.shouldBeInstanceOf<ParseResult.Matched>()
        taught.transaction.ruleId shouldNotBe shipped.transaction.ruleId
        taught.transaction.ruleId shouldBe "user.t1"
    }
}
