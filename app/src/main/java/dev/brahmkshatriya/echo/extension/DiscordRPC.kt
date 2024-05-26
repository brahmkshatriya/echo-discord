package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.clients.TrackerClient
import dev.brahmkshatriya.echo.common.exceptions.LoginRequiredException
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.ExtensionType
import dev.brahmkshatriya.echo.common.models.ImageHolder
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

class DiscordRPC : ExtensionClient, LoginClient.WebView.Evaluate, TrackerClient {

    private val applicationId = "1135077904435396718"

    override suspend fun onExtensionSelected() {}

    override val settingItems: List<Setting> = listOf(
        SettingCategory(
            "Looks",
            "looks",
            listOf(
                SettingSwitch(
                    "Show Time",
                    "showTime",
                    "Show Remaining/Elapsed Time on the RPC",
                    true
                ),
                SettingSwitch(
                    "Always show Elapsed Time",
                    "showElapsedTime",
                    "Show Elapsed Time instead of Remaining Time",
                    true
                ),
                SettingSwitch(
                    "Show Echo Icon",
                    "showEchoIcon",
                    "Show Small Echo Icon on the RPC",
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

    private val showTime
        get() = setting.getBoolean("showTime") ?: true

    private val showElapsedTime
        get() = setting.getBoolean("showElapsedTime") ?: true

    private val showEchoIcon
        get() = setting.getBoolean("showEchoIcon") ?: true

    private val showButtons
        get() = setting.getString("buttons") ?: "play_echo"

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
        if(data.isBlank()) throw Exception("Login Failed")
        return listOf(User(data, "Discord User"))
    }

    private var rpc: RPC? = null
    override suspend fun onSetLoginUser(user: User?) {
        val token = user?.id?.trim('"') ?: return
        rpc?.stop()
        println("Setting RPC : $token")
        rpc = RPC(token, applicationId).apply {
//            createWebSocket()
        }
    }

    private fun loginRequiredException() =
        LoginRequiredException("discord-rpc", "Discord", ExtensionType.TRACKER)

    override suspend fun onMarkAsPlayed(clientId: String, context: EchoMediaItem?, track: Track) {}

    override suspend fun onStartedPlaying(clientId: String, context: EchoMediaItem?, track: Track) {
        val rpc = rpc ?: throw loginRequiredException()
        println("Sending  : ${track.title}")
        rpc.send {

            type = if (showTime) Type.PLAYING else Type.LISTENING

            activityName = if (showTime) "Echo" else track.title
            details = track.title.takeIf { showTime }
            state = track.artists.joinToString(", ") { it.name }

            startTimestamp = System.currentTimeMillis()
            endTimeStamp =
                track.duration?.let { startTimestamp!! + it }.takeIf { !showElapsedTime }

            largeImage = when (val cover = track.cover) {
                is ImageHolder.UrlRequestImageHolder -> {
                    val url = cover.request.url
                    Link(track.title, url)
                }

                else -> null
            }

            smallImage = Link(
                "Echo",
                "mp:app-icons/1135077904435396718/7ac162cf125e5e5e314a5e240726da41.png"
            ).takeIf { showEchoIcon }

            buttons = when (showButtons) {
                "play" -> listOf(
                    Link("Play", "echo://music/$clientId/tracks/${track.id}")
                )

                "play_echo" -> listOf(
                    Link("Play", "echo://music/$clientId/tracks/${track.id}"),
                    Link("Listen on Echo", "https://github.com/brahmkshatriya/echo")
                )

                else -> emptyList()
            }
        }
    }

    override suspend fun onStoppedPlaying(clientId: String, context: EchoMediaItem?, track: Track) {
        val rpc = rpc ?: throw loginRequiredException()
        println("Sending : Offline")
        rpc.close()
    }

}