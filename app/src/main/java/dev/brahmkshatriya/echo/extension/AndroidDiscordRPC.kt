package dev.brahmkshatriya.echo.extension

import android.annotation.SuppressLint
import android.app.Application

@Suppress("unused")
@SuppressLint("PrivateApi")
class AndroidDiscordRPC : DiscordRPC() {

    private val application by lazy {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication")
            .invoke(null) as Application
    }

    override val uploader = UriImageUploader(application)
    override val platform = "android"
    override val filesDir = application.filesDir.resolve("discord")
}