package com.ivy.home.savings

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
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * The savings goals block, which lives inside the expanded home menu.
 *
 * Snapshotted on its own rather than through the menu: the menu needs an
 * `IvyWalletCtx` and a measured `BoxWithConstraints` behind it, while this
 * section takes nothing but its state - so it is the part that can actually be
 * pinned, and it is the part that is new.
 */
@RunWith(TestParameterInjector::class)
class SavingsGoalsPaparazziTest(
    @TestParameter
    private val theme: PaparazziTheme,
) : PaparazziScreenshotTest() {

    private val goals = SavingsGoalsState(
        goals = persistentListOf(
            SavingsGoalUi(
                id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                name = "New phone",
                saved = 18000.0,
                target = 65000.0,
                currency = "INR",
            ),
            SavingsGoalUi(
                id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                name = "Monitor",
                saved = 12000.0,
                target = 12000.0,
                currency = "INR",
            ),
        ),
        currency = "INR",
    )

    @Test
    fun `snapshot savings goals`() {
        snapshotSection(goals)
    }

    @Test
    fun `snapshot no savings goals yet`() {
        snapshotSection(SavingsGoalsState(goals = persistentListOf(), currency = "INR"))
    }

    /**
     * [IvyWalletPreview] supplies the legacy `UI.colors` locals, which this
     * section reads. Without it the composition fails with "No IvyColors"
     * rather than rendering unthemed.
     */
    private fun snapshotSection(state: SavingsGoalsState) {
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
                    SavingsGoalsSection(state = state, onEvent = {})
                }
            }
        }
    }
}
