package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Message
import dev.brahmkshatriya.echo.extension.models.Activity
import dev.brahmkshatriya.echo.extension.models.Identity
import dev.brahmkshatriya.echo.extension.models.ImageLink
import dev.brahmkshatriya.echo.extension.models.Link
import dev.brahmkshatriya.echo.extension.models.Presence
import dev.brahmkshatriya.echo.extension.models.Res
import dev.brahmkshatriya.echo.extension.models.Type
import dev.brahmkshatriya.echo.extension.models.User
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.SocketException
import java.net.UnknownHostException

class RPC(
    private val client: OkHttpClient,
    private val json: Json,
    private val token: String,
    private val applicationId: String,
    private val imageUploader: ImageUploader,
    private val messageFlow: MutableSharedFlow<Message>,
) {

    @OptIn(DelicateCoroutinesApi::class)
    private val scope = CoroutineScope(Dispatchers.IO) + CoroutineExceptionHandler { _, t ->
        GlobalScope.launch(Dispatchers.IO) {
            messageFlow.emit(
                Message("Discord RPC: ${t.message}")
            )
        }
    }

    private val creationTime = System.currentTimeMillis()

    private val request = Request.Builder()
        .url("wss://gateway.discord.gg/?encoding=json&v=10")
        .build()

    private val listener = Listener()
    private var webSocket = client.newWebSocket(request, listener)

    var type: Type? = null
    var activityName: String? = null
    var detail: String? = null
    var state: String? = null
    var largeImage: ImageLink? = null
    var smallImage: ImageLink? = null
    var startTimestamp: Long? = null
    var endTimeStamp: Long? = null
    var buttons = listOf<Link>()

    private fun status(invisible: Boolean) = if (invisible) "invisible" else "idle"

    private suspend fun createPresence(invisible: Boolean): String {
        val buttons = buttons.ifEmpty { null }
        return json.encodeToString(Presence.Response(
            3,
            Presence(
                activities = listOf(
                    Activity(
                        name = activityName,
                        state = state,
                        details = detail,
                        type = type?.value,
                        timestamps = if (startTimestamp != null)
                            Activity.Timestamps(startTimestamp, endTimeStamp)
                        else null,
                        assets = Activity.Assets(
                            largeImage = largeImage?.discordUri(),
                            largeText = largeImage?.label,
                            smallImage = smallImage?.discordUri(),
                            smallText = smallImage?.label
                        ),
                        buttons = buttons?.map { it.label },
                        metadata = buttons?.map { it.url }?.let {
                            Activity.Metadata(buttonUrls = it)
                        },
                        applicationId = applicationId,
                    )
                ),
                afk = true,
                since = creationTime,
                status = status(invisible)
            )
        )
        )
    }

    private val assetApi = RPCExternalAsset(applicationId, token, client, json)
    private suspend fun ImageLink.discordUri(): String? {
        val url = imageUploader.getImageUrl(imageHolder) ?: return null
        return assetApi.getDiscordUri(url)
    }

    private fun sendIdentity() {
        val response = Identity.Response(
            op = 2,
            d = Identity(
                token = token,
                properties = Identity.Properties(
                    os = "windows",
                    browser = "Chrome",
                    device = "disco"
                ),
                compress = false,
                intents = 0
            )
        )
        webSocket.send(json.encodeToString(response))
        sendDefaultPresence(true)
    }

    val user = MutableStateFlow<User?>(null)
    suspend fun send(invisible: Boolean, block: suspend RPC.() -> Unit) {
        block.invoke(this@RPC)
        user.first { it != null }
        val presence = createPresence(invisible)
        println("Sending: $presence")
        webSocket.send(presence)
    }

    fun sendDefaultPresence(invisible: Boolean) {
        webSocket.send(
            json.encodeToString(
                Presence.Response(
                    3,
                    Presence(status = status(invisible))
                )
            ).also {
                println("Sending Default Presence: $it")
            }
        )
    }

    fun stop() {
        webSocket.close(4000, "Stop")
        scope.cancel()
    }

    inner class Listener : WebSocketListener() {
        private var seq: Int? = null
        private var heartbeatInterval: Long? = null

        private var previous: Job? = null
        private fun sendHeartBeat() {
            previous?.cancel()
            previous = scope.launch {
                delay(heartbeatInterval!!)
                webSocket.send("{\"op\":1, \"d\":$seq}")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val res = json.decodeFromString<Res>(text)
            println(json.encodeToString(res).take(2000))
            seq = res.s
            when (res.op) {
                10 -> {
                    res.d as JsonObject
                    heartbeatInterval = res.d["heartbeat_interval"]!!.jsonPrimitive.long
                    sendHeartBeat()
                    sendIdentity()
                }

                0 -> if (res.t == "READY") {
                    user.value = json.decodeFromString<User.Response>(text).d.user
                }

                1 -> {
                    if (previous?.isActive == true) previous?.cancel()
                    webSocket.send("{\"op\":1, \"d\":$seq}")
                }

                11 -> sendHeartBeat()
                7 -> webSocket.close(4000, "Reconnect")
                9 -> {
                    sendHeartBeat()
                    sendIdentity()
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (code == 4000) {
                previous?.cancel()
            }
            scope.launch { throw Exception("Discord WebSocket Server Closed : $code $reason") }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            scope.launch {
                if (t is SocketException || t is UnknownHostException) {
                    delay(3000)
                    this@RPC.webSocket = client.newWebSocket(request, Listener())
                } else {
                    throw t
                }
            }
        }
    }
}