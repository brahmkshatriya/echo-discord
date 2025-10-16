package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.ImageHolder.Companion.toImageHolder
import dev.brahmkshatriya.echo.common.models.NetworkRequest.Companion.toGetRequest
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.TrackDetails
import dev.brahmkshatriya.echo.common.models.User
import dev.brahmkshatriya.echo.extension.models.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class)
@ExperimentalCoroutinesApi
class ExtensionUnitTest {
    private val extension = DiscordRPC()
    val token = ""
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        extension.setSettings(MockedSettings())
        runBlocking {
            extension.onExtensionSelected()
            extension.setLoginUser(
                User(
                    "burh",
                    "Discord User",
                    extras = mapOf(
                        "token" to token
                    )
                )
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // reset the main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    @Suppress("SameParameterValue")
    private fun testIn(title: String, block: suspend CoroutineScope.() -> Unit) = runBlocking {
        println("\n-- $title --")
        block.invoke(this)
        println("\n")
    }

    @Test
    fun testRPC() = testIn("Testing RPC") {
        println("Starting test")
        val track = Track(
            "1",
            "Track 1",
            Track.Type.Song,
            ImageHolder.NetworkRequestImageHolder(
                "https://i1.sndcdn.com/artworks-eU7CAR7zFWrT0eAq-IQjtQg-t500x500.jpg"
                    .toGetRequest(mapOf("brih" to "brurh")),
                true
            ),
            listOf(Artist("1", "Artist 1")),
            Album("1", "Album 1"),
            duration = 120000
        )
        val details = TrackDetails("", track, null, 30000, 120000)
        extension.onTrackChanged(details)
        extension.onPlayingStateChanged(details, true)
        delay(30000)
        extension.onPlayingStateChanged(details, false)
        delay(10000)
        extension.onPlayingStateChanged(null, false)
    }

    val filesDir = extension.filesDir
    val rpc = RPC(token, filesDir)

    @Test
    fun getUser() = testIn("Token Manager") {
        val userDetails = rpc.getUserDetails()
        val id = userDetails["id"]!!.jsonPrimitive.content
        val avatar = userDetails["avatar"]?.jsonPrimitive?.content?.let {
            "https://cdn.discordapp.com/avatars/$id/$it".toImageHolder()
        }
        val user = User(
            id = id,
            name = userDetails["global_name"]!!.jsonPrimitive.content,
            cover = avatar,
            subtitle = "@${userDetails["username"]!!.jsonPrimitive.content}",
            extras = mapOf("token" to token)
        )
        println(user)
    }

    @Test
    fun session() = testIn("Session") {
        val activity = Activity(
            applicationId = "802958833214423081",
            name = "Echo",
            platform = "desktop",
            type = 2,
            statusDisplayType = 1,
            details = "BOLD TALK",
            detailsURL = "https://soundcloud.com/nimda_uk/nimda-bold-talk",
            state = "NIMDA",
            stateURL = "https://soundcloud.com/nimda_uk",
            assets = Activity.Assets(
                largeImage = "https://i1.sndcdn.com/artworks-eAX7cRXyuLES7rA8-WHRJhQ-t50x50.png",
                largeUrl = "https://soundcloud.com/nimda_uk/nimda-bold-talk",
            ),
            timestamps = Activity.Timestamps(
                start = System.currentTimeMillis() - 1000L * 30,
                end = System.currentTimeMillis() + 1000L * 120
            ),
        )
        rpc.newActivity(activity)
        println("Posted session")
        println("Session token: ${rpc.activityToken}")
        delay(30.seconds)
        rpc.deleteSession()
        println("Deleted session")
    }

}