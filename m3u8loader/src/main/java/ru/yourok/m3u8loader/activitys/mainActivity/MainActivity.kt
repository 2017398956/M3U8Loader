package ru.yourok.m3u8loader.activitys.mainActivity

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.activity_main.*
import ru.yourok.dwl.manager.Manager
import ru.yourok.dwl.settings.Preferences
import ru.yourok.dwl.updater.Updater
import ru.yourok.m3u8loader.BuildConfig
import ru.yourok.m3u8loader.R
import ru.yourok.m3u8loader.activitys.DonateActivity
import ru.yourok.m3u8loader.activitys.preferenceActivity.PreferenceActivity
import ru.yourok.m3u8loader.fragment.DownloadedFragment
import ru.yourok.m3u8loader.fragment.DownloadingFragment
import ru.yourok.m3u8loader.theme.Theme
import java.util.*
import kotlin.concurrent.schedule

class MainActivity : AppCompatActivity() {

    // 是否一直刷新任务列表
    private var canRefresh: Boolean = true
    private val tabNames = mutableListOf("下载中", "已下")
    private val fragments = mutableListOf(
            DownloadingFragment(R.layout.fragment_downloading),
            DownloadedFragment(R.layout.fragment_downloaded)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.set(this)
        setContentView(R.layout.activity_main)
        requestPermissionWithRationale()
        initView()
        // 版本升级提示，这里先关闭
        Timer().schedule(1000) {
            if (false && Updater.hasNewUpdate()) {
                Updater.showSnackbar(this@MainActivity)
            }
        }
    }

    private fun initView() {
        vp_2.adapter = object : FragmentStateAdapter(supportFragmentManager, lifecycle) {
            override fun getItemCount(): Int {
                return fragments.size
            }

            override fun createFragment(position: Int): Fragment {
                return fragments[position]
            }
        }
        TabLayoutMediator(tabLayout,
                vp_2,
                true,
                TabLayoutMediator.TabConfigurationStrategy { tab, position -> tab.text = tabNames[position] })
                .attach()
    }

    override fun onResume() {
        super.onResume()
        canRefresh = true
        // 当任务列表界面重新展示在前台时要刷新一下，以更新其它位置的操作
        showDonate()
    }

    override fun onPause() {
        super.onPause()
        canRefresh = false
    }

    override fun onStop() {
        super.onStop()
        Manager.saveLists()
    }

    override fun onBackPressed() {
        if ((fragments[0] as DownloadingFragment).closeDrawer()) {
            return
        }

        if (Manager.isLoading()) {
            moveTaskToBack(true)
        }
        super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i("NFL", "requestCode $requestCode")
        if (requestCode == PreferenceActivity.Result && PreferenceActivity.changTheme) {
            Theme.changeNow(this, Preferences.get("ThemeDark", true) as Boolean)
        }
    }


    private fun requestPermissionWithRationale() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Snackbar.make(findViewById<View>(R.id.main_layout), R.string.permission_storage_msg, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.permission_btn)
                    {
                        ActivityCompat.requestPermissions(this,
                                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
                    }
                    .show()
        } else {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && BuildConfig.FLAVOR != "lite"
                && !(Preferences.get("DozeRequestCancel", false) as Boolean)) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val inWhiteList = powerManager.isIgnoringBatteryOptimizations(packageName)
            // 添加电池优化白名单
            if (!inWhiteList) {
                AlertDialog.Builder(this)
                        .setTitle(R.string.doze_request_title)
                        .setMessage(R.string.doze_request)
                        .setPositiveButton(android.R.string.yes, DialogInterface.OnClickListener { dialogInterface, i ->
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + packageName))
                            startActivity(intent)
                        })
                        .setNeutralButton("", null)
                        .setNegativeButton(android.R.string.no, DialogInterface.OnClickListener { dialogInterface, i ->
                            Preferences.set("DozeRequestCancel", true)
                        })
                        .show()
            }
        }
    }

    // 是否展示过打赏栏
    private var hasShowDonate = false

    /**
     * 展示打赏栏
     */
    private fun showDonate() {
        val last: Long = Preferences.get("LastViewDonate", 0L) as Long
        if (last == -1L || System.currentTimeMillis() < last || hasShowDonate) {
            return
        }
        // 至少 5 分钟后才在重新打开该界面时展示打赏提示
        Preferences.set("LastViewDonate", System.currentTimeMillis() + 5 * 60 * 1000)
        val snackBar = Snackbar.make(findViewById(R.id.main_layout), R.string.donation, Snackbar.LENGTH_INDEFINITE)
        // 用户进入主界面 5s 后，展示捐赠界面
        Handler().postDelayed({
            snackBar
                    .setAction(android.R.string.ok) {
                        Preferences.set("LastViewDonate", System.currentTimeMillis())
                        startActivity(Intent(this@MainActivity, DonateActivity::class.java))
                    }
                    .show()
            hasShowDonate = true
        }, 5000)

        // 用户如果没有点击捐赠栏，则在 10s 后自动关闭打赏栏
        Handler().postDelayed(Runnable {
            if (snackBar.isShown) {
                snackBar.dismiss()
            }
            hasShowDonate = false
        }, 15000)

    }
}
