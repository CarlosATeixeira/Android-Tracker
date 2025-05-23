package dev.carlosalberto.locationtrackerapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Intent(context, LocationService::class.java).also {
                ContextCompat.startForegroundService(context, it)
            }
        }
    }
}