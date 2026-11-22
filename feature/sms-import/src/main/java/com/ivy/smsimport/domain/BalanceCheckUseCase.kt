package com.ivy.smsimport.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.ivy.data.model.AccountId
import com.ivy.data.model.CategoryId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.PositiveValue
import com.ivy.data.model.Transaction
import com.ivy.data.model.TransactionId
import com.ivy.data.model.TransactionMetadata
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.primitive.PositiveDouble
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.usecase.account.AccountBalanceUseCase
import com.ivy.smsimport.data.SmsAccountMapEntity
import com.ivy.smsimport.data.SmsImportDao
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Compares the balance the bank last stated with the balance the wallet adds up
 * to, and writes the difference off as one correction.
 *
 * This exists because of how people actually start using a wallet app: they
 * type a round number as the opening balance, or start mid-month, and from then
 * on every figure in the app is out by that amount however carefully they
 * record transactions. The bank states its own balance in almost every message,
 * which makes the drift measurable rather than a thing the user has to notice
 * and work out by hand.
 *
 * The correction is a normal transaction, not a hidden fudge of the account: it
 * shows up in the ledger, it can be found, and it can be deleted.
 */
class BalanceCheckUseCase @Inject constructor(
    private val dao: SmsImportDao,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val accountBalance: AccountBalanceUseCase,
) {
    /** One account whose wallet balance disagrees with the bank. */
    data class Drift(
        val accountHint: String,
        val accountId: UUID,
        val accountName: String,
        val bankBalance: BigDecimal,
        val appBalance: BigDecimal,
        val currency: String?,
        val observedAtMillis: Long,
        val dismissed: Boolean,
    ) {
        /** What has to be added to the wallet to agree with the bank. */
        val difference: BigDecimal get() = bankBalance.subtract(appBalance)
    }

    /**
     * Accounts worth asking about.
     *
     * Only cards already mapped to an account are considered. A card nothing
     * has been imported from is one the user does not track here, and offering
     * to reconcile it would mean asking about every credit card the bank texts
     * about - repeatedly, since each new message carries a new balance.
     *
     * Deliberately not a flow: totalling an account reads its transactions, so
     * this runs when the review queue is empty and the question is actually in
     * front of the user, not on every database change.
     */
    suspend fun drifts(): List<Drift> {
        val snapshots = dao.allBalanceSnapshots().take(MAX_ACCOUNTS_CHECKED)
        if (snapshots.isEmpty()) return emptyList()

        val accounts = accountRepository.findAll().associateBy { it.id.value }

        return snapshots.mapNotNull { snapshot ->
            val bankBalance = snapshot.balance.toBigDecimalOrNull() ?: return@mapNotNull null
            val account = dao.accountForHint(snapshot.accountHint)
                ?.let { uuidOrNull(it) }
                ?.let { accounts[it] }
                ?: return@mapNotNull null

            val totals = accountBalance.calculate(account.id)
            Drift(
                accountHint = snapshot.accountHint,
                accountId = account.id.value,
                accountName = account.name.value,
                bankBalance = bankBalance,
                appBalance = BigDecimal.valueOf(totals[account.asset]?.value ?: 0.0),
                currency = snapshot.currency ?: account.asset.code,
                observedAtMillis = snapshot.observedAtMillis,
                dismissed = snapshot.settledBalance == snapshot.balance,
            )
        }.filter { it.difference.abs() >= MIN_DIFFERENCE }
    }

    /**
     * Writes the difference into the wallet as one correcting transaction, and
     * remembers that this reading has been dealt with.
     */
    suspend fun reconcile(
        hint: String,
        accountId: UUID,
        categoryId: UUID?,
        title: String = DEFAULT_TITLE,
    ): Either<String, BigDecimal> = either {
        val snapshot = ensureNotNull(dao.balanceSnapshot(hint)) { "No bank balance for this card" }
        val bankBalance = ensureNotNull(snapshot.balance.toBigDecimalOrNull()) {
            "The bank's balance could not be read"
        }
        val account = ensureNotNull(accountRepository.findById(AccountId(accountId))) {
            "Account no longer exists"
        }

        // Learned here as well as on import, because mapping a card to an
        // account while answering this question is the user's answer to it.
        dao.rememberAccount(
            SmsAccountMapEntity(accountHint = hint, accountId = accountId.toString()),
        )

        val totals = accountBalance.calculate(account.id)
        val appBalance = BigDecimal.valueOf(totals[account.asset]?.value ?: 0.0)
        val difference = bankBalance.subtract(appBalance)
        ensure(difference.abs() >= MIN_DIFFERENCE) { "The wallet already agrees with the bank" }

        val amount = difference.abs()
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            .toDouble()
        val value = PositiveValue(
            amount = PositiveDouble.from(amount).bind(),
            asset = account.asset,
        )
        val metadata = TransactionMetadata(
            recurringRuleId = null,
            paidForDateTime = null,
            loanId = null,
            loanRecordId = null,
        )
        val time = Instant.ofEpochMilli(snapshot.observedAtMillis)
        val transactionTitle = NotBlankTrimmedString.from(title).getOrNull()

        val transaction: Transaction = if (difference > BigDecimal.ZERO) {
            // The bank holds more than the wallet knows about: unrecorded income.
            Income(
                id = TransactionId(UUID.randomUUID()),
                title = transactionTitle,
                description = null,
                category = categoryId?.let { CategoryId(it) },
                time = time,
                settled = true,
                metadata = metadata,
                tags = emptyList(),
                value = value,
                account = AccountId(accountId),
            )
        } else {
            Expense(
                id = TransactionId(UUID.randomUUID()),
                title = transactionTitle,
                description = null,
                category = categoryId?.let { CategoryId(it) },
                time = time,
                settled = true,
                metadata = metadata,
                tags = emptyList(),
                value = value,
                account = AccountId(accountId),
            )
        }

        transactionRepository.save(transaction)
        dao.settleBalance(hint, snapshot.balance)
        difference
    }

    /** "Not now": keep the reading, stop asking about this one. */
    suspend fun dismiss(hint: String) {
        dao.balanceSnapshot(hint)?.let { dao.settleBalance(hint, it.balance) }
    }

    companion object {
        const val DEFAULT_TITLE = "Balance adjustment"

        /**
         * Below a paisa the two figures agree; a correction of 0.004 would be
         * noise in the ledger rather than information.
         */
        private val MIN_DIFFERENCE = BigDecimal("0.01")
        private const val MONEY_SCALE = 2

        /**
         * Totalling an account walks its transactions, so the number of
         * accounts checked in one pass is bounded. Newest reading first, which
         * is the one worth asking about.
         */
        private const val MAX_ACCOUNTS_CHECKED = 6

        fun uuidOrNull(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()
    }
}
