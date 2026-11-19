package dev.mantikafasi.manticord.activenow

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import kotlin.math.roundToInt

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

internal fun Context.discordTypeface(attribute: Int, fallbackFont: Int): Typeface? {
    val value = TypedValue()
    if (theme.resolveAttribute(attribute, value, true) && value.resourceId != 0) {
        runCatching { ResourcesCompat.getFont(this, value.resourceId) }.getOrNull()?.let { return it }
    }
    return runCatching { ResourcesCompat.getFont(this, fallbackFont) }.getOrNull()
}
