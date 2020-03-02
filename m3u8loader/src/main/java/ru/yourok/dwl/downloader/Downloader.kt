package ru.yourok.dwl.downloader

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import ru.yourok.converter.ConverterHelper
import ru.yourok.dwl.client.ClientBuilder
import ru.yourok.dwl.list.DownloadInfo
import ru.yourok.dwl.manager.NotificationUtil
import ru.yourok.dwl.settings.Settings
import ru.yourok.dwl.storage.Storage
import ru.yourok.dwl.utils.Saver
import ru.yourok.m3u8loader.App
import ru.yourok.m3u8loader.R.string.error_load_subs
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


/**
 * Created by yourok on 09.11.17.
 */
class Downloader(val downloadInfo: DownloadInfo) {
    private var pool: Pool? = null
    private var workers: kotlin.collections.List<Pair<Worker, DownloadStatus>>? = null
    private var executor: ExecutorService? = null

    private var error: String = ""

    private var complete: Boolean = false
    private var isLoading: Boolean = false
    private var stop: Boolean = false
    private var starting: Any = Any()

    init {
        var ncomp = true
        downloadInfo.downloadItems.forEach {
            if (!it.isComplete) {
                ncomp = false
                return@forEach
            }
        }
        complete = ncomp
    }

    fun load() {
        synchronized(starting) {
            synchronized(isLoading) {
                if (isLoading)
                    return
                isLoading = true
            }
            try {
                stop = false
                isLoading = true
                complete = false
                error = ""

                loadSubtitles()

                val file = FileWriter(downloadInfo.filePath)
                var resize = true
                var size = 0L
                workers = null
                if (stop) {
                    isLoading = false
                    return@synchronized
                }
                preloadSize(file)
                val tmpWorkers = mutableListOf<Pair<Worker, DownloadStatus>>()
                downloadInfo.downloadItems.forEach {
                    if (it.isLoad) {
                        val stat = DownloadStatus()
                        val wrk = Worker(it, stat, file)
                        tmpWorkers.add(Pair(wrk, stat))
                        if (it.size == 0L)
                            resize = false
                        size += it.size
                    }
                }
                if (resize)
                    file.resize(size)

                tmpWorkers.sortBy { it.first.downloadItem.index }
                workers = tmpWorkers.toList()

                file.setWorkers(workers!!)
                if (stop) {
                    isLoading = false
                    return@synchronized
                }
                downloadInfo.isPlayed = false
                pool = Pool(workers!!)
                pool!!.start()
                pool!!.onEnd {
                    complete = true
                    workers!!.forEach {
                        if (!it.first.downloadItem.isComplete)
                            complete = false
                    }

                    if (!resize && complete) {
                        size = 0L
                        workers!!.forEach {
                            size += it.first.downloadItem.size
                        }
                        file.resize(size)
                    }
                    file.close()
                    Saver.saveList(downloadInfo)
                    isLoading = false
                    if (complete && downloadInfo.isConvert) {
                        ConverterHelper.convert(mutableListOf(downloadInfo))
                        ConverterHelper.startConvert()
                    }
                    NotificationUtil.toastEnd(downloadInfo, complete, error)
                }

                pool!!.onFinishWorker {
                    Saver.saveList(downloadInfo)
                }
                pool!!.onError {
                    error = it
                    workers?.forEach { it.first.stop() }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                error = e.message ?: ""
                isLoading = false
            }
        }
    }

    fun stop() {
        stop = true
        pool?.stop()
        executor?.shutdownNow()
    }

    fun waitEnd() {
        synchronized(starting) {}
        if (pool != null)
            pool!!.waitEnd()
    }

    fun isComplete(): Boolean = complete
    fun isLoading(): Boolean = isLoading

    fun clear() {
        stop()
        waitEnd()
        complete = false
    }

    fun getState(): State {
        val state = State()
        synchronized(downloadInfo) {
            state.name = downloadInfo.title
            state.url = downloadInfo.url
            state.file = downloadInfo.filePath
            state.threads = pool?.size() ?: 0
            state.error = error
            state.isComplete = complete
            state.isPlayed = downloadInfo.isPlayed
            if (isLoading)
                state.state = LoadState.ST_LOADING
            else if (complete)
                state.state = LoadState.ST_COMPLETE
            else if (!error.isEmpty())
                state.state = LoadState.ST_ERROR


            if (workers?.size != 0 && pool?.isWorking() == true) {
                state.fragments = workers!!.size
                workers!!.forEach {
                    if (it.first.downloadItem.isLoad) {
                        val itmState = ItemState()
                        itmState.loaded = it.first.downloadItem.loaded
                        itmState.size = it.first.downloadItem.size
                        itmState.complete = it.first.downloadItem.isComplete
                        itmState.error = it.second.isError
                        state.loadedItems.add(itmState)

                        state.size += it.first.downloadItem.size
                        if (it.first.downloadItem.isComplete)
                            state.loadedBytes += it.first.downloadItem.size
                        else
                            state.loadedBytes += it.first.downloadItem.loaded
                        if (it.first.downloadItem.isComplete) {
                            state.loadedFragments++
                        }
                        if (it.second.isLoading)
                            state.speed += it.second.speed
                    }
                }
            } else {
                downloadInfo.downloadItems.forEach {
                    if (it.isLoad) {
                        val itmState = ItemState()
                        itmState.size = it.size
                        itmState.complete = it.isComplete
                        itmState.loaded = it.loaded
                        state.loadedItems.add(itmState)

                        state.fragments++
                        state.size += it.size
                        if (it.isComplete) {
                            state.loadedBytes += itmState.size
                            state.loadedFragments++
                        } else
                            state.loadedBytes += itmState.loaded
                    }
                }
                if (state.isComplete) {
                    val fSize = Storage.getDocument(downloadInfo.filePath).length()
                    if (fSize > 0)
                        state.size = fSize
                }
            }
        }
        return state
    }

    private fun loadSubtitles() {
        if (!downloadInfo.subsUrl.isEmpty()) {
            try {
                val file = File(Settings.downloadPath, downloadInfo.title + ".srt")
                if (!file.exists() || file.length() == 0L) {
                    val client = ClientBuilder.new(Uri.parse(downloadInfo.subsUrl))
                    client.connect()
                    val subs = client.getInputStream()?.bufferedReader()?.readText() ?: ""
                    client.close()
                    if (subs.isNotEmpty()) {
                        val writer = FileWriter(File(Settings.downloadPath, downloadInfo.title + ".srt").path)
                        writer.resize(0)
                        writer.write(subs.toByteArray(), 0)
                        writer.close()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(App.getContext(), error_load_subs, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun preloadSize(file: FileWriter) {
        if (Settings.preloadSize) {
            executor = Executors.newFixedThreadPool(20)
            downloadInfo.downloadItems.forEach {
                if (it.isLoad && it.size == 0L && !stop) {
                    val worker = Runnable {
                        for (i in 1..Settings.errorRepeat)
                            try {
                                val clientPS = ClientBuilder.new(Uri.parse(it.url))
                                clientPS.connect()
                                it.size = clientPS.getSize()
                                clientPS.close()
                                if (it.size == 0L)
                                    break
                                return@Runnable
                            } catch (e: Exception) {
                            }
                        executor?.shutdownNow()
                    }
                    executor?.execute(worker)
                }
            }
            executor?.shutdown()
            executor?.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
            executor = null
            Saver.saveList(downloadInfo)
        }
    }
}