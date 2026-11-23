package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

object FcmHelper {
    const val CHANNEL_ID = "jsc_club_alerts"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Journalist Sports Club Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Match announcements, availability reminders, payments, and club updates"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun fetchTokenSafely(context: Context, onToken: (String) -> Unit) {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) onToken(token)
                }
            }
        }
    }
}
