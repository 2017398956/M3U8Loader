package ru.yourok.dwl.client

import android.net.Uri
import java.io.IOException
import java.io.InputStream


/**
 * Created by yourok on 07.11.17.
 */
interface Client {
    fun connect()
    fun connect(pos: Long): Long
    fun isConnected(): Boolean
    fun getSize(): Long
    fun getUrl(): String
    fun getInputStream(): InputStream?
    fun read(b: ByteArray): Int
    fun getErrorMessage(): String
    fun close()
}

object ClientBuilder {
    fun new(url: Uri): Client {
        url.scheme ?: throw IOException("wrong url: $url")
        return when {
            url.scheme!!.startsWith("http", 0, true) -> {
                Http(url)
            }
            url.scheme!!.startsWith("content", 0, true) -> {
                Content(url)
            }
            else -> {
                File(url)
            }
        }
    }
}