package com.ivy.data.db.dao.write

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ivy.data.db.entity.SavingsGoalEntity
import java.util.UUID

@Dao
interface WriteSavingsGoalDao {
    @Upsert
    suspend fun save(value: SavingsGoalEntity)

    @Upsert
    suspend fun saveMany(value: List<SavingsGoalEntity>)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAll()
}
