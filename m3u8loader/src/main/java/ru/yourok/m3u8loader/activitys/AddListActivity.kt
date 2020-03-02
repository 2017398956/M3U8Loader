package ru.yourok.m3u8loader.activitys

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_add_list.*
import ru.yourok.converter.ConverterHelper
import ru.yourok.dwl.list.DownloadInfo
import ru.yourok.dwl.manager.Manager
import ru.yourok.dwl.manager.NotificationUtil
import ru.yourok.dwl.parser.Parser
import ru.yourok.dwl.settings.Settings
import ru.yourok.dwl.storage.Storage
import ru.yourok.dwl.utils.Utils
import ru.yourok.dwl.utils.Utils.cleanFileName
import ru.yourok.m3u8loader.R
import ru.yourok.m3u8loader.activitys.preferenceActivity.DirectoryActivity
import ru.yourok.m3u8loader.theme.Theme
import kotlin.concurrent.thread


class AddListActivity : AppCompatActivity() {

    private var downloadInfo: MutableList<DownloadInfo>? = null
    // 下载文件保存目录
    private var downloadPath = Settings.downloadPath
    private var showNotify = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.set(this)
        setContentView(R.layout.activity_add_list)
        textViewError.text = ""
        var download = false
        try {
            if (intent.extras != null) {
                val bundle = intent.extras
                for (key in bundle!!.keySet()) {
                    if (key.toLowerCase().contains("name") || key.toLowerCase().contains("title")) {
                        val value = bundle.get(key)
                        if (value != null) {
                            val name = cleanFileName(value.toString().trim { it <= ' ' })
                            if (!name.isEmpty()) editTextFileName.setText(name)
                        }
                    }
                    if (key.toLowerCase().contains("subs") || key.toLowerCase().contains("subtitles")) {
                        val value = bundle.get(key)
                        if (value != null) editTextSubtitles.setText(value.toString().trim { it <= ' ' })
                    }
                    if (key.toLowerCase().contains("download")) {
                        download = true
                    }
                }
            }

            if (intent.action != null && intent.action.equals(Intent.ACTION_SEND)) {
                if (intent.getStringExtra(Intent.EXTRA_TEXT) != null) {
                    editTextUrl.setText(intent.getStringExtra(Intent.EXTRA_TEXT))
                    showNotify = true
                }
                if (intent.extras!!.get(Intent.EXTRA_STREAM) != null) {
                    editTextUrl.setText(intent.extras!!.get(Intent.EXTRA_STREAM).toString())
                    showNotify = true
                }
            }

            if (intent.data != null && !intent.data.toString().isEmpty()) {
                val path = intent.data.toString()
                editTextUrl.setText(path)
                showNotify = true
            }

            if (editTextFileName.text.toString().isEmpty()) {
                var count = 0
                var found = true
                while (found) {
                    found = false
                    for (i in 0 until Manager.getLoadersSize()) {
                        val info = Manager.getLoaderStat(i) ?: continue
                        val name = info.name
                        if (name == "video$count") {
                            count++
                            found = true
                            break
                        }
                    }
                }
                editTextFileName.setText("video$count")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: " + e.localizedMessage, Toast.LENGTH_SHORT).show()
            finish()
        }

        checkboxConvertAdd.visibility = if (ConverterHelper.isSupport()) View.VISIBLE else View.GONE

        checkboxConvertAdd.setOnCheckedChangeListener { _, b ->
            if (b) {
                if (!ConverterHelper.isSupport()) {
                    checkboxConvertAdd.isChecked = false
                    Toast.makeText(this, R.string.warn_install_convertor, Toast.LENGTH_SHORT).show()
                }
            }
        }
        checkboxConvertAdd.isChecked = Settings.convertVideo && ConverterHelper.isSupport()

        updateDownloadPath()


        if (download) {
            downloadBtnClick(buttonDownload)
        }
        setListeners()
    }

