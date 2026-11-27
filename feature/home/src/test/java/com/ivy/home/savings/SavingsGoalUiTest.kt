package com.ivy.home.savings

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.util.UUID

class SavingsGoalUiTest {

    private fun goal(saved: Double, target: Double) = SavingsGoalUi(
        id = UUID.randomUUID(),
        name = "New phone",
        saved = saved,
        target = target,
        currency = "INR",
    )

    @Test
    fun `progress is the fraction saved`() {
        goal(saved = 18000.0, target = 65000.0).percent shouldBe 28
        goal(saved = 0.0, target = 65000.0).percent shouldBe 0
    }

    @Test
    fun `overshooting a goal does not overflow the bar`() {
        // Putting aside more than the target is normal - prices change - and
        // a progress bar drawn past its end looks broken.
        val overshot = goal(saved = 70000.0, target = 65000.0)
        overshot.progress shouldBe 1f
        overshot.percent shouldBe 100
        overshot.reached shouldBe true
        overshot.remaining shouldBe 0.0
    }

    @Test
    fun `a goal with no target does not divide by zero`() {
        val noTarget = goal(saved = 500.0, target = 0.0)
        noTarget.progress shouldBe 0f
        noTarget.reached shouldBe false
    }

    @Test
    fun `remaining is what is still to be found`() {
        goal(saved = 18000.0, target = 65000.0).remaining shouldBe 47000.0
    }

    @Test
    fun `exactly reaching the target counts as reached`() {
        val exact = goal(saved = 12000.0, target = 12000.0)
        exact.reached shouldBe true
        exact.percent shouldBe 100
    }

    @Test
    fun `totals add up across goals`() {
        val state = SavingsGoalsState(
            goals = kotlinx.collections.immutable.persistentListOf(
                goal(saved = 18000.0, target = 65000.0),
                goal(saved = 12000.0, target = 12000.0),
            ),
            currency = "INR",
        )
        state.totalSaved shouldBe 30000.0
        state.totalTarget shouldBe 77000.0
    }
}
