package info.nightscout.androidaps.plugins.general.nsclient2

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.lifecycle.Observer
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.sharedPreferences.SP
import kotlinx.android.synthetic.main.nsclient2_fragment.*
import javax.inject.Inject

class NSClient2Fragment : DaggerFragment() {

    @Inject lateinit var sp: SP
    @Inject lateinit var nsClient2Plugin: NSClient2Plugin

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.nsclient2_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        nsclient_url.text = sp.getString(R.string.key_nsclient2_baseurl, "")
        nsclient_autoscroll.isChecked = sp.getBoolean(R.string.key_nsclientinternal_autoscroll, true)

        nsclient_clearlog.paintFlags = nsclient_clearlog.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        nsclient_clearlog.setOnClickListener { nsClient2Plugin.clearLog() }

        nsclient_delivernow.paintFlags = nsclient_delivernow.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        nsclient_delivernow.setOnClickListener { nsClient2Plugin.sync("GUI") }

        nsclient_fullsync.paintFlags = nsclient_fullsync.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        nsclient_fullsync.setOnClickListener { nsClient2Plugin.fullSync("GUI") }

        nsclient_paused.setOnCheckedChangeListener { _, isChecked ->
            nsClient2Plugin.pause(isChecked)
        }

        nsclient_autoscroll.setOnCheckedChangeListener { _, isChecked ->
            sp.putBoolean(R.string.key_nsclientinternal_autoscroll, isChecked)
        }

        nsclient2_test_Button.setOnClickListener { nsClient2Plugin.testConnection() }
        nsclient2_status_Button.setOnClickListener { nsClient2Plugin.exampleStatusCall() }
        nsclient2_lastmodified_Button.setOnClickListener { nsClient2Plugin.lastModifiedCall() }
        nsclient2_postglucosevalue_Button.setOnClickListener { nsClient2Plugin.postGlucoseValueCall() }
        nsclient2_getentries_Button.setOnClickListener { nsClient2Plugin.getEntriesCall() }

        nsClient2Plugin.logLiveData.observe(viewLifecycleOwner, Observer {
            nsclient_log.text = it
            if (nsclient_autoscroll.isChecked) nsclient_logscrollview.fullScroll(ScrollView.FOCUS_DOWN)
        })

        nsClient2Plugin.statusLiveData.observe(viewLifecycleOwner, Observer { nsclient_status.text = it })

    }
}