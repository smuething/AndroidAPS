package info.nightscout.androidaps.plugins.general.nsclient2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import kotlinx.android.synthetic.main.nsclient2_fragment.*
import javax.inject.Inject

/**
 * Created by adrian on 2019-12-25.
 */

class NSClient2Fragment : DaggerFragment() {

    @Inject lateinit var viewModel: NSClient2Plugin

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.nsclient2_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        nsclient2_test_Button.setOnClickListener { viewModel.testConnection() }
        nsclient2_status_Button.setOnClickListener { viewModel.exampleStatusCall() }
        nsclient2_lastmodified_Button.setOnClickListener { viewModel.lastModifiedCall() }

        viewModel.testResultLiveData.observe(viewLifecycleOwner, Observer {
            nsclient2_test_textoutput.text = it
        })

    }
}