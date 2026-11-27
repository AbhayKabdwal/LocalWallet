package com.ivy.smsimport.ui

import androidx.compose.runtime.Immutable
import com.ivy.smsimport.data.SmsPendingEntity
import kotlinx.collections.immutable.ImmutableList
import java.util.UUID

@Immutable
data class AccountOption(val id: UUID, val name: String)

@Immutable
data class CategoryOption(val id: UUID, val name: String)

/**
 * An account whose balance in the wallet disagrees with the bank's last word
 * on it.
 *
 * Pre-formatted for display: the screen should not be doing arithmetic on
 * money, and the sign of [difference] has already decided whether the fix is
 * an income or an expense.
 */
@Immutable
data class BalanceDrift(
    val accountHint: String,
    val accountId: UUID,
    val accountName: String,
    val bankBalance: String,
    val appBalance: String,
    val difference: String,
    val walletIsBehind: Boolean,
    val currency: String,
    val observed: String,
)

@Immutable
data class SmsReviewState(
    val permissionGranted: Boolean,
    /** Whether the user has switched on reading bank alerts from notifications. */
    val notificationSourceOn: Boolean,
    /** Whether Android has actually granted notification access. */
    val notificationAccessGranted: Boolean,
    val scanning: Boolean,
    val current: SmsPendingEntity?,
    val remaining: Int,
    val reviewedThisSession: Int,
    val accounts: ImmutableList<AccountOption>,
    val categories: ImmutableList<CategoryOption>,
    val suggestedAccountId: UUID?,
    val suggestedCategoryId: UUID?,
    val balanceDrifts: ImmutableList<BalanceDrift>,
    val suggestedAdjustmentCategoryId: UUID?,
    val error: String?,
) {
    val total: Int get() = remaining + reviewedThisSession
    val position: Int get() = reviewedThisSession + 1
}

sealed interface SmsReviewEvent {
    data object Scan : SmsReviewEvent
    data object PermissionGranted : SmsReviewEvent
    data class Add(
        val accountId: UUID,
        val categoryId: UUID?,
        val amount: String,
        val title: String?,
    ) : SmsReviewEvent

    data object Skip : SmsReviewEvent
    data object NotATransaction : SmsReviewEvent
    data object IgnoreSender : SmsReviewEvent
    data object AddAllHighConfidence : SmsReviewEvent
    data object DismissError : SmsReviewEvent

    /** Turn reading bank alerts from notifications on or off. */
    data class SetNotificationSource(val enabled: Boolean) : SmsReviewEvent

    /** Re-check the system grant after returning from Android settings. */
    data object RefreshNotificationAccess : SmsReviewEvent

    /** Write the gap between the wallet and the bank off as one transaction. */
    data class ApplyBalance(
        val accountHint: String,
        val accountId: UUID,
        val categoryId: UUID?,
    ) : SmsReviewEvent

    data class DismissBalance(val accountHint: String) : SmsReviewEvent
}
