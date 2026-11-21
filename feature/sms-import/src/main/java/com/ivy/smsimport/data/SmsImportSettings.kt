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
         * the fastest way to get this feature switched off. Start small.
         */
        const val DEFAULT_WINDOW_DAYS = 7L

        private val LAST_SCANNED_AT = longPreferencesKey("sms_last_scanned_at")
        private val WINDOW_DAYS = longPreferencesKey("sms_scan_window_days")
        private val REVIEW_ON_OPEN = booleanPreferencesKey("sms_review_on_open")
    }
}
