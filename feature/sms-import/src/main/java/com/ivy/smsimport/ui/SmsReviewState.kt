package com.ivy.smsimport.ui

import androidx.compose.runtime.Immutable
import com.ivy.smsimport.data.SmsPendingEntity
import kotlinx.collections.immutable.ImmutableList
import java.util.UUID

@Immutable
data class AccountOption(val id: UUID, val name: String)

@Immutable
data class CategoryOption(val id: UUID, val name: String)

@Immutable
data class SmsReviewState(
    val permissionGranted: Boolean,
    val scanning: Boolean,
    val current: SmsPendingEntity?,
    val remaining: Int,
    val reviewedThisSession: Int,
    val accounts: ImmutableList<AccountOption>,
    val categories: ImmutableList<CategoryOption>,
    val suggestedAccountId: UUID?,
    val suggestedCategoryId: UUID?,
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
}
