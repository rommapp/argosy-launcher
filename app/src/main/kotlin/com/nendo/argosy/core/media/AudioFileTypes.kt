package com.nendo.argosy.core.media

object AudioFileTypes {
    val EXTENSIONS: Set<String> = setOf("mp3", "ogg", "wav", "flac", "m4a", "aac", "opus", "wma")

    fun isAudioFile(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in EXTENSIONS
}
