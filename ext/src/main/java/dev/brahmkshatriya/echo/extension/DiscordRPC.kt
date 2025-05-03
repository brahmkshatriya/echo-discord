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
import dev.brahmkshatriya.echo.common.models.Message
import dev.brahmkshatriya.echo.common.models.Request
import dev.brahmkshatriya.echo.common.models.TrackDetails
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.providers.MessageFlowProvider
import dev.brahmkshatriya.echo.common.providers.MusicExtensionsProvider
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingMultipleChoice
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.models.ImageLink
import dev.brahmkshatriya.echo.extension.models.Link
import dev.brahmkshatriya.echo.extension.models.Type
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit.SECONDS

open class DiscordRPC : ExtensionClient, LoginClient.WebView.Evaluate, TrackerClient,
    MusicExtensionsProvider, MessageFlowProvider {

    val json = Json {
        encodeDefaults = true
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    val client = OkHttpClient.Builder()
        .connectTimeout(10, SECONDS)
        .readTimeout(10, SECONDS)
        .writeTimeout(10, SECONDS)
        .build()

    open val uploader = ImageUploader(client, json)

    private val applicationId = "1135077904435396718"
    private val appName = "Echo"
    private val appUrl = "https://github.com/brahmkshatriya/echo"

    override val settingItems: List<Setting>
        get() = listOf(
            SettingCategory(
                "Looks",
                "looks",
                listOf(
                    SettingMultipleChoice(
                        "Activity Type",
                        "activityType",
                        "How the RPC activity title will be shown",
                        Type.entries.map { it.title },
                        Type.entries.map { it.name },
                    ),
                    SettingMultipleChoice(
                        "Activity Name",
                        "activityName",
                        "Name of the Activity in \"Listening to [...]\"",
                        listOf(appName, "[Extension Name]", "[Album/Playlist Name]", "[Song Name]", "[Artist Name]"),
                        listOf("a_echo", "b_extension", "c_context", "d_track", "e_name"),
                        setOf(0)
                    ),
                    SettingSwitch(
                        "Show Echo Icon",
                        "showEchoIcon",
                        "Show Small Echo Icon on the RPC.",
                        true
                    ),
                    SettingMultipleChoice(
                        "RPC Buttons",
                        "selectedButtons",
                        "The buttons to show on the RPC. (Only 2 will be visible)",
                        listOf(
                            "Play/Play on $appName",
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
                        setOf(0, 1)
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
                        extensionsMap.values.map { it.name },
                        extensionsMap.keys.toList(),
                    )
                )
            )
        )

    private val activityType
        get() = setting.getStringSet("activityType")?.firstOrNull() ?: Type.Listening.name

    private val activityNameType
        get() = setting.getStringSet("activityName")?.firstOrNull() ?: "a_echo"

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
    override val loginWebViewStopUrlRegex = "https://discord\\.com/app".toRegex()

    override suspend fun getCurrentUser() = rpc?.user?.value?.run {
        User(id, username, userAvatar().toImageHolder())
    }

    private fun getRPC(token: String) =
        RPC(client, json, token, applicationId, uploader, messageFlow)

    override suspend fun onLoginWebviewStop(url: String, data: Map<String, String>): List<User> {
        val result = data.values.first()
        if (result.isBlank()) throw Exception("No token found")
        val token = result.trim('"')
        val rpc = getRPC(token)
        val user =
            runCatching { rpc.user.first { it != null } }.getOrNull()
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

    private val appIconImage =
        "mp:app-icons/1135077904435396718/7ac162cf125e5e5e314a5e240726da41.png".toImageHolder()

    private suspend fun sendRpc(details: TrackDetails) {
        val (extensionId, track, context) = details
        val rpc = rpc ?: throw ClientException.LoginRequired()
        if (extensionId in disableClients) return

        rpc.send(invisibility) {
            type = Type.valueOf(activityType)

            activityName = when (activityNameType) {
                "a_echo" -> appName
                "b_extension" -> extensionsMap[extensionId]?.name ?: extensionId
                "c_context" -> context?.title ?: track.album?.title ?: track.title
                "d_track" -> track.title
                "e_name" -> track.artists.joinToString(", ") { it.name }.ifEmpty { track.title }
                else -> appName
            }

            val artists = track.artists.joinToString(", ") { it.name }
            state = artists
            detail = track.title
            startTimestamp = System.currentTimeMillis()
            endTimeStamp = track.duration?.let { startTimestamp!! + it }
            largeImage = track.cover?.let { ImageLink(track.album?.title ?: track.title, it) }
            smallImage = ImageLink(appName, appIconImage).takeIf { showEchoIcon }

            val item = track.toMediaItem()
            val uri = Link("Play on $appName", getPlayerUrl(extensionId, item))
            val playLink = uri.takeIf { !useMusicUrl }
                ?: getSharableUrl(extensionId, item)?.let { Link("Play", it) }
                ?: uri
            buttons = showButtons.mapNotNull { buttonId ->
                when (buttonId) {
                    "a_play" -> playLink
                    "b_artist" -> track.artists.firstOrNull()?.run {
                        getSharableUrl(extensionId, toMediaItem())?.let { Link(name, it) }
                    }

                    "c_profile" -> getUserData(extensionId)?.let { Link("Profile", it.second) }
                    "d_try_echo" -> Link("Try $appName", appUrl)
                    else -> null
                }
            }
        }
    }

    private val pauseWaitTime = 10000L // if the track isn't played in 10sec, show pause status
    private var timer = Timer()
    override suspend fun onPlayingStateChanged(details: TrackDetails?, isPlaying: Boolean) {
        if (details == null) rpc?.sendDefaultPresence(invisibility)
        else if (isPlaying) {
            timer.cancel()
            sendRpc(details)
        } else {
            timer = Timer()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    rpc?.sendDefaultPresence(invisibility)
                }
            }, pauseWaitTime)
        }
    }

    private suspend fun getSharableUrl(clientId: String, item: EchoMediaItem): String? {
        val client = extensionsMap[clientId] as? ShareClient ?: return null
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

    override val requiredMusicExtensions: List<String> = listOf()
    private val extensionsMap = mutableMapOf<String, MusicExtension>()
    override fun setMusicExtensions(extensions: List<MusicExtension>) {
        extensions.forEach { extensionsMap[it.id] = it }
    }

    private suspend fun getUserData(extensionId: String) = runCatching {
        val client = extensionsMap[extensionId]?.instance?.value()?.getOrNull()
        if (client is LoginClient && client is ShareClient) {
            val user = client.getCurrentUser() ?: return@runCatching null
            val link = client.onShare(user.toMediaItem())
            user to link
        } else null
    }.getOrNull()

    private lateinit var messageFlow: MutableSharedFlow<Message>
    override fun setMessageFlow(messageFlow: MutableSharedFlow<Message>) {
        this.messageFlow = messageFlow
    }
}