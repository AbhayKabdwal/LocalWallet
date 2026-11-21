package com.ivy.smsimport.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.ivy.data.datastore.IvyDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsImportSettings @Inject constructor(
    private val dataStore: IvyDataStore,
) {
    val reviewOnOpen: Flow<Boolean> = dataStore.data.map { it[REVIEW_ON_OPEN] ?: false }

    suspend fun lastScannedAt(): Long = dataStore.data.map { it[LAST_SCANNED_AT] }.first()
        ?: defaultStartWindow()

    /**
     * Only ever advanced to the newest message that was actually written to the
     * log, so a crash mid-scan cannot silently skip messages.
     */
    suspend fun setLastScannedAt(millis: Long) {
        dataStore.edit { prefs ->
            val current = prefs[LAST_SCANNED_AT] ?: 0L
            if (millis > current) prefs[LAST_SCANNED_AT] = millis
        }
    }

    suspend fun scanWindowDays(): Long = dataStore.data.map { it[WINDOW_DAYS] }.first()
        ?: DEFAULT_WINDOW_DAYS

    suspend fun setScanWindowDays(days: Long) {
        dataStore.edit { it[WINDOW_DAYS] = days }
    }

    suspend fun setReviewOnOpen(enabled: Boolean) {
        dataStore.edit { it[REVIEW_ON_OPEN] = enabled }
    }

    /** Called when the feature is first enabled, so the first scan is bounded. */
    suspend fun seedStartWindow() {
        val days = scanWindowDays()
        dataStore.edit {
            it[LAST_SCANNED_AT] = System.currentTimeMillis() - Duration.ofDays(days).toMillis()
        }
    }

    private suspend fun defaultStartWindow(): Long =
        System.currentTimeMillis() - Duration.ofDays(scanWindowDays()).toMillis()

    companion object {
        /**
         * A fresh install offering 1,400 transactions from a five-year inbox is
         * the fastest way to get this feature switched off, so the first look
         * back is bounded - but a week was too short to show anything on an
         * account that is used a few times a month. A month of history, capped
         * by [SmsInboxSource.DEFAULT_LIMIT], is enough to be useful on the
         * first open without being a wall of work.
         */
        const val DEFAULT_WINDOW_DAYS = 30L

        /**
         * Bumped to `_v2` deliberately.
         *
         * A scan advances this watermark past every message it examined,
         * including the ones it rejected. Earlier builds rejected the formats
         * the largest banks actually send, so anyone who opened the review
         * screen on one of those builds has a watermark sitting at the newest
         * message in their inbox - and fixing the rules alone would surface
         * nothing, because nothing is newer than the mark. Reading from a new
         * key resets the window once, so the corrected rules get a look at the
         * recent history they should have matched the first time.
         */
        private val LAST_SCANNED_AT = longPreferencesKey("sms_last_scanned_at_v2")
        private val WINDOW_DAYS = longPreferencesKey("sms_scan_window_days")
        private val REVIEW_ON_OPEN = booleanPreferencesKey("sms_review_on_open")
    }
}
