package com.ivy.smsimport.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    @Query("SELECT sourceId FROM sms_pending")
    suspend fun pendingIds(): List<String>

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

    // ---- formats the user taught ----

    @Query("SELECT * FROM sms_template ORDER BY createdAtMillis DESC")
    fun templates(): Flow<List<SmsTemplateEntity>>

    @Query("SELECT * FROM sms_template WHERE enabled = 1")
    suspend fun enabledTemplates(): List<SmsTemplateEntity>

    @Query("SELECT * FROM sms_template WHERE id = :id")
    suspend fun template(id: String): SmsTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTemplate(entity: SmsTemplateEntity)

    @Query("DELETE FROM sms_template WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("UPDATE sms_template SET enabled = :enabled WHERE id = :id")
    suspend fun setTemplateEnabled(id: String, enabled: Boolean)

    // ---- bank-stated balances ----

    @Query("SELECT * FROM sms_balance_snapshot ORDER BY observedAtMillis DESC")
    fun balanceSnapshots(): Flow<List<SmsBalanceSnapshotEntity>>

    @Query("SELECT * FROM sms_balance_snapshot ORDER BY observedAtMillis DESC")
    suspend fun allBalanceSnapshots(): List<SmsBalanceSnapshotEntity>

    @Query("SELECT * FROM sms_balance_snapshot WHERE accountHint = :hint")
    suspend fun balanceSnapshot(hint: String): SmsBalanceSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBalanceSnapshot(entity: SmsBalanceSnapshotEntity)

    @Query("UPDATE sms_balance_snapshot SET settledBalance = :balance WHERE accountHint = :hint")
    suspend fun settleBalance(hint: String, balance: String)

    @Query("DELETE FROM sms_balance_snapshot")
    suspend fun clearBalanceSnapshots()
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
        SmsTemplateEntity::class,
        SmsBalanceSnapshotEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SmsImportDatabase : RoomDatabase() {
    abstract fun dao(): SmsImportDao

    companion object {
        const val NAME = "ivy_sms_import.db"

        /**
         * Adds the taught-format and bank-balance tables.
         *
         * Written out rather than left to the destructive fallback: the
         * mappings in v1 are what stop review from asking the same account
         * question every week, and dropping them to add two unrelated tables
         * would be a pointless regression for anyone upgrading.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sms_template` (" +
                        "`id` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`senderFragment` TEXT, " +
                        "`template` TEXT NOT NULL, " +
                        "`direction` TEXT NOT NULL, " +
                        "`currency` TEXT, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sms_balance_snapshot` (" +
                        "`accountHint` TEXT NOT NULL, " +
                        "`balance` TEXT NOT NULL, " +
                        "`currency` TEXT, " +
                        "`observedAtMillis` INTEGER NOT NULL, " +
                        "`settledBalance` TEXT, " +
                        "PRIMARY KEY(`accountHint`))",
                )
            }
        }
    }
}
