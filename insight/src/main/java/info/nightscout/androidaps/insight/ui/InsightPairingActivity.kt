package info.nightscout.androidaps.insight.ui

import android.animation.LayoutTransition
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.core.di.ViewModel
import info.nightscout.androidaps.insight.R
import info.nightscout.androidaps.insight.databinding.ActivityInsightPairingBinding
import info.nightscout.androidaps.utils.getColorFromAttr
import javax.inject.Inject
import kotlin.math.roundToInt

class InsightPairingActivity : DaggerAppCompatActivity() {

    @Inject
    @ViewModel
    internal lateinit var viewModel: InsightPairingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityInsightPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        (binding.root as ViewGroup).layoutTransition.enableTransitionType(LayoutTransition.CHANGING)

        val adapter = DeviceAdapter() {
            viewModel.pair(it)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        viewModel.requestBluetoothEnable.observe(this) {
            if (it) {
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 0)
                viewModel.requestBluetoothEnable.value = false
            }
        }

        viewModel.code.observe(this) {
            binding.code.text = it
        }

        viewModel.bluetoothDevices.observe(this) {
            adapter.bluetoothDevices = it
            if (it.isNotEmpty() && viewModel.screen.value == InsightPairingViewModel.Screen.DEVICE_SEARCH) {
                binding.showRecycler()
            } else {
                binding.hideRecycler()
            }
        }

        viewModel.screen.observe(this) {
            when (it!!) {
                InsightPairingViewModel.Screen.DEVICE_SEARCH     -> {
                    binding.pumpIcon.visibility = View.VISIBLE
                    binding.pumpIcon.imageTintList = ColorStateList.valueOf(getColorFromAttr(R.attr.colorOnBackground))
                    binding.code.visibility = View.GONE
                    binding.text.setText(R.string.searching_for_devices)
                    binding.progressIndicator.isIndeterminate = true
                    binding.progressIndicator.show()
                    binding.codeButtons.visibility = View.GONE
                    binding.close.visibility = View.GONE
                    binding.retry.visibility = View.GONE
                    if (viewModel.bluetoothDevices.value!!.isNotEmpty()) {
                        binding.showRecycler()
                    } else {
                        binding.hideRecycler()
                    }
                }

                InsightPairingViewModel.Screen.PLEASE_WAIT       -> {
                    binding.pumpIcon.visibility = View.VISIBLE
                    binding.pumpIcon.imageTintList = ColorStateList.valueOf(getColorFromAttr(R.attr.colorOnBackground))
                    binding.code.visibility = View.GONE
                    binding.text.setText(R.string.please_wait)
                    binding.progressIndicator.show()
                    binding.codeButtons.visibility = View.GONE
                    binding.close.visibility = View.GONE
                    binding.retry.visibility = View.GONE
                    binding.hideRecycler()
                }

                InsightPairingViewModel.Screen.VERIFICATION_CODE -> {
                    binding.pumpIcon.visibility = View.GONE
                    binding.code.visibility = View.VISIBLE
                    binding.text.setText(R.string.code_compare)
                    binding.progressIndicator.hide()
                    binding.codeButtons.visibility = View.VISIBLE
                    binding.close.visibility = View.GONE
                    binding.retry.visibility = View.GONE
                    binding.hideRecycler()
                }

                InsightPairingViewModel.Screen.ERROR             -> {
                    binding.pumpIcon.visibility = View.VISIBLE
                    binding.pumpIcon.imageTintList = ColorStateList.valueOf(getColorFromAttr(R.attr.colorError))
                    binding.code.visibility = View.GONE
                    binding.text.setText(R.string.hmm_that_didnt_go_to_plan)
                    binding.progressIndicator.hide()
                    binding.codeButtons.visibility = View.GONE
                    binding.close.visibility = View.GONE
                    binding.retry.visibility = View.VISIBLE
                    binding.hideRecycler()
                }

                InsightPairingViewModel.Screen.SUCCESS           -> {
                    binding.pumpIcon.visibility = View.VISIBLE
                    binding.pumpIcon.imageTintList = ColorStateList.valueOf(getColorFromAttr(R.attr.colorPrimary))
                    binding.code.visibility = View.GONE
                    binding.text.setText(R.string.pump_successfully_paired)
                    binding.progressIndicator.hide()
                    binding.codeButtons.visibility = View.GONE
                    binding.close.visibility = View.VISIBLE
                    binding.retry.visibility = View.GONE
                    binding.hideRecycler()
                }
            }
        }

        binding.retry.setOnClickListener {
            viewModel.startDiscovery()
        }

        binding.close.setOnClickListener {
            finish()
        }

        binding.yes.setOnClickListener {
            viewModel.confirmCode()
        }

        binding.no.setOnClickListener {
            viewModel.rejectCode()
        }
    }

    private fun ActivityInsightPairingBinding.showRecycler() {
        recyclerView.visibility = View.VISIBLE
        ConstraintSet().apply {
            clone(constraintLayout)
            connect(R.id.handles_container, ConstraintSet.TOP, R.id.toolbar, ConstraintSet.BOTTOM, (resources.displayMetrics.density * 16.0).roundToInt())
            clear(R.id.handles_container, ConstraintSet.BOTTOM)
            applyTo(constraintLayout)
        }
    }

    private fun ActivityInsightPairingBinding.hideRecycler() {
        recyclerView.visibility = View.GONE
        ConstraintSet().apply {
            clone(constraintLayout)
            connect(R.id.handles_container, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(R.id.handles_container, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            applyTo(constraintLayout)
        }
    }
}