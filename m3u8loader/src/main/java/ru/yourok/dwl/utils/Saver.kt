package ru.yourok.dwl.utils

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import ru.yourok.dwl.list.DownloadItem
import ru.yourok.dwl.list.DownloadInfo
import ru.yourok.dwl.settings.Settings
import ru.yourok.m3u8loader.App
import java.io.File
import java.io.FileOutputStream

/**
 * Created by yourok on 09.12.17.
 */

object Saver {

    fun removeList(downloadInfo: DownloadInfo) {
        val path = App.getContext().filesDir?.path
        val file = File(path, downloadInfo.title + ".lst")
        if (file.exists()) {
            file.delete()
        }
    }

    fun saveList(downloadInfo: DownloadInfo) {
        try {
            synchronized(downloadInfo) {
                val js = list2Json(downloadInfo)
                val path = App.getContext().filesDir?.path
                val file = File(path, downloadInfo.title + ".lst")
                val str = js.toString(1)
                val stream = FileOutputStream(file)
                stream.write(str.toByteArray())
                stream.flush()
                stream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveSettings() {
        try {
            val js = JSONObject()
            js.put("threads", Settings.threads)
            js.put("errorRepeat", Settings.errorRepeat)
            js.put("downloadPath", Settings.downloadPath)
            js.put("preloadSize", Settings.preloadSize)
            js.put("convertVideo", Settings.convertVideo)
            js.put("headers", JSONObject(Settings.headers as Map<*, *>))
            val path = App.getContext().filesDir
            val file = File(path, "settings.cfg")
            val str = js.toString(1)
            val stream = FileOutputStream(file)
            stream.write(str.toByteArray())
            stream.flush()
            stream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun list2Json(downloadInfo: DownloadInfo): JSONObject {
        val js = JSONObject()
        js.put("url", downloadInfo.url)
        js.put("filePath", downloadInfo.filePath)
        js.put("title", downloadInfo.title)
        js.put("bandwidth", downloadInfo.bandwidth)
        js.put("isConvert", downloadInfo.isConvert)
        js.put("isPlayed", downloadInfo.isPlayed)
        js.put("subsUrl", downloadInfo.subsUrl)
        js.put("items", items2Json(downloadInfo.downloadItems))

        return js
    }

    private fun items2Json(downloadItems: kotlin.collections.List<DownloadItem>): JSONArray {
        val jsarr = JSONArray()

        downloadItems.forEach {
            val js = JSONObject()
            js.put("index", it.index)
            js.put("url", it.url)
            js.put("loaded", it.loaded)
            js.put("size", it.size)
            js.put("duration", it.duration.toDouble())
            js.put("isLoad", it.isLoad)
            js.put("isComplete", it.isComplete)
            it.encData?.key?.let {
                val key = Base64.encodeToString(it, Base64.NO_PADDING or Base64.NO_WRAP)
                js.put("encDataKey", key)
            }
            it.encData?.key?.let {
                val iv = Base64.encodeToString(it, Base64.NO_PADDING or Base64.NO_WRAP)
                js.put("encDataIV", iv)
            }
            jsarr.put(js)
        }
        return jsarr
    }
}