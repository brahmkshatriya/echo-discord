package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.User
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DiscordRPCTest {

    @Test
    fun `onLoginWebviewStop throws exception for invalid token length`() = runBlocking {
        val discordRPC = DiscordRPC()
        val invalidTokenData = mapOf("key" to "short_token") // length is 11
        
        val exception = assertThrows<Exception> {
            discordRPC.onLoginWebviewStop("some_url", invalidTokenData)
        }
        
        assertEquals("No token found, result size: 11", exception.message)
    }
}
