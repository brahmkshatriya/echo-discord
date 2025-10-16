package dev.brahmkshatriya.echo.extension.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Activity (
    @SerialName("application_id")
    val applicationId: String? = null,

    val name: String? = null,
    val platform: String? = null,
    val type: Int? = null,

    @SerialName("status_display_type")
    val statusDisplayType: Int? = null,

    val details: String? = null,

    @SerialName("details_url")
    val detailsURL: String? = null,

    val state: String? = null,

    @SerialName("state_url")
    val stateURL: String? = null,

    val assets: Assets? = null,
    val timestamps: Timestamps? = null,
    val buttons: List<Button>? = null
) {

    @Serializable
    data class Assets(
        @SerialName("large_text")
        val largeText: String? = null,

        @SerialName("large_image")
        val largeImage: String? = null,

        @SerialName("large_url")
        val largeUrl: String? = null,

        @SerialName("small_image")
        val smallImage: String? = null,

        @SerialName("small_url")
        val smallUrl: String? = null,

        @SerialName("small_text")
        val smallText: String? = null,
    )

    @Serializable
    data class Button(
        val label: String? = null,
        val url: String? = null,
    )

    @Serializable
    data class Timestamps(
        val start: Long? = null,
        val end: Long? = null,
    )
}