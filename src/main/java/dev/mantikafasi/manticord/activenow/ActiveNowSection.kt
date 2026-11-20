package dev.mantikafasi.manticord.activenow

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.discord.R
import com.discord.api.presence.ClientStatus
import com.discord.utilities.color.ColorCompat
import com.discord.utilities.icon.IconUtils
import com.discord.utilities.rx.ui
import com.discord.views.VitoImageView
import rx.Subscription

internal class ActiveNowSection(
    context: Context,
    openProfile: (Long) -> Unit,
) : LinearLayout(context) {
    private var subscription: Subscription? = null
    private val cards = ActiveNowCards(openProfile)
    private val layout = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
    private val snap = PagerSnapHelper()
    private val carousel = ActiveNowCarousel(context).apply {
        layoutManager = layout
        isNestedScrollingEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        setPaddingRelative(context.dp(12), 0, context.dp(12), 0)
        itemAnimator = null
        adapter = cards
    }
    private val previous = navigationButton("Previous active friend") { moveBy(-1) }
    private val next = navigationButton("Next active friend") { moveBy(1) }

    init {
        orientation = VERTICAL
        visibility = GONE
        setPaddingRelative(0, 0, 0, context.dp(14))

        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(40)
            setPaddingRelative(context.dp(16), 0, context.dp(16), context.dp(6))
            addView(TextView(context).apply {
                text = "Active Now"
                textSize = 17f
                setTextColor(context.themeColor(R.attr.colorTextNormal))
                typeface = context.discordTypeface(R.attr.font_display_bold, R.font.ginto_bold)
                ViewCompat.setAccessibilityHeading(this, true)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(previous, LayoutParams(context.dp(40), context.dp(40)))
            addView(next, LayoutParams(context.dp(40), context.dp(40)).apply { marginStart = context.dp(4) })
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(carousel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        snap.attachToRecyclerView(carousel)
        carousel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateNavigation() }
        carousel.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) updateNavigation()
            }
        })
        updateNavigation()
    }

    fun connect() {
        subscription = observeFeed()
            .onBackpressureLatest()
            .ui()
            .subscribe(::render) { error ->
                render(emptyList())
                Log.w("ActiveNow", "Unable to update the DM section", error)
            }
    }

    private fun render(entries: List<FriendActivity>) {
        visibility = if (entries.isEmpty()) GONE else VISIBLE
        cards.submitList(entries) { carousel.post { updateNavigation() } }
    }

    private fun currentPosition(): Int = snap.findSnapView(layout)?.let(layout::getPosition)
        ?.takeIf { it in 0 until cards.itemCount } ?: 0

    private fun moveBy(offset: Int) {
        val target = currentPosition() + offset
        if (target in 0 until cards.itemCount) carousel.smoothScrollToPosition(target)
    }

    private fun updateNavigation() {
        val position = currentPosition()
        val show = cards.itemCount > 1
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        previous.update(show, position > 0, rtl)
        next.update(show, position + 1 < cards.itemCount, !rtl)
    }

    private fun ImageButton.update(show: Boolean, canMove: Boolean, pointRight: Boolean) {
        visibility = if (show) VISIBLE else GONE
        isEnabled = canMove
        alpha = if (canMove) 1f else 0.35f
        scaleX = if (pointRight) -1f else 1f
    }

    private fun navigationButton(label: String, onClick: () -> Unit) = ImageButton(context).apply {
        visibility = GONE
        contentDescription = label
        setImageResource(R.drawable.ic_chevron_left_24dp)
        drawable?.isAutoMirrored = false
        imageTintList = ColorStateList.valueOf(context.themeColor(R.attr.colorInteractiveNormal))
        scaleType = ImageView.ScaleType.CENTER
        background = context.pressableSurface(10, context.sidebarSurface(0.06f), null)
        setOnClickListener { onClick() }
    }

    fun dispose() {
        subscription?.unsubscribe()
        subscription = null
        (parent as? ViewGroup)?.removeView(this)
    }
}

private class ActiveNowCarousel(context: Context) : RecyclerView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        // The sidebar can intercept the first MOVE before RecyclerView starts dragging.
        if (action == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val handled = super.dispatchTouchEvent(event)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
            (action == MotionEvent.ACTION_DOWN && !handled)
        ) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }
}

