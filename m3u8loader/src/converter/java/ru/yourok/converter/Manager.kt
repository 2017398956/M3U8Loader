package ru.yourok.m3u8converter.converter

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import ru.yourok.converter.Converter
import ru.yourok.dwl.list.DownloadInfo
import ru.yourok.dwl.manager.NotificationUtil
import ru.yourok.m3u8loader.App
import ru.yourok.m3u8loader.R
import kotlin.concurrent.thread


/**
 * Created by yourok on 28.11.17.
 */
object Manager {
    @Volatile
    private var convDownloadInfo: MutableList<DownloadInfo> = mutableListOf()
    private val lock = Any()
    private var converting = false
    private var currentConvert: DownloadInfo? = null

    fun add(item: DownloadInfo) {
        synchronized(convDownloadInfo) {
            if (!convDownloadInfo.contains(item))
                convDownloadInfo.add(item)
        }
    }

    fun getCurrent(): DownloadInfo? {
        return currentConvert
    }

    fun contain(item: DownloadInfo): Boolean {
        synchronized(convDownloadInfo) {
            return (convDownloadInfo.contains(item))
        }
    }

    fun clear() {
        synchronized(convDownloadInfo) {
            convDownloadInfo.clear()
        }
    }

    fun startConvert(onEndConvertList: ((downloadInfo: DownloadInfo?) -> Unit)?) {
        synchronized(lock) {
            if (converting)
                return
            converting = true
        }
        thread {
            val errors = mutableListOf<String>()
            while (convDownloadInfo.size > 0 && converting) {
                synchronized(convDownloadInfo) {
                    currentConvert = convDownloadInfo[0]
                }
                currentConvert?.let {
                    NotificationUtil.sendNotification(App.getContext(), NotificationUtil.TYPE_NOTIFICATION_CONVERT, App.getContext().getString(R.string.converting), it.title, -1)
                    val err = Converter.convert(it)
                    onEndConvertList?.invoke(it)
                    Handler(Looper.getMainLooper()).post {
                        if (err.isNotEmpty()) {
                            Toast.makeText(App.getContext(), err, Toast.LENGTH_SHORT).show()
                            errors.add(it.title)
                        } else
                            Toast.makeText(App.getContext(), "Converted: " + it.title, Toast.LENGTH_SHORT).show()
                    }
                }
                synchronized(convDownloadInfo) {
                    convDownloadInfo.removeAt(0)
                }
            }
            onEndConvertList?.invoke(null)
            converting = false
            currentConvert = null
            if (errors.isEmpty())
                NotificationUtil.sendNotification(App.getContext(), NotificationUtil.TYPE_NOTIFICATION_CONVERT, App.getContext().getString(R.string.converting_complete))
            else
                NotificationUtil.sendNotification(App.getContext(), NotificationUtil.TYPE_NOTIFICATION_CONVERT, App.getContext().getString(R.string.converting_error), errors.joinToString(", "))
        }
    }

    fun stop() {
        converting = false
    }
}