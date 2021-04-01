package ru.yourok.m3u8loader

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.PowerManager
import android.util.Log
import com.uuzuche.lib_zxing.activity.ZXingLibrary
import ru.yourok.dwl.manager.Manager
import ru.yourok.dwl.settings.Settings
import ru.yourok.dwl.utils.Loader


class App : Application() {
    companion object {
        private lateinit var contextApp: Context
        private lateinit var wakeLock: PowerManager.WakeLock

        fun getContext(): Context {
            return contextApp
        }

        fun wakeLock(timeout: Long) {
            wakeLock.acquire(timeout)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ZXingLibrary.initDisplayOpinion(this);
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "M3U8Loader:WakeLock")

        ACR.get(application = this)
                .setEmailAddresses("2017398956@qq.com")
                .setEmailSubject(getString(R.string.app_name) + " Crash Report")
                .start {
                    try {
                        Manager.saveLists()
                    } catch (e: Exception) {
                    }
                }

        contextApp = applicationContext

        Loader.loadSettings()

        if (Settings.downloadPath.isEmpty()) {
            // TODO 修复过时方法
            Settings.downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path
        }

    }
}
