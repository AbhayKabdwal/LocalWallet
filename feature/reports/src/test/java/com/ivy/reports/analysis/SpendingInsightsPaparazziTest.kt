package com.ivy.reports.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.ivy.base.legacy.Theme
import com.ivy.design.l0_system.UI
import com.ivy.legacy.IvyWalletPreview
import com.ivy.ui.testing.PaparazziScreenshotTest
import com.ivy.ui.testing.PaparazziTheme
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class SpendingInsightsPaparazziTest(
    @TestParameter
    private val theme: PaparazziTheme,
) : PaparazziScreenshotTest() {

    private val kept = SpendingInsightsCalculator.calculate(
        rows = listOf(
            SpendRow(45_000.0, isExpense = false, categoryName = null, title = "Salary", dayIndex = 1),
            SpendRow(9_800.0, isExpense = true, categoryName = "Food", title = "Groceries", dayIndex = 2),
            SpendRow(6_400.0, isExpense = true, categoryName = "Rent", title = "Rent", dayIndex = 3),
            SpendRow(3_100.0, isExpense = true, categoryName = "Transport", title = "Fuel", dayIndex = 4),
            SpendRow(2_000.0, isExpense = true, categoryName = null, title = "ATM", dayIndex = 5),
        ),
        days = 30,
    )

    private val overspent = SpendingInsightsCalculator.calculate(
        rows = listOf(
            SpendRow(10_000.0, isExpense = false, categoryName = null, title = "Salary", dayIndex = 1),
            SpendRow(14_500.0, isExpense = true, categoryName = "Travel", title = "Flight", dayIndex = 2),
        ),
        days = 30,
    )

    @Test
    fun `snapshot insights when money was kept`() {
        snapshotCard(kept)
    }

    @Test
    fun `snapshot insights when overspent`() {
        snapshotCard(overspent)
    }

    private fun snapshotCard(insights: SpendingInsights) {
        snapshot(theme) {
            IvyWalletPreview(
                theme = when (theme) {
                    PaparazziTheme.Light -> Theme.LIGHT
                    PaparazziTheme.Dark -> Theme.DARK
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(UI.colors.pure),
                ) {
                    SpendingInsightsCard(insights = insights, currency = "INR")
                }
            }
        }
    }
}
