package com.ivy.smsimport.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.ivy.sms.RawMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Reads the device SMS inbox. The only part of this feature that touches
 * platform message storage, and it is strictly read-only.
 */
class SmsInboxSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Messages received after [sinceMillis], oldest first.
     *
     * Capped by [limit] because a first scan on a multi-year inbox would
     * otherwise do thousands of regex passes in one go.
     */
    @Suppress("TooGenericExceptionCaught")
    fun fetchSince(sinceMillis: Long, limit: Int = DEFAULT_LIMIT): List<RawMessage> {
        if (!hasPermission()) return emptyList()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        return try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                "${Telephony.Sms.DATE} > ?",
                arrayOf(sinceMillis.toString()),
                "${Telephony.Sms.DATE} ASC LIMIT $limit",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            RawMessage(
                                sourceId = "sms:${cursor.getString(idCol)}",
                                sender = cursor.getString(addressCol) ?: "",
                                body = cursor.getString(bodyCol) ?: "",
                                receivedAtMillis = cursor.getLong(dateCol),
                            ),
                        )
                    }
                }
            }.orEmpty()
        } catch (e: Exception) {
            // Never log the message bodies - only that the read failed.
            Timber.e(e, "Could not read the SMS inbox")
            emptyList()
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 500
    }
}
