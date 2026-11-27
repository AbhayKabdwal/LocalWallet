package com.ivy.home.savings

import com.ivy.data.db.dao.read.SavingsGoalDao
import com.ivy.data.db.dao.write.WriteSavingsGoalDao
import com.ivy.data.db.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the user's savings goals.
 *
 * Amounts are stored as plain doubles to match the rest of the wallet's
 * schema; the clamping and ordering rules live here so no caller can write a
 * negative target or a saved amount below zero.
 */
@Singleton
class SavingsGoalRepository @Inject constructor(
    private val dao: SavingsGoalDao,
    private val writeDao: WriteSavingsGoalDao,
) {
    val goals: Flow<List<SavingsGoalEntity>> = dao.findAllFlow()

    suspend fun add(name: String, target: Double, saved: Double, currency: String?) {
        val nextOrder = (dao.findMaxOrderNum() ?: 0.0) + 1.0
        writeDao.save(
            SavingsGoalEntity(
                name = name.trim(),
                targetAmount = target.coerceAtLeast(0.0),
                savedAmount = saved.coerceAtLeast(0.0),
                currency = currency,
                colorArgb = null,
                iconName = null,
                targetDateMillis = null,
                archived = false,
                orderNum = nextOrder,
            ),
        )
    }

    /**
     * Moves [amount] into the goal, or out of it when negative.
     *
     * Never goes below zero: "I put aside 500 too much" is a correction, not a
     * debt against the goal.
     */
    suspend fun contribute(id: UUID, amount: Double) {
        val goal = dao.findById(id) ?: return
        writeDao.save(
            goal.copy(savedAmount = (goal.savedAmount + amount).coerceAtLeast(0.0)),
        )
    }

    suspend fun edit(id: UUID, name: String, target: Double, saved: Double) {
        val goal = dao.findById(id) ?: return
        writeDao.save(
            goal.copy(
                name = name.trim().ifBlank { goal.name },
                targetAmount = target.coerceAtLeast(0.0),
                savedAmount = saved.coerceAtLeast(0.0),
            ),
        )
    }

    suspend fun delete(id: UUID) = writeDao.deleteById(id)

    fun uiGoals(baseCurrency: String): Flow<List<SavingsGoalUi>> = goals.map { rows ->
        rows.map { row ->
            SavingsGoalUi(
                id = row.id,
                name = row.name,
                saved = row.savedAmount,
                target = row.targetAmount,
                currency = row.currency ?: baseCurrency,
            )
        }
    }
}