private class ActiveNowCards(
    private val openProfile: (Long) -> Unit,
) : ListAdapter<FriendActivity, ActiveNowCards.Card>(object : DiffUtil.ItemCallback<FriendActivity>() {
    override fun areItemsTheSame(old: FriendActivity, new: FriendActivity): Boolean =
        old.user.id == new.user.id

    override fun areContentsTheSame(old: FriendActivity, new: FriendActivity): Boolean = old == new
}) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Card = Card(parent.context)

    override fun onBindViewHolder(holder: Card, position: Int) = holder.bind(getItem(position), openProfile)

    override fun onViewRecycled(holder: Card) {
        holder.clear()
        super.onViewRecycled(holder)
    }

    class Card(context: Context) : RecyclerView.ViewHolder(LinearLayout(context)) {
        private val root = itemView as LinearLayout
        private val surface = context.sidebarSurface(0.08f)
        private val avatar = VitoImageView(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(context.themeColor(R.attr.colorBackgroundSecondary))
            }
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val statusIcon = ImageView(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surface)
            }
            val inset = context.dp(2)
            setPadding(inset, inset, inset, inset)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val name = label(17f, R.attr.colorTextNormal, 1).apply {
            typeface = context.discordTypeface(R.attr.font_primary_semibold, R.font.whitney_semibold)
        }
        private val voiceIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_channel_voice_16dp)
            imageTintList = ColorStateList.valueOf(context.themeColor(R.attr.colorTextBrand))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        private val summary = label(14f, R.attr.colorTextNormal, 2)
        private val extra = label(13f, R.attr.colorTextMuted, 2)

        init {
            root.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(4)
                marginEnd = context.dp(4)
            }
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.isFocusable = true
            root.minimumHeight = context.dp(90)
            root.setPadding(context.dp(14), context.dp(11), context.dp(14), context.dp(11))
            root.background = context.pressableSurface(14, surface, context.sidebarSurface(0.2f))

            root.addView(FrameLayout(context).apply {
                addView(avatar, FrameLayout.LayoutParams(context.dp(46), context.dp(46), Gravity.START or Gravity.CENTER_VERTICAL))
                addView(statusIcon, FrameLayout.LayoutParams(context.dp(17), context.dp(17), Gravity.END or Gravity.BOTTOM))
            }, LinearLayout.LayoutParams(context.dp(49), context.dp(49)).apply {
                marginEnd = context.dp(12)
            })

            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(name)
                addView(LinearLayout(context).apply {
                    gravity = Gravity.TOP
                    addView(voiceIcon, LinearLayout.LayoutParams(context.dp(16), context.dp(16)).apply {
                        marginEnd = context.dp(6)
                        topMargin = context.dp(2)
                    })
                    addView(summary, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = context.dp(3)
                })
                addView(extra, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = context.dp(3)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        fun bind(entry: FriendActivity, openProfile: (Long) -> Unit) {
            val voice = entry.voice
            val status = when (entry.status) {
                ClientStatus.ONLINE -> root.context.getString(R.string.status_online)
                ClientStatus.IDLE -> root.context.getString(R.string.status_idle)
                ClientStatus.DND -> root.context.getString(R.string.status_dnd)
                else -> null
            }
            val statusDrawable = when (entry.status) {
                ClientStatus.ONLINE -> R.drawable.ic_status_online_16dp
                ClientStatus.IDLE -> R.drawable.ic_status_idle_16dp
                ClientStatus.DND -> R.drawable.ic_status_dnd_16dp
                else -> 0
            }
            val first = if (voice == null) entry.activity ?: status else voice.channelName ?: voice.fallbackName
            val detail = voice?.let { listOfNotNull(it.serverName, entry.activity).joinToString(" · ") }
                ?.takeIf(String::isNotEmpty)
            name.text = entry.name
            summary.text = first
            voiceIcon.visibility = if (voice == null) View.GONE else View.VISIBLE
            extra.text = detail
            extra.visibility = if (detail == null) View.GONE else View.VISIBLE
            statusIcon.setImageResource(statusDrawable)
            statusIcon.visibility = if (statusDrawable == 0) View.GONE else View.VISIBLE
            avatar.setImageURI(IconUtils.getForUser(entry.user, size = root.context.dp(46)))
            root.contentDescription = listOfNotNull(entry.name, voice?.summary, entry.activity, status)
                .joinToString(". ")
            root.setOnClickListener { openProfile(entry.user.id) }
        }

        fun clear() {
            avatar.release()
            root.setOnClickListener(null)
        }

        private fun label(size: Float, color: Int, lines: Int) = TextView(itemView.context).apply {
            textSize = size
            typeface = context.discordTypeface(R.attr.font_primary_normal, R.font.whitney_medium)
            setTextColor(context.themeColor(color))
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
        }
    }
}

private fun Context.themeColor(attr: Int): Int = ColorCompat.getThemedColor(this, attr)

private fun Context.sidebarSurface(textWeight: Float): Int = ColorUtils.blendARGB(
    themeColor(R.attr.colorBackgroundSecondary),
    themeColor(R.attr.colorTextNormal),
    textWeight,
)

private fun Context.pressableSurface(radius: Int, fill: Int, border: Int?) = RippleDrawable(
    ColorStateList.valueOf(themeColor(R.attr.colorBackgroundModifierHover)),
    GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        border?.let { setStroke(dp(1), it) }
    },
    null,
)
