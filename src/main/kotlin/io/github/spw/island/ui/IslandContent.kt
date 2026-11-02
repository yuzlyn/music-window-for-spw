package io.github.spw.island.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.LyricsLine
import com.xuncorp.spw.workshop.api.WorkshopApi
import io.github.spw.island.IslandController
import io.github.spw.island.IslandSettings
import io.github.spw.island.ThemeMode
import io.github.spw.island.theme.FluentSpacing
import io.github.spw.island.theme.FluentTheme

private val CardShape = RoundedCornerShape(8.dp)
private val CoverShape = RoundedCornerShape(8.dp)

/**
 * 悬浮窗主体。
 *
 * 展开：左侧方形封面（点击收起），右侧歌词 + 进度，下方居中控制按钮。
 * 收起：只剩封面（点击展开）。
 */
@Composable
fun IslandContent(
    controller: IslandController,
    settings: IslandSettings,
    collapsed: Boolean,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IslandTheme(settings) {
        if (collapsed) {
            val interaction = remember { MutableInteractionSource() }
            val clickable = modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onCoverClick,
                )

            when (settings.collapsedStyle) {
                io.github.spw.island.CollapsedStyle.Cover -> Box(
                    modifier = clickable,
                    contentAlignment = Alignment.Center,
                ) {
                    CoverBlock(
                        controller = controller,
                        settings = settings,
                        size = 84.dp,
                        modal = true,
                        fill = true,
                    )
                }

                io.github.spw.island.CollapsedStyle.Capsule -> CapsuleContent(
                    controller = controller,
                    settings = settings,
                    modifier = clickable,
                )
            }
        } else {
            Surface(
                modifier = modifier.fillMaxSize(),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                ExpandedContent(
                    controller = controller,
                    settings = settings,
                    onCoverClick = onCoverClick,
                )
            }
        }
    }
}

@Composable
private fun ExpandedContent(
    controller: IslandController,
    settings: IslandSettings,
    onCoverClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = FluentSpacing.md, vertical = FluentSpacing.md),
        // 窗口高度可配置，多余空间上下均分，保持垂直居中
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverBlock(
                controller = controller,
                settings = settings,
                size = 84.dp,
                onClick = onCoverClick,
            )
            Spacer(Modifier.width(FluentSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                LyricOrTitle(controller = controller, settings = settings)
                Spacer(Modifier.height(6.dp))
                ProgressRow(controller = controller)
            }
        }

        Spacer(Modifier.height(FluentSpacing.sm))
        ControlsRow(controller = controller)
    }
}