    private fun setListeners() {
        editTextUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val strList = s.toString()?.split("/")
                if (s.toString()?.endsWith(".m3u8", true)) {
                    if (strList.size > 1) {
                        editTextFileName.setText(strList[strList.size - 2])
                    }
                } else if (s.toString()?.endsWith(".mp4", true)) {
                    editTextFileName.setText(strList[strList.size - 1])
                }
            }
        })

        buttonSetDownloadPath.setOnClickListener {
            val intent = Intent(this, DirectoryActivity::class.java)
            intent.data = Uri.parse(downloadPath)
            startActivityForResult(intent, 1202)
        }
    }

    private fun updateDownloadPath() {
        textViewDirectoryPathAdd.text = downloadPath
        val totalSpace = Storage.getSpace(Storage.getDocument(downloadPath), true)
        val freeSpace = Storage.getSpace(Storage.getDocument(downloadPath), false)
        textViewDiskSize.text = "%s / %s".format(Utils.byteFmt(totalSpace - freeSpace), Utils.byteFmt(totalSpace))
        progressBarFreeSpace.progress = 100 - (freeSpace * 100 / (totalSpace + 1)).toInt()
    }

    fun addBtnClick(view: View) {
        addList(false)
    }

    fun downloadBtnClick(view: View) {
        addList(true)
    }

    fun cancelBtnClick(view: View) {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun addList(download: Boolean) {
        val url = editTextUrl.text.toString().trim()
        val name = cleanFileName(editTextFileName.text.toString().trim())
        val subsUrl = editTextSubtitles.text.toString().trim()

        if (url.isEmpty()) {
            toastErr(R.string.error_empty_url)
            return
        }
        if (name.isEmpty()) {
            toastErr(R.string.error_empty_name)
            return
        }

        waitView(true)

        thread {
            try {
                val lists = Parser(name, url, downloadPath).parse()
                lists.forEach {
                    it.subsUrl = subsUrl
                    it.isConvert = (checkboxConvertAdd?.isChecked
                            ?: Settings.convertVideo) && ConverterHelper.isSupport()
                }
                Manager.addList(lists)
                if (download) {
                    val start = Manager.getLoadersSize() - lists.size
                    for (i in start until Manager.getLoadersSize()) Manager.load(i)
                } else {
                    val names = lists.joinToString { it.title }
                    NotificationUtil.sendNotification(this, NotificationUtil.TYPE_NOTIFICATION_LOAD, getString(R.string.added), names)
                }
                this.setResult(RESULT_OK)
                this.finish()
            } catch (e: java.text.ParseException) {
                if (e.errorOffset == -1) {
                    runOnUiThread {
                        AlertDialog.Builder(this).setTitle(R.string.error_wrong_format).setMessage(R.string.warn_wrong_format).setPositiveButton(android.R.string.yes) { p0, p1 ->
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(Uri.parse(url), "video/*")
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            intent.putExtra("title", name)
                            val chooser = Intent.createChooser(intent, "")
                            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(chooser)
                            finish()
                        }.setNegativeButton(android.R.string.no) { p0, p1 ->
                            if (e.message != null) toastErr(e.message!!)
                        }.setNeutralButton("", null).show()
                    }
                } else if (e.message != null) toastErr(e.message!!)
            } catch (e: Exception) {
                if (e.message != null) toastErr(e.message!!)
            }
            waitView(false)
        }
    }

    private fun toastErr(msg: String) {
        if (msg.isNotEmpty()) runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            textViewError.text = msg
        }
    }

    private fun toastErr(msg: Int) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            textViewError.setText(msg)
        }
    }

    /**
     * 是否展示等待视图
     */
    private fun waitView(set: Boolean) {
        runOnUiThread {
            if (set) {
                findViewById<View>(R.id.progressBar).visibility = View.VISIBLE
                textViewError.text = ""
            } else findViewById<View>(R.id.progressBar).visibility = View.GONE
            findViewById<View>(R.id.buttonAdd).isEnabled = !set
            findViewById<View>(R.id.buttonDownload).isEnabled = !set
            findViewById<View>(R.id.buttonCancel).isEnabled = !set
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1202 && data != null) {
            downloadPath = data.getStringExtra("filename")
            updateDownloadPath()
        }
    }
}