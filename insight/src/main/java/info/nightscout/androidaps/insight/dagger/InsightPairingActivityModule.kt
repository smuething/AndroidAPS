package info.nightscout.androidaps.insight.dagger

import dagger.Module
import info.nightscout.androidaps.core.di.ViewModelModule
import info.nightscout.androidaps.insight.ui.InsightPairingActivity
import info.nightscout.androidaps.insight.ui.InsightPairingViewModel

@Module
internal class InsightPairingActivityModule : ViewModelModule<InsightPairingActivity, InsightPairingViewModel>(InsightPairingViewModel::class.java)