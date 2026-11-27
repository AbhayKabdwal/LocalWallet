package com.ivy.smsimport.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.annotation.Keep
import com.ivy.sms.RawMessage
import com.ivy.smsimport.domain.IngestMessagesUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Reads bank alerts from the notification shade instead of the SMS inbox.
 *
 * `READ_SMS` is one of the permissions Android and Google Play treat as
 * restricted: the prompt is alarming, it grants access to the user's entire
 * message history, and on Play it needs a written justification. All of that
 * to read the handful of lines a bank sends. Notification access asks for a
 * different and, for this purpose, better-scoped thing - alerts as they
 * arrive - and it has a real advantage besides: banks and UPI apps post their
 * own notifications, so payments made in those apps are caught too, not only
 * the ones that happen to come by SMS.
 *
 * The trade-off is honest and worth stating in the UI: this sees nothing that
 * arrived before it was switched on. Only [com.ivy.smsimport.data.SmsInboxSource]
 * can read history, which is why SMS stays available as an opt-in extra.
 */
@Keep
@AndroidEntryPoint
class BankNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var ingest: IngestMessagesUseCase

    @Inject
    lateinit var settings: NotificationSourceSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("TooGenericExceptionCaught")
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val message = sbn.toRawMessage() ?: return

        scope.launch {
            if (!settings.enabled()) return@launch
            try {
                ingest.ingest(listOf(message))
            } catch (e: Exception) {
                // Never log the text itself - only that the read failed.
                Timber.e(e, "Could not read a notification")
            }
        }
    }

    /**
     * Flattens a notification into the same shape an SMS arrives in.
     *
     * The sender is the posting package rather than a phone number, so a
     * taught format's "sender contains" fragment can key on `bob` for
     * `com.bankofbaroda.upi` just as it keys on `BOBTXN` for an SMS header.
     */
    private fun StatusBarNotification.toRawMessage(): RawMessage? {
        val extras = notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return null

        val body = listOf(title, text).filter { it.isNotBlank() }.joinToString(". ")
        if (body.length < MIN_BODY_LENGTH) return null

        return RawMessage(
            // Keyed on the posting app and the notification's own key, so the
            // same alert updated in place is not queued twice.
            sourceId = "notif:$packageName:${key ?: postTime}",
            sender = senderFor(packageName, title),
            body = body,
            receivedAtMillis = postTime,
        )
    }

    /**
     * What a taught format matches its sender fragment against.
     *
     * A messaging app relaying a bank SMS posts under its own package
     * (`com.google.android.apps.messaging`), which says nothing about the
     * bank - the title holds the real sender there. Everything else is keyed
     * on the package, which is the bank's own app.
     */
    private fun senderFor(packageName: String, title: String): String = when {
        packageName in relayPackages && title.isNotBlank() -> title
        else -> packageName
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Timber.d("Bank notification listener connected")
    }

    companion object {
        /** Apps that post someone else's message, so the title is the sender. */
        private val relayPackages = setOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.messaging",
            "com.android.mms",
            "org.thoughtcrime.securesms",
        )

        /** Below this a notification is a status line, not a bank alert. */
        private const val MIN_BODY_LENGTH = 16

        /** Whether the user has granted notification access to this app. */
        fun isGranted(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            val component = ComponentName(context, BankNotificationListener::class.java)
            return enabled.split(":").any {
                ComponentName.unflattenFromString(it) == component
            }
        }

        /** The system screen where notification access is granted. */
        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
