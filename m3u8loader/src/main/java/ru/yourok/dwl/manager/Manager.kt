package ru.yourok.dwl.manager

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import ru.yourok.dwl.downloader.Downloader
import ru.yourok.dwl.downloader.LoadState
import ru.yourok.dwl.downloader.State
import ru.yourok.dwl.list.DownloadInfo
import ru.yourok.dwl.storage.Storage
import ru.yourok.dwl.utils.Loader
import ru.yourok.dwl.utils.Saver
import ru.yourok.m3u8loader.App
import ru.yourok.m3u8loader.R
import ru.yourok.m3u8loader.activitys.mainActivity.MainActivity
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread


object Manager {
    @Volatile
    private var loaderList: MutableList<Downloader> = mutableListOf()
    @Volatile
    private var queueList: MutableList<Int> = mutableListOf()

    init {
        Loader.loadLists()?.forEach {
            loaderList.add(Downloader(it))
        }
    }

    fun getLoader(index: Int): Downloader? {
        if (index in 0 until loaderList.size)
            return loaderList[index]
        return null
    }

    fun findLoader(downloadInfo: DownloadInfo): Downloader? {
        loaderList.forEach {
            if (it.downloadInfo.url + it.downloadInfo.title == downloadInfo.url + downloadInfo.title)
                return it
        }
        return null
    }

    fun getLoadersSize(): Int {
        return loaderList.size
    }

    fun getLoaderStat(i: Int): State? {
        return getLoader(i)?.getState()
    }

    /**
     * 添加新的下载任务
     */
    fun addList(downloadInfo: MutableList<DownloadInfo>) {
        synchronized(loaderList) {
            downloadInfo.forEach {
                var isFindUrl = false
                loaderList.forEach { item ->
                    // 检查是否已经添加过该下载任务
                    if (it.url == item.downloadInfo.url) {
                        // 这里虽然抛出异常，但是在调用的地方 try catch 了，并且弹出了 Toast
                        throw IOException(App.getContext().getString(R.string.error_same_url))
                    }
                }
                // 如果没有添加过这个任务，则添加
                if (!isFindUrl) {
                    //find equal url
                    val flist = loaderList.find { downloader ->
                        downloader.getState().name == it.title
                    }
                    //replace urls if find eq
                    if (flist != null) {
                        flist.downloadInfo.url = it.url
                        if (flist.downloadInfo.downloadItems.size != it.downloadItems.size) {
                            it.downloadItems = flist.downloadInfo.downloadItems
                        } else
                            flist.downloadInfo.downloadItems.forEachIndexed { index, item ->
                                if (index < it.downloadItems.size)
                                    item.url = it.downloadItems[index].url
                            }

                    } else
                        loaderList.add(Downloader(it))
                }
            }
            downloadInfo.forEach { Saver.saveList(it) }
        }
    }

    fun removes(indexes: Set<Int>, activity: Activity) {
        synchronized(loaderList) {
            var isFile = false
            indexes.forEach {
                if (File(loaderList[it].downloadInfo.filePath).exists()) {
                    isFile = true
                    return@forEach
                }
            }
            if (isFile) {
                with(activity) {
                    AlertDialog.Builder(activity)
                            .setTitle(this@with.getString(R.string.delete) + "?")
                            .setPositiveButton(R.string.delete_with_files) { _, _ ->
                                removesSome(indexes, true)
//                                (activity as? MainActivity)?.update()
                            }
                            .setNegativeButton(R.string.remove_from_list) { _, _ ->
                                removesSome(indexes, false)
//                                (activity as? MainActivity)?.update()
                            }
                            .setNeutralButton(" ", null)
                            .show()
                }
            } else {
                removesSome(indexes, false)
            }
        }
    }

    private fun removesSome(indexes: Set<Int>, withFile: Boolean) {
        val delLoader: MutableList<Downloader> = mutableListOf()
        //save deleted items and delete from queue
        indexes.forEach {
            stop(it)
            delLoader.add(loaderList[it])
        }

        //remove loaders
        delLoader.forEach {
            it.waitEnd()
            Saver.removeList(it.downloadInfo)
            loaderList.remove(it)
            if (withFile) {
                Storage.getDocument(it.downloadInfo.filePath).delete()
                if (it.downloadInfo.subsUrl.isNotEmpty()) {
                    Storage.getDocument(File(File(it.downloadInfo.filePath).parent, it.downloadInfo.title + ".srt").canonicalPath)?.delete()
                }
            }
        }

        //shift queue list
        synchronized(lockQueue) {
            indexes.forEach {
                for (i in 0 until queueList.size)
                    if (queueList[i] > it)
                        queueList[i] -= delLoader.size
            }
        }
    }

