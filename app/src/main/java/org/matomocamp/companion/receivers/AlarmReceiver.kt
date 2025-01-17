package org.matomocamp.companion.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.matomocamp.companion.BuildConfig
import org.matomocamp.companion.alarms.AppAlarmManager
import org.matomocamp.companion.utils.BackgroundWorkScope
import org.matomocamp.companion.utils.goAsync
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Entry point for system-generated events: boot complete and alarms.
 *
 * @author Christophe Beyls
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmManager: AppAlarmManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NOTIFY_EVENT -> intent.dataString?.toLongOrNull()?.let { eventId ->
                goAsync(BackgroundWorkScope) {
                    alarmManager.notifyEvent(eventId)
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> goAsync(BackgroundWorkScope) {
                alarmManager.onBootCompleted()
            }
        }
    }

    companion object {
        const val ACTION_NOTIFY_EVENT = BuildConfig.APPLICATION_ID + ".action.NOTIFY_EVENT"
    }
}