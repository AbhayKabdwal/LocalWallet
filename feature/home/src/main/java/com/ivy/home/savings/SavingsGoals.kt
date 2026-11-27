package com.ivy.home.savings

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import java.util.UUID
import kotlin.math.roundToInt

/**
 * A savings goal as the home menu shows it.
 *
 * The progress arithmetic lives here rather than in the composable so it can
 * be tested without rendering anything, and so the screen cannot disagree with
 * itself about what "87%" means.
 */
@Immutable
data class SavingsGoalUi(
    val id: UUID,
    val name: String,
    val saved: Double,
    val target: Double,
    val currency: String,
) {
    /** Clamped to 1.0: a goal can be overshot, a progress bar cannot. */
    val progress: Float
        get() = when {
            target <= 0.0 -> 0f
            else -> (saved / target).coerceIn(0.0, 1.0).toFloat()
        }

    val percent: Int
        get() = (progress * PERCENT).roundToInt()

    val remaining: Double
        get() = (target - saved).coerceAtLeast(0.0)

    val reached: Boolean
        get() = target > 0.0 && saved >= target

    private companion object {
        const val PERCENT = 100
    }
}

@Immutable
data class SavingsGoalsState(
    val goals: ImmutableList<SavingsGoalUi>,
    val currency: String,
) {
    val totalSaved: Double get() = goals.sumOf { it.saved }
    val totalTarget: Double get() = goals.sumOf { it.target }
}

sealed interface SavingsGoalEvent {
    data class Add(
        val name: String,
        val target: Double,
        val saved: Double,
    ) : SavingsGoalEvent

    /** Add to (or, with a negative amount, take back from) what is put aside. */
    data class Contribute(val id: UUID, val amount: Double) : SavingsGoalEvent

    data class Edit(
        val id: UUID,
        val name: String,
        val target: Double,
        val saved: Double,
    ) : SavingsGoalEvent

    data class Delete(val id: UUID) : SavingsGoalEvent
}
