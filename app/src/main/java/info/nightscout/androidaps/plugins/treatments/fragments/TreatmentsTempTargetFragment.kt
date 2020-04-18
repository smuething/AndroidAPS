package info.nightscout.androidaps.plugins.treatments.fragments

import android.annotation.SuppressLint
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.transactions.InvalidateTemporaryTargetTransaction
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.events.EventTempTargetChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsTempTargetFragment.RecyclerViewAdapter.TempTargetsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.treatments_temptarget_fragment.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsTempTargetFragment : DaggerFragment() {

    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private val disposable = CompositeDisposable()
    private val historyInMillis = T.days(10).msecs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.treatments_temptarget_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        temptarget_recyclerview.setHasFixedSize(true)
        temptarget_recyclerview.layoutManager = LinearLayoutManager(view.context)
    }

    @Synchronized override fun onResume() {
        super.onResume()

        val now = System.currentTimeMillis()
        disposable += repository
            .compatGetTemporaryTargetDataFromTime(now - historyInMillis, false)
            .observeOn(aapsSchedulers.main)
            .subscribe { list -> temptarget_recyclerview?.adapter = RecyclerViewAdapter(list) }

        disposable.add(rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({
                disposable += repository
                    .compatGetTemporaryTargetDataFromTime(now - historyInMillis, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> temptarget_recyclerview?.swapAdapter(RecyclerViewAdapter(list), true) }

            }) { fabricPrivacy::logException }
        )
    }

    @Synchronized override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    inner class RecyclerViewAdapter internal constructor(private var tempTargetList: List<TemporaryTarget>) : RecyclerView.Adapter<TempTargetsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TempTargetsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_temptarget_item, viewGroup, false)
            return TempTargetsViewHolder(v)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: TempTargetsViewHolder, position: Int) {
            val units = profileFunction.getUnits()
            val tempTarget = TempTarget(tempTargetList[position])
            holder.ns.visibility = (tempTarget.data.interfaceIDs.nightscoutId != null).toVisibility()
            if (!tempTarget.isEndingEvent) {
                holder.date.text = DateUtil.dateAndTimeString(tempTarget.data.timestamp) + " - " + DateUtil.timeString(tempTarget.originalEnd())
                holder.duration.text = resourceHelper.gs(R.string.format_mins, TimeUnit.MILLISECONDS.toMinutes(tempTarget.data.duration))
                holder.low.text = tempTarget.lowValueToUnitsToString(units)
                holder.high.text = tempTarget.highValueToUnitsToString(units)
                holder.reason.text = tempTarget.data.reason.text
            } else {
                holder.date.text = DateUtil.dateAndTimeString(tempTarget.data.timestamp)
                holder.duration.setText(R.string.cancel)
                holder.low.text = ""
                holder.high.text = ""
                holder.reason.text = ""
                holder.reasonLabel.text = ""
            }
            holder.date.setTextColor(when {
                tempTarget.isInProgress                    -> resourceHelper.gc(R.color.colorActive)
                tempTarget.data.timestamp > DateUtil.now() -> resourceHelper.gc(R.color.colorScheduled)
                else                                       -> holder.duration.currentTextColor
            })
            holder.remove.tag = tempTarget
        }

        override fun getItemCount(): Int = tempTargetList.size

        inner class TempTargetsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var date: TextView = itemView.findViewById(R.id.temptarget_date)
            var duration: TextView = itemView.findViewById(R.id.temptarget_duration)
            var low: TextView = itemView.findViewById(R.id.temptarget_low)
            var high: TextView = itemView.findViewById(R.id.temptarget_high)
            var reason: TextView = itemView.findViewById(R.id.temptarget_reason)
            var reasonLabel: TextView = itemView.findViewById(R.id.temptarget_reason_label)
            var remove: TextView = itemView.findViewById(R.id.temptarget_remove)
            var ns: ImageView = itemView.findViewById(R.id.ns_sign)

            init {
                remove.paintFlags = remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                remove.setOnClickListener { v: View ->
                    val tempTarget = v.tag as TempTarget
                    activity?.let { activity ->
                        val text = resourceHelper.gs(R.string.careportal_temporarytarget) + "\n" +
                            tempTarget.friendlyDescription(profileFunction.getUnits(), resourceHelper) + "\n" +
                            DateUtil.dateAndTimeString(tempTarget.data.timestamp)
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord), text, Runnable {
                            disposable.add(repository.runTransaction(InvalidateTemporaryTargetTransaction(tempTarget.data.id)).subscribe())
                        })
                    }
                }
            }
        }
    }
}