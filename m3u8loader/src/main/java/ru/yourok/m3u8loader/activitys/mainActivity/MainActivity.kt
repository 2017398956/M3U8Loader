package ru.yourok.m3u8loader.activitys.mainActivity

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.materialdrawer.Drawer
import dalvik.system.DexClassLoader
import kotlinx.android.synthetic.main.activity_main.*
import ru.yourok.dwl.downloader.LoadState
import ru.yourok.dwl.manager.Manager
import ru.yourok.dwl.settings.Preferences
import ru.yourok.dwl.updater.Updater
import ru.yourok.m3u8loader.BuildConfig
import ru.yourok.m3u8loader.R
import ru.yourok.m3u8loader.activitys.DonateActivity
import ru.yourok.m3u8loader.activitys.preferenceActivity.PreferenceActivity
import ru.yourok.m3u8loader.navigationBar.NavigationBar
import ru.yourok.m3u8loader.player.PlayIntent
import ru.yourok.m3u8loader.theme.Theme
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    // 是否一直刷新任务列表
    private var canRefresh: Boolean = true
    private lateinit var drawer: Drawer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.set(this)

        setContentView(R.layout.activity_main)
        requestPermissionWithRationale()
        listViewLoader.adapter = LoaderListAdapter(this)
        drawer = NavigationBar.setup(this, listViewLoader.adapter as LoaderListAdapter)
        setListeners()
        showMenuHelp()
        // 版本升级提示，这里先关闭
        Timer().schedule(1000) {
            if (false && Updater.hasNewUpdate()) {
                Updater.showSnackbar(this@MainActivity)
            }
        }
    }

    private fun setListeners() {
        listViewLoader.setMultiChoiceModeListener(LoaderListSelectionMenu(this, listViewLoader.adapter as LoaderListAdapter))
        listViewLoader.setOnItemClickListener { _, _, i: Int, _ ->
            val loader = Manager.getLoader(i)
            if (loader?.getState()?.state == LoadState.ST_COMPLETE) {
                // 如果这个任务已经完成，点击的时候就播放
                PlayIntent(this).start(loader)
            } else {
                if (Manager.inQueue(i)) {
                    Manager.stop(i)
                } else {
                    Manager.load(i)
                }
            }
            update()
        }
        listViewLoader.setOnItemLongClickListener { _, _, i, _ ->
            try {
                listViewLoader.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
                listViewLoader.setItemChecked(i, true)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 刷新任务列表
     */
    fun update() {
        (listViewLoader.adapter as LoaderListAdapter).notifyDataSetChanged()
    }

    /**
     * 当下载列表中没有任务时，1s 后自动打开左侧菜单
     */
    private fun showMenuHelp() {
        if (Manager.getLoadersSize() == 0)
            Timer().schedule(1000) {
                if (Manager.getLoadersSize() == 0)
                    runOnUiThread { drawer.openDrawer() }
            }
        else drawer.closeDrawer()
    }

    override fun onResume() {
        super.onResume()
        canRefresh = true
        // 当任务列表界面重新展示在前台时要刷新一下，以更新其它位置的操作
        update()
        showDonate()
        thread {
            // 刷新任务列表
            while (canRefresh && Manager.isLoading()) {
                runOnUiThread { update() }
                // 每隔 500ms 刷新一次下载列表
                Thread.sleep(500)
            }
            // 为了防止最后刷新后下载任务完成导致状态没有更新，这里再次刷新一下
            runOnUiThread { update() }
            // TODO 取消通知栏的下载进度
        }
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
        if (drawer.isDrawerOpen) {
            drawer.closeDrawer()
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
