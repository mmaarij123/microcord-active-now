package io.github.mmaarij123.activenow

import com.discord.api.activity.Activity
import com.discord.api.activity.ActivityType
import com.discord.api.channel.Channel
import com.discord.api.permission.Permission
import com.discord.api.presence.ClientStatus
import com.discord.api.voice.state.VoiceState
import com.discord.models.domain.ModelUserRelationship
import com.discord.models.presence.Presence
import com.discord.models.user.CoreUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityFeedTest {
    @Test
    fun onlyActiveFriendsAppearAndVoiceAndPresenceShareACard() {
        val users = (1L..5L).associateWith { CoreUser(it, username = "friend-$it") }
        val relationships = users.keys.associateWith {
            if (it == 4L) ModelUserRelationship.TYPE_BLOCKED else ModelUserRelationship.TYPE_FRIEND
        }
        val listening = Presence(ClientStatus.ONLINE, null, listOf(activity(ActivityType.LISTENING, "Spotify", "Song")))
        val playing = Presence(ClientStatus.DND, null, listOf(activity(ActivityType.PLAYING, "Minecraft")))
        val voiceStates = mapOf(
            0L to mapOf(2L to voiceState(2, 0, 10)),
            8L to mapOf(1L to voiceState(1, 8, 20), 2L to voiceState(2, 8, 21)),
        )

        val entries = collectActivities(
            relationships, setOf(5L), mapOf(2L to "Sam"), users,
            mapOf(1L to playing, 2L to listening, 4L to playing, 5L to playing), voiceStates,
        ) { _, channelId ->
            when (channelId) {
                10L -> describeVoice(channel(Channel.DM, 0, null), null, null)
                21L -> describeVoice(channel(Channel.GUILD_VOICE, 8, "Lobby"), Permission.VIEW_CHANNEL, null)
                else -> null
            }
        }

        assertEquals(listOf(2L, 1L), entries.map { it.user.id })
        assertEquals("Sam", entries[0].name)
        assertEquals("In a DM call", entries[0].voice?.summary)
        assertEquals("Listening to Song", entries[0].activity)
        assertNull(entries[1].voice)
        assertEquals("Playing Minecraft", entries[1].activity)
    }

    @Test
    fun onlineIdleAndBusyFriendsAppearWithoutAnActivity() {
        val users = (1L..6L).associateWith { CoreUser(it, username = "friend-$it") }
        val relationships = users.keys.associateWith { ModelUserRelationship.TYPE_FRIEND }
        val presences = mapOf(
            1L to Presence(ClientStatus.ONLINE, null, emptyList()),
            2L to Presence(ClientStatus.IDLE, null, listOf(activity(ActivityType.CUSTOM_STATUS, "Away"))),
            3L to Presence(ClientStatus.DND, null, null),
            4L to Presence(ClientStatus.OFFLINE, null, null),
            5L to Presence(ClientStatus.INVISIBLE, null, null),
        )

        val entries = collectActivities(
            relationships, emptySet(), emptyMap(), users, presences, emptyMap(),
        ) { _, _ -> null }

        assertEquals(listOf(1L, 2L, 3L), entries.map { it.user.id })
        assertEquals(listOf(ClientStatus.ONLINE, ClientStatus.IDLE, ClientStatus.DND), entries.map { it.status })
        entries.forEach { assertNull(it.activity) }
    }

    @Test
    fun unknownPermissionsOrHiddenGuildChannelsDoNotRevealVoice() {
        val voice = channel(Channel.GUILD_VOICE, 8, "Lobby")
        assertNull(describeVoice(voice, null, "Community"))
        assertNull(describeVoice(voice, Permission.CONNECT, "Community"))
        val location = describeVoice(voice, Permission.VIEW_CHANNEL, "Community")
        assertEquals("In voice · Lobby · Community", location?.summary)
        assertEquals("Lobby", location?.channelName)
        assertEquals("Community", location?.serverName)
        assertNull(describeVoice(voice.copy(type = Channel.GUILD_TEXT), Permission.VIEW_CHANNEL, "Community"))
    }

    @Test
    fun knownDmAndGroupCallsHaveTheirOwnLabels() {
        assertEquals("In a DM call", describeVoice(channel(Channel.DM, 0, null), null, null)?.summary)
        assertEquals(
            "In call · Study group",
            describeVoice(channel(Channel.GROUP_DM, 0, "Study group"), null, null)?.summary,
        )
    }

    @Test
    fun profileVoiceActionsUseChannelAndServerIdsAndRespectConnectPermission() {
        val voice = channel(Channel.GUILD_VOICE, 8, "Lobby")
        val visible = requireNotNull(describeVoice(voice, Permission.VIEW_CHANNEL, "Community"))
        assertEquals(voice.id, visible.channelId)
        assertEquals(8L, visible.guildId)
        assertFalse(visible.canJoin)

        val joinable = requireNotNull(describeVoice(voice, Permission.VIEW_CHANNEL or Permission.CONNECT, "Community"))
        assertTrue(joinable.canJoin)
        assertTrue(requireNotNull(describeVoice(channel(Channel.DM, 0, null), null, null)).canJoin)
        assertNull(describeVoice(voice, Permission.CONNECT, "Community"))
    }

    @Test
    fun profilePrefersItsServerAndFallsBackToAnotherVisibleCall() {
        val server = describeVoice(channel(Channel.GUILD_VOICE, 8, "Lobby"), Permission.VIEW_CHANNEL, "Community")
        val dm = describeVoice(channel(Channel.DM, 0, null), null, null)
        val states = mapOf(
            0L to mapOf(2L to voiceState(2, 0, 10)),
            8L to mapOf(2L to voiceState(2, 8, 20)),
        )
        val resolve = { guild: Long, _: Long -> if (guild == 8L) server else dm }

        assertEquals(server, findVoiceLocation(2, 8, states, resolve))
        assertEquals(dm, findVoiceLocation(2, null, states, resolve))
        assertEquals(dm, findVoiceLocation(2, 8, states) { guild, _ -> if (guild == 0L) dm else null })
        assertNull(findVoiceLocation(2, 8, emptyMap(), resolve))
        assertNull(findVoiceLocation(2, 8, states) { _, _ -> null })
    }

    @Test
    fun customStatusesAndOfflinePresencesAreSkipped() {
        val custom = activity(ActivityType.CUSTOM_STATUS, "A custom status")
        val playing = activity(ActivityType.PLAYING, "Minecraft")
        assertNull(describeActivity(Presence(ClientStatus.ONLINE, null, listOf(custom))))
        assertNull(describeActivity(Presence(ClientStatus.INVISIBLE, null, listOf(playing))))
        assertNull(describeActivity(Presence(ClientStatus.OFFLINE, null, listOf(playing))))
        assertEquals("Playing Minecraft", describeActivity(Presence(ClientStatus.IDLE, null, listOf(custom, playing))))
    }

    private fun activity(type: ActivityType, name: String, details: String? = null) = Activity(
        name, type, null, 0, null, null, details, null, null, null,
        null, null, null, null, null, null, null, null,
    )

    private fun voiceState(userId: Long, guildId: Long, channelId: Long) = VoiceState(
        userId, false, null, false, false, false, false, false, guildId, false, null, null, channelId,
    )

    private fun channel(type: Int, guildId: Long, name: String?) = Channel(
        topic = null, type = type, guildId = guildId, name = name, lastMessageId = 0, id = 7,
        ownerId = 0, recipientIds = null, recipients = null, position = 0, permissionOverwrites = null,
        userLimit = 0, bitrate = 0, icon = null, originChannelId = 0, applicationId = 0, nicks = null,
        nsfw = false, parentId = 0, memberListId = null, rateLimitPerUser = 0,
        defaultAutoArchiveDuration = null, rtcRegion = null, flags = null, guildHashes = null,
        availableTags = null, appliedTags = null, threadMetadata = null, messageCount = null,
        memberCount = null, memberIdsPreview = null, member = null,
    )
}
