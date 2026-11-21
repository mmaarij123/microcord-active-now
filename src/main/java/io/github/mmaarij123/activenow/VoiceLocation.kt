package io.github.mmaarij123.activenow

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
    val guildId: Long,
    val channelId: Long,
    val channelName: String?,
    val serverName: String?,
    val summary: String,
    val fallbackName: String,
    val canJoin: Boolean,
)

internal class VoiceDirectory {
    val states = StoreStream.getVoiceStates()
    val channels = StoreStream.getChannels()
    val permissions = StoreStream.getPermissions()
    val relationships = StoreStream.getUserRelationships()
    private val guilds = StoreStream.getGuilds()

    fun resolve(guildId: Long, channelId: Long): VoiceLocation? {
        val channel = channels.getChannel(channelId)?.takeIf { it.guildId == guildId } ?: return null
        return describeVoice(
            channel,
            permissions.getPermissionsByChannel()[channelId],
            guilds.getGuilds()[guildId]?.name,
        )
    }

    fun profileLocation(userId: Long, preferredGuildId: Long?): VoiceLocation? {
        if (relationships.isBlockedOrIgnored(userId)) return null
        return findVoiceLocation(userId, preferredGuildId, states.get(), ::resolve)
    }
}

internal fun observeProfileVoice(userId: Long, preferredGuildId: Long?): Observable<VoiceLocation?> {
    val voice = VoiceDirectory()
    return ObservationDeckProvider.get().connectRx(
        voice.states, voice.channels, voice.permissions, StoreGuilds.GuildsUpdate, voice.relationships,
        observerName = "Active Now profile voice",
    ) {
        voice.profileLocation(userId, preferredGuildId)
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
            channel.isDM() -> VoiceLocation(
                channel.guildId, channel.id, null, null, "In a DM call", "Direct message call", canJoin = true,
            )
            channel.isGroup() -> {
                val name = channel.getDisplayName().takeIf(String::isNotBlank)
                VoiceLocation(
                    channel.guildId, channel.id, name, null,
                    name?.let { "In call · $it" } ?: "In a group call", "Group call", canJoin = true,
                )
            }
            else -> null
        }
    }
    if (!channel.isGuildVocalChannel() || !PermissionUtils.can(Permission.VIEW_CHANNEL, permissions)) return null
    val channelName = channel.name?.takeIf(String::isNotBlank)
    val serverName = guildName?.takeIf(String::isNotBlank)
    val summary = if (channelName == null) "In voice" else
        listOfNotNull("In voice", channelName, serverName).joinToString(" · ")
    return VoiceLocation(
        channel.guildId, channel.id, channelName, serverName, summary, "Voice channel",
        canJoin = PermissionUtils.can(Permission.CONNECT, permissions),
    )
}
