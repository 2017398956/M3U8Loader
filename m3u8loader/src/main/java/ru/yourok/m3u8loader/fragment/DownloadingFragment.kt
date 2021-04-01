package ru.yourok.m3u8loader.fragment

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import com.mikepenz.materialdrawer.Drawer
import kotlinx.android.synthetic.main.fragment_downloading.*
import kotlinx.android.synthetic.main.loader_list_adaptor.view.*
import ru.yourok.converter.ConverterHelper
import ru.yourok.dwl.downloader.Downloader
import ru.yourok.dwl.downloader.LoadState
import ru.yourok.dwl.manager.Manager
import ru.yourok.dwl.settings.Preferences
import ru.yourok.dwl.utils.Utils
import ru.yourok.m3u8loader.R
import ru.yourok.m3u8loader.activitys.mainActivity.LoaderListSelectionMenu
import ru.yourok.m3u8loader.navigationBar.NavigationBar
import ru.yourok.m3u8loader.player.PlayIntent
import kotlin.concurrent.thread

class DownloadingFragment(contentLayoutId: Int) : Fragment(contentLayoutId) {

    private lateinit var drawer: Drawer
    private val canDownloadingList = mutableListOf<Downloader>()
    private var updateThread: Thread? = null

    // 是否一直刷新任务列表
    private var canRefresh: Boolean = true
    private var excOnce = true

    override fun onResume() {
        super.onResume()
        canRefresh = true
        if (excOnce) {
            initView()
            setListeners()
            drawer = NavigationBar.setup(context as Activity, lv_downloading.adapter as BaseAdapter)
            showMenuHelp()
            updateThread = thread {
                // 刷新任务列表
                while (canRefresh && Manager.isLoading()) {
                    activity?.runOnUiThread { update() }
                    // 每隔 500ms 刷新一次下载列表
                    Thread.sleep(500)
                }
                // 为了防止最后刷新后下载任务完成导致状态没有更新，这里再次刷新一下
                activity?.runOnUiThread { update() }
                // TODO 取消通知栏的下载进度
            }
            excOnce = false
        }
    }

    override fun onPause() {
        super.onPause()
        canRefresh = false
    }

    private fun initView() {
        lv_downloading.adapter = object : BaseAdapter() {

            init {
                Manager.canDownloadingList(canDownloadingList)
            }

            override fun getCount(): Int {
                return canDownloadingList.size
            }

            override fun getItem(i: Int): Any? {
                return canDownloadingList[i]
            }

            override fun getItemId(i: Int): Long {
                return i.toLong()
            }

            override fun getView(position: Int, convertView: View?, viewGroup: ViewGroup?): View {
                var holder: ViewHolder
                var view: View? = convertView
                if (null == view) {
                    view = LayoutInflater.from(context).inflate(R.layout.loader_list_adaptor, null)
                    holder = ViewHolder(view)
                } else {
                    holder = view.tag as ViewHolder
                }
                //
                holder.itemView.textViewNameItem?.text = canDownloadingList[position].downloadInfo.title
                val state = canDownloadingList[position].getState()
                when (state.state) {
                    LoadState.ST_PAUSE -> {
                        if (Manager.inQueue(position)) {
                            holder.itemView.imageViewLoader.setImageResource(R.drawable.ic_pause_circle_outline_black_24dp)
                        } else {
                            holder.itemView.imageViewLoader.setImageResource(R.drawable.ic_pause_black_24dp)
                        }
                    }
                    LoadState.ST_LOADING -> {
                        holder.itemView.imageViewLoader.setImageResource(R.drawable.ic_file_download_black_24dp)
                    }
                    LoadState.ST_COMPLETE -> {
                        holder.itemView.imageViewLoader.setImageResource(R.drawable.ic_check_black_24dp)
                    }
                    LoadState.ST_ERROR -> {
                        holder.itemView.imageViewLoader.setImageResource(R.drawable.ic_report_problem_black_24dp)
                    }
                }
                if (ConverterHelper.isConvert(canDownloadingList[position].downloadInfo)) {
                    holder.itemView.imageViewLoader.setImageResource(R.drawable.ic_convert_black)
                }

                if (state.isPlayed) {
                    holder.itemView.imageViewPlayed.visibility = View.VISIBLE
                } else {
                    holder.itemView.imageViewPlayed.visibility = View.GONE
                }

                val err = state.error
                if (err.isNotEmpty()) {
                    holder.itemView.textViewError.text = err
                } else {
                    holder.itemView.textViewError.text = ""
                }
                if (Preferences.get("SimpleProgress", false) as Boolean) {
                    holder.itemView.li_progress.visibility = View.GONE
                    holder.itemView.simpleProgress.visibility = View.VISIBLE

                    var frags = "%d/%d".format(state.loadedFragments, state.fragments)
                    if (state.threads > 0)
                        frags = "%-3d: %s".format(state.threads, frags)
                    var speed = ""
                    var size = ""
                    if (state.speed > 0) {
                        speed = "  %s/sec ".format(Utils.byteFmt(state.speed))
                    }
                    if (state.size > 0 && state.isComplete) {
                        size = "  %s".format(Utils.byteFmt(state.size))
                    } else {
                        size = "  %s/%s".format(Utils.byteFmt(state.loadedBytes), Utils.byteFmt(state.size))
                    }
                    holder.itemView.li_s_progress.progress = state.loadedFragments * 100 / state.fragments
                    holder.itemView.textViewFragmentsStat.text = frags
                    holder.itemView.textViewSpeedStat.text = speed
                    holder.itemView.textViewSizeStat.text = size
                } else {
                    holder.itemView.li_progress.visibility = View.VISIBLE
                    holder.itemView.simpleProgress.visibility = View.GONE
                    holder.itemView.li_progress.setIndexList(position)
                }
                return view!!
            }
        }
    }

    private fun setListeners() {
        lv_downloading.setMultiChoiceModeListener(
                LoaderListSelectionMenu(context as Activity, lv_downloading.adapter as BaseAdapter))
        lv_downloading.setOnItemClickListener { _, _, i: Int, _ ->
            val loader = Manager.getLoader(i)
            if (loader?.getState()?.state == LoadState.ST_COMPLETE) {
                // 如果这个任务已经完成，点击的时候就播放
                context?.let { PlayIntent(it).start(loader) }
            } else {
                if (Manager.inQueue(i)) {
                    Manager.stop(i)
                } else {
                    Manager.load(i)
                }
            }
            update()
        }
        lv_downloading.setOnItemLongClickListener { _, _, i, _ ->
            try {
                lv_downloading.choiceMode = ListView.CHOICE_MODE_MULTIPLE_MODAL
                lv_downloading.setItemChecked(i, true)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private fun update() {
        Manager.canDownloadingList(canDownloadingList)
        (lv_downloading.adapter as BaseAdapter)?.notifyDataSetChanged()
    }

    /**
     * 当下载列表中没有任务时，1s 后自动打开左侧菜单
     */
    private fun showMenuHelp() {
//        if (Manager.getLoadersSize() == 0)
//            Timer().schedule(1000) {
//                if (Manager.getLoadersSize() == 0)
//                    (context as Activity).runOnUiThread { drawer.openDrawer() }
//            }
//        else drawer.closeDrawer()
    }

    private class ViewHolder(view: View) {
        val itemView = view

        init {
            view.tag = this
        }
    }

    /**
     * @return 是否执行了关闭操作
     */
    fun closeDrawer(): Boolean {
        if (drawer.isDrawerOpen) {
            drawer.closeDrawer()
            return true
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (null != updateThread){
            canRefresh = false
            updateThread = null
        }
    }

}