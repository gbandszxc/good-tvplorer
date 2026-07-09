package com.goodtvplorer.domain

import com.goodtvplorer.data.FileKind

object FileTypeDetector {
    private val image = setOf("jpg", "jpeg", "png", "webp", "gif")
    private val text = setOf("txt", "log", "md", "json", "xml", "yml", "yaml", "ini", "conf")
    private val audio = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg")
    private val video = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts")

    fun detect(name: String, isDirectory: Boolean): FileKind {
        if (isDirectory) return FileKind.Directory
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in image -> FileKind.Image
            in text -> FileKind.Text
            in audio -> FileKind.Audio
            in video -> FileKind.Video
            else -> FileKind.Other
        }
    }
}
