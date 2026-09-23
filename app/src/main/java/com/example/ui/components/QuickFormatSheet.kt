package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.DownloadFormat
import com.example.service.DownloadManagerHelper
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceCard
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.yausername.youtubedl_android.mapper.VideoInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickFormatSheet(
    url: String,
    videoInfo: VideoInfo?,
    isAnalyzing: Boolean,
    selectedFormat: DownloadFormat,
    onFormatSelected: (DownloadFormat) -> Unit,
    onConfirmDownload: (DownloadFormat) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurfaceElevated,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(TextMuted)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (isAnalyzing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = NeonCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Analyzing video details & audio streams...",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
            } else if (videoInfo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceCard)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!videoInfo.thumbnail.isNullOrBlank()) {
                        AsyncImage(
                            model = videoInfo.thumbnail,
                            contentDescription = videoInfo.title,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = videoInfo.title ?: "Media Stream",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val uploaderName = videoInfo.uploader
                            if (!uploaderName.isNullOrBlank()) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = uploaderName,
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            if (videoInfo.duration > 0) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = DownloadManagerHelper.formatDuration(videoInfo.duration.toLong()),
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "🎵 Audio Extractor Presets",
                color = NeonPurple,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            FormatSelectRow(
                title = DownloadFormat.ULTRA_FAST_AUDIO.displayName,
                subtitle = DownloadFormat.ULTRA_FAST_AUDIO.description,
                extension = "M4A",
                badge = "Fastest",
                isSelected = selectedFormat == DownloadFormat.ULTRA_FAST_AUDIO,
                onClick = { onFormatSelected(DownloadFormat.ULTRA_FAST_AUDIO) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            FormatSelectRow(
                title = DownloadFormat.MP3_320.displayName,
                subtitle = DownloadFormat.MP3_320.description,
                extension = "MP3",
                badge = "Studio HQ",
                isSelected = selectedFormat == DownloadFormat.MP3_320,
                onClick = { onFormatSelected(DownloadFormat.MP3_320) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            FormatSelectRow(
                title = DownloadFormat.MP3_192.displayName,
                subtitle = DownloadFormat.MP3_192.description,
                extension = "MP3",
                badge = "Standard",
                isSelected = selectedFormat == DownloadFormat.MP3_192,
                onClick = { onFormatSelected(DownloadFormat.MP3_192) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "🎬 Video Download Presets",
                color = NeonCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            FormatSelectRow(
                title = DownloadFormat.VIDEO_BEST.displayName,
                subtitle = DownloadFormat.VIDEO_BEST.description,
                extension = "MP4",
                badge = "Best Quality",
                isSelected = selectedFormat == DownloadFormat.VIDEO_BEST,
                onClick = { onFormatSelected(DownloadFormat.VIDEO_BEST) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            FormatSelectRow(
                title = DownloadFormat.VIDEO_1080P.displayName,
                subtitle = DownloadFormat.VIDEO_1080P.description,
                extension = "MP4",
                badge = "1080p FHD",
                isSelected = selectedFormat == DownloadFormat.VIDEO_1080P,
                onClick = { onFormatSelected(DownloadFormat.VIDEO_1080P) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            FormatSelectRow(
                title = DownloadFormat.VIDEO_720P.displayName,
                subtitle = DownloadFormat.VIDEO_720P.description,
                extension = "MP4",
                badge = "720p HD",
                isSelected = selectedFormat == DownloadFormat.VIDEO_720P,
                onClick = { onFormatSelected(DownloadFormat.VIDEO_720P) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            FormatSelectRow(
                title = DownloadFormat.VIDEO_480P.displayName,
                subtitle = DownloadFormat.VIDEO_480P.description,
                extension = "MP4",
                badge = "480p SD",
                isSelected = selectedFormat == DownloadFormat.VIDEO_480P,
                onClick = { onFormatSelected(DownloadFormat.VIDEO_480P) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onConfirmDownload(selectedFormat)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_download_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.horizontalGradient(listOf(NeonCyan, NeonPurple)),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = DarkBackground,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download in ${selectedFormat.extension.uppercase()} (${selectedFormat.displayName})",
                            color = DarkBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatSelectRow(
    title: String,
    subtitle: String,
    extension: String,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) NeonCyan.copy(alpha = 0.12f) else DarkSurfaceCard)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) NeonCyan else DarkSurfaceBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = if (isSelected) NeonCyan else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) NeonCyan else DarkSurfaceBorder)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        color = if (isSelected) DarkBackground else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = NeonCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
