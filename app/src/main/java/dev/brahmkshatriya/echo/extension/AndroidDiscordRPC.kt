package dev.brahmkshatriya.echo.extension

import android.annotation.SuppressLint
import android.app.Application

class AndroidDiscordRPC : DiscordRPC() {

    @SuppressLint("PrivateApi")
    private fun getApplication(): Application {
        return Class.forName("android.app.ActivityThread").getMethod("currentApplication")
            .invoke(null) as Application
    }

    override val uploader = UriImageUploader(getApplication(), client, json)
}