package ru.yourok.dwl.list

import ru.yourok.dwl.settings.Settings

/**
 * 下载信息
 */
class DownloadInfo {
    var downloadItems: MutableList<DownloadItem> = mutableListOf()
    var url: String = ""
    var filePath: String = ""
    var bandwidth: Int = 0
    var title: String = ""
    var isConvert: Boolean = Settings.convertVideo
    var isPlayed: Boolean = false
    var subsUrl: String = ""
}

class DownloadItem {
    var index: Int = -1
    var url: String = ""
    var loaded: Long = 0
    var size: Long = 0
    var duration: Float = 0F
    var isLoad: Boolean = true
    var isComplete = false
    var encData: EncKey? = null
}
