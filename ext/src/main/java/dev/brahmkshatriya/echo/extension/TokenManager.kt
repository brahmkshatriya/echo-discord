package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.random.Random

class TokenManager(
    val authToken: String,
    val filesDir: File,
    val ifUnauthorized: Throwable? = null,
    val testAccessToken: suspend (String) -> Unit,
) {
    val client = OkHttpClient()

    val mutex = Mutex()
    var accessToken: String? = null

    fun clear() {
        accessToken = null
        filesDir.resolve("access.txt").delete()
    }

    suspend fun getToken() = mutex.withLock {
        if (accessToken == null) runCatching {
            accessToken = filesDir.resolve("access.txt").readText()
            testAccessToken(accessToken!!)
        }.getOrElse {
            accessToken = createRefreshToken()
            val file = filesDir.resolve("access.txt")
            file.parentFile?.mkdirs()
            file.writeText(accessToken!!)
        }
        accessToken!!
    }

    @Serializable
    data class Response(
        @SerialName("access_token")
        val accessToken: String? = null,
        @SerialName("token_type")
        val tokenType: String? = null,
        @SerialName("expires_in")
        val expiresIn: Long? = null,
        @SerialName("refresh_token")
        val refreshToken: String? = null,
        val scope: String? = null,
    )

    private suspend fun createRefreshToken(): String {
        val codeVerifier = generateCode()
        val challenge = generateCodeChallenge(codeVerifier)
        val httpUrl = HttpUrl.Builder()
            .scheme("https")
            .host("discord.com")
            .addPathSegments("api/v9/oauth2/authorize")
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("scope", scopes.joinToString(" "))
            .addQueryParameter("state", "undefined")
            .build()

        val payloadJson = buildJsonObject {
            put("authorize", true)
        }.toRequestBody()

        val res = client.newCall(
            Request.Builder()
                .url(httpUrl)
                .header("Authorization", authToken)
                .post(payloadJson)
                .build()
        ).await()

        if (!res.isSuccessful) throw ifUnauthorized
            ?: IllegalStateException("Failed to authorize: ${res.code} ${res.body.string()}")

        val location = Json.decodeFromString<JsonObject>(res.body.string())["location"]!!
        val code = location.jsonPrimitive.content.toHttpUrl().queryParameter("code")!!

        val new = Request.Builder()
            .url("https://discord.com/api/v10/oauth2/token")
            .post(
                FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("code", code)
                    .add("code_verifier", codeVerifier)
                    .add("grant_type", "authorization_code")
                    .add("redirect_uri", REDIRECT_URI)
                    .build()
            )
            .build()
        val response = Json.decodeFromString<Response>(
            client.newCall(new).await().body.string()
        )
        return response.accessToken!!
    }

    private val possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private fun randomString(length: Int = 128): String = buildString {
        repeat(length) { append(possible[Random.nextInt(possible.length)]) }
    }

    private fun generateCode() = randomString()

    private fun generateCodeChallenge(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")!!
        val hashed = digest.digest(code.toByteArray(StandardCharsets.UTF_8))
        return Base64.encode(hashed).replace("=", "")
            .replace("+", "-")
            .replace("/", "_")
    }

    companion object {
        const val CLIENT_ID = "503557087041683458"
        const val REDIRECT_URI = "https://login.premid.app"
        val scopes = listOf("identify", "activities.write")

        fun JsonObject.toRequestBody() =
            toString().toRequestBody("application/json".toMediaType())
    }
}