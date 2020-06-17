package info.nightscout.androidaps.insight.dagger

import dagger.BindsInstance
import dagger.Subcomponent
import info.nightscout.androidaps.insight.InsightPump
import info.nightscout.androidaps.insight.data.PairingDataParameter
import kotlin.coroutines.CoroutineContext

@Subcomponent(modules = [InsightPumpModule::class])
@PerPump
internal interface InsightPumpComponent {

    fun insightPump(): InsightPump

    @Subcomponent.Factory
    interface Factory {

        operator fun invoke(@BindsInstance coroutineContext: CoroutineContext, @BindsInstance @MacAddress macAddress: String,
                            @BindsInstance pairingDataParameter: PairingDataParameter? = null): InsightPumpComponent
    }
}