package com.ivy.smsimport.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CategoryRepository
import com.ivy.smsimport.data.SmsImportDao
import com.ivy.smsimport.data.SmsImportLogEntity
import com.ivy.smsimport.data.SmsInboxSource
import com.ivy.smsimport.data.SmsPendingEntity
import com.ivy.smsimport.domain.BalanceCheckUseCase
import com.ivy.smsimport.domain.CommitSmsTransactionUseCase
import com.ivy.smsimport.domain.CommitSmsTransactionUseCase.Companion.merchantKey
import com.ivy.smsimport.domain.ScanMessagesUseCase
import com.ivy.smsimport.notifications.BankNotificationListener
import com.ivy.smsimport.notifications.NotificationSourceSettings
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import javax.inject.Inject

@Stable
@SuppressLint("StaticFieldLeak")
@HiltViewModel
class SmsReviewViewModel @Inject constructor(
    private val dao: SmsImportDao,
    private val inbox: SmsInboxSource,
    private val scanMessages: ScanMessagesUseCase,
    private val commit: CommitSmsTransactionUseCase,
    private val balanceCheck: BalanceCheckUseCase,
    private val notificationSource: NotificationSourceSettings,
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val appContext: android.content.Context,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) : ComposeViewModel<SmsReviewState, SmsReviewEvent>() {

    private var scanning by mutableStateOf(false)
    private var reviewed by mutableIntStateOf(0)
    private var error by mutableStateOf<String?>(null)
    private var permissionGranted by mutableStateOf(inbox.hasPermission())
    private var notificationSourceOn by mutableStateOf(false)
    private var notificationAccessGranted by mutableStateOf(
        BankNotificationListener.isGranted(appContext),
    )

    private var accounts by mutableStateOf<ImmutableList<AccountOption>>(persistentListOf())
    private var categories by mutableStateOf<ImmutableList<CategoryOption>>(persistentListOf())

    private var suggestedAccountId by mutableStateOf<UUID?>(null)
    private var suggestedCategoryId by mutableStateOf<UUID?>(null)

    private var drifts by mutableStateOf<ImmutableList<BalanceDrift>>(persistentListOf())
    private var adjustmentCategoryId by mutableStateOf<UUID?>(null)

    private var autoScanned = false

    init {
        loadPickerOptions()
        loadNotificationSource()
        // Opening this screen is an explicit request to sync, so scan straight
        // away when the permission is already granted instead of waiting for
        // the user to find a "Scan now" button.
        if (permissionGranted) {
            autoScanned = true
            scan()
        }
    }

    @Composable
    override fun uiState(): SmsReviewState {
        val pending by dao.pending().collectAsState(initial = emptyList())
        val current = pending.firstOrNull()

        // Suggestions come from what the user chose last time for this card /
        // merchant, which is what makes repeat reviews fast.
        LaunchedSuggestions(current)
        // Balance checks run only once the queue is empty. Asking earlier would
        // compare the wallet against the bank while transactions the user is
        // about to accept are still missing from it, and "correct" the
        // difference they were in the middle of importing.
        LaunchedBalanceChecks(queueEmpty = current == null && !scanning)

        return SmsReviewState(
            permissionGranted = permissionGranted,
            notificationSourceOn = notificationSourceOn,
            notificationAccessGranted = notificationAccessGranted,
            scanning = scanning,
            current = current,
            remaining = pending.size,
            reviewedThisSession = reviewed,
            accounts = accounts,
            categories = categories,
            suggestedAccountId = suggestedAccountId,
            suggestedCategoryId = suggestedCategoryId,
            balanceDrifts = drifts,
            suggestedAdjustmentCategoryId = adjustmentCategoryId,
            error = error,
        )
    }

    @Composable
    private fun LaunchedSuggestions(current: SmsPendingEntity?) {
        androidx.compose.runtime.LaunchedEffect(current?.sourceId) {
            suggestedAccountId = current?.accountHint
                ?.let { dao.accountForHint(it) }
                ?.let(::uuidOrNull)
            suggestedCategoryId = current?.merchant
                ?.let { dao.categoryForMerchant(it.merchantKey()) }
                ?.let(::uuidOrNull)
        }
    }

    @Composable
    private fun LaunchedBalanceChecks(queueEmpty: Boolean) {
        androidx.compose.runtime.LaunchedEffect(queueEmpty) {
            if (queueEmpty) refreshBalanceChecks() else drifts = persistentListOf()
        }
    }

    override fun onEvent(event: SmsReviewEvent) {
        when (event) {
            SmsReviewEvent.Scan -> scan()
            is SmsReviewEvent.ApplyBalance -> applyBalance(event)
            is SmsReviewEvent.DismissBalance -> dismissBalance(event.accountHint)
            SmsReviewEvent.PermissionGranted -> {
                permissionGranted = true
                scan()
            }

            is SmsReviewEvent.Add -> add(event)
            SmsReviewEvent.Skip -> decide(SmsImportLogEntity.STATUS_SKIPPED)
            SmsReviewEvent.NotATransaction -> decide(SmsImportLogEntity.STATUS_NOT_A_TRANSACTION)
            SmsReviewEvent.IgnoreSender -> ignoreSender()
            SmsReviewEvent.AddAllHighConfidence -> addAllHighConfidence()
            SmsReviewEvent.DismissError -> error = null
            is SmsReviewEvent.SetNotificationSource -> setNotificationSource(event.enabled)
            SmsReviewEvent.RefreshNotificationAccess -> {
                notificationAccessGranted = BankNotificationListener.isGranted(appContext)
            }
        }
    }

    private fun scan() {
        if (scanning) return
        viewModelScope.launch {
            scanning = true
            runCatching { scanMessages.scan() }
                .onFailure { error = "Could not scan messages" }
            scanning = false
        }
    }

    private fun add(event: SmsReviewEvent.Add) {
        viewModelScope.launch {
            val current = dao.pending().first().firstOrNull() ?: return@launch
            commit.commit(
                CommitSmsTransactionUseCase.Input(
                    pending = current,
                    accountId = event.accountId,
                    categoryId = event.categoryId,
                    amountOverride = event.amount,
                    titleOverride = event.title,
                ),
            ).fold(
                ifLeft = { error = it },
                ifRight = { reviewed++ },
            )
        }
    }

    private fun decide(status: String) {
        viewModelScope.launch {
            dao.pending().first().firstOrNull()?.let {
                commit.dismiss(it, status)
                reviewed++
            }
        }
    }

    private fun ignoreSender() {
        viewModelScope.launch {
            dao.pending().first().firstOrNull()?.let {
                commit.blockSender(it)
                reviewed++
            }
        }
    }

    /**
     * Bulk accept, but only for items the parser is confident about *and* whose
     * account is already known. Anything else still needs a look.
     */
    private fun addAllHighConfidence() {
        viewModelScope.launch {
            val items = dao.pending().first()
            for (item in items) {
                if (item.confidence < ScanMessagesUseCase.HIGH_CONFIDENCE) continue
                if (item.possibleDuplicate) continue
                val accountId = item.accountHint
                    ?.let { dao.accountForHint(it) }
                    ?.let(::uuidOrNull) ?: continue
                val categoryId = item.merchant
                    ?.let { dao.categoryForMerchant(it.merchantKey()) }
                    ?.let(::uuidOrNull)

                commit.commit(
                    CommitSmsTransactionUseCase.Input(
                        pending = item,
                        accountId = accountId,
                        categoryId = categoryId,
                    ),
                ).onRight { reviewed++ }
            }
        }
    }

    private fun loadNotificationSource() {
        viewModelScope.launch {
            notificationSourceOn = notificationSource.enabled()
        }
    }

    private fun setNotificationSource(enabled: Boolean) {
        viewModelScope.launch {
            notificationSource.setEnabled(enabled)
            notificationSourceOn = enabled
            notificationAccessGranted = BankNotificationListener.isGranted(appContext)
        }
    }

    private fun loadPickerOptions() {
        viewModelScope.launch {
            accounts = accountRepository.findAll()
                .map { AccountOption(id = it.id.value, name = it.name.value) }
                .toImmutableList()
            categories = categoryRepository.findAll()
                .map { CategoryOption(id = it.id.value, name = it.name.value) }
                .toImmutableList()
            // A correction is not really spending, so it wants the catch-all
            // category if the user has one rather than a guess at a real one.
            adjustmentCategoryId = categories
                .firstOrNull { option ->
                    adjustmentCategoryNames.any { option.name.lowercase().contains(it) }
                }
                ?.id
        }
    }

    private fun refreshBalanceChecks() {
        viewModelScope.launch {
            drifts = runCatching { balanceCheck.drifts() }
                .getOrDefault(emptyList())
                .filterNot { it.dismissed }
                .map { it.toUi() }
                .toImmutableList()
        }
    }

    private fun applyBalance(event: SmsReviewEvent.ApplyBalance) {
        viewModelScope.launch {
            balanceCheck.reconcile(
                hint = event.accountHint,
                accountId = event.accountId,
                categoryId = event.categoryId,
            ).fold(
                ifLeft = { error = it },
                ifRight = { refreshBalanceChecks() },
            )
        }
    }

    private fun dismissBalance(hint: String) {
        viewModelScope.launch {
            balanceCheck.dismiss(hint)
            refreshBalanceChecks()
        }
    }

    private fun BalanceCheckUseCase.Drift.toUi() = BalanceDrift(
        accountHint = accountHint,
        accountId = accountId,
        accountName = accountName,
        bankBalance = bankBalance.toMoney(),
        appBalance = appBalance.toMoney(),
        difference = difference.abs().toMoney(),
        walletIsBehind = difference > BigDecimal.ZERO,
        currency = currency.orEmpty(),
        observed = dateFormat.format(Date(observedAtMillis)),
    )

    private fun BigDecimal.toMoney(): String =
        setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString()

    private fun uuidOrNull(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()

    companion object {
        private const val MONEY_SCALE = 2
        private val dateFormat: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

        /**
         * Names people give the category that means "not really a thing I
         * bought", in rough order of how clearly they mean it.
         */
        private val adjustmentCategoryNames = listOf("misc", "other", "adjust", "unknown")
    }
}