/** 曲库展开态。 */
@Composable
fun IslandListContent(
    controller: IslandController,
    settings: IslandSettings,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IslandTheme(settings) {
        Surface(
            modifier = modifier.fillMaxSize(),
            shape = CardShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = FluentSpacing.md,
                            end = FluentSpacing.md,
                            top = FluentSpacing.sm,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverBlock(
                        controller = controller,
                        settings = settings,
                        size = 40.dp,
                        onClick = onCoverClick,
                    )
                    Spacer(Modifier.width(FluentSpacing.md))
                    Box(modifier = Modifier.weight(1f)) {
                        TrackTitle(controller = controller)
                    }
                }

                IslandLibraryPanel(
                    controller = controller,
                    settings = settings,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 收起态的胶囊：左封面 + 右歌词，宽度更小。 */
@Composable
private fun CapsuleContent(
    controller: IslandController,
    settings: IslandSettings,
    modifier: Modifier = Modifier,
) {
    val line by controller.currentLine.collectAsState()
    val track by controller.track.collectAsState()

    val text = line?.pureMainText?.takeIf { it.isNotBlank() }
        ?: track?.title
        ?: "♪ 等待歌词"

    Surface(
        modifier = modifier,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverBlock(
                controller = controller,
                settings = settings,
                size = 40.dp,
                modal = false,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 歌词为主；鼠标移入显示歌名与艺术家。 */
@Composable
private fun LyricOrTitle(
    controller: IslandController,
    settings: IslandSettings,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val track by controller.track.collectAsState()
    val line by controller.currentLine.collectAsState()

    // 命中区固定成"两行歌词"的高度：内容切换不会改变尺寸，避免指针在边界处反复进出导致闪烁
    val hoverHeight = ((settings.lyricSize + 6) * 2).dp.coerceAtLeast(44.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(hoverHeight)
            .hoverable(interaction),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (hovered || !settings.showLyric) {
            Column {
                Text(
                    text = track?.title ?: "未在播放",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(track?.artist, track?.album)
                        .filterNotNull()
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            LyricLineView(line = line, settings = settings, maxLines = 2)
        }
    }
}

@Composable
private fun TrackTitle(controller: IslandController) {
    val track by controller.track.collectAsState()

    Column {
        Text(
            text = track?.title ?: "未在播放",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOf(track?.artist, track?.album)
                .filterNotNull()
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 进度条 + 时间，占右侧整行。 */
@Composable
private fun ProgressRow(controller: IslandController) {
    val position by controller.positionMs.collectAsState()
    val track by controller.track.collectAsState()

    val duration = (track?.duration ?: 0L).coerceAtLeast(1L)

    Column(modifier = Modifier.fillMaxWidth()) {
        FluentProgressBar(
            progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth(),
            height = 10.dp,
            onSeek = { fraction -> controller.seekToFraction(fraction) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(position),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(track?.duration ?: 0L),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ControlsRow(controller: IslandController) {
    val isPlaying by controller.isPlaying.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
    ) {
        // 播放控件在整窗水平居中
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackButton(onClick = { runCatching { WorkshopApi.playback.previous() } }) {
                IslandIconView(IslandIcon.Previous, MaterialTheme.colorScheme.onSurface, size = 16.dp)
            }

            Spacer(Modifier.width(FluentSpacing.xxl))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp),
                onClick = { controller.togglePlayPause() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    IslandIconView(
                        icon = if (isPlaying) IslandIcon.Pause else IslandIcon.Play,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        size = 14.dp,
                    )
                }
            }

            Spacer(Modifier.width(FluentSpacing.xxl))

            PlaybackButton(onClick = { runCatching { WorkshopApi.playback.next() } }) {
                IslandIconView(IslandIcon.Next, MaterialTheme.colorScheme.onSurface, size = 16.dp)
            }
        }

        // 列表按钮贴右，与内容右边距对齐
        PlaybackButton(
            onClick = { controller.toggleLibrary() },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            IslandIconView(IslandIcon.Library, MaterialTheme.colorScheme.onSurfaceVariant, size = 15.dp)
        }
    }
}

/** Fluent 图标按钮：28dp 命中区、透明底。 */
@Composable
private fun PlaybackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(28.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
internal fun IslandTheme(settings: IslandSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.System -> io.github.spw.island.platform.SystemTheme.isDark()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    FluentTheme(dark = dark, accent = settings.accent, content = content)
}

@Composable
private fun CoverBlock(
    controller: IslandController,
    settings: IslandSettings,
    size: Dp,
    modal: Boolean = false,
    fill: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val cover by controller.cover.collectAsState()
    val track by controller.track.collectAsState()

    if (!settings.showCover) return

    val shape: Shape = if (modal) CircleShape else CoverShape
    val interaction = remember { MutableInteractionSource() }
    val modifier = if (onClick != null) {
        // indication = null：去掉方形涟漪/遮罩，同时裁到封面形状内
        Modifier
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
    } else {
        Modifier
    }

    Box(modifier = modifier) {
        CoverArt(
            cover = cover,
            title = track?.title.orEmpty(),
            size = size,
            shape = shape,
            fill = fill,
        )
    }
}

@Composable
private fun CoverArt(
    cover: ImageBitmap?,
    title: String,
    size: Dp,
    shape: Shape,
    fill: Boolean = false,
) {
    Surface(
        modifier = if (fill) Modifier.fillMaxSize() else Modifier.size(size),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            if (cover == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title.firstOrNull()?.uppercase() ?: "♪",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** 歌词行：主歌词 + 可选翻译。 */
@Composable
internal fun LyricLineView(
    line: LyricsLine?,
    settings: IslandSettings,
    maxLines: Int = 2,
) {
    val main = line?.pureMainText?.takeIf { it.isNotBlank() } ?: "♪ 等待歌词"
    val sub = line?.pureSubText?.takeIf { it.isNotBlank() }

    Column {
        Text(
            text = main,
            fontSize = settings.lyricSize.sp,
            lineHeight = (settings.lyricSize + 6).sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (settings.showTranslation && sub != null) {
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun formatTime(milliseconds: Long): String {
    if (milliseconds <= 0) return "0:00"
    val totalSeconds = milliseconds / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
