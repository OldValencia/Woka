package com.woka.probe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import com.woka.core.data.ScalePreset
import com.woka.feature.viewer.shell.MediaViewerContent
import com.woka.feature.viewer.shell.NO_VIEWER_ACTIONS
import com.woka.feature.viewer.shell.SizeMode
import com.woka.feature.viewer.shell.ViewerMedia
import com.woka.feature.viewer.shell.ViewerTransform
import com.woka.feature.viewer.video.engine.VideoPlayerState
import com.woka.feature.viewer.video.engine.decode.FrameRetention
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/** TEMPORARY (8K investigation §7): the real viewer component, without gallery/background work. */
internal class EightKProbeWindow(player: VideoPlayerState, metrics: EightKProbeMetrics) {
    val window = ComposeWindow()
    private val screen = window.graphicsConfiguration.bounds
    private val retentionType = FrameRetention::class.java
    private val drawnId = retentionType.getDeclaredField("lastDrawnId").apply {
        isAccessible = true
    }
    @Volatile var surface = "unknown"
    val acknowledged: Long get() = drawnId.getLong(playerRetention)
    private val playerRetention = player.frameRetention

    init {
        window.title = "Woka — 8K diagnostic playback"
        window.isUndecorated = true
        window.isAutoRequestFocus = false
        val media = ViewerMedia.Video(player, ImageBitmap(16, 9))
        window.setContent {
            Box(Modifier.fillMaxSize().drawWithContent {
                // Child graphics layers invalidate independently; observe the frame here as well.
                val published = player.currentFrameId
                drawContent()
                metrics.drawn(minOf(published, acknowledged))
            }) {
                MediaViewerContent(
                    media = media,
                    animationDirection = 0,
                    transform = ViewerTransform(SizeMode.Preset(ScalePreset.Fit), 0f, 0f),
                    actions = NO_VIEWER_ACTIONS,
                    onSizeModeChange = {},
                    onOffsetChange = { _, _ -> },
                    onEffectiveScaleChange = {},
                    onMetricsChange = { size, _ -> surface = "${size.width}x${size.height}" },
                    onZoomHud = {},
                    onPlayPause = {},
                    onToggleFullscreen = {}
                )
            }
        }
        resize(false)
        window.isVisible = true
        val transform = window.graphicsConfiguration.defaultTransform
        println("DV8K screen=$screen awtScale=${transform.scaleX}x${transform.scaleY}")
    }

    fun resize(full: Boolean) {
        val area = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val width = (area.width * .8f).coerceIn(800f, 1440f).toInt()
        val height = (area.height * .8f).coerceIn(580f, 900f).toInt()
        window.bounds = if (full) screen else Rectangle(screen.x + 30, screen.y + 30, width, height)
    }
}
