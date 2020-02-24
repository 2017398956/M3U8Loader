package ru.yourok.converter

import ru.yourok.dwl.list.DownloadInfo
import ru.yourok.m3u8converter.converter.Manager
import ru.yourok.m3u8loader.App

/**
 * Created by yourok on 05.02.18.
 */

object ConverterHelper {

    fun isSupport(): Boolean {
        return true
    }

    fun getCurrentConvert(): DownloadInfo? {
        return Manager.getCurrent()
    }

    fun isConvert(downloadInfo: DownloadInfo): Boolean {
        return Manager.contain(downloadInfo)
    }

    fun convert(downloadInfo: kotlin.collections.List<DownloadInfo>) {
        downloadInfo.forEach {
            Manager.add(it)
        }
    }

    fun startConvert() {
        App.wakeLock(1000)
        Manager.startConvert({
            if (it != null)
                App.wakeLock(1000)
        })
    }
}