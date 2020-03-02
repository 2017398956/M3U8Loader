package ru.yourok.dwl.parser

import android.net.Uri
import com.iheartradio.m3u8.data.MasterPlaylist
import ru.yourok.dwl.client.Util
import ru.yourok.dwl.list.DownloadInfo

/**
 * Created by yourok on 09.11.17.
 */
class ParseMaster {
    fun parse(url: Uri, masterPlaylist: MasterPlaylist): MutableList<DownloadInfo> {
        val downloadInfoList = mutableListOf<DownloadInfo>()
        masterPlaylist.playlists.forEach {
            val downloadInfo = DownloadInfo()
            downloadInfo.url = Util.concatUriList(url, it.uri)
            downloadInfo.title = it.streamInfo.closedCaptions ?: ""
            downloadInfo.bandwidth = it.streamInfo.bandwidth
            downloadInfoList.add(downloadInfo)
        }

        masterPlaylist.iFramePlaylists.forEach {
            val downloadInfo = DownloadInfo()
            downloadInfo.url = Util.concatUriList(url, it.uri)
            downloadInfo.bandwidth = it.bandwidth
            downloadInfoList.add(downloadInfo)
        }

        return downloadInfoList
    }
}