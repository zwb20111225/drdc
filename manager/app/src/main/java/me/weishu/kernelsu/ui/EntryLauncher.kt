package me.weishu.kernelsu.ui

import android.Manifest
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import me.weishu.kernelsu.R

/**
 * Raises [MainActivity] from a hidden entry point (dialer secret code, calculator hook …).
 *
 * Android 10+ blocks background activity starts, so a `BroadcastReceiver` can no longer
 * simply `startActivity()`. The ladder implemented here, in order:
 *
 * 1. plain `startActivity` – still succeeds when the caller happens to be foreground;
 * 2. `PendingIntent.send` with
 *    [ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED] – Android 14+ honours this
 *    only when the app holds the right to start from the background, and Android 15 keeps
 *    rejecting it in most cases, hence step 3;
 * 3. a normal high‑importance notification whose content intent opens the manager. The
 *    user taps it once. No full‑screen intent, no silent background launch.
 */
object EntryLauncher {
    private const val CHANNEL_ID = "drdc_entry"
    private const val NOTIFICATION_ID = 0x4452

    /** True when the manager is already on screen (then nothing needs to be raised). */
    fun openManager(context: Context): Result {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        if (startDirectly(context, intent)) return Result.DIRECT

        // A notification is the only way to get the user's hand on the app when the
        // background activity start is refused; post it before burning time on the
        // PendingIntent attempt so a slow send() cannot swallow the fallback.
        val notifications = postNotification(context, intent)

        if (startViaPendingIntent(context, intent)) {
            if (notifications) cancelNotification(context)
            return Result.PENDING_INTENT
        }

        return if (notifications) Result.NOTIFICATION else Result.FAILED
    }

    private fun startDirectly(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun startViaPendingIntent(context: Context, intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return runCatching {
            val options = ActivityOptions.makeBasic()
            options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pending.send(context, 0, null, null, null, null, options.toBundle())
            true
        }.getOrDefault(false)
    }

    private fun postNotification(context: Context, intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }

        return runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
                ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            context.getString(R.string.entry_channel_name),
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = context.getString(R.string.entry_channel_description)
                            setShowBadge(false)
                            enableVibration(false)
                        }
                    )
                }
            }

            val content = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(context.getString(R.string.entry_notification_title))
                .setContentText(context.getString(R.string.entry_notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setContentIntent(content)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    private fun cancelNotification(context: Context) = runCatching {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    enum class Result { DIRECT, PENDING_INTENT, NOTIFICATION, FAILED }
}
