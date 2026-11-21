package io.github.mmaarij123.activenow

import android.content.Context
import android.content.Intent
import com.discord.R
import com.discord.api.channel.Channel
import com.discord.api.channel.isStageVoiceChannel
import com.discord.api.user.NsfwAllowance
import com.discord.app.AppActivity
import com.discord.app.AppToast
import com.discord.dialogs.GuildVideoAtCapacityDialog
import com.discord.stores.StoreNavigation
import com.discord.stores.StoreStream
import com.discord.stores.StoreVoiceChannelSelected.JoinVoiceChannelResult
import com.discord.utilities.guilds.MemberVerificationUtils
import com.discord.utilities.rx.ui
import com.discord.utilities.voice.VoiceChannelJoinability
import com.discord.utilities.voice.VoiceChannelJoinabilityUtils
import com.discord.widgets.channels.SpoilerChannelGate
import com.discord.widgets.stage.StageChannelJoinHelper
import com.discord.widgets.user.usersheet.WidgetUserSheet
import com.discord.widgets.voice.fullscreen.WidgetCallFullscreen
import com.discord.widgets.voice.fullscreen.WidgetCallPreviewFullscreen

internal fun openVoiceServer(sheet: WidgetUserSheet, guildId: Long) {
    val activity = sheet.activity ?: return
    if (guildId == 0L || StoreStream.getGuilds().getGuild(guildId) == null) {
        AppToast.show(activity, R.string.inaccessible_channel_link_title)
        return
    }
    if (MemberVerificationUtils.maybeShowVerificationGate(activity, sheet.parentFragmentManager, guildId)) return

    StoreStream.selectGuild(guildId)
    StoreStream.getTabsNavigation().selectHomeTab(StoreNavigation.PanelAction.CLOSE, dismissTabsDialogs = true)
    sheet.dismiss()
    if (activity !is AppActivity.Main) {
        activity.startActivity(
            Intent(activity, AppActivity.Main::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }
}

internal fun joinProfileVoice(
    sheet: WidgetUserSheet,
    readChannel: () -> Channel?,
    onJoining: (Boolean) -> Unit,
) {
    val activity = sheet.activity ?: return
    val channel = readChannel() ?: return activity.showVoiceChanged()
    val manager = sheet.parentFragmentManager
    if (manager.isStateSaved) return

    val retry = { joinProfileVoice(sheet, readChannel, onJoining) }
    if (!sheet.passVoiceGates(channel, retry)) return

    if (channel.isStageVoiceChannel()) {
        StageChannelJoinHelper.connectToStage(activity, manager, channel.id) {
            if (sheet.isAdded) sheet.dismissAllowingStateLoss()
        }
        return
    }
    val voiceStore = StoreStream.getVoiceChannelSelected()
    if (voiceStore.getSelectedVoiceChannelId() == channel.id) {
        sheet.openConnectedCall(channel.id)
        return
    }
    if (!sheet.checkCurrentJoinability(channel.id)) return

    onJoining(true)
    sheet.requestMicrophone(onFailure = { onJoining(false) }) {
        onJoining(false)
        val current = readChannel()
        if (current == null || current.id != channel.id) {
            if (sheet.isAdded) activity.showVoiceChanged()
            return@requestMicrophone
        }
        if (manager.isStateSaved || !sheet.passVoiceGates(current, retry) || !sheet.checkCurrentJoinability(current.id)) {
            return@requestMicrophone
        }
        onJoining(true)
        voiceStore.selectVoiceChannel(current.id)
            .defaultIfEmpty(JoinVoiceChannelResult.ALREADY_CONNECTED)
            .ui()
            .subscribe({ result ->
                onJoining(false)
                if (sheet.isAdded) sheet.handleJoinResult(current.id, result)
            }, {
                onJoining(false)
                if (sheet.isAdded) AppToast.show(activity, R.string.default_failure_to_perform_action_message)
            })
    }
}

private fun WidgetUserSheet.passVoiceGates(channel: Channel, retry: () -> Unit): Boolean {
    if (channel.guildId == 0L) return true
    val context = context ?: return false
    val manager = parentFragmentManager
    if (MemberVerificationUtils.maybeShowVerificationGate(context, manager, channel.guildId, "Voice Channel")) return false
    if (SpoilerChannelGate.maybeShowForVoiceChannel(context, manager, channel.id, retry)) return false
    val needsAgeGate = channel.nsfw &&
    (!StoreStream.getGuildsNsfw().isGuildNsfwGateAgreed(channel.guildId) ||
        StoreStream.getUsers().getMe().nsfwAllowance == NsfwAllowance.DISALLOWED)
    if (needsAgeGate) {
        dismiss()
        WidgetCallPreviewFullscreen.launch(context, channel.id, animate = true)
        return false
    }
    return true
}

private fun WidgetUserSheet.handleJoinResult(channelId: Long, result: JoinVoiceChannelResult) {
    val context = context ?: return
    when (result) {
        JoinVoiceChannelResult.SUCCESS, JoinVoiceChannelResult.ALREADY_CONNECTED -> {
            if (StoreStream.getVoiceChannelSelected().getSelectedVoiceChannelId() == channelId) {
                openConnectedCall(channelId)
            } else {
                AppToast.show(context, R.string.default_failure_to_perform_action_message)
            }
        }
        JoinVoiceChannelResult.FAILED_PERMISSIONS_MISSING -> AppToast.show(context, R.string.channel_locked)
        JoinVoiceChannelResult.FAILED_CHANNEL_FULL -> AppToast.show(context, R.string.unable_to_join_channel_full)
        JoinVoiceChannelResult.FAILED_GUILD_VIDEO_AT_CAPACITY ->
            if (!parentFragmentManager.isStateSaved) GuildVideoAtCapacityDialog.show(parentFragmentManager) else Unit
        JoinVoiceChannelResult.FAILED_CHANNEL_DOES_NOT_EXIST -> context.showVoiceChanged()
        JoinVoiceChannelResult.DEFERRED_UNTIL_SESSION_START -> AppToast.show(context, "Waiting to reconnect to voice")
    }
}

private fun WidgetUserSheet.checkCurrentJoinability(channelId: Long): Boolean {
    val context = context ?: return false
    when (VoiceChannelJoinabilityUtils.getJoinability(channelId)) {
        VoiceChannelJoinability.CAN_JOIN -> return true
        VoiceChannelJoinability.PERMISSIONS_MISSING -> AppToast.show(context, R.string.channel_locked)
        VoiceChannelJoinability.CHANNEL_FULL -> AppToast.show(context, R.string.unable_to_join_channel_full)
        VoiceChannelJoinability.GUILD_VIDEO_AT_CAPACITY ->
            if (!parentFragmentManager.isStateSaved) GuildVideoAtCapacityDialog.show(parentFragmentManager)
        VoiceChannelJoinability.CHANNEL_DOES_NOT_EXIST -> context.showVoiceChanged()
    }
    return false
}

private fun WidgetUserSheet.openConnectedCall(channelId: Long) {
    val activity = activity ?: return
    dismissAllowingStateLoss()
    WidgetCallFullscreen.launch(activity, channelId, animate = true)
}

private fun Context.showVoiceChanged() {
    AppToast.show(this, "Their voice channel changed. Try again.")
}
