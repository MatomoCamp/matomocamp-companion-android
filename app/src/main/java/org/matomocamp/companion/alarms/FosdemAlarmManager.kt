package org.matomocamp.companion.alarms

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.annotation.MainThread
import androidx.preference.PreferenceManager
import org.matomocamp.companion.model.AlarmInfo
import org.matomocamp.companion.services.AlarmIntentService
import org.matomocamp.companion.utils.PreferenceKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * This class monitors bookmarks and preferences changes to dispatch alarm update work to AlarmIntentService.
 *
 * @author Christophe Beyls
 */
@Singleton
class FosdemAlarmManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val onSharedPreferenceChangeListener = OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            PreferenceKeys.NOTIFICATIONS_ENABLED -> {
                val isEnabled = sharedPreferences.getBoolean(PreferenceKeys.NOTIFICATIONS_ENABLED, false)
                this.isEnabled = isEnabled
                if (isEnabled) {
                    startUpdateAlarms()
                } else {
                    startDisableAlarms()
                }
            }
            PreferenceKeys.NOTIFICATIONS_DELAY -> startUpdateAlarms()
        }
    }

    @MainThread
    fun init() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.context)
        isEnabled = sharedPreferences.getBoolean(PreferenceKeys.NOTIFICATIONS_ENABLED, false)
        sharedPreferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
    }

    var isEnabled: Boolean = false
        private set

    @MainThread
    fun onScheduleRefreshed() {
        if (isEnabled) {
            startUpdateAlarms()
        }
    }

    @MainThread
    fun onBookmarksAdded(alarmInfos: List<AlarmInfo>) {
        if (isEnabled) {
            val arrayList = if (alarmInfos is ArrayList<AlarmInfo>) alarmInfos else ArrayList(alarmInfos)
            val serviceIntent = Intent(AlarmIntentService.ACTION_ADD_BOOKMARKS)
                    .putParcelableArrayListExtra(AlarmIntentService.EXTRA_ALARM_INFOS, arrayList)
            AlarmIntentService.enqueueWork(context, serviceIntent)
        }
    }

    @MainThread
    fun onBookmarksRemoved(eventIds: LongArray) {
        if (isEnabled) {
            val serviceIntent = Intent(AlarmIntentService.ACTION_REMOVE_BOOKMARKS)
                    .putExtra(AlarmIntentService.EXTRA_EVENT_IDS, eventIds)
            AlarmIntentService.enqueueWork(context, serviceIntent)
        }
    }

    private fun startUpdateAlarms() {
        val serviceIntent = Intent(AlarmIntentService.ACTION_UPDATE_ALARMS)
        AlarmIntentService.enqueueWork(context, serviceIntent)
    }

    private fun startDisableAlarms() {
        val serviceIntent = Intent(AlarmIntentService.ACTION_DISABLE_ALARMS)
        AlarmIntentService.enqueueWork(context, serviceIntent)
    }
}