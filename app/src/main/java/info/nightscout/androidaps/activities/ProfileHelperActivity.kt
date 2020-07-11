package info.nightscout.androidaps.activities

import android.os.Bundle
import android.widget.PopupMenu
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.defaultProfile.DefaultProfile
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.stats.TddCalculator
import kotlinx.android.synthetic.main.activity_profilehelper.*
import java.text.DecimalFormat
import javax.inject.Inject

class ProfileHelperActivity : NoSplashAppCompatActivity() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var defaultProfile: DefaultProfile
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var dateUtil: DateUtil

    enum class ProfileType {
        DEFAULT,
        CURRENT,
        AVAILABLE_PROFILE,
        PROFILE_SWITCH
    }

    var tabSelected = 0
    val typeSelected = arrayOf(ProfileType.DEFAULT, ProfileType.CURRENT)
    val ageUsed = arrayOf(15.0, 15.0)
    val weightUsed = arrayOf(50.0, 50.0)
    val tddUsed = arrayOf(50.0, 50.0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profilehelper)

        profilehelper_menu1.setOnClickListener {
            switchTab(0)
        }
        profilehelper_menu2.setOnClickListener {
            switchTab(1)
        }

        profilehelper_profiletype.setOnClickListener {
            PopupMenu(this, profilehelper_profiletype).apply {
                menuInflater.inflate(R.menu.menu_profilehelper, menu)
                setOnMenuItemClickListener { item ->
                    profilehelper_profiletype.setText(item.title)
                    when (item.itemId) {
                        R.id.menu_default       -> switchContent(ProfileType.DEFAULT)
                        R.id.menu_current       -> switchContent(ProfileType.CURRENT)
                        R.id.menu_available     -> switchContent(ProfileType.AVAILABLE_PROFILE)
                        R.id.menu_profileswitch -> switchContent(ProfileType.PROFILE_SWITCH)
                    }
                    true
                }
                show()
            }
        }

        profilehelper_copytolocalprofile.setOnClickListener {
            val age = profilehelper_age.value
            val weight = profilehelper_weight.value
            val tdd = profilehelper_tdd.value
            defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())?.let { profile ->
                OKDialog.showConfirmation(this, resourceHelper.gs(R.string.careportal_profileswitch), resourceHelper.gs(R.string.copytolocalprofile), Runnable {
                    localProfilePlugin.addProfile(LocalProfilePlugin.SingleProfile().copyFrom(localProfilePlugin.rawProfile, profile, "DefaultProfile" + dateUtil.dateAndTimeAndSecondsString(dateUtil._now())))
                    rxBus.send(EventLocalProfileChanged())
                })
            }
        }

        profilehelper_age.setParams(15.0, 1.0, 80.0, 1.0, DecimalFormat("0"), false, null)
        profilehelper_weight.setParams(50.0, 5.0, 150.0, 1.0, DecimalFormat("0"), false, null)
        profilehelper_tdd.setParams(50.0, 3.0, 200.0, 1.0, DecimalFormat("0"), false, null)

        profilehelper_tdds.text = tddCalculator.stats()

        profilehelper_profile.setOnClickListener {
            val age = profilehelper_age.value
            val weight = profilehelper_weight.value
            val tdd = profilehelper_tdd.value
            if (age < 1 || age > 120) {
                ToastUtils.showToastInUiThread(this, R.string.invalidage)
                return@setOnClickListener
            }
            if ((weight < 5 || weight > 150) && tdd == 0.0) {
                ToastUtils.showToastInUiThread(this, R.string.invalidweight)
                return@setOnClickListener
            }
            if ((tdd < 5 || tdd > 150) && weight == 0.0) {
                ToastUtils.showToastInUiThread(this, R.string.invalidweight)
                return@setOnClickListener
            }
            profileFunction.getProfile()?.let { runningProfile ->
                defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())?.let { profile ->
                    ProfileViewerDialog().also { pvd ->
                        pvd.arguments = Bundle().also {
                            it.putLong("time", DateUtil.now())
                            it.putInt("mode", ProfileViewerDialog.Mode.PROFILE_COMPARE.ordinal)
                            it.putString("customProfile", runningProfile.data.toString())
                            it.putString("customProfile2", profile.data.toString())
                            it.putString("customProfileUnits", profile.units)
                            it.putString("customProfileName", "Age: $age TDD: $tdd Weight: $weight")
                        }
                    }.show(supportFragmentManager, "ProfileViewDialog")
                    return@setOnClickListener
                }
            }
            ToastUtils.showToastInUiThread(this, R.string.invalidinput)
        }

        switchTab(0)
    }

    private fun switchTab(tab: Int) {
        setBackgroundColorOnSelected(tab)
        when (typeSelected[tabSelected]) {
            ProfileType.DEFAULT           -> {
                ageUsed[tabSelected] = profilehelper_age.value
                weightUsed[tabSelected] = profilehelper_weight.value
                tddUsed[tabSelected] = profilehelper_tdd.value
                profilehelper_age.value = ageUsed[tab]
                profilehelper_weight.value = weightUsed[tab]
                profilehelper_tdd.value = tddUsed[tab]
                profilehelper_profiletype.setText(resourceHelper.gs(R.string.defaultprofile))
            }

            ProfileType.CURRENT           -> {
                profilehelper_profiletype.setText(resourceHelper.gs(R.string.currentprofile))
            }

            ProfileType.AVAILABLE_PROFILE -> {
                profilehelper_profiletype.setText(resourceHelper.gs(R.string.availableprofile))
            }

            ProfileType.PROFILE_SWITCH    -> {
                profilehelper_profiletype.setText(resourceHelper.gs(R.string.careportal_profileswitch))
            }
        }
        tabSelected = tab
        switchContent(typeSelected[tabSelected])
    }

    private fun switchContent(newContent: ProfileType) {
        profilehelper_default_profile.visibility = (newContent == ProfileType.DEFAULT).toVisibility()
        profilehelper_current_profile.visibility = (newContent == ProfileType.CURRENT).toVisibility()
        profilehelper_available_profile.visibility = (newContent == ProfileType.AVAILABLE_PROFILE).toVisibility()
        profilehelper_profile_switch.visibility = (newContent == ProfileType.PROFILE_SWITCH).toVisibility()

        typeSelected[tabSelected] = newContent
        when (newContent) {
            ProfileType.DEFAULT           -> {
                profilehelper_age.value = ageUsed[tabSelected]
                profilehelper_weight.value = weightUsed[tabSelected]
                profilehelper_tdd.value = tddUsed[tabSelected]

            }

            ProfileType.CURRENT           -> {
            }

            ProfileType.AVAILABLE_PROFILE -> {
            }

            ProfileType.PROFILE_SWITCH    -> {
            }
        }
    }

    private fun setBackgroundColorOnSelected(tab: Int) {
        profilehelper_menu1.setBackgroundColor(resourceHelper.gc(if (tab == 1) R.color.defaultbackground else R.color.tabBgColorSelected))
        profilehelper_menu2.setBackgroundColor(resourceHelper.gc(if (tab == 0) R.color.defaultbackground else R.color.tabBgColorSelected))
    }

}
