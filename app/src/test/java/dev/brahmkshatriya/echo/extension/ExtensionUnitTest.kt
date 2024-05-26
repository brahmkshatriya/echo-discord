package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Album
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Request.Companion.toRequest
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.models.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(DelicateCoroutinesApi::class)
@ExperimentalCoroutinesApi
class ExtensionUnitTest {
    private val extension = DiscordRPC()
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        extension.setSettings(MockedSettings())
        runBlocking {
            extension.onExtensionSelected()
            extension.onSetLoginUser(
                User(
                    "USER_TOKEN",
                    "Discord User"
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
        val track = Track(
            "1",
            "Track 1",
            listOf(Artist("1", "Artist 1")),
            Album("1", "Album 1"),
            ImageHolder.UrlRequestImageHolder(
                "https://i1.sndcdn.com/artworks-eU7CAR7zFWrT0eAq-IQjtQg-t500x500.jpg".toRequest(),
                true
            )
        )

        suspend fun playStop() {
            extension.onStartedPlaying("", null, track)
            delay(30000)
            extension.onStoppedPlaying("", null, track)
        }
        playStop()
    }
}