    fun removeAll(activity: Activity) {
        synchronized(loaderList) {
            var isFile = false
            loaderList.forEach {
                if (File(it.downloadInfo.filePath).exists()) {
                    isFile = true
                    return@forEach
                }
            }
            if (isFile) {
                with(activity) {
                    AlertDialog.Builder(activity)
                            .setTitle(this@with.getString(R.string.delete_all_items) + "?")
                            .setPositiveButton(R.string.delete_with_files) { _, _ ->
                                stopAll()
                                loaderList.forEach {
                                    it.waitEnd()
                                    Saver.removeList(it.downloadInfo)
                                    Storage.getDocument(it.downloadInfo.filePath).delete()
                                    if (it.downloadInfo.subsUrl.isNotEmpty())
                                        Storage.getDocument(File(File(it.downloadInfo.filePath).parent, it.downloadInfo.title + ".srt").canonicalPath).delete()
                                }
                                loaderList.clear()
                            }
                            .setNegativeButton(R.string.remove_from_list) { _, _ ->
                                stopAll()
                                loaderList.forEach {
                                    it.waitEnd()
                                    Saver.removeList(it.downloadInfo)
                                }
                                loaderList.clear()
                            }
                            .setNeutralButton(" ", null)
                            .show()
                }
            } else {
                stopAll()
                loaderList.forEach {
                    it.waitEnd()
                    Saver.removeList(it.downloadInfo)
                }
                loaderList.clear()
            }
        }
    }

    fun saveLists() {
        try {
            loaderList.forEach { Saver.saveList(it.downloadInfo) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /////////Queue funcs
    private val lockQueue: Any = Any()
    private var loading: Boolean = false
    private var currentLoader = -1

    fun isLoading(): Boolean {
        loaderList.forEach {
            if (it.isLoading())
                return true
        }
        return false
    }

    /**
     * 下载任务列表中的第 index 个任务
     */
    fun load(index: Int) {
        if (index in 0 until loaderList.size) {
            if (loaderList[index].isComplete())
                return
            if (!inQueue(index))
                queueList.add(index)
            synchronized(lockQueue) {
                thread { startLoading() }
            }
        }
    }

    fun loadAll() {
        loaderList.forEachIndexed { index, downloader ->
            if (!downloader.isComplete() && !inQueue(index))
                queueList.add(index)
        }
        synchronized(lockQueue) {
            thread { startLoading() }
        }
    }

    fun stop(index: Int) {
        loaderList[index].stop()
        if (queueList.contains(index))
            synchronized(lockQueue) {
                queueList.remove(index)
            }
    }

    fun stopAll() {
        synchronized(lockQueue) {
            queueList.clear()
            loaderList.forEach { it.stop() }
            currentLoader = -1
        }
    }

    private fun startLoading() {
        synchronized(lockQueue) {
            if (queueList.isEmpty())
                return
            if (loading)
                return
            loading = true
        }
        thread {
            LoaderService.start()
            currentLoader = -1
            while (queueList.size > 0) {
                if (!loading)
                    break
                var loader: Downloader? = null
                synchronized(lockQueue) {
                    currentLoader = queueList[0]
                    if (currentLoader in 0 until loaderList.size)
                        loader = loaderList[currentLoader]
                    thread { loader?.load() }
                    queueList.removeAt(0)
                }
                loader?.let {
                    Thread.sleep(100)
                    it.waitEnd()
                    if (currentLoader != -1 && it.getState().state == LoadState.ST_ERROR) {
                        loading = false
                        queueList.clear()
                    }
                    Saver.saveList(it.downloadInfo)
                }
            }
            loading = false
            currentLoader = -1
            LoaderService.stop()
            saveLists()
        }
    }

    fun inQueue(index: Int): Boolean {
        synchronized(lockQueue) {
            if (index == currentLoader)
                return true
            if (index in 0 until loaderList.size)
                queueList.forEach { if (it == index) return true }
            return false
        }
    }

    fun getCurrentLoader(): Int {
        return currentLoader
    }

    /**
     * 获取未下载完成的任务
     */
    fun canDownloadingList(canDownloadingList: MutableList<Downloader>) {
        for (i in 0 until loaderList.size) {
            if (!loaderList[i].isComplete()) {
                canDownloadingList.add(loaderList[i])
            }
        }
    }

    /**
     * 获取已下载完成的任务
     */
    fun downloadedList(downloadedList: MutableList<Downloader>) {
        for (i in 0 until loaderList.size) {
            if (loaderList[i].isComplete()) {
                downloadedList.add(loaderList[i])
            }
        }
    }
}