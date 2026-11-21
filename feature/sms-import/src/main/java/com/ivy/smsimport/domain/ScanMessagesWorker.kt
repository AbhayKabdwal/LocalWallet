package com.ivy.smsimport.domain

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Runs the message scan off the main thread.
 *
 * The scan is content-provider I/O plus a regex pass over up to 500 messages, so
 * doing it synchronously at launch would be a reliable cold-start ANR on a large
 * inbox. The app opens normally and a banner appears once there is something to
 * review.
 */
@HiltWorker
class ScanMessagesWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scanMessages: ScanMessagesUseCase,
) : CoroutineWorker(context, params) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result = try {
        val result = scanMessages.scan()
        Timber.d("Message scan finished: %d found, %d skipped", result.found, result.skipped)
        Result.success()
    } catch (e: Exception) {
        Timber.e(e, "Message scan failed")
        Result.failure()
    }

    companion object {
        private const val WORK_NAME = "ivy_sms_scan"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScanMessagesWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
