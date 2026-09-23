package com.example.data

enum class DownloadFormat(
    val formatId: String,
    val displayName: String,
    val description: String,
    val extension: String,
    val isAudioOnly: Boolean,
    val ytDlpFormatSpec: String,
    val extraArgs: List<String> = emptyList()
) {
    ULTRA_FAST_AUDIO(
        formatId = "ultra_fast_audio",
        displayName = "Fast Audio (M4A/AAC)",
        description = "Instant extraction without re-encoding",
        extension = "m4a",
        isAudioOnly = true,
        ytDlpFormatSpec = "ba[ext=m4a]/ba/b",
        extraArgs = listOf("-x", "--audio-format", "m4a")
    ),
    MP3_320(
        formatId = "mp3_320",
        displayName = "MP3 Studio (320 kbps)",
        description = "High fidelity audio for music players",
        extension = "mp3",
        isAudioOnly = true,
        ytDlpFormatSpec = "ba/b",
        extraArgs = listOf("-x", "--audio-format", "mp3", "--audio-quality", "320K")
    ),
    MP3_192(
        formatId = "mp3_192",
        displayName = "MP3 Standard (192 kbps)",
        description = "Standard balance of quality and size",
        extension = "mp3",
        isAudioOnly = true,
        ytDlpFormatSpec = "ba/b",
        extraArgs = listOf("-x", "--audio-format", "mp3", "--audio-quality", "192K")
    ),
    VIDEO_BEST(
        formatId = "video_best",
        displayName = "Best Quality MP4",
        description = "Highest resolution available (up to 4K)",
        extension = "mp4",
        isAudioOnly = false,
        ytDlpFormatSpec = "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b",
        extraArgs = listOf("--merge-output-format", "mp4")
    ),
    VIDEO_1080P(
        formatId = "video_1080p",
        displayName = "1080p Full HD",
        description = "Sharp Full HD video (MP4)",
        extension = "mp4",
        isAudioOnly = false,
        ytDlpFormatSpec = "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[height<=1080][ext=mp4]/bv*[height<=1080]+ba/b",
        extraArgs = listOf("--merge-output-format", "mp4")
    ),
    VIDEO_720P(
        formatId = "video_720p",
        displayName = "720p HD",
        description = "Fast download, great quality",
        extension = "mp4",
        isAudioOnly = false,
        ytDlpFormatSpec = "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b",
        extraArgs = listOf("--merge-output-format", "mp4")
    ),
    VIDEO_480P(
        formatId = "video_480p",
        displayName = "480p SD",
        description = "Low data / small file size",
        extension = "mp4",
        isAudioOnly = false,
        ytDlpFormatSpec = "bv*[height<=480]+ba/b[height<=480]/b",
        extraArgs = listOf("--merge-output-format", "mp4")
    );

    companion object {
        fun fromId(id: String): DownloadFormat {
            return entries.firstOrNull { it.formatId == id } ?: VIDEO_BEST
        }
    }
}
