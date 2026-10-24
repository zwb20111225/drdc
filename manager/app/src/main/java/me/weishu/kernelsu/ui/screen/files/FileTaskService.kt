package me.weishu.kernelsu.ui.screen.files

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R

/**
 * Foreground service that executes long file operations (big copies, extracts, archives) so the
 * process keeps running and the notification shows progress. Idle by default.
 */
class FileTaskService : Service() {

    companion object {
        private const val EXTRA_GENERATION = "generation"
        private const val EXTRA_TITLE = "title"
        private const val NOTIFICATION_ID = 0x4452
        const val CHANNEL_ID = "file_tasks"

        fun start(context: Context, generation: Long, title: String) {
            val intent = Intent(context, FileTaskService::class.java)
                .putExtra(EXTRA_GENERATION, generation)
                .putExtra(EXTRA_TITLE, title)
            context.startForegroundService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private var observingState = false
    private var currentGeneration = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.files_tasks_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val generation = intent?.getLongExtra(EXTRA_GENERATION, -1L) ?: -1L
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        currentGeneration = generation
        startForegroundCompat(buildNotification(title, null, null))

        if (!observingState) {
            observingState = true
            scope.launch {
                FileTasks.state.collect { task ->
                    if (task != null && task.generation == currentGeneration) {
                        notificationManager.notify(
                            NOTIFICATION_ID,
                            buildNotification(task.title, task.progress, task.message),
                        )
                    }
                }
            }
        }

        val block = FileTasks.takePending()
        if (block == null || generation < 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                block { progress, message -> FileTasks.report(generation, progress, message) }
                FileTasks.complete(generation, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                FileTasks.complete(generation, e.message ?: getString(R.string.files_task_done))
            } finally {
                delay(600)
                FileTasks.clear(generation)
                if (FileTasks.state.value == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(title: String, progress: Float?, message: String?): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title.ifBlank { getString(R.string.files_title) })
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
        if (progress != null) {
            builder.setProgress(100, (progress * 100).toInt().coerceIn(0, 100), false)
        } else {
            builder.setProgress(100, 0, true)
        }
        if (!message.isNullOrBlank()) builder.setContentText(message)
        return builder.build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
