package com.ivy.smsimport.data

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A message the parser has already decided on.
 *
 * Deliberately stores **no message text** - only the id, a fingerprint and the
 * outcome. The import log must not become a shadow copy of the user's inbox.
 */
@Keep
@Entity(tableName = "sms_import_log")
data class SmsImportLogEntity(
    @PrimaryKey val sourceId: String,
    val fingerprint: String,
    val status: String,
    val decidedAtMillis: Long,
) {
    companion object {
        const val STATUS_IMPORTED = "imported"
        const val STATUS_SKIPPED = "skipped"
        const val STATUS_IGNORED = "ignored"
        const val STATUS_NOT_A_TRANSACTION = "not_a_transaction"
    }
}

/**
 * A parsed transaction awaiting the user's decision.
 *
 * Persisted rather than held in the ViewModel so backgrounding the app mid-review
 * does not lose the queue or re-show decided items. Rows are deleted as soon as
 * the user decides, which is also when the message body stops being stored.
 */
@Keep
@Entity(tableName = "sms_pending")
data class SmsPendingEntity(
    @PrimaryKey val sourceId: String,
    val sender: String,
    val body: String,
    val amount: String,
    val direction: String,
    val currency: String?,
    val accountHint: String?,
    val merchant: String?,
    val reference: String?,
    val confidence: Double,
    val ruleId: String,
    val receivedAtMillis: Long,
    val fingerprint: String,
    val possibleDuplicate: Boolean = false,
)

/**
 * Remembers which Ivy account an account hint (`XX1234`) belongs to, so the
 * user only answers that question once per card.
 */
@Keep
@Entity(tableName = "sms_account_map")
data class SmsAccountMapEntity(
    @PrimaryKey val accountHint: String,
    val accountId: String,
)

/**
 * Remembers the category the user chose for a merchant, so review gets faster
 * every week instead of staying equally tedious.
 */
@Keep
@Entity(tableName = "sms_category_rule")
data class SmsCategoryRuleEntity(
    @PrimaryKey val merchantKey: String,
    val categoryId: String,
    val hits: Int = 1,
)

/** Senders the user never wants to see again. */
@Keep
@Entity(tableName = "sms_blocked_sender")
data class SmsBlockedSenderEntity(
    @PrimaryKey val sender: String,
)
