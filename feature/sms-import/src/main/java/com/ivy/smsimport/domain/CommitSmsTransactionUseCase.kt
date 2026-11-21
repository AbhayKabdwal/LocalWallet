package com.ivy.smsimport.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.ivy.data.model.AccountId
import com.ivy.data.model.CategoryId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.PositiveValue
import com.ivy.data.model.Transaction
import com.ivy.data.model.TransactionId
import com.ivy.data.model.TransactionMetadata
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.PositiveDouble
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.sms.SenderFilter
import com.ivy.smsimport.data.SmsAccountMapEntity
import com.ivy.smsimport.data.SmsBlockedSenderEntity
import com.ivy.smsimport.data.SmsCategoryRuleEntity
import com.ivy.smsimport.data.SmsImportDao
import com.ivy.smsimport.data.SmsImportLogEntity
import com.ivy.smsimport.data.SmsPendingEntity
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Writes an accepted message into the wallet as a normal transaction, and
 * remembers the user's choices so the next review is quicker.
 */
class CommitSmsTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val dao: SmsImportDao,
) {
    data class Input(
        val pending: SmsPendingEntity,
        val accountId: UUID,
        val categoryId: UUID?,
        val amountOverride: String? = null,
        val titleOverride: String? = null,
    )

    suspend fun commit(input: Input): Either<String, Unit> = either {
        val pending = input.pending
        val amount = ensureNotNull(
            (input.amountOverride ?: pending.amount).toDoubleOrNull(),
        ) { "Amount is not a number" }

        val account = ensureNotNull(
            accountRepository.findById(AccountId(input.accountId)),
        ) { "Account no longer exists" }

        val asset = pending.currency
            ?.let { AssetCode.from(it).getOrNull() }
            ?: account.asset

        val value = PositiveValue(
            amount = PositiveDouble.from(amount).bind(),
            asset = asset,
        )
        val title = (input.titleOverride ?: pending.merchant)
            ?.let { NotBlankTrimmedString.from(it).getOrNull() }

        val metadata = TransactionMetadata(
            recurringRuleId = null,
            paidForDateTime = null,
            loanId = null,
            loanRecordId = null,
        )
        val transaction: Transaction = if (pending.direction == ScanMessagesUseCase.DIRECTION_INCOME) {
            Income(
                id = TransactionId(UUID.randomUUID()),
                title = title,
                description = null,
                category = input.categoryId?.let { CategoryId(it) },
                time = Instant.ofEpochMilli(pending.receivedAtMillis),
                settled = true,
                metadata = metadata,
                tags = emptyList(),
                value = value,
                account = AccountId(input.accountId),
            )
        } else {
            Expense(
                id = TransactionId(UUID.randomUUID()),
                title = title,
                description = null,
                category = input.categoryId?.let { CategoryId(it) },
                time = Instant.ofEpochMilli(pending.receivedAtMillis),
                settled = true,
                metadata = metadata,
                tags = emptyList(),
                value = value,
                account = AccountId(input.accountId),
            )
        }

        transactionRepository.save(transaction)
        rememberChoices(input)
        finish(pending, SmsImportLogEntity.STATUS_IMPORTED)
    }

    /** Skip, ignore, or "not a transaction" - all recorded so it is never re-offered. */
    suspend fun dismiss(pending: SmsPendingEntity, status: String) {
        finish(pending, status)
    }

    suspend fun blockSender(pending: SmsPendingEntity) {
        dao.blockSender(SmsBlockedSenderEntity(SenderFilter.normalize(pending.sender)))
        finish(pending, SmsImportLogEntity.STATUS_IGNORED)
    }

    /**
     * Learning the account for a card hint and the category for a merchant is
     * what stops review from being equally tedious every week.
     */
    private suspend fun rememberChoices(input: Input) {
        input.pending.accountHint?.let { hint ->
            dao.rememberAccount(
                SmsAccountMapEntity(accountHint = hint, accountId = input.accountId.toString()),
            )
        }
        val merchantKey = input.pending.merchant?.merchantKey()
        if (merchantKey != null && input.categoryId != null) {
            dao.rememberCategory(
                SmsCategoryRuleEntity(
                    merchantKey = merchantKey,
                    categoryId = input.categoryId.toString(),
                ),
            )
        }
    }

    /**
     * Records the decision and drops the queued row - which is also when the
     * message body stops being stored anywhere in the app.
     */
    private suspend fun finish(pending: SmsPendingEntity, status: String) {
        dao.logDecision(
            SmsImportLogEntity(
                sourceId = pending.sourceId,
                fingerprint = pending.fingerprint,
                status = status,
                decidedAtMillis = System.currentTimeMillis(),
            ),
        )
        dao.removePending(pending.sourceId)
    }

    companion object {
        fun String.merchantKey(): String = lowercase().trim().take(40)
    }
}
