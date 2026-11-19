package dev.mantikafasi.manticord.activenow

import com.discord.api.channel.Channel
import com.discord.api.channel.getDisplayName
import com.discord.api.channel.isDM
import com.discord.api.channel.isGroup
import com.discord.api.channel.isGuildVocalChannel
import com.discord.api.permission.Permission
import com.discord.api.voice.state.VoiceState
import com.discord.stores.StoreGuilds
import com.discord.stores.StoreStream
import com.discord.stores.StoreVoiceStates
import com.discord.stores.updates.ObservationDeckProvider
import com.discord.utilities.permissions.PermissionUtils
import rx.Observable

internal data class VoiceLocation(
    val channelName: String?,
    val serverName: String?,
    val summary: String,
    val fallbackName: String,
)

internal class VoiceDirectory {
    val states = StoreStream.getVoiceStates()
    val channels = StoreStream.getChannels()
    val permissions = StoreStream.getPermissions()
    private val guilds = StoreStream.getGuilds()

    fun resolve(guildId: Long, channelId: Long): VoiceLocation? {
        val channel = channels.getChannel(channelId)?.takeIf { it.guildId == guildId } ?: return null
        return describeVoice(
            channel,
            permissions.getPermissionsByChannel()[channelId],
            guilds.getGuilds()[guildId]?.name,
        )
    }
}

internal fun observeProfileVoice(userId: Long, preferredGuildId: Long?): Observable<VoiceLocation?> {
    val voice = VoiceDirectory()
    val relationships = StoreStream.getUserRelationships()
    return ObservationDeckProvider.get().connectRx(
        voice.states, voice.channels, voice.permissions, StoreGuilds.GuildsUpdate, relationships,
        observerName = "Active Now profile voice",
    ) {
        if (relationships.isBlockedOrIgnored(userId)) null else findVoiceLocation(
            userId, preferredGuildId, voice.states.get(), voice::resolve,
        )
    }.distinctUntilChanged()
}

internal fun findVoiceLocation(
    userId: Long,
    preferredGuildId: Long?,
    states: Map<Long, Map<Long, VoiceState>>,
    resolve: (guildId: Long, channelId: Long) -> VoiceLocation?,
): VoiceLocation? {
    fun locationIn(guildId: Long): VoiceLocation? =
        states[guildId]?.get(userId)?.channelId?.let { resolve(guildId, it) }

    return preferredGuildId?.let(::locationIn)
        ?: states.keys.sorted().firstNotNullOfOrNull(::locationIn)
}

internal fun describeVoice(channel: Channel, permissions: Long?, guildName: String?): VoiceLocation? {
    if (channel.guildId == StoreVoiceStates.PRIVATE_CHANNELS_GUILD_ID) {
        return when {
            channel.isDM() -> VoiceLocation(null, null, "In a DM call", "Direct message call")
            channel.isGroup() -> {
                val name = channel.getDisplayName().takeIf(String::isNotBlank)
                VoiceLocation(name, null, name?.let { "In call · $it" } ?: "In a group call", "Group call")
            }
            else -> null
        }
    }
    if (!channel.isGuildVocalChannel() || !PermissionUtils.can(Permission.VIEW_CHANNEL, permissions)) return null
    val channelName = channel.name?.takeIf(String::isNotBlank)
    val serverName = guildName?.takeIf(String::isNotBlank)
    val summary = if (channelName == null) "In voice" else
        listOfNotNull("In voice", channelName, serverName).joinToString(" · ")
    return VoiceLocation(channelName, serverName, summary, "Voice channel")
}
