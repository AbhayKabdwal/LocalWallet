package com.ivy.wallet.domain.deprecated.logic.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ivy.base.legacy.SharedPrefs
import com.ivy.legacy.utils.timeNowLocal
import com.ivy.legacy.utils.toEpochSeconds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Schedules the daily check for planned payments falling due.
 *
 * Runs in the morning rather than in the evening like the "record your
 * transactions" nudge: a bill you are told about at 9am can still be paid
 * that day, which is the entire point of the reminder.
 */
class PlannedPaymentReminderLogic @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val sharedPrefs: SharedPrefs,
) {
    fun scheduleReminder() {
        if (!sharedPrefs.getBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true)) return

        val now = timeNowLocal()
        val todayAtNine = now.withHour(REMINDER_HOUR).withMinute(0).withSecond(0)
        val firstRun = if (todayAtNine.isAfter(now)) todayAtNine else todayAtNine.plusDays(1)
        val initialDelaySeconds = firstRun.toEpochSeconds() - now.toEpochSeconds()

        val work = PeriodicWorkRequestBuilder<PlannedPaymentReminderWorker>(24, TimeUnit.HOURS)
            .apply {
                if (initialDelaySeconds > 0) {
                    setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
                }
            }
            .build()

        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                // KEEP, so re-opening the app does not reset the schedule and
                // push the next run another day out.
                ExistingPeriodicWorkPolicy.KEEP,
                work,
            )
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "planned_payment_reminder_work"
        private const val REMINDER_HOUR = 9
    }
}
