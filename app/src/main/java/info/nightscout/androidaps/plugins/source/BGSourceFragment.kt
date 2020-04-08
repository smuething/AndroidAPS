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
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.bgsource_fragment.*
import javax.inject.Inject

class BGSourceFragment : DaggerFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var repository: AppRepository

    private val disposable = CompositeDisposable()
    private val MILLS_TO_THE_PAST = T.hours(12).msecs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bgsource_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bgsource_recyclerview.setHasFixedSize(true)
        bgsource_recyclerview.layoutManager = LinearLayoutManager(view.context)
        val now = System.currentTimeMillis()
        disposable += repository
            .compatGetBgreadingsDataFromTime(now - MILLS_TO_THE_PAST, false)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { list -> bgsource_recyclerview.adapter = RecyclerViewAdapter(list) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(Schedulers.io())
            .subscribe({
                val now = System.currentTimeMillis()
                disposable += repository
                    .compatGetBgreadingsDataFromTime(now - MILLS_TO_THE_PAST, false)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { list -> bgsource_recyclerview?.swapAdapter(RecyclerViewAdapter(list), true) }
            }) { fabricPrivacy.logException(it) }
        )
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
            holder.ns.visibility = NSUpload.isIdValid(glucoseValue.interfaceIDs.nightscoutId).toVisibility()
            holder.invalid.visibility = (!glucoseValue.isValid).toVisibility()
            holder.date.text = DateUtil.dateAndTimeString(glucoseValue.timestamp)
            holder.value.text = glucoseValue.valueToUnitsString(profileFunction.getUnits())
            holder.direction.text = glucoseValue.trendArrow.toSymbol()
            holder.remove.tag = glucoseValue
        }

        override fun getItemCount(): Int {
            return glucoseValues.size
        }

        inner class GlucoseValuesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var date: TextView = itemView.findViewById(R.id.bgsource_date)
            var value: TextView = itemView.findViewById(R.id.bgsource_value)
            var direction: TextView = itemView.findViewById(R.id.bgsource_direction)
            var invalid: TextView = itemView.findViewById(R.id.invalid_sign)
            var ns: TextView = itemView.findViewById(R.id.ns_sign)
            var remove: TextView = itemView.findViewById(R.id.bgsource_remove)

            init {
                remove.setOnClickListener { v: View ->
                    val glucoseValue = v.tag as GlucoseValue
                    activity?.let { activity ->
                        val text = DateUtil.dateAndTimeString(glucoseValue.timestamp) + "\n" + glucoseValue.valueToUnitsString(profileFunction.getUnits())
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord), text, Runnable {
                            disposable += repository.runTransaction(InvalidateGlucoseValueTransaction(glucoseValue.id)).subscribe()
                        })
                    }
                }
                remove.paintFlags = remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }
}