package com.ivy.smsimport.notifications

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.ivy.data.datastore.IvyDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether reading bank alerts from notifications is switched on.
 *
 * Kept separate from the system's own notification-access grant: Android can
 * only say whether the app *may* read notifications, and the user may well
 * have granted that for some other reason. This is the user's answer to
 * "should this wallet read my bank alerts", and the listener checks it on
 * every notification so turning it off takes effect at once, without the user
 * having to go and revoke the system permission too.
 */
@Singleton
class NotificationSourceSettings @Inject constructor(
    private val dataStore: IvyDataStore,
) {
    val enabledFlow: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: false }

    suspend fun enabled(): Boolean = enabledFlow.first()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("sms_read_from_notifications")
    }
}
