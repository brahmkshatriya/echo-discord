package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Res(
    val t: String?,
    val s: Int?,
    val op: Int,
    val d: JsonElement
)