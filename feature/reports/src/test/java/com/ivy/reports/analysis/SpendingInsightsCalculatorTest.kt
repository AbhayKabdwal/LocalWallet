package com.ivy.reports.analysis

import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class SpendingInsightsCalculatorTest {

    private fun expense(
        amount: Double,
        category: String? = null,
        title: String? = null,
        day: Long = 0,
    ) = SpendRow(
        amount = amount,
        isExpense = true,
        categoryName = category,
        title = title,
        dayIndex = day,
    )

    private fun income(amount: Double, day: Long = 0) = SpendRow(
        amount = amount,
        isExpense = false,
        categoryName = null,
        title = null,
        dayIndex = day,
    )

    @Test
    fun `savings rate is the share of income not spent`() {
        val insights = SpendingInsightsCalculator.calculate(
            rows = listOf(income(10_000.0), expense(2_500.0)),
            days = 30,
        )
        insights.savingsRate!!.shouldBeWithinPercentageOf(0.75, 0.001)
        insights.overspent shouldBe false
    }

    @Test
    fun `spending more than you earned is flagged`() {
        val insights = SpendingInsightsCalculator.calculate(
            rows = listOf(income(1_000.0), expense(1_500.0)),
            days = 30,
        )
        insights.overspent shouldBe true
        insights.savingsRate!!.shouldBeWithinPercentageOf(-0.5, 0.001)
    }

    @Test
    fun `no income means there is no savings rate to report`() {
        // Not zero: "you saved 0% of nothing" is a different and misleading
        // claim from "there is nothing to work this out from".
        SpendingInsightsCalculator.calculate(
            rows = listOf(expense(500.0)),
            days = 30,
        ).savingsRate.shouldBeNull()
    }

    @Test
    fun `the daily average covers the whole period, not just active days`() {
        // One purchase in a month is a low daily spend, not a 3000 per day
        // habit - averaging over active days only would say the latter.
        val insights = SpendingInsightsCalculator.calculate(
            rows = listOf(expense(3_000.0, day = 5)),
            days = 30,
        )
        insights.dailyAverageSpend.shouldBeWithinPercentageOf(100.0, 0.001)
    }

    @Test
    fun `categories are ranked by spend with their share`() {
        val insights = SpendingInsightsCalculator.calculate(
            rows = listOf(
                expense(600.0, category = "Food"),
                expense(300.0, category = "Food"),
                expense(100.0, category = "Transport"),
            ),
            days = 30,
        )
        insights.topCategories.map { it.name } shouldBe listOf("Food", "Transport")
        insights.topCategories.first().amount shouldBe 900.0
        insights.topCategories.first().share.shouldBeWithinPercentageOf(0.9, 0.001)
    }

    @Test
    fun `only the five biggest categories are reported`() {
        val insights = SpendingInsightsCalculator.calculate(
            rows = (1..8).map { expense(it * 100.0, category = "Cat$it") },
            days = 30,
        )
        insights.topCategories.size shouldBe 5
        insights.topCategories.first().name shouldBe "Cat8"
    }

    @Test
    fun `spending with no category is surfaced rather than hidden`() {
        // The actionable insight here is "categorise your transactions", so
        // the share has to be visible instead of silently lumped in.
        val insights = SpendingInsightsCalculator.calculate(
            rows = listOf(
                expense(750.0),
                expense(250.0, category = "Food"),
            ),
            days = 30,
        )
        insights.uncategorisedShare.shouldBeWithinPercentageOf(0.75, 0.001)
        insights.topCategories.first().name shouldBe SpendingInsightsCalculator.UNCATEGORISED
    }

    @Test
    fun `the largest expense is named`() {
        val insights = SpendingInsightsCalculator.calculate(
            rows = listOf(
                expense(200.0, title = "Groceries"),
                expense(9_999.0, title = "Flight"),
            ),
            days = 30,
        )
        insights.largestExpense?.title shouldBe "Flight"
        insights.largestExpense?.amount shouldBe 9_999.0
    }

    @Test
    fun `an untitled largest expense falls back to its category`() {
        SpendingInsightsCalculator.calculate(
            rows = listOf(expense(500.0, category = "Rent")),
            days = 30,
        ).largestExpense?.title shouldBe "Rent"
    }

    @Test
    fun `expense amounts are treated as magnitudes`() {
        // Expenses reach this from two code paths, one of which signs them
        // negative; a mix must not cancel out into a smaller total.
        val insights = SpendingInsightsCalculator.calculate(
            rows = listOf(expense(-300.0), expense(200.0)),
            days = 10,
        )
        insights.expenseTotal shouldBe 500.0
        insights.averageExpense shouldBe 250.0
    }

    @Test
    fun `an empty period reports nothing rather than dividing by zero`() {
        val insights = SpendingInsightsCalculator.calculate(rows = emptyList(), days = 0)
        insights.hasAnything shouldBe false
        insights.dailyAverageSpend shouldBe 0.0
        insights.averageExpense shouldBe 0.0
        insights.uncategorisedShare shouldBe 0.0
        insights.largestExpense.shouldBeNull()
    }
}
