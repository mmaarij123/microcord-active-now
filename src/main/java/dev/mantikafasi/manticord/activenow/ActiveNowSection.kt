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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.discord.R
import com.discord.api.presence.ClientStatus
import com.discord.utilities.color.ColorCompat
import com.discord.utilities.icon.IconUtils
import com.discord.utilities.rx.ui
import com.discord.views.VitoImageView
import kotlin.math.roundToInt
import rx.Subscription

internal class ActiveNowSection(
    context: Context,
    openProfile: (Long) -> Unit,
) : LinearLayout(context) {
    private var subscription: Subscription? = null
    private val cards = ActiveNowCards(openProfile)

    init {
        orientation = VERTICAL
        visibility = GONE
        setPaddingRelative(0, context.dp(10), 0, context.dp(4))

        addView(TextView(context).apply {
            text = "Active Now"
            textSize = 16f
            setTextColor(context.themeColor(R.attr.colorInteractiveActive))
            typeface = context.discordTypeface(R.attr.font_display_bold, R.font.ginto_bold)
            setPaddingRelative(context.dp(16), 0, context.dp(16), context.dp(8))
            ViewCompat.setAccessibilityHeading(this, true)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        addView(ActiveNowCarousel(context).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            isNestedScrollingEnabled = false
            clipToPadding = false
            setPaddingRelative(context.dp(16), 0, context.dp(8), 0)
            itemAnimator = null
            adapter = cards
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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
        cards.submitList(entries)
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
        private val avatar = VitoImageView(context)
        private val name = label(14f, R.attr.colorTextNormal).apply {
            typeface = context.discordTypeface(R.attr.font_primary_semibold, R.font.whitney_semibold)
        }
        private val summary = label(12f, R.attr.colorTextMuted)
        private val secondLine = label(12f, R.attr.colorTextMuted)

        init {
            val width = (192 * context.resources.configuration.fontScale.coerceIn(1f, 1.45f)).roundToInt()
            root.layoutParams = RecyclerView.LayoutParams(context.dp(width), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = context.dp(8)
            }
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.isFocusable = true
            root.setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
            root.background = RippleDrawable(
                ColorStateList.valueOf(context.themeColor(R.attr.colorBackgroundModifierHover)),
                GradientDrawable().apply {
                    cornerRadius = context.dp(10).toFloat()
                    setColor(context.themeColor(R.attr.colorBackgroundTertiary))
                },
                null,
            )

            avatar.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(context.themeColor(R.attr.colorBackgroundSecondary))
            }
            avatar.clipToOutline = true
            avatar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            root.addView(avatar, LinearLayout.LayoutParams(context.dp(32), context.dp(32)).apply {
                marginEnd = context.dp(9)
            })

            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(name)
                addView(summary)
                addView(secondLine)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        fun bind(entry: FriendActivity, openProfile: (Long) -> Unit) {
            val first = entry.voice ?: entry.activity ?: when (entry.status) {
                ClientStatus.ONLINE -> root.context.getString(R.string.status_online)
                ClientStatus.IDLE -> root.context.getString(R.string.status_idle)
                ClientStatus.DND -> root.context.getString(R.string.status_dnd)
                else -> null
            }
            val second = entry.activity.takeIf { entry.voice != null }
            name.text = entry.name
            summary.text = first
            secondLine.text = second
            secondLine.visibility = if (second == null) View.INVISIBLE else View.VISIBLE
            avatar.setImageURI(IconUtils.getForUser(entry.user, size = itemView.context.dp(32)))
            root.contentDescription = listOfNotNull(entry.name, first, second).joinToString(". ")
            root.setOnClickListener { openProfile(entry.user.id) }
        }

        fun clear() {
            avatar.release()
            root.setOnClickListener(null)
        }

        private fun label(size: Float, color: Int) = TextView(itemView.context).apply {
            textSize = size
            typeface = context.discordTypeface(R.attr.font_primary_normal, R.font.whitney_medium)
            setTextColor(context.themeColor(color))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }
}

private fun Context.themeColor(attr: Int): Int = ColorCompat.getThemedColor(this, attr)
