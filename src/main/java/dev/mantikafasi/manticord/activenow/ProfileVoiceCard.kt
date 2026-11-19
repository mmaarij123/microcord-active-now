package dev.mantikafasi.manticord.activenow

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.discord.R
import com.discord.utilities.rx.ui
import dev.mantikafasi.manticord.plugins.api.PluginProfileContext
import rx.Subscription

internal class ProfileVoiceCard(context: Context) : LinearLayout(context) {
    private var profile: PluginProfileContext? = null
    private var subscription: Subscription? = null
    private val frame = GradientDrawable().apply { cornerRadius = context.dp(12).toFloat() }
    private val iconBackground = GradientDrawable().apply { cornerRadius = context.dp(9).toFloat() }
    private val icon = ImageView(context).apply {
        setImageResource(R.drawable.ic_channel_voice_16dp)
        scaleType = ImageView.ScaleType.CENTER
        background = iconBackground
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val label = TextView(context).apply {
        text = "In voice"
        textSize = 12f
        typeface = context.discordTypeface(R.attr.font_primary_semibold, R.font.whitney_semibold)
    }
    private val channel = TextView(context).apply {
        textSize = 16f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        typeface = context.discordTypeface(R.attr.font_primary_semibold, R.font.whitney_semibold)
    }
    private val server = TextView(context).apply {
        textSize = 13f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        typeface = context.discordTypeface(R.attr.font_primary_normal, R.font.whitney_medium)
    }

    init {
        visibility = GONE
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = frame
        val padding = context.dp(12)
        setPadding(padding, padding, padding, padding)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = context.dp(16)
            marginEnd = context.dp(16)
            topMargin = context.dp(12)
            bottomMargin = context.dp(4)
        }
        ViewCompat.setScreenReaderFocusable(this, true)

        addView(icon, LayoutParams(context.dp(36), context.dp(36)).apply { marginEnd = context.dp(12) })
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(label)
            addView(channel)
            addView(server, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dp(2)
            })
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    fun bind(profile: PluginProfileContext) {
        val previous = this.profile
        if (previous == profile) return
        this.profile = profile
        frame.setColor(profile.surfaceColor)
        frame.setStroke(context.dp(1), profile.borderColor)
        iconBackground.setColor(ColorUtils.setAlphaComponent(profile.primaryTextColor, 25))
        icon.imageTintList = ColorStateList.valueOf(profile.primaryTextColor)
        label.setTextColor(profile.secondaryTextColor)
        channel.setTextColor(profile.primaryTextColor)
        server.setTextColor(profile.secondaryTextColor)

        if (previous?.userId != profile.userId || previous.guildId != profile.guildId) {
            observeBoundProfile()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observeBoundProfile()
    }

    override fun onDetachedFromWindow() {
        disconnect()
        super.onDetachedFromWindow()
    }

    fun dispose() {
        profile = null
        disconnect()
        render(null)
        (parent as? ViewGroup)?.removeView(this)
    }

    private fun observeBoundProfile() {
        disconnect()
        render(null)
        val profile = profile ?: return
        if (!isAttachedToWindow) return
        subscription = observeProfileVoice(profile.userId, profile.guildId)
            .ui()
            .subscribe(::render) { error ->
                render(null)
                Log.w("ActiveNow", "Unable to update profile voice", error)
            }
    }

    private fun disconnect() {
        subscription?.unsubscribe()
        subscription = null
    }

    private fun render(location: VoiceLocation?) {
        if (location == null) {
            visibility = GONE
            return
        }
        val name = location.channelName ?: location.fallbackName
        channel.text = name
        server.text = location.serverName
        server.visibility = if (location.serverName == null) GONE else VISIBLE
        contentDescription = listOfNotNull(label.text.toString(), name, location.serverName).joinToString(". ")
        visibility = VISIBLE
    }
}
