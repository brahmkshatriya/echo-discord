package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.ImageHolder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


open class ImageUploader(
    private val client: OkHttpClient, private val json: Json
) {
    @Serializable
    data class ApiResponse(
        val status: String, val data: Data
    )

    @Serializable
    data class Data(val url: String)

    private val api = "https://tmpfiles.org/api/v1/upload"
    suspend fun uploadImage(byteArray: ByteArray): String? {
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(
            "file", "image.png", byteArray.toRequestBody("image/*".toMediaType())
        ).build()
        val request = Request.Builder().url(api).post(requestBody).build()
        return runCatching {
            val res = client.newCall(request).await()
            json.decodeFromString<ApiResponse>(res.body.string())
        }.getOrNull()?.data?.url?.replace("https://tmpfiles.org/", "https://tmpfiles.org/dl/")
    }

    open suspend fun getImageUrl(image: ImageHolder): String? {
        when (image) {
            is ImageHolder.NetworkRequestImageHolder -> {
                if (image.request.headers.isEmpty()) return image.request.url
                else {
                    val request = Request.Builder().url(image.request.url)
                    request.headers(image.request.headers.toHeaders())
                    val byteArray = runCatching {
                        client.newCall(request.build()).await().body.bytes()
                    }.getOrNull() ?: return null
                    return uploadImage(byteArray)
                }
            }

            else -> throw IllegalArgumentException("Invalid image holder type")
        }
    }

    private suspend inline fun Call.await(): okhttp3.Response {
        return suspendCoroutine {
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    it.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    it.resume(response)
                }
            })
        }
    }
}