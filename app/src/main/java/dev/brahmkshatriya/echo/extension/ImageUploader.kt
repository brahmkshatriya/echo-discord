package dev.brahmkshatriya.echo.extension

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class ImageUploader(
    private val client: OkHttpClient, private val json: Json
) {

    @SuppressLint("PrivateApi")
    private fun getApplication(): Application {
        return Class.forName("android.app.ActivityThread").getMethod("currentApplication")
            .invoke(null) as Application
    }

    @Serializable
    data class ApiResponse(
        val status: String, val data: Data
    )

    @Serializable
    data class Data(val url: String)

    private val api = "https://tmpfiles.org/api/v1/upload"
    private suspend fun uploadImage(byteArray: ByteArray): String? {
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(
                "file", "image.png", byteArray.toRequestBody("image/*".toMediaType())
            ).build()
        val request = Request.Builder().url(api).post(requestBody).build()
        return runCatching {
            val res = client.newCall(request).await()
            json.decodeFromString<ApiResponse>(res.body.string())
        }.getOrNull()?.data?.url?.replace("https://tmpfiles.org/", "https://tmpfiles.org/dl/")
    }

    suspend fun getImageUrl(image: ImageHolder): String? {
        when (image) {
            is ImageHolder.UrlRequestImageHolder -> {
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

            is ImageHolder.BitmapImageHolder -> {
                val byteArray =
                    runCatching { image.bitmap.toByteArray() }.getOrNull() ?: return null
                return uploadImage(byteArray)
            }

            is ImageHolder.UriImageHolder -> {
                val byteArray = runCatching {
                    getApplication().contentResolver.openInputStream(image.uri)?.readBytes()
                }.getOrNull() ?: return null
                return uploadImage(byteArray)
            }
        }
    }

    private fun Bitmap.toByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
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