package com.ivy.reports.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.utils.format
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.Red
import kotlin.math.roundToInt

/**
 * The "where did it actually go" panel.
 *
 * The report already showed income, expenses and a balance, which tells the
 * user what they could have added up themselves. What was missing was any
 * reading of it: whether they kept any of what came in, how fast they are
 * spending, which few categories account for most of it, and how much is
 * sitting uncategorised and therefore unexplained.
 */
@Composable
fun SpendingInsightsCard(
    insights: SpendingInsights,
    currency: String,
    modifier: Modifier = Modifier,
) {
    if (!insights.hasAnything) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(UI.colors.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "How you used your money",
            style = UI.typo.b1.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )

        Headline(insights = insights, currency = currency)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(
                modifier = Modifier.weight(1f),
                label = "Per day",
                value = insights.dailyAverageSpend.format(currency),
            )
            Stat(
                modifier = Modifier.weight(1f),
                label = "Typical expense",
                value = insights.averageExpense.format(currency),
            )
            Stat(
                modifier = Modifier.weight(1f),
                label = "Expenses",
                value = insights.expenseCount.toString(),
            )
        }

        insights.largestExpense?.let { largest ->
            Text(
                text = "Biggest single expense: ${largest.title} - " +
                    "${largest.amount.format(currency)} $currency",
                style = UI.typo.c.style(color = Gray),
            )
        }

        if (insights.topCategories.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Where it went",
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold,
                ),
            )
            insights.topCategories.forEach { category ->
                CategoryBar(category = category, currency = currency)
            }
        }

        // Spending with no category is the one number here the user can act
        // on immediately, so it is called out rather than left implicit in a
        // bar labelled "Uncategorised".
        if (insights.uncategorisedShare >= UNCATEGORISED_NAG) {
            Text(
                text = "${percent(insights.uncategorisedShare)}% of your spending has " +
                    "no category. Categorising it would make this breakdown useful.",
                style = UI.typo.c.style(color = Gray),
            )
        }
    }
}

@Composable
private fun Headline(insights: SpendingInsights, currency: String) {
    val rate = insights.savingsRate
    val text = when {
        rate == null ->
            "No income recorded in this period, so there is nothing to " +
                "compare your spending against."

        rate >= 0 ->
            "You kept ${percent(rate)}% of what came in - " +
                "${(insights.incomeTotal - insights.expenseTotal).format(currency)} " +
                "$currency of ${insights.incomeTotal.format(currency)} $currency."

        else ->
            "You spent ${percent(-rate)}% more than came in - " +
                "${(insights.expenseTotal - insights.incomeTotal).format(currency)} " +
                "$currency beyond your income."
    }

    Text(
        text = text,
        style = UI.typo.b2.style(
            color = when {
                rate == null -> Gray
                rate >= 0 -> Green
                else -> Red
            },
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = UI.typo.b2.style(
                color = UI.colors.pureInverse,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
        Text(text = label, style = UI.typo.c.style(color = Gray))
    }
}

@Composable
private fun CategoryBar(category: CategoryShare, currency: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = category.name,
                style = UI.typo.c.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${category.amount.format(currency)} $currency",
                style = UI.typo.c.style(color = UI.colors.pureInverse),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${percent(category.share)}%",
                style = UI.typo.c.style(color = Gray),
            )
        }

        // Drawn by hand rather than with a progress indicator so the bar can
        // be exactly as wide as the share it represents, with no minimum
        // width making a 1% category look like a 5% one.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(UI.colors.pure),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(category.share.toFloat().coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Ivy),
            )
        }
    }
}

private const val UNCATEGORISED_NAG = 0.15
private const val PERCENT = 100

private fun percent(fraction: Double): Int = (fraction * PERCENT).roundToInt()
