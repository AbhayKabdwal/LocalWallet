package com.ivy.widget.messagesync

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ivy.domain.AppStarter
import com.ivy.smsimport.data.SmsImportDao
import com.ivy.smsimport.domain.ScanMessagesWorker
import com.ivy.ui.R
import com.ivy.widgets.WidgetBase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private object SyncPrefsKey {
    const val PENDING = "pending_messages"
}

/**
 * A home-screen shortcut for the message importer.
 *
 * The existing widgets are all for entering a transaction by hand, which is
 * exactly the work this feature exists to remove. This one shows how many
 * transactions the app has already found in bank alerts and gets them
 * reviewed in a tap, so the automatic path is as reachable from the home
 * screen as the manual one.
 */
class MessageSyncWidget(
    private val getAppStarter: () -> AppStarter,
) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val pending = prefs[intPreferencesKey(SyncPrefsKey.PENDING)] ?: 0

            Content(
                pending = pending,
                onReviewClick = actionStartActivity(getAppStarter().defaultStartIntent()),
            )
        }
    }

    @Composable
    private fun Content(
        pending: Int,
        onReviewClick: androidx.glance.action.Action,
    ) {
        Box(
            modifier = GlanceModifier
                .background(ImageProvider(R.drawable.shape_widget_background))
                .clickable(onReviewClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (pending > 0) pending.toString() else "—",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color.White),
                        textAlign = TextAlign.Center,
                    ),
                )
                Text(
                    text = when (pending) {
                        0 -> "No messages waiting"
                        1 -> "transaction found in messages"
                        else -> "transactions found in messages"
                    },
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = ColorProvider(Color.White),
                        textAlign = TextAlign.Center,
                    ),
                )
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        modifier = GlanceModifier.clickable(actionRunCallback<ScanNowAction>()),
                        text = "Sync now",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(Color.White),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Kicks off a scan from the widget.
 *
 * Enqueues the same worker the app uses rather than scanning inline: a
 * widget callback runs on a short leash, and a regex pass over a few hundred
 * messages is not work to do there.
 */
class ScanNowAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        ScanMessagesWorker.enqueue(context)
        MessageSyncWidgetReceiver.updateBroadcast(context)
    }
}

@Keep
@AndroidEntryPoint
class MessageSyncWidgetReceiver : GlanceAppWidgetReceiver() {
    companion object {
        fun updateBroadcast(context: Context) {
            WidgetBase.updateBroadcast(context, MessageSyncWidgetReceiver::class.java)
        }
    }

    override val glanceAppWidget: GlanceAppWidget = MessageSyncWidget(
        getAppStarter = { appStarter },
    )
    private val coroutineScope = MainScope()

    @Inject
    lateinit var appStarter: AppStarter

    @Inject
    lateinit var dao: SmsImportDao

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateData(context)
    }

    private fun updateData(context: Context) {
        coroutineScope.launch {
            val pending = dao.pendingCount().first()

            GlanceAppWidgetManager(context).getGlanceIds(MessageSyncWidget::class.java)
                .forEach { id ->
                    updateAppWidgetState(
                        context,
                        PreferencesGlanceStateDefinition,
                        id,
                    ) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[intPreferencesKey(SyncPrefsKey.PENDING)] = pending
                        }
                    }
                    glanceAppWidget.update(context, id)
                }
        }
    }
}
