package com.ivy.data.db.dao.read

import androidx.room.Dao
import androidx.room.Query
import com.ivy.data.db.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals WHERE archived = 0 ORDER BY orderNum ASC")
    suspend fun findAll(): List<SavingsGoalEntity>

    /**
     * A flow, unlike the other read DAOs here, because the goals are shown in
     * the home menu while the user edits them from that same menu - polling or
     * manual refresh would show a stale figure straight after a top-up.
     */
    @Query("SELECT * FROM savings_goals WHERE archived = 0 ORDER BY orderNum ASC")
    fun findAllFlow(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY orderNum ASC")
    suspend fun findAllIncludingArchived(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun findById(id: UUID): SavingsGoalEntity?

    @Query("SELECT MAX(orderNum) FROM savings_goals")
    suspend fun findMaxOrderNum(): Double?
}
