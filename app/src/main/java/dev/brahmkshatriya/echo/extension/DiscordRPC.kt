package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.TrackerClient
import dev.brahmkshatriya.echo.common.exceptions.LoginRequiredException
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.EchoMediaItem.Companion.toMediaItem
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Request
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.models.Link
import dev.brahmkshatriya.echo.extension.models.Type
import kotlinx.coroutines.flow.first

class DiscordRPC : ExtensionClient, LoginClient.WebView.Evaluate, TrackerClient {

    private val applicationId = "1135077904435396718"

    override suspend fun onExtensionSelected() {}

    override val settingItems: List<Setting> = listOf(
        SettingCategory(
            "Behavior",
            "behavior",
            listOf(
                SettingSwitch(
                    "Stay Invisible",
                    "invisible",
                    "Stay invisible when you are not actually online. If this is off, you will become \"idle\" on discord.",
                    false
                ),
                SettingSwitch(
                    "Show Album/Playlist Name as Activity",
                    "showContext",
                    "\"Listening to [Album/Playlist Name]\", instead of the current Song Name.",
                    true
                )
            )
        ),
        SettingCategory(
            "Looks",
            "looks",
            listOf(
                SettingSwitch(
                    "Show as Playing Echo",
                    "typePlaying",
                    "Enabling this will show Remaining/Elapsed Time for PC Users too.",
                    false
                ),
                SettingSwitch(
                    "Always show Elapsed Time",
                    "showElapsedTime",
                    "Show Elapsed Time instead of Remaining Time.",
                    true
                ),
                SettingSwitch(
                    "Show Echo Icon",
                    "showEchoIcon",
                    "Show Small Echo Icon on the RPC.",
                    true
                ),
                SettingList(
                    "Buttons",
                    "buttons",
                    "Select the which buttons to show on the RPC",
                    listOf(
                        "None",
                        "Play",
                        "Play & Echo"
                    ),
                    listOf(
                        "none",
                        "play",
                        "play_echo"
                    ),
                    2
                )
            )
        )
    )

    private val typePlaying
        get() = setting.getBoolean("typePlaying") ?: false

    private val showContext
        get() = setting.getBoolean("showContext") ?: true

    private val showElapsedTime
        get() = setting.getBoolean("showElapsedTime") ?: true

    private val showEchoIcon
        get() = setting.getBoolean("showEchoIcon") ?: true

    private val showButtons
        get() = setting.getString("buttons") ?: "play_echo"

    private val invisibility
        get() = setting.getBoolean("invisible") ?: false

    private lateinit var setting: Settings
    override fun setSettings(settings: Settings) {
        setting = settings
    }

    override val javascriptToEvaluate = """(function() {
    return (webpackChunkdiscord_app.push([[''],{},e=>{m=[];for(let c in e.c)m.push(e.c[c])}]),m).find(m=>m?.exports?.default?.getToken!==void 0).exports.default.getToken();
})()"""

    override val loginWebViewInitialUrl = Request("https://discord.com/login")
    override val loginWebViewStopUrlRegex = "https://discord\\.com/channels/@me".toRegex()

    override suspend fun onLoginWebviewStop(url: String, data: String): List<User> {
        if (data.isBlank()) throw Exception("Login Failed")
        val token = data.trim('"')
        val rpc = RPC(token, applicationId)
        val user = rpc.user.first { it != null }
        rpc.stop()
        return listOf(
            User(token, user?.username ?: "Discord User", user?.userAvatar()?.toImageHolder())
        )
    }

    private var rpc: RPC? = null
    override suspend fun onSetLoginUser(user: User?) {
        rpc?.stop()
        val token = user?.id ?: return
        rpc = RPC(token, applicationId)
    }

    private fun loginRequiredException() =
        LoginRequiredException("discord-rpc", "Discord", ExtensionType.TRACKER)

    override suspend fun onMarkAsPlayed(clientId: String, context: EchoMediaItem?, track: Track) {}

    override suspend fun onStartedPlaying(clientId: String, context: EchoMediaItem?, track: Track) {
        val rpc = rpc ?: throw loginRequiredException()
        rpc.send(invisibility) {

            type = if (typePlaying) Type.PLAYING else Type.LISTENING

            activityName = if (typePlaying) "Echo"
            else if(showContext) context?.title ?: track.title
            else track.title

            val artists = track.artists.joinToString(", ") { it.name }
            state = artists
            details = track.title

            startTimestamp = System.currentTimeMillis()
            endTimeStamp =
                track.duration?.let { startTimestamp!! + it }.takeIf { !showElapsedTime }

            largeImage = when (val cover = track.cover) {
                is ImageHolder.UrlRequestImageHolder -> {
                    val url = cover.request.url
                    Link(track.album?.title ?: track.title, url)
                }

                else -> null
            }

            smallImage = Link(
                "Echo",
                "mp:app-icons/1135077904435396718/7ac162cf125e5e5e314a5e240726da41.png"
            ).takeIf { showEchoIcon }


            val playLink =
                Link("Play", getPlayerUrl(clientId, context ?: track.toMediaItem()))
            buttons = when (showButtons) {
                "play" -> listOf(playLink)
                "play_echo" -> listOf(
                    playLink,
                    Link("Listen on Echo", "https://github.com/brahmkshatriya/echo")
                )

                else -> emptyList()
            }
        }
    }

    private fun getPlayerUrl(clientId: String, mediaItem: EchoMediaItem): String {
        val type = when (mediaItem) {
            is EchoMediaItem.TrackItem -> "track"
            is EchoMediaItem.Lists.AlbumItem -> "album"
            is EchoMediaItem.Lists.PlaylistItem -> "playlist"
            is EchoMediaItem.Profile.ArtistItem -> "artist"
            is EchoMediaItem.Profile.UserItem -> "user"
            else -> ""
        }
        return "echo://music/$clientId/$type/${mediaItem.id}"
    }

    override suspend fun onStoppedPlaying(clientId: String, context: EchoMediaItem?, track: Track) {
        val rpc = rpc ?: throw loginRequiredException()
        rpc.sendDefaultPresence(invisibility)
    }

}