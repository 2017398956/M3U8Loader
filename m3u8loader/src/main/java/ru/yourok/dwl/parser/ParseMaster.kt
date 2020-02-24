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
        val retList = mutableListOf<DownloadInfo>()
        masterPlaylist.playlists.forEach {
            val list = DownloadInfo()
            list.url = Util.concatUriList(url, it.uri)
            list.title = it.streamInfo.closedCaptions ?: ""
            list.bandwidth = it.streamInfo.bandwidth
            retList.add(list)
        }

        masterPlaylist.iFramePlaylists.forEach {
            val list = DownloadInfo()
            list.url = Util.concatUriList(url, it.uri)
            list.bandwidth = it.bandwidth
            retList.add(list)
        }

        return retList
    }
}