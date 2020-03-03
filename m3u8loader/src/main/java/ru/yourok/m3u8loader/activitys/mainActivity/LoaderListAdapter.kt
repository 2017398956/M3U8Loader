package ru.yourok.m3u8loader.activitys.mainActivity

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import ru.yourok.converter.ConverterHelper
import ru.yourok.dwl.downloader.LoadState
import ru.yourok.dwl.manager.Manager
import ru.yourok.dwl.settings.Preferences
import ru.yourok.dwl.utils.Utils
import ru.yourok.m3u8loader.R

class LoaderListAdapter(val context: Context) : BaseAdapter() {
    override fun getCount(): Int {
        return Manager.getLoadersSize()
    }

    override fun getItem(i: Int): Any? {
        return Manager.getLoader(i)
    }

    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    override fun getView(index: Int, convertView: View?, viewGroup: ViewGroup?): View {
        var viewHolder: ViewHolder
        var vi: View? = convertView
        if (null == vi) {
            vi = LayoutInflater.from(context).inflate(R.layout.loader_list_adaptor, null)
            viewHolder = ViewHolder(vi)
            vi.tag = viewHolder
        } else {
            viewHolder = vi.tag as ViewHolder
        }
        Manager.getLoader(index)?.let {
            viewHolder.textViewNameItem?.text = it.downloadInfo.title
            val state = it.getState()
            when (state.state) {
                LoadState.ST_PAUSE -> {
                    if (Manager.inQueue(index)) {
                        viewHolder.imageStatus?.setImageResource(R.drawable.ic_pause_circle_outline_black_24dp)
                    } else {
                        viewHolder.imageStatus?.setImageResource(R.drawable.ic_pause_black_24dp)
                    }
                }
                LoadState.ST_LOADING -> {
                    viewHolder.imageStatus?.setImageResource(R.drawable.ic_file_download_black_24dp)
                }
                LoadState.ST_COMPLETE -> {
                    viewHolder.imageStatus?.setImageResource(R.drawable.ic_check_black_24dp)
                }
                LoadState.ST_ERROR -> {
                    viewHolder.imageStatus?.setImageResource(R.drawable.ic_report_problem_black_24dp)
                }
            }
            if (ConverterHelper.isConvert(it.downloadInfo)) {
                viewHolder.imageStatus?.setImageResource(R.drawable.ic_convert_black)
            }

            if (state.isPlayed) {
                viewHolder.imageViewPlayed?.visibility = View.VISIBLE
            } else {
                viewHolder.imageViewPlayed?.visibility = View.GONE
            }

            val err = state.error
            if (!err.isEmpty()) {
                viewHolder.textViewError?.text = err
            } else {
                viewHolder.textViewError?.text = ""
            }
            if (Preferences.get("SimpleProgress", false) as Boolean) {
                viewHolder.progressF?.visibility = View.GONE
                viewHolder.progressS?.visibility = View.VISIBLE

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
                viewHolder.progressFS?.progress = state.loadedFragments * 100 / state.fragments
                viewHolder.textViewFragmentsStat?.text = frags
                viewHolder.textViewSpeedStat?.text = speed
                viewHolder.textViewSizeStat?.text = size
            } else {
                viewHolder.progressF?.visibility = View.VISIBLE
                viewHolder.progressS?.visibility = View.GONE
                viewHolder.progressF?.setIndexList(index)
            }
        }
        return vi!!
    }

    private class ViewHolder(view: View?) {
        val textViewNameItem = view?.findViewById<TextView>(R.id.textViewNameItem)
        val imageStatus = view?.findViewById<ImageView>(R.id.imageViewLoader)
        val imageViewPlayed = view?.findViewById<View>(R.id.imageViewPlayed)
        val textViewError = view?.findViewById<TextView>(R.id.textViewError)
        val progressF = view?.findViewById<ProgressView>(R.id.li_progress)
        val progressS = view?.findViewById<View>(R.id.simpleProgress)
        val progressFS = view?.findViewById<ProgressBar>(R.id.li_s_progress)
        val textViewFragmentsStat = view?.findViewById<TextView>(R.id.textViewFragmentsStat)
        val textViewSpeedStat = view?.findViewById<TextView>(R.id.textViewSpeedStat)
        val textViewSizeStat = view?.findViewById<TextView>(R.id.textViewSizeStat)
    }
}