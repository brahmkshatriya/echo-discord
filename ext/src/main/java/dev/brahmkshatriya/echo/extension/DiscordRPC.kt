package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.ShareClient
import dev.brahmkshatriya.echo.common.clients.TrackerClient
import dev.brahmkshatriya.echo.common.helpers.ClientException
import dev.brahmkshatriya.echo.common.helpers.WebViewRequest
import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.NetworkRequest
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.models.Playlist
import dev.brahmkshatriya.echo.common.models.Radio
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.TrackDetails
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.common.providers.MusicExtensionsProvider
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingList
import dev.brahmkshatriya.echo.common.settings.SettingMultipleChoice
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.models.Activity
import dev.brahmkshatriya.echo.extension.models.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.time.Duration.Companion.minutes

open class DiscordRPC : ExtensionClient, LoginClient.WebView, TrackerClient,
    MusicExtensionsProvider {
    companion object {
        private const val APP_ID = "1135077904435396718"
        private const val APP_NAME = "Echo"
        private const val APP_URL = "https://github.com/brahmkshatriya/echo"
        private const val PLAY_IMG = "https://files.catbox.moe/7ajka9.gif"
        private const val PAUSE_IMG = "https://files.catbox.moe/8hopuj.png"
        private const val EMPTY_IMG = "https://files.catbox.moe/w9wb39.png"
    }

    open val uploader = ImageUploader()
    open val filesDir = File("discord")
    open val platform = "desktop"

    private lateinit var setting: Settings
    override fun setSettings(settings: Settings) {
        setting = settings
    }

    private val activityType
        get() = setting.getString("ActivityType") ?: Type.Listening.name

    private val activityNameType
        get() = setting.getString("ActivityName") ?: "a_echo"

    private val activityStatusType
        get() = setting.getString("ActivityStatusType") ?: "b_artist"

    private val showEchoIcon
        get() = setting.getBoolean("ShowEchoIcon") ?: true

    private val showButtons
        get() = setting.getStringSet("SelectedButtons") ?: setOf("c_profile", "d_try_echo")

    private val useMusicUrl
        get() = setting.getBoolean("UseMusicUrl") ?: false

    private val disableClients
        get() = setting.getStringSet("Disable") ?: emptySet()

    override suspend fun getSettingItems() = listOf(
        SettingCategory(
            "Looks",
            "looks",
            listOf(
                SettingList(
                    "Activity Type",
                    "ActivityType",
                    "How the RPC activity title will be shown",
                    Type.entries.map { it.title },
                    Type.entries.map { it.name },
                    0
                ),
                SettingList(
                    "Activity Name",
                    "ActivityName",
                    "Name of the Activity in \"Listening to [...]\"",
                    listOf(
                        APP_NAME,
                        "[Extension Name]",
                        "[Album/Playlist Name]",
                    ),
                    listOf("a_echo", "b_extension", "c_context"),
                    0
                ),
                SettingList(
                    "Status Display Type",
                    "ActivityStatusType",
                    "How the short status will be displayed",
                    listOf(
                        "Show Activity Name",
                        "Show Artist Name",
                        "Show Track Name",
                    ),
                    listOf("a_activity", "b_artist", "c_track"),
                    1
                ),
                SettingSwitch(
                    "Show Echo Icon",
                    "ShowEchoIcon",
                    "Show Small Echo Icon on the RPC. Also acts as a visual indicator for play/pause state.",
                    true
                ),
                SettingMultipleChoice(
                    "RPC Buttons",
                    "SelectedButtons",
                    "The buttons to show on the RPC. (Only 2 will be visible)",
                    listOf(
                        "Play/Play on $APP_NAME",
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
                    setOf(1, 3)
                )
            )
        ),
        SettingCategory(
            "Behavior",
            "Behavior",
            listOf(
                SettingSwitch(
                    "Use Music Url instead of Echo URI",
                    "UseMusicUrl",
                    "The Play Button will allow opening music's actual link on extension's site. This will disable people to directly play music on Echo when clicked on play button & instead open the site.",
                    false
                ),
                SettingMultipleChoice(
                    "Disable for Extensions",
                    "Disable",
                    "Disable RPC for these extensions.",
                    extensionsMap.values.map { it.name },
                    extensionsMap.keys.toList(),
                )
            )
        )
    )

    override val webViewRequest = object : WebViewRequest.Evaluate<List<User>> {
        override val initialUrl = "https://discord.com/login".toGetRequest()
        override val javascriptToEvaluateOnPageStart = """
            function() { 
                window.LOCAL = localStorage;
                localStorage.removeItem = function(key) { return true; };
            }""".trimIndent()

        override val stopUrlRegex = "https://discord\\.com/app".toRegex()
        override val javascriptToEvaluate = "function() { return window.LOCAL.getItem('token'); }"

        override suspend fun onStop(
            url: NetworkRequest, data: String?,
        ): List<User>? {
            val token = data.orEmpty().trim('"')
            if (token.length <= 69) throw Exception("Token not found, token size: ${token.length}")
            val rpc = RPC(token, filesDir.resolve("tmp"))
            val userDetails = rpc.getUserDetails()
            val id = userDetails["id"]!!.jsonPrimitive.content
            val avatar = userDetails["avatar"]?.jsonPrimitive?.content?.let {
                "https://cdn.discordapp.com/avatars/$id/$it".toImageHolder()
            }
            rpc.stop()
            return listOf(
                User(
                    id = id,
                    name = userDetails["global_name"]!!.jsonPrimitive.content,
                    cover = avatar,
                    subtitle = "@${userDetails["username"]!!.jsonPrimitive.content}",
                    extras = mapOf("token" to token)
                )
            )
        }
    }

    private var rpc: RPC? = null
    override fun setLoginUser(user: User?) {
        rpc?.stop()
        if (user == null) {
            rpc = null
            return
        }
        val unauthorized = ClientException.Unauthorized(user.id)
        val token = user.extras["token"] ?: throw unauthorized
        rpc = RPC(token, filesDir, unauthorized)
    }

    override suspend fun getCurrentUser(): User? {
        val userDetails = rpc?.getUserDetails() ?: return null
        val id = userDetails["id"]!!.jsonPrimitive.content
        val avatar = userDetails["avatar"]?.jsonPrimitive?.content?.let {
            "https://cdn.discordapp.com/avatars/$id/$it".toImageHolder()
        }
        return User(
            id = id,
            name = userDetails["global_name"]!!.jsonPrimitive.content,
            cover = avatar,
            subtitle = "@${userDetails["username"]!!.jsonPrimitive.content}",
        )
    }

    suspend fun ImageHolder.discordUri() = uploader.getImageUrl(this)

    private suspend fun sendRpc(details: TrackDetails, isPlaying: Boolean) {
        val (extensionId, track, context) = details
        val rpc = rpc ?: throw ClientException.LoginRequired()
        if (extensionId in disableClients) return
        val artists = track.artists.joinToString(", ") { it.name }
        val uri = Activity.Button("Play on $APP_NAME", getPlayerUrl(extensionId, track))
        val buttons = showButtons.take(2)
            .mapNotNull { buttonId ->
                when (buttonId) {
                    "a_play" -> uri.takeIf { !useMusicUrl }
                        ?: getSharableUrl(extensionId, track)?.let { Activity.Button("Play", it) }
                        ?: uri

                    "b_artist" -> track.artists.firstOrNull()?.run {
                        getSharableUrl(extensionId, this)?.let { Activity.Button(name, it) }
                    }

                    "c_profile" -> getUserData(extensionId)?.let {
                        Activity.Button("Profile", it.second)
                    }

                    "d_try_echo" -> Activity.Button("Try $APP_NAME", APP_URL)
                    else -> null
                }
            }
            .filter { button ->
                val url = button?.url
                url != null && (url.startsWith("http://") || url.startsWith("https://"))
            }
            .takeIf { it.isNotEmpty() }
        val startTimestamp = System.currentTimeMillis() - details.currentPosition
        val endTimeStamp = (details.totalDuration ?: track.duration)?.let {
            startTimestamp + it - details.currentPosition
        }
        rpc.newActivity(
            Activity(
                applicationId = APP_ID,
                name = when (activityNameType) {
                    "b_extension" -> extensionsMap[extensionId]?.name ?: extensionId
                    "c_context" -> context?.title ?: track.album?.title ?: APP_NAME
                    else -> APP_NAME
                },
                platform = platform,
                type = Type.valueOf(activityType).value,
                statusDisplayType = when (activityStatusType) {
                    "c_track" -> 2
                    "b_artist" -> 1
                    else -> 0
                },
                details = track.title,
                state = artists,
                assets = Activity.Assets(
                    largeImage = track.cover?.discordUri() ?: EMPTY_IMG,
                    smallImage = if (showEchoIcon) if (isPlaying) PLAY_IMG else PAUSE_IMG else null,
                    smallText = APP_NAME,
                    smallUrl = APP_URL
                ),
                timestamps = if (isPlaying) Activity.Timestamps(startTimestamp, endTimeStamp)
                else Activity.Timestamps(System.currentTimeMillis()),
                buttons = buttons,
            )
        )
    }

    override suspend fun onTrackChanged(details: TrackDetails?) {}

    val scope = CoroutineScope(Dispatchers.IO)
    var job: Job? = null
    override suspend fun onPlayingStateChanged(details: TrackDetails?, isPlaying: Boolean) {
        job?.cancel()
        if (details != null) {
            sendRpc(details, isPlaying)
            if (!isPlaying) job = scope.launch {
                delay(1.minutes)
                runCatching { rpc?.clear() }
            }
        } else rpc?.clear()
    }

    private suspend
    fun getSharableUrl(clientId: String, item: EchoMediaItem): String? {
        val client = extensionsMap[clientId]?.instance?.value as? ShareClient ?: return null
        return runCatching {
            val url = client.onShare(item)
            if (url.startsWith("http")) url else null
        }.getOrNull()
    }

    private fun getPlayerUrl(clientId: String, mediaItem: EchoMediaItem): String {
        val type = when (mediaItem) {
            is Artist -> "artist"
            is Album -> "album"
            is Playlist -> "playlist"
            is Radio -> "radio"
            is Track -> "track"
        }
        return "https://echo.brahmkshatriya.dev/share/$clientId/$type/${mediaItem.id}"
    }

    override
    val requiredMusicExtensions: List<String> = listOf()

    private
    val extensionsMap = mutableMapOf<String, MusicExtension>()
    override fun setMusicExtensions(extensions: List<MusicExtension>) {
        extensions.forEach { extensionsMap[it.id] = it }
    }

    private suspend
    fun getUserData(extensionId: String) = runCatching {
        val client = extensionsMap[extensionId]?.instance?.value()?.getOrNull()
        if (client is LoginClient && client is ShareClient) {
            val user = client.getCurrentUser() ?: return@runCatching null
            val link = user.id
            user to link
        } else null
    }.getOrNull()
}
