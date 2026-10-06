package com.rslnabk.aivideotest.data.settings

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rslnabk.aivideotest.MainActivity
import com.rslnabk.aivideotest.R
import com.rslnabk.aivideotest.model.GenerationJob

class DemoNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val channel = "demo_creations_v1"
    init {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channel,
            context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
    }
    fun allowed(): Boolean = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE)
    fun cancel() { manager.cancelAll() }
    fun preview() = post(41, context.getString(R.string.fresh_update), context.getString(R.string.fresh_update_body), null)
    fun ready(job: GenerationJob) = post(job.id.hashCode(), context.getString(R.string.notification_ready),
        context.getString(R.string.notification_ready_body), job.id)
    private fun post(id: Int, title: String, body: String, jobId: String?) {
        if (!allowed()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = "aivideotest://demo/${jobId ?: "preview"}".toUri()
            if (jobId != null) putExtra("notification_job", jobId)
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_sparkle)
            .setContentTitle(title).setContentText(body).setSubText(context.getString(R.string.demo_notification))
            .setContentIntent(pending).setAutoCancel(true).build()
        // Permission may change between the check and delivery; that never breaks the creation.
        try { manager.notify(id, notification) } catch (_: SecurityException) { }
    }
}
