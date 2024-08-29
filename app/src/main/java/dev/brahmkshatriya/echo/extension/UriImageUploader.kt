package dev.brahmkshatriya.echo.extension

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class UriImageUploader(
    private val app: Application,
    client: OkHttpClient,
    json: Json
) : ImageUploader(client, json) {

    override suspend fun getByteArray(uri: String) = withContext(Dispatchers.Main) {
        runCatching { app.contentResolver.openInputStream(Uri.parse(uri))!!.readBytes() }
    }.getOrNull()
}