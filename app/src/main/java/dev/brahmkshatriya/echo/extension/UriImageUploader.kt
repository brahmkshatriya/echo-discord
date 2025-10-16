package dev.brahmkshatriya.echo.extension

import android.app.Application
import android.net.Uri
import dev.brahmkshatriya.echo.common.models.ImageHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UriImageUploader(
    private val app: Application,
) : ImageUploader() {

    override suspend fun getImageUrl(image: ImageHolder): String? {
        val byteArray = when (image) {
            is ImageHolder.NetworkRequestImageHolder -> return super.getImageUrl(image)
            is ImageHolder.ResourceIdImageHolder -> withContext(Dispatchers.Main) {
                runCatching { app.resources.openRawResource(image.resId).readBytes() }
            }.getOrNull()

            is ImageHolder.ResourceUriImageHolder -> withContext(Dispatchers.Main) {
                runCatching {
                    app.contentResolver.openInputStream(Uri.parse(image.uri))!!.readBytes()
                }
            }.getOrNull()

            is ImageHolder.HexColorImageHolder -> null
        }
        return byteArray?.let { uploadImage(it) }
    }
}