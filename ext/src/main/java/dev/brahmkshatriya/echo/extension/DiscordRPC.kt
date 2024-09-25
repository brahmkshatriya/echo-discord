package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.clients.TrackerClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.EchoMediaItem.Companion.toMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.Request
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.providers.MusicClientsProvider
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingMultipleChoice
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.models.ImageLink
import dev.brahmkshatriya.echo.extension.models.Link
import dev.brahmkshatriya.echo.extension.models.Type
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit.SECONDS

open class DiscordRPC : ExtensionClient, LoginClient.WebView.Evaluate, TrackerClient,
    MusicClientsProvider {

    val json = Json {
        encodeDefaults = true
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
    }

    val client = OkHttpClient.Builder()
        .connectTimeout(10, SECONDS)
        .readTimeout(10, SECONDS)
        .writeTimeout(10, SECONDS)
        .build()

    open val uploader = ImageUploader(client, json)

    private val applicationId = "1135077904435396718"

    override suspend fun onExtensionSelected() {}

    override val settingItems: List<Setting>
        get() = listOf(
            SettingCategory(
                "Looks",
                "looks",
                listOf(
                    SettingSwitch(
                        "Show as \"Playing Echo\"",
                        "typePlaying",
                        "Instead of \"Listening to [...]\", this will also allow to show Remaining/Elapsed Time for PC Users too.",
                        false
                    ),
                    SettingSwitch(
                        "Show Album/Playlist Name as Activity",
                        "showContext",
                        "\"Listening to [Album/Playlist Name]\", instead of the current Song's Name.",
                        false
                    ),
//                    SettingSwitch(
//                        "Show Cover Image",
//                        "showCoverImage",
//                        "Show cover image of the music on the RPC.",
//                        true
//                    ),
                    SettingSwitch(
                        "Show Echo Icon",
                        "showEchoIcon",
                        "Show Small Echo Icon on the RPC.",
                        true
                    ),
//                    SettingSwitch(
//                        "Always show Elapsed Time",
//                        "showElapsedTime",
//                        "Show Elapsed Time instead of Remaining Time.",
//                        true
//                    ),
                    SettingMultipleChoice(
                        "RPC Buttons",
                        "selectedButtons",
                        "The buttons to show on the RPC. (Only 2 will be visible)",
                        listOf(
                            "Play/Play on Echo",
                            "Song Artist",
                            "Profile",
                            "Try Echo"
                        ),
                        listOf(
                            "a_play",
                            "b_artist",
                            "c_profile",
                            "d_try_echo"
                        ),
                        setOf(0, 2)
                    )
                )
            ),
            SettingCategory(
                "Behavior",
                "behavior",
                listOf(
                    SettingSwitch(
                        "Stay Invisible",
                        "invisible",
                        "Stay invisible when you are not actually online. If this is off, you will become \"idle\" on discord when listening to songs on Echo.",
                        false
                    ),
                    SettingSwitch(
                        "Use Music Url instead of Echo URI",
                        "useMusicUrl",
                        "The Play Button will allow opening music's actual link on extension's site. This will disable people to directly play music on Echo when clicked on play button & instead open the site.",
                        false
                    ),
                    SettingMultipleChoice(
                        "Disable for Extensions",
                        "disable",
                        "Disable RPC for these extensions.",
                        clients.values.map { it.name },
                        clients.keys.toList(),
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

    private val showCoverImage
        get() = setting.getBoolean("showCoverImage") ?: true

    private val showEchoIcon
        get() = setting.getBoolean("showEchoIcon") ?: true

    private val showButtons
        get() = setting.getStringSet("selectedButtons") ?: setOf("a_play", "c_profile")

    private val invisibility
        get() = setting.getBoolean("invisible") ?: false

    private val useMusicUrl
        get() = setting.getBoolean("useMusicUrl") ?: false

    private val disableClients
        get() = setting.getStringSet("disable") ?: emptySet()

    private lateinit var setting: Settings
    override fun setSettings(settings: Settings) {
        setting = settings
    }

    override val javascriptToEvaluate = """(function() {
    return (webpackChunkdiscord_app.push([[''],{},e=>{m=[];for(let c in e.c)m.push(e.c[c])}]),m).find(m=>m?.exports?.default?.getToken!==void 0).exports.default.getToken();
})()"""

    override val loginWebViewInitialUrl = Request("https://discord.com/login")
    override val loginWebViewStopUrlRegex = "https://discord\\.com/channels/@me".toRegex()
    override suspend fun getCurrentUser() = rpc?.user?.value?.run {
        User(id, username, userAvatar().toImageHolder())
    }

    private fun getRPC(token: String) = RPC(client, json, token, applicationId, uploader)

    override suspend fun onLoginWebviewStop(url: String, data: String): List<User> {
        if (data.isBlank()) throw Exception("Login Failed")
        val token = data.trim('"')
        val rpc = getRPC(token)
        val user = rpc.user.first { it != null }
        rpc.stop()
        return listOf(
            User(
                token,
                user?.globalName ?: "Discord User",
                user?.userAvatar()?.toImageHolder(),
                user?.username?.let { "@$it" }
            )
        )
    }

    private var rpc: RPC? = null
    override suspend fun onSetLoginUser(user: User?) {
        rpc?.stop()
        val token = user?.id ?: return
        rpc = getRPC(token)
    }

    override suspend fun onMarkAsPlayed(clientId: String, context: EchoMediaItem?, track: Track) {}

    private val appIconImage =
        "mp:app-icons/1135077904435396718/7ac162cf125e5e5e314a5e240726da41.png".toImageHolder()

    override suspend fun onStartedPlaying(clientId: String, context: EchoMediaItem?, track: Track) {
        val rpc = rpc ?: throw ClientException.LoginRequired()
        if (clientId in disableClients) return

        rpc.send(invisibility) {
            type = if (typePlaying) Type.PLAYING else Type.LISTENING

            activityName = if (typePlaying) "Echo"
            else if (showContext) context?.title ?: track.title
            else track.title

            val artists = track.artists.joinToString(", ") { it.name }
            state = artists
            details = track.title

            startTimestamp = System.currentTimeMillis()
            endTimeStamp =
                track.duration?.let { startTimestamp!! + it }.takeIf { !showElapsedTime }

            largeImage = track.cover?.let {
                ImageLink(track.album?.title ?: track.title, it)
            }.takeIf { showCoverImage }
            smallImage = ImageLink("Echo", appIconImage).takeIf { showEchoIcon }

            val item = track.toMediaItem()
            val uri = Link("Play on Echo", getPlayerUrl(clientId, item))
            val playLink = uri.takeIf { !useMusicUrl }
                ?: getSharableUrl(clientId, item)?.let { Link("Play", it) }
                ?: uri
            buttons = showButtons.mapNotNull { buttonId ->
                when (buttonId) {
                    "a_play" -> playLink
                    "b_artist" -> track.artists.firstOrNull()?.run {
                        getSharableUrl(clientId, toMediaItem())?.let { Link(name, it) }
                    }

                    "c_profile" -> getUserData(clientId)?.let { Link("Profile", it.second) }
                    "d_try_echo" -> Link("Try Echo", "https://github.com/brahmkshatriya/echo")
                    else -> null
                }
            }
        }
    }

    private suspend fun getSharableUrl(clientId: String, item: EchoMediaItem): String? {
        val client = clients[clientId] as? ShareClient ?: return null
        return runCatching { client.onShare(item) }.getOrNull()
    }

    private fun getPlayerUrl(clientId: String, mediaItem: EchoMediaItem): String {
        val type = when (mediaItem) {
            is EchoMediaItem.TrackItem -> "track"
            is EchoMediaItem.Lists.AlbumItem -> "album"
            is EchoMediaItem.Lists.PlaylistItem -> "playlist"
            is EchoMediaItem.Profile.ArtistItem -> "artist"
            is EchoMediaItem.Profile.UserItem -> "user"
            is EchoMediaItem.Lists.RadioItem -> "radio"
        }
        return "echo://music/$clientId/$type/${mediaItem.id}"
    }

    override suspend fun onStoppedPlaying(clientId: String, context: EchoMediaItem?, track: Track) {
        val rpc = rpc ?: throw ClientException.LoginRequired()
        rpc.sendDefaultPresence(invisibility)
    }

    override val requiredMusicClients: List<String> = listOf()
    private val clients = mutableMapOf<String, MusicExtension>()
    override fun setMusicExtensions(list: List<MusicExtension>) {
        list.forEach { clients[it.id] = it }
    }

    private suspend fun getUserData(clientId: String) = runCatching {
        val client = clients[clientId]?.instance?.value?.getOrNull()
        if (client is LoginClient && client is ShareClient) {
            val user = client.getCurrentUser() ?: return@runCatching null
            val link = client.onShare(user.toMediaItem())
            user to link
        } else null
    }.getOrNull()

}