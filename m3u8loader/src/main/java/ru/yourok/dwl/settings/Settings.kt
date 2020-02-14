package ru.yourok.dwl.settings

import androidx.preference.PreferenceManager
import ru.yourok.m3u8loader.App


/**
 * Created by yourok on 07.11.17.
 */
object Settings {
    var threads: Int = 10
    // 发生下载错误后可自动重试下载的次数
    var errorRepeat: Int = 5
    var downloadPath: String = ""
    var preloadSize: Boolean = true
    var convertVideo: Boolean = false
    var headers: MutableMap<String, String> = mutableMapOf()
}

object Preferences {
    fun get(name: String, def: Any): Any? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext())
        if (prefs.all.containsKey(name))
            return prefs.all[name]
        return def
    }

    fun set(name: String, value: Any?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(App.getContext())
        when (value) {
            is String -> prefs.edit().putString(name, value).apply()
            is Boolean -> prefs.edit().putBoolean(name, value).apply()
            is Float -> prefs.edit().putFloat(name, value).apply()
            is Int -> prefs.edit().putInt(name, value).apply()
            is Long -> prefs.edit().putLong(name, value).apply()
            is MutableSet<*>? -> prefs.edit().putStringSet(name, value as MutableSet<String>?).apply()
            else -> prefs.edit().putString(name, value.toString()).apply()
        }
    }
}