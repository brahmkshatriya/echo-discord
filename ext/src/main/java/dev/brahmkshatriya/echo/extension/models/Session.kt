package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val activities: List<Activity>? = null,
    val token: String? = null,
)