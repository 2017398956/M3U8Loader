package ru.yourok.dwl.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.widget.Toast
import ru.yourok.dwl.list.DownloadInfo
import ru.yourok.m3u8loader.App
import ru.yourok.m3u8loader.R
import ru.yourok.m3u8loader.activitys.mainActivity.MainActivity

/**
 * Created by yourok on 19.11.17.
 */
object NotificationUtil {
    private var notificationManager: NotificationManager? = null

    const val TYPE_NOTIFICATION_LOAD = 0
    const val TYPE_NOTIFICATION_CONVERT = 1

    fun sendNotification(context: Context, type: Int, title: String, text: String, progress: Int) {
        val channelId = getChannelId(type)
        val channelName = getChannelName(type)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            context.getSystemService<NotificationManager>(NotificationManager::class.java)!!.createNotificationChannel(channel)
        }

        val builder: NotificationCompat.Builder = NotificationCompat.Builder(context, channelId)

        builder.setContentTitle(title)
        builder.setContentText(text)
        // LargeIcon 是右侧的大图
        // builder.setLargeIcon(BitmapFactory.decodeResource(context.resources , R.mipmap.ic_launcher))
        builder.setAutoCancel(true)
        var typeIcon = 0
        when (progress) {
            in 0..100 -> {
                builder.setProgress(100, progress, false)
                typeIcon = 1
            }
            -1 -> {
                builder.setProgress(100, 0, true)
                typeIcon = 2
            }
            else -> {
                typeIcon = 0
                builder.setProgress(0, 0, false)
            }
        }

        if (error.isNotEmpty()) {
            typeIcon = 3
        }
        // SmallIcon 是标题左侧的小图
        builder.setSmallIcon(createIcon(typeIcon))

        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT)
        builder.setContentIntent(pendingIntent)

        getManager().notify(type, builder.build())
    }

    fun sendNotification(context: Context, type: Int, title: String) {
        sendNotification(context, type, title, "", -2)
    }

    fun sendNotification(context: Context, type: Int, title: String, text: String) {
        sendNotification(context, type, title, text, -2)
    }

    private fun getChannelId(type: Int): String {
        return when (type) {
            TYPE_NOTIFICATION_LOAD -> "ru.yourok.m3u8loader.load"
            TYPE_NOTIFICATION_CONVERT -> "ru.yourok.m3u8loader.convert"
            else -> "ru.yourok.m3u8loader"
        }
    }

    private fun getChannelName(type: Int): String {
        return when (type) {
            TYPE_NOTIFICATION_LOAD -> "Loading"
            TYPE_NOTIFICATION_CONVERT -> "Converting"
            else -> "M3U8 Loader"
        }
    }

    private fun createIcon(type: Int): Int {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            if (Notifyer.error.isEmpty())
//                return R.drawable.ic_check_black_24dp
//            else
//                return R.drawable.ic_report_problem_black_24dp
//        } else {
        return when (type) {
            1 ->
                android.R.drawable.stat_sys_download
            2 ->
                android.R.drawable.stat_notify_sync_noanim
            3 ->
                android.R.drawable.stat_notify_error
            else -> R.mipmap.ic_launcher
        }
    }

    private fun getManager(): NotificationManager {
        notificationManager?.let {
            return it
        }
        notificationManager = App.getContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager!!
    }

    var error: String = ""

    fun toastEnd(downloadInfo: DownloadInfo, complete: Boolean, err: String) {
        error = err
        with(App.getContext()) {
            Handler(Looper.getMainLooper()).post {
                if (complete && err.isEmpty()) {
                    Toast.makeText(this, this.getText(R.string.complete).toString() + ": " + downloadInfo.title, Toast.LENGTH_SHORT).show()
                } else if (!err.isEmpty()) {
                    Toast.makeText(this, this.getText(R.string.error).toString() + ": " + downloadInfo.title + ", " + err, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}