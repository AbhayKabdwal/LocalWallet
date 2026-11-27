package com.ivy.reports.analysis

import androidx.compose.runtime.Immutable
import kotlin.math.absoluteValue

/**
 * One transaction, reduced to what the analysis needs.
 *
 * Amounts are already converted to the wallet's base currency by the caller -
 * mixing currencies in a total is the one mistake that would make every figure
 * on the screen quietly wrong.
 */
data class SpendRow(
    val amount: Double,
    val isExpense: Boolean,
    val categoryName: String?,
    val title: String?,
    val dayIndex: Long,
)

@Immutable
data class CategoryShare(
    val name: String,
    val amount: Double,
    /** 0..1 of total spending. */
    val share: Double,
)

@Immutable
data class LargestExpense(
    val title: String,
    val amount: Double,
)

/**
 * What the reports screen can tell the user about their spending.
 *
 * Totals alone answer "how much", which they already knew. These answer
 * "where did it go, how fast am I spending it, and did I keep any of it" -
 * the questions that change behaviour.
 */
@Immutable
data class SpendingInsights(
    val expenseTotal: Double,
    val incomeTotal: Double,
    /** Share of income not spent; null when there was no income to keep. */
    val savingsRate: Double?,
    val dailyAverageSpend: Double,
    val expenseCount: Int,
    val averageExpense: Double,
    val largestExpense: LargestExpense?,
    val topCategories: List<CategoryShare>,
    val uncategorisedShare: Double,
) {
    val hasAnything: Boolean
        get() = expenseCount > 0 || incomeTotal > 0.0

    /** True when the period cost more than it brought in. */
    val overspent: Boolean
        get() = expenseTotal > incomeTotal && incomeTotal > 0.0
}

object SpendingInsightsCalculator {

    const val UNCATEGORISED = "Uncategorised"
    private const val TOP_CATEGORIES = 5

    /**
     * [days] is the length of the period being reported on, so the daily
     * average describes the period the user chose rather than only the days
     * that happen to have a transaction on them. A month with one purchase
     * should read as a very low daily average, not as the price of that one
     * purchase.
     */
    fun calculate(rows: List<SpendRow>, days: Int): SpendingInsights {
        val expenses = rows.filter { it.isExpense }
        val incomes = rows.filterNot { it.isExpense }

        val expenseTotal = expenses.sumOf { it.amount.absoluteValue }
        val incomeTotal = incomes.sumOf { it.amount.absoluteValue }

        val byCategory = expenses
            .groupBy { it.categoryName?.takeIf(String::isNotBlank) ?: UNCATEGORISED }
            .mapValues { (_, rows) -> rows.sumOf { it.amount.absoluteValue } }

        val topCategories = byCategory.entries
            .sortedByDescending { it.value }
            .take(TOP_CATEGORIES)
            .map { (name, amount) ->
                CategoryShare(
                    name = name,
                    amount = amount,
                    share = shareOf(amount, expenseTotal),
                )
            }

        val largest = expenses.maxByOrNull { it.amount.absoluteValue }

        return SpendingInsights(
            expenseTotal = expenseTotal,
            incomeTotal = incomeTotal,
            savingsRate = if (incomeTotal > 0.0) {
                (incomeTotal - expenseTotal) / incomeTotal
            } else {
                null
            },
            dailyAverageSpend = if (days > 0) expenseTotal / days else expenseTotal,
            expenseCount = expenses.size,
            averageExpense = if (expenses.isNotEmpty()) {
                expenseTotal / expenses.size
            } else {
                0.0
            },
            largestExpense = largest?.let {
                LargestExpense(
                    title = it.title?.takeIf(String::isNotBlank)
                        ?: it.categoryName?.takeIf(String::isNotBlank)
                        ?: UNCATEGORISED,
                    amount = it.amount.absoluteValue,
                )
            },
            topCategories = topCategories,
            uncategorisedShare = shareOf(byCategory[UNCATEGORISED] ?: 0.0, expenseTotal),
        )
    }

    private fun shareOf(amount: Double, total: Double): Double =
        if (total > 0.0) amount / total else 0.0
}
