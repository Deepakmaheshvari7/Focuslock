package com.focuslock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot: restore schedule alarms and resume blocking if a session/window is still running. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Scheduler.sync(context)
    }
}
