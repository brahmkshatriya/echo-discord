package dev.brahmkshatriya.echo.extension

import com.lagradost.nicehttp.Requests.Companion.await
import dev.brahmkshatriya.echo.extension.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit.SECONDS

open class RPC(
    private val token: String,
    private val applicationId: String
) {

    private val creationTime = System.currentTimeMillis()

    private val json = Json {
        encodeDefaults = true
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, SECONDS)
        .readTimeout(10, SECONDS)
        .writeTimeout(10, SECONDS)
        .addInterceptor(logging)
        .build()

    private val request = Request.Builder()
        .url("wss://gateway.discord.gg/?encoding=json&v=10")
        .build()

    private var webSocket = client.newWebSocket(request, Listener())

    private var status = "idle"
    var type: Type? = null
    var activityName: String? = null
    var details: String? = null
    var state: String? = null
    var largeImage: Link? = null
    var smallImage: Link? = null
    var startTimestamp: Long? = null
    var endTimeStamp: Long? = null
    var buttons = listOf<Link>()

    private suspend fun createPresence(): String {
        val buttons = buttons.ifEmpty { null }
        return json.encodeToString(Presence.Response(
            3,
            Presence(
                activities = listOf(
                    Activity(
                        name = activityName,
                        state = state,
                        details = details,
                        type = type?.ordinal,
                        timestamps = if (startTimestamp != null)
                            Activity.Timestamps(startTimestamp, endTimeStamp)
                        else null,
                        assets = Activity.Assets(
                            largeImage = largeImage?.url?.discordUrl(),
                            largeText = largeImage?.label,
                            smallImage = smallImage?.url?.discordUrl(),
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
                status = status
            )
        )
        )
    }

    @Serializable
    data class KizzyApi(val id: String)

    private val api = "https://kizzy-api.vercel.app/image?url="
    private suspend fun String.discordUrl(): String? {
        if (startsWith("mp:")) return this
        val request = Request.Builder().url("$api$this").build()
        return runCatching {
            val res = client.newCall(request).await()
            json.decodeFromString<KizzyApi>(res.body.string()).id
        }.getOrNull()
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
        webSocket.send(json.encodeToString(response).also { println("Identity : $it") })
    }

    private val user = MutableStateFlow<User?>(null)
    suspend fun send(block: RPC.() -> Unit) {
        block.invoke(this@RPC)
        user.first { it != null }
        val presence = createPresence()
        println("Sending Presence : $presence")
        webSocket.send(presence)
    }

    fun close() {
        println("Sending Presence : $status")
        webSocket.send(
            json.encodeToString(
                Presence.Response(
                    3,
                    Presence(status = status)
                )
            )
        )
    }

    fun stop() {
        webSocket.close(4000, "Stop")
    }

    inner class Listener : WebSocketListener() {
        private var seq: Int? = null
        private var heartbeatInterval: Long? = null

        private var scope = CoroutineScope(Dispatchers.IO)
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
                7 -> webSocket.close(400, "Reconnect")
                9 -> {
                    sendHeartBeat()
                    sendIdentity()
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            println("Server Closed : $code $reason")
            if (code == 4000) {
                previous?.cancel()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            println("Failure : ${t.message}")
            if (t.message != "Interrupt")
                this@RPC.webSocket = client.newWebSocket(request, Listener())
        }
    }
}