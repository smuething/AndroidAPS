package info.nightscout.androidaps.utils.alertDialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.SystemClock
import androidx.annotation.StringRes
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.extensions.runOnUiThread

object ErrorDialog {

    @SuppressLint("InflateParams")
    @JvmStatic
    @JvmOverloads
    fun showError(context: Context, title: String, message: String, @StringRes positiveButton: Int = -1, ok: (() -> Unit)? = null, cancel: (() -> Unit)? = null) {

        val builder = AlertDialogHelper.Builder(context, R.style.AppThemeWarningDialog)
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title, R.drawable.ic_header_error, R.style.AppThemeErrorDialog))
            .setNegativeButton(R.string.dismiss) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                if (cancel != null) {
                    runOnUiThread(Runnable {
                        cancel()
                    })
                }
            }

        if (positiveButton != -1) {
            builder.setPositiveButton(positiveButton) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                if (ok != null) {
                    runOnUiThread(Runnable {
                        ok()
                    })
                }
            }
        }

        val dialog = builder.show()
        dialog.setCanceledOnTouchOutside(true)
    }

}