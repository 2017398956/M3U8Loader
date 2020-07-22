package ru.yourok.m3u8loader.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_downloaded.*
import kotlinx.android.synthetic.main.loader_list_adaptor.view.*
import ru.yourok.converter.ConverterHelper
import ru.yourok.dwl.downloader.Downloader
import ru.yourok.dwl.downloader.LoadState
import ru.yourok.dwl.manager.Manager
import ru.yourok.dwl.settings.Preferences
import ru.yourok.dwl.utils.Utils
import ru.yourok.m3u8loader.R
import ru.yourok.m3u8loader.player.PlayIntent

class DownloadedFragment(contentLayoutId: Int) : Fragment(contentLayoutId) {

    private var excOnce = true
    private val downloadedList = mutableListOf<Downloader>()
    override fun onResume() {
        super.onResume()
        if (excOnce) {
            initView()
            setListeners()
            excOnce = false
        }
        downloadedList.clear()
        Manager.downloadedList(downloadedList)
        (lv_downloaded.adapter as BaseAdapter).notifyDataSetChanged()
    }

    private fun initView() {
        lv_downloaded.adapter = object : BaseAdapter() {

            override fun getCount(): Int {
                return downloadedList.size
            }

            override fun getItem(i: Int): Any? {
                return downloadedList[i]
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
                holder.itemView.textViewNameItem?.text = downloadedList[position].downloadInfo.title
                val state = downloadedList[position].getState()
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
                if (ConverterHelper.isConvert(downloadedList[position].downloadInfo)) {
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
        lv_downloaded.setOnItemClickListener { _, _, i: Int, _ ->
            context?.let { PlayIntent(it).start(downloadedList[i]) }
        }
    }

    private class ViewHolder(view: View) {
        val itemView = view

        init {
            view.tag = this
        }
    }
}