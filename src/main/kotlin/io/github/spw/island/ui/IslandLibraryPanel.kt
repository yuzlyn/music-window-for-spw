package io.github.spw.island.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint.MediaItem
import io.github.spw.island.IslandController
import io.github.spw.island.IslandSettings
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.spw.island.theme.FluentSpacing

/**
 * 悬浮窗内嵌的曲库面板（替代独立窗口）。
 */
@Composable
fun IslandLibraryPanel(
    controller: IslandController,
    settings: IslandSettings,
    modifier: Modifier = Modifier,
) {
    val tracks by controller.library.collectAsState()
    val loading by controller.libraryLoading.collectAsState()
    val error by controller.libraryError.collectAsState()
    val current by controller.track.collectAsState()
    val selected by controller.selectedTrack.collectAsState()

    var query by remember { mutableStateOf("") }

    val filtered = remember(tracks, query) {
        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (tracks.isEmpty()) controller.loadLibrary()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FluentSpacing.lg, vertical = FluentSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                placeholder = {
                    Text("搜索歌曲 / 艺术家 / 专辑", style = MaterialTheme.typography.bodySmall)
                },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyMedium,
                // 显式指定配色，避免在深/浅主题下出现低对比或"反色"
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.width(FluentSpacing.xs))
            Text(
                text = "${tracks.size} 首",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { controller.closeLibrary() }) {
                IslandIconView(
                    icon = IslandIcon.Collapse,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 18.dp,
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                loading && tracks.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary,
                )

                error != null -> Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(FluentSpacing.lg),
                )

                filtered.isEmpty() -> Text(
                    text = if (tracks.isEmpty()) "曲库为空或尚未读取" else "没有匹配的歌曲",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(FluentSpacing.lg),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id.ifEmpty { it.path } }) { track ->
                        LibraryRow(
                            track = track,
                            isCurrent = track.id.isNotEmpty() && track.id == current?.id,
                            onClick = { controller.selectTrack(track) },
                        )
                    }
                }
            }
        }

        val hint = selected
        Text(
            text = if (hint != null) {
                "已选中：${hint.title} · 当前 API 不支持按 ID 点播"
            } else {
                "点击条目可查看详情；播放控制请使用上方按钮"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = FluentSpacing.lg, vertical = FluentSpacing.sm),
        )
    }
}

@Composable
private fun LibraryRow(
    track: MediaItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FluentSpacing.md, vertical = 1.dp)
            .clickable(onClick = onClick),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = FluentSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatTime(track.duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
