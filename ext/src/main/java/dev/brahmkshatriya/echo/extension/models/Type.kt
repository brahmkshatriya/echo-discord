package dev.brahmkshatriya.echo.extension.models

enum class Type(val value: Int, val title: String) {
    Listening(2, "Listening to [...]"),
    Watching(3, "Watching [...]"),
    Playing(0, "Playing [...]"),
}