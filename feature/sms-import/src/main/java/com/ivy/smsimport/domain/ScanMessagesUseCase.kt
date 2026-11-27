package com.ivy.smsimport.domain

import com.ivy.smsimport.data.SmsImportSettings
import com.ivy.smsimport.data.SmsInboxSource
import java.time.Duration
import javax.inject.Inject

/**
 * Scans for new bank messages and queues what it finds for review.
 *
 * Runs entirely on-device: nothing here touches the network, and message bodies
 * are only persisted in the pending queue until the user decides, at which
 * point they are discarded.
 */
class ScanMessagesUseCase @Inject constructor(
    private val inbox: SmsInboxSource,
    private val settings: SmsImportSettings,
    private val ingest: IngestMessagesUseCase,
) {
    data class Result(val found: Int, val skipped: Int)

    /**
     * [rescan] looks at the whole window again instead of resuming from the
     * watermark.
     *
     * Needed because a scan advances the watermark past every message it
     * examined, including the ones it rejected. Teaching the app a new format
     * would otherwise change nothing visible - the messages that format exists
     * to read are all behind the mark.
     */
    suspend fun scan(rescan: Boolean = false): Result {
        if (!inbox.hasPermission()) return Result(found = 0, skipped = 0)

        val since = if (rescan) windowStart() else settings.lastScannedAt()
        val messages = inbox.fetchSince(since)
        if (messages.isEmpty()) return Result(found = 0, skipped = 0)

        val result = ingest.ingest(messages)

        // Advanced past every message examined, matched or not, so the next
        // scan resumes rather than re-reading the whole window.
        settings.setLastScannedAt(messages.maxOf { it.receivedAtMillis })

        return Result(found = result.found, skipped = result.skipped)
    }

    private suspend fun windowStart(): Long =
        System.currentTimeMillis() - Duration.ofDays(settings.scanWindowDays()).toMillis()

    companion object {
        const val DIRECTION_EXPENSE = "expense"
        const val DIRECTION_INCOME = "income"

        /** Items at or above this confidence are safe to pre-fill and bulk accept. */
        const val HIGH_CONFIDENCE = 0.9
    }
}
