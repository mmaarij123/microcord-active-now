package io.github.mmaarij123.activenow

import com.discord.api.activity.Activity
import com.discord.api.activity.ActivityType
import com.discord.api.presence.ClientStatus
import com.discord.api.voice.state.VoiceState
import com.discord.models.domain.ModelUserRelationship
import com.discord.models.presence.Presence
import com.discord.models.user.User
import com.discord.stores.StoreGuildSelected
import com.discord.stores.StoreGuilds
import com.discord.stores.StoreStream
import com.discord.stores.updates.ObservationDeckProvider
import rx.Observable

internal data class FriendActivity(
    val user: User,
    val name: String,
    val voice: VoiceLocation?,
    val activity: String?,
    val status: ClientStatus?,
)

internal fun observeFeed(): Observable<List<FriendActivity>> {
    val selectedGuild = StoreStream.getGuildSelected()
    val relationships = StoreStream.getUserRelationships()
    val users = StoreStream.getUsers()
    val presence = StoreStream.getPresences()
    val voice = VoiceDirectory()

    return ObservationDeckProvider.get().connectRx(
        selectedGuild, relationships, users, presence, voice.states, voice.channels, voice.permissions, StoreGuilds.GuildsUpdate,
        observerName = "Active Now",
    ) {
        if (selectedGuild.getSelectedGuildId() != StoreGuildSelected.UNSELECTED) {
            emptyList()
        } else {
            collectActivities(
                relationships.getRelationships(),
                relationships.getIgnoredUserIds(),
                relationships.getNicknames(),
                users.getUsers(),
                presence.getPresences(),
                voice.states.get(),
                voice::resolve,
            )
        }
    }.distinctUntilChanged()
}

internal fun collectActivities(
    relationships: Map<Long, Int>,
    ignored: Set<Long>,
    nicknames: Map<Long, String>,
    users: Map<Long, User>,
    presences: Map<Long, Presence>,
    voiceStates: Map<Long, Map<Long, VoiceState>>,
    describeChannel: (guildId: Long, channelId: Long) -> VoiceLocation?,
): List<FriendActivity> {
    val friends = relationships.keys.filterTo(HashSet()) {
        relationships[it] == ModelUserRelationship.TYPE_FRIEND && it !in ignored && it in users
    }
    if (friends.isEmpty()) return emptyList()

    val voiceByFriend = mutableMapOf<Long, VoiceLocation>()
    // The private-call bucket (0) comes first when the host knows of more than one session.
    for ((guildId, states) in voiceStates.toSortedMap()) {
        val candidates = if (friends.size < states.size) friends else states.keys
        for (userId in candidates) {
            if (userId !in friends || userId in voiceByFriend) continue
            val channelId = states[userId]?.channelId ?: continue
            describeChannel(guildId, channelId)?.let { voiceByFriend[userId] = it }
        }
    }

    return friends.mapNotNull { userId ->
        val user = users.getValue(userId)
        val voice = voiceByFriend[userId]
        val presence = presences[userId]
        val status = presence?.status.visibleStatus()
        if (voice == null && status == null) return@mapNotNull null
        val activity = describeActivity(presence)
        val name = nicknames[userId] ?: user.globalName ?: user.username
        FriendActivity(user, name, voice, activity, status)
    }.sortedWith(compareBy<FriendActivity> { it.voice == null }
        .thenBy { it.activity == null }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.user.id })
}

internal fun describeActivity(presence: Presence?): String? {
    if (presence?.status.visibleStatus() == null) return null
    return presence?.activities.orEmpty().firstNotNullOfOrNull(::describeActivity)
}

private fun ClientStatus?.visibleStatus(): ClientStatus? = when (this) {
    ClientStatus.ONLINE, ClientStatus.IDLE, ClientStatus.DND -> this
    else -> null
}

private fun describeActivity(activity: Activity): String? {
    val name = activity.name.takeIf(String::isNotBlank) ?: return null
    return when (activity.type) {
        ActivityType.PLAYING -> "Playing $name"
        ActivityType.STREAMING -> "Streaming $name"
        ActivityType.LISTENING -> "Listening to ${activity.details?.takeIf(String::isNotBlank) ?: name}"
        ActivityType.WATCHING -> "Watching $name"
        ActivityType.COMPETING -> "Competing in $name"
        ActivityType.HANG -> "Hanging out in $name"
        else -> null
    }
}
