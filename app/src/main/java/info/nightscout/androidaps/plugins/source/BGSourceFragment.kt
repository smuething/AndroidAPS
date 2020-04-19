package info.nightscout.androidaps.plugins.source

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.transactions.InvalidateGlucoseValueTransaction
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.general.nsclient2.data.toNs
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.valueToUnitsString
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.bgsource_fragment.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BGSourceFragment : DaggerFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private val disposable = CompositeDisposable()
    private val historyInMillis = T.hours(12).msecs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bgsource_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bgsource_recyclerview.setHasFixedSize(true)
        bgsource_recyclerview.layoutManager = LinearLayoutManager(view.context)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()

        val now = System.currentTimeMillis()
        disposable += repository
            .compatGetBgReadingsDataFromTime(now - historyInMillis, false)
            .observeOn(aapsSchedulers.main)
            .subscribe { list -> bgsource_recyclerview?.adapter = RecyclerViewAdapter(list) }

        disposable += rxBus
            .toObservable(EventNewBG::class.java)
            .observeOn(aapsSchedulers.io)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({
                disposable += repository
                    .compatGetBgReadingsDataFromTime(now - historyInMillis, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> bgsource_recyclerview?.swapAdapter(RecyclerViewAdapter(list), true) }
            }) { fabricPrivacy.logException(it) }
    }

    @Synchronized
    override fun onPause() {
        disposable.clear()
        super.onPause()
    }

    inner class RecyclerViewAdapter internal constructor(private var glucoseValues: List<GlucoseValue>) : RecyclerView.Adapter<RecyclerViewAdapter.GlucoseValuesViewHolder>() {
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): GlucoseValuesViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.bgsource_item, viewGroup, false)
            return GlucoseValuesViewHolder(v)
        }

        override fun onBindViewHolder(holder: GlucoseValuesViewHolder, position: Int) {
            val glucoseValue = glucoseValues[position]
            holder.ns.visibility = (glucoseValue.interfaceIDs.nightscoutId != null).toVisibility()
            holder.invalid.visibility = (!glucoseValue.isValid).toVisibility()
            holder.date.text = DateUtil.dateAndTimeString(glucoseValue.timestamp)
            holder.value.text = glucoseValue.valueToUnitsString(profileFunction.getUnits())
            holder.direction.text = glucoseValue.trendArrow.toNs().symbol
            holder.remove.tag = glucoseValue
        }

        override fun getItemCount(): Int = glucoseValues.size

        inner class GlucoseValuesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var date: TextView = itemView.findViewById(R.id.bgsource_date)
            var value: TextView = itemView.findViewById(R.id.bgsource_value)
            var direction: TextView = itemView.findViewById(R.id.bgsource_direction)
            var invalid: TextView = itemView.findViewById(R.id.invalid_sign)
            var ns: TextView = itemView.findViewById(R.id.ns_sign)
            var remove: TextView = itemView.findViewById(R.id.bgsource_remove)

            init {
                remove.paintFlags = remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                remove.setOnClickListener { v: View ->
                    val glucoseValue = v.tag as GlucoseValue
                    activity?.let { activity ->
                        val text = DateUtil.dateAndTimeString(glucoseValue.timestamp) + "\n" + glucoseValue.valueToUnitsString(profileFunction.getUnits())
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord), text, Runnable {
                            disposable += repository.runTransaction(InvalidateGlucoseValueTransaction(glucoseValue.id)).subscribe()
                        })
                    }
                }
            }
        }
    }
}