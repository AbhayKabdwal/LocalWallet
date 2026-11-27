package com.ivy.wallet.domain.deprecated.logic.notification

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.entity.TransactionEntity
import com.ivy.domain.AppStarter
import com.ivy.legacy.utils.atEndOfDay
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

/**
 * Tells the user, on the day, about a payment they planned.
 *
 * A planned payment that only appears inside the app is a reminder the user
 * has to remember to go and look at, which is the thing they were trying to
 * avoid by planning it. The bill is due whether or not the app was opened
 * that morning.
 */
@HiltWorker
class PlannedPaymentReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val notificationService: NotificationService,
    private val sharedPrefs: SharedPrefs,
    private val appStarter: AppStarter,
    private val timeProvider: TimeProvider,
    private val timeConverter: TimeConverter,
) : CoroutineWorker(appContext, params) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!sharedPrefs.getBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true)) {
            return@withContext Result.success()
        }

        try {
            dueToday().forEach(::notify)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    /**
     * Planned payments whose due date is today and which have not been paid.
     *
     * A paid one carries a `dateTime`; an unpaid one has only a `dueDate`.
     * Without that check the user would be reminded to pay a bill they
     * settled an hour earlier, which is the fastest way to have the whole
     * reminder ignored.
     */
    private suspend fun dueToday(): List<TransactionEntity> = with(timeConverter) {
        val today = timeProvider.localDateNow()
        transactionDao.findAllDueToBetween(
            startDate = today.atStartOfDay().toUTC(),
            endDate = today.atEndOfDay().toUTC(),
        ).filter { it.dateTime == null }
    }

    private fun notify(payment: TransactionEntity) {
        val notification = notificationService
            .defaultIvyNotification(
                channel = IvyNotificationChannel.PLANNED_PAYMENT,
                priority = NotificationCompat.PRIORITY_HIGH,
            )
            .setContentTitle(payment.title ?: "Payment due today")
            .setContentText(
                "${payment.amount.absoluteValue} is due today. Tap to record it.",
            )
            .setContentIntent(contentIntent())

        // Keyed on the payment, so a second run of the worker replaces the
        // earlier notification for the same bill instead of stacking a
        // duplicate on top of it.
        notificationService.showNotification(notification, payment.id.hashCode())
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        PLANNED_PAYMENTS_REQUEST_CODE,
        appStarter.defaultStartIntent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val PLANNED_PAYMENTS_REQUEST_CODE = 2001
    }
}
