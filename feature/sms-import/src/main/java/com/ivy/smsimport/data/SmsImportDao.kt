package com.ivy.smsimport.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsImportDao {

    // ---- decision log (dedupe) ----

    @Query("SELECT sourceId FROM sms_import_log")
    suspend fun allDecidedIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM sms_import_log WHERE fingerprint = :fingerprint)")
    suspend fun fingerprintSeen(fingerprint: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun logDecision(entity: SmsImportLogEntity)

    // ---- pending queue ----

    @Query("SELECT * FROM sms_pending ORDER BY receivedAtMillis ASC")
    fun pending(): Flow<List<SmsPendingEntity>>

    @Query("SELECT COUNT(*) FROM sms_pending")
    fun pendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPending(items: List<SmsPendingEntity>)

    @Query("DELETE FROM sms_pending WHERE sourceId = :sourceId")
    suspend fun removePending(sourceId: String)

    @Query("DELETE FROM sms_pending")
    suspend fun clearPending()

    // ---- learned mappings ----

    @Query("SELECT accountId FROM sms_account_map WHERE accountHint = :hint")
    suspend fun accountForHint(hint: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun rememberAccount(entity: SmsAccountMapEntity)

    @Query("SELECT * FROM sms_account_map")
    suspend fun allAccountMappings(): List<SmsAccountMapEntity>

    @Query("SELECT categoryId FROM sms_category_rule WHERE merchantKey = :key")
    suspend fun categoryForMerchant(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun rememberCategory(entity: SmsCategoryRuleEntity)

    @Query("DELETE FROM sms_account_map")
    suspend fun clearAccountMappings()

    @Query("DELETE FROM sms_category_rule")
    suspend fun clearCategoryRules()

    // ---- blocked senders ----

    @Query("SELECT sender FROM sms_blocked_sender")
    suspend fun blockedSenders(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockSender(entity: SmsBlockedSenderEntity)

    @Query("DELETE FROM sms_blocked_sender WHERE sender = :sender")
    suspend fun unblockSender(sender: String)
}

/**
 * SMS import state lives in its own database, separate from the wallet's
 * transactions.
 *
 * That keeps this feature's schema changes away from the user's financial data
 * (no migration of the main database at v130), and makes "forget everything the
 * message importer learned" a single file delete.
 */
@Database(
    entities = [
        SmsImportLogEntity::class,
        SmsPendingEntity::class,
        SmsAccountMapEntity::class,
        SmsCategoryRuleEntity::class,
        SmsBlockedSenderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SmsImportDatabase : RoomDatabase() {
    abstract fun dao(): SmsImportDao

    companion object {
        const val NAME = "ivy_sms_import.db"
    }
}
