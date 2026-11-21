package io.github.mmaarij123.activenow

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.discord.R
import com.discord.widgets.channels.list.WidgetChannelsList
import com.discord.widgets.user.usersheet.WidgetUserSheet
import com.google.android.material.appbar.AppBarLayout
import dev.mantikafasi.manticord.plugins.api.ManticordPlugin
import dev.mantikafasi.manticord.plugins.api.PluginScope
import dev.mantikafasi.manticord.plugins.api.pluginProfileContribution
import java.util.Collections
import java.util.WeakHashMap
import rx.Completable

class ActiveNowPlugin : ManticordPlugin, Application.ActivityLifecycleCallbacks {
    private val main = Handler(Looper.getMainLooper())
    private val watched = mutableSetOf<FragmentActivity>()
    private val sections = mutableMapOf<WidgetChannelsList, ActiveNowSection>()
    private val profileCards = Collections.newSetFromMap(WeakHashMap<ProfileVoiceCard, Boolean>())
    private var application: Application? = null

    private val fragments = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: FragmentManager,
            fragment: Fragment,
            view: View,
            savedInstanceState: Bundle?,
        ) {
            if (fragment is WidgetChannelsList) mount(fragment, view)
        }

        override fun onFragmentViewDestroyed(fm: FragmentManager, fragment: Fragment) {
            if (fragment is WidgetChannelsList) unmount(fragment)
        }
    }

    override fun start(scope: PluginScope): Completable = Completable.fromAction {
        scope.ui.registerProfile(
            pluginProfileContribution(
                "profile_voice",
                "In voice",
                { context -> ProfileVoiceCard(context).also { profileCards.add(it) } },
            ) {
                priority = 900
                isVisible = { !it.isBot }
                bind = { view, profile -> (view as ProfileVoiceCard).bind(profile) }
            },
        )
        scope.runOnMainThread { context ->
            val app = context.applicationContext as Application
            application = app
            app.registerActivityLifecycleCallbacks(this)
            (context as? FragmentActivity)?.let(::watch)
        }
    }

    override fun stop(): Completable = Completable.fromAction {
        main.post {
            application?.unregisterActivityLifecycleCallbacks(this)
            application = null
            watched.toList().forEach(::unwatch)
            sections.keys.toList().forEach(::unmount)
            profileCards.toList().forEach(ProfileVoiceCard::dispose)
            profileCards.clear()
        }
    }

    override fun onActivityStarted(activity: Activity) {
        (activity as? FragmentActivity)?.let(::watch)
    }

    override fun onActivityStopped(activity: Activity) {
        (activity as? FragmentActivity)?.let(::unwatch)
    }

    override fun onActivityDestroyed(activity: Activity) {
        (activity as? FragmentActivity)?.let(::unwatch)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun watch(activity: FragmentActivity) {
        if (activity.isDestroyed || !watched.add(activity)) return
        val manager = activity.supportFragmentManager
        manager.registerFragmentLifecycleCallbacks(fragments, true)
        mountExisting(manager)
    }

    private fun mountExisting(manager: FragmentManager) {
        for (fragment in manager.fragments) {
            if (!fragment.isAdded) continue
            if (fragment is WidgetChannelsList) fragment.view?.let { mount(fragment, it) }
            mountExisting(fragment.childFragmentManager)
        }
    }

    private fun unwatch(activity: FragmentActivity) {
        if (!watched.remove(activity)) return
        activity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragments)
        sections.keys.filter { it.activity === activity }.forEach(::unmount)
    }

    private fun mount(fragment: WidgetChannelsList, root: View) {
        if (sections.containsKey(fragment)) return
        val header = root.findViewById<View>(R.id.channels_list_private_channels_header) ?: return
        val appBar = header.parent as? AppBarLayout ?: return
        if (appBar.id != R.id.app_bar_layout) return

        runCatching {
            val section = ActiveNowSection(header.context) { userId ->
                if (fragment.isAdded) {
                    val manager = fragment.parentFragmentManager
                    if (!manager.isStateSaved) WidgetUserSheet.show(manager, userId)
                }
            }
            val params = AppBarLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                scrollFlags = 0
            }
            appBar.addView(section, appBar.indexOfChild(header) + 1, params)
            sections[fragment] = section
            section.connect()
        }.onFailure {
            unmount(fragment)
            Log.w("ActiveNow", "Unable to mount the DM section", it)
        }
    }

    private fun unmount(fragment: WidgetChannelsList) {
        sections.remove(fragment)?.dispose()
    }
}

class ActiveNowDiscoveryService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
