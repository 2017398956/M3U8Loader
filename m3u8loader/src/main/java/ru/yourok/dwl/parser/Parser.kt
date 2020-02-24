package ru.yourok.dwl.parser

import android.net.Uri
import android.util.Log
import com.iheartradio.m3u8.*
import ru.yourok.dwl.client.Client
import ru.yourok.dwl.client.ClientBuilder
import ru.yourok.dwl.list.DownloadInfo
import ru.yourok.dwl.settings.Settings
import ru.yourok.dwl.utils.Utils
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset

/**
 * Created by yourok on 07.11.17.
 */
class Parser(val name: String, val url: String, val downloadPath: String) {

    var stop = false
    var parseMedia: ParseMedia? = null

    fun parse(): MutableList<DownloadInfo> {
        var clientEx: Client? = null
        try {
            stop = false
            val retList = mutableListOf<DownloadInfo>()
            val client = ClientBuilder.new(Uri.parse(url))
            clientEx = client
            // 尝试网络连接
            for (i in 0..Settings.errorRepeat) {
                try {
                    client.connect()
                    break
                } catch (e: Exception) {
                    if (i == Settings.errorRepeat) {
                        throw e
                    }
                }
            }
            val chkBuf = ByteArray(40)
            val chkSz = client.getInputStream()?.read(chkBuf) ?: 0
            if (chkSz != 0 && !Utils.isTextBuffer(chkBuf)) {
                throw java.text.ParseException("m3u8 list contains wrong characters: " + chkBuf.toString(Charset.defaultCharset()), -1)
            }
            var listStr = client.getInputStream()?.bufferedReader()?.readText() ?: ""
            if (listStr.isNotEmpty()) {
                listStr = chkBuf.toString(Charset.defaultCharset()) + listStr
            }
            client.close()

            listStr = listStr.split("\n").filter { it.isNotEmpty() }.joinToString("\n")

            val m3u8PlaylistParser = PlaylistParser(ByteArrayInputStream(listStr.toByteArray()), Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT)
            val m3u8Playlist = m3u8PlaylistParser.parse()

            if (m3u8Playlist.hasMasterPlaylist()) {
                val mList = ParseMaster().parse(Uri.parse(client.getUrl()), m3u8Playlist.masterPlaylist)
                retList.addAll(mList)
            }
            if (m3u8Playlist.hasMediaPlaylist()) {
                val list = DownloadInfo()
                list.url = client.getUrl()
                list.title = name
                retList.add(list)
            }
            for (i in 0 until retList.size) {
                val list = retList[i]
                if (list.title.isEmpty()) {
                    var band = list.bandwidth
                    if (band == 0)
                        band = i
                    list.title = name + "_" + band
                }
                list.filePath = File(downloadPath, Utils.cleanFileName(list.title) + ".mp4").canonicalPath
                parseMedia = ParseMedia(downloadPath)
                retList.addAll(parseMedia!!.parse(list))
                if (list.downloadItems.size == 0) {
                    retList.remove(list)
                }
                if (stop) break
            }
            return retList

        } catch (e: java.text.ParseException) {
            e.printStackTrace()
            throw java.text.ParseException("Wrong format: " + clientEx?.getUrl() + " " + e.message, e.errorOffset)
        } catch (e: ParseException) {
            e.printStackTrace()
            throw java.text.ParseException("Error parse list: " + clientEx?.getUrl() + " " + e.message, 0)
        } catch (e: Exception) {
            e.printStackTrace()
            throw IOException("Error loadList list: " + clientEx?.getUrl() + " " + clientEx?.getErrorMessage() + " " + e.message)
        }
    }

    fun stop() {
        stop = true
        if (parseMedia != null)
            parseMedia!!.stop()
    }
}