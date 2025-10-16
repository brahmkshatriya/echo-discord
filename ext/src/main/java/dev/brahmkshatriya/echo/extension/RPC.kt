package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.extension.TokenManager.Companion.CLIENT_ID
import dev.brahmkshatriya.echo.extension.TokenManager.Companion.toRequestBody
import dev.brahmkshatriya.echo.extension.models.Activity
import dev.brahmkshatriya.echo.extension.models.Session
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class RPC(
    val authToken: String,
    filesDir: File,
    ifUnauthorized: Throwable? = null,
) {
    val client = OkHttpClient()
    val tokenManager = TokenManager(authToken, filesDir, ifUnauthorized) {
        val res = client.newCall(
            Request.Builder()
                .url("https://discord.com/api/v10/users/@me")
                .header("Authorization", "Bearer $it")
                .head()
                .build()
        ).await()
        if (!res.isSuccessful) throw IllegalStateException("Invalid token")
    }

    var activityToken: String? = null

    suspend fun deleteSession() {
        val activity = activityToken ?: return
        val auth = tokenManager.getToken()
        val url = Request.Builder()
            .url("https://discord.com/api/v10/users/@me/headless-sessions/delete")
            .header("Authorization", "Bearer $auth")
            .post(
                buildJsonObject {
                    put("token", activity)
                }.toRequestBody()
            )
            .build()
        val resp = client.newCall(url).await()
        if (!resp.isSuccessful) {
            throw IllegalStateException("Failed to delete session: ${resp.code} ${resp.body.string()}")
        }
        activityToken = null
    }

    suspend fun getUserDetails(): JsonObject {
        val request = Request.Builder()
            .url("https://discord.com/api/v9/oauth2/authorize?client_id=$CLIENT_ID")
            .header("Authorization", authToken)
            .build()
        val response = client.newCall(request).await().body.string()
        return Json.decodeFromString<JsonObject>(response)["user"]!!.jsonObject
    }

    suspend fun newActivity(activity: Activity?) {
        if (activity == null) return deleteSession()
        post(
            Session(
                activities = listOf(activity),
                token = activityToken
            )
        )
    }

    private suspend fun post(session: Session) {
        val auth = tokenManager.getToken()
        val url = Request.Builder()
            .url("https://discord.com/api/v10/users/@me/headless-sessions")
            .header("Authorization", "Bearer $auth")
            .post(
                Json.encodeToString<Session>(session)
                    .toRequestBody("application/json".toMediaType())
            )
            .build()
        val resp = client.newCall(url).await()
        if (!resp.isSuccessful) {
            throw IllegalStateException("Failed to create session: ${resp.code} ${resp.body.string()}")
        }
        val responseBody = resp.body.string()
        val responseJson = Json.decodeFromString<JsonObject>(responseBody)
        activityToken = responseJson["token"]!!.jsonPrimitive.content
    }

    fun stop() {
        tokenManager.clear()
    }

    suspend fun clear() {
        newActivity(null)
    }
}