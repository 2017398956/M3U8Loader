package ru.yourok.m3u8loader.activitys.mainActivity

import android.app.Activity
import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import ru.yourok.converter.ConverterHelper
import ru.yourok.dwl.list.DownloadInfo
import ru.yourok.dwl.manager.Manager
import ru.yourok.m3u8loader.R
import ru.yourok.m3u8loader.activitys.editorActivity.EditorActivity
import ru.yourok.m3u8loader.player.PlayIntent
import kotlin.concurrent.thread

class LoaderListSelectionMenu(val activity: Activity, private val adapter: BaseAdapter) : AbsListView.MultiChoiceModeListener {

    private val selected: MutableSet<Int> = mutableSetOf()

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        mode?.menuInflater?.inflate(R.menu.loader_selector_menu, menu)
        selected.clear()
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        activity.findViewById<ListView>(R.id.lv_downloading).choiceMode = ListView.CHOICE_MODE_NONE
        activity.findViewById<ListView>(R.id.lv_downloading).adapter = LoaderListAdapter(activity)
//        mode?.finish()
//        when (item?.itemId) {
//            R.id.itemLoad -> {
//                selected.forEach {
//                    Manager.load(it)
//                    adapter.notifyDataSetChanged()
//                }
//            }
//            R.id.itemPause -> {
//                selected.forEach {
//                    Manager.stop(it)
//                    adapter.notifyDataSetChanged()
//                }
//            }
//            R.id.itemConvert -> {
//                if (!ConverterHelper.isSupport()) {
//                    Toast.makeText(activity, R.string.warn_install_convertor, Toast.LENGTH_SHORT).show()
//                    return false
//                }
//                val sendList = mutableListOf<DownloadInfo>()
//                selected.forEach {
//                    Manager.getLoader(it)?.let {
//                        sendList.add(it.downloadInfo)
//                    }
//                }
//                ConverterHelper.convert(sendList)
//                ConverterHelper.startConvert()
////                ConverterHelper.startActivity(activity)
//            }
//            R.id.itemEdit -> {
//                thread {
//                    EditorActivity.editorList.clear()
//                    selected.forEach {
//                        Manager.getLoader(it)?.let {
//                            it.stop()
//                            it.waitEnd()
//                            EditorActivity.editorList.add(it.downloadInfo)
//                        }
//                    }
//                    if (EditorActivity.editorList.size > 0)
//                        activity.startActivity(Intent(activity, EditorActivity::class.java))
//                }
//            }
//            R.id.itemPlay -> {
//                if (selected.size > 1) {
//                    Toast.makeText(activity, R.string.toast_multi_selected_play, LENGTH_LONG).show()
//                }
//                Manager.getLoader(selected.last())?.let { PlayIntent(activity).start(it) }
//            }
//            R.id.itemRemove -> {
//                Manager.removes(selected, activity)
//            }
//            else -> return false
//        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
    }

    override fun onItemCheckedStateChanged(mode: ActionMode?, position: Int, id: Long, checked: Boolean) {
        if (checked) {
            selected.add(position)
        } else {
            selected.remove(position)
        }
    }
}