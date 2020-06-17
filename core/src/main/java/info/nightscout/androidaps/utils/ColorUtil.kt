package info.nightscout.androidaps.utils

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@ColorInt
fun Context.getColorFromAttr(@AttrRes res: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(res, typedValue, true)
    return typedValue.data
}