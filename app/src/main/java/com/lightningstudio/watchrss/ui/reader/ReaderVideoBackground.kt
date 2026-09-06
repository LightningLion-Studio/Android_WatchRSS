package com.lightningstudio.watchrss.ui.reader

import android.media.MediaMetadataRetriever
import android.os.Build
import android.graphics.Matrix
import android.graphics.Paint
import android.view.TextureView
import android.view.View
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.Presentation
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lightningstudio.watchrss.data.reader.ReaderBackground
import com.lightningstudio.watchrss.data.reader.ReaderBackgroundFit
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.ceil

/** Full media plane, positioned after zoom so focus remains useful even at matching aspect ratios. */
@Composable
internal fun ReaderBackgroundMedia(
    file: File?,
    video: File?,
    background: ReaderBackground
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val legacyBlur = Build.VERSION.SDK_INT < 31
    val imageModel = remember(file?.path, background.blurDp, density) {
        if (file != null && legacyBlur && background.blurDp > 0f) {
            ImageRequest.Builder(context).data(file).allowHardware(false)
                .transformations(ReaderBackgroundBlurTransformation(context, background.blurDp * density))
                .build()
        } else file
    }
    var width by remember(file?.path, video?.path) { mutableFloatStateOf(1f) }
    var height by remember(file?.path, video?.path) { mutableFloatStateOf(1f) }
    var videoSizeReady by remember(video?.path) { mutableStateOf(false) }
    LaunchedEffect(video?.path) {
        if (video != null) {
            val dimensions = withContext(Dispatchers.IO) {
                runCatching {
                    val metadata = MediaMetadataRetriever()
                    try {
                        metadata.setDataSource(video.path)
                        val w = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toFloat()
                        val h = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toFloat()
                        val rotation = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        require(w > 0 && h > 0)
                        if (Math.floorMod(rotation, 180) == 90) h to w else w to h
                    } finally { metadata.release() }
                }.onFailure { Log.e("ReaderVideoBackground", "Cannot read background video dimensions", it) }.getOrNull()
            }
            if (dimensions != null) {
                width = dimensions.first
                height = dimensions.second
                videoSizeReady = true
            }
        }
    }
    val contentScale = remember(background.fit, background.zoom) {
        object : ContentScale {
            override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
                val sx = dstSize.width / srcSize.width
                val sy = dstSize.height / srcSize.height
                val scale = if (background.fit == ReaderBackgroundFit.CROP) maxOf(sx, sy) else minOf(sx, sy)
                return if (background.fit == ReaderBackgroundFit.FILL) ScaleFactor(sx * background.zoom, sy * background.zoom)
                    else ScaleFactor(scale * background.zoom, scale * background.zoom)
            }
        }
    }
    Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = background.rotationDegrees }.blur((if (legacyBlur) 0f else background.blurDp).dp)) {
        if (file != null) AsyncImage(
            model = imageModel,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            alignment = BiasAlignment(background.focusX * 2f - 1f, background.focusY * 2f - 1f),
            colorFilter = ColorFilter.colorMatrix(backgroundColorMatrix(background.brightness, background.saturation)),
            onSuccess = {
                val size = it.painter.intrinsicSize
                if (size.width > 0 && size.height > 0) { width = size.width; height = size.height }
            }
        )
        if (video != null && videoSizeReady) ReaderVideoTexture(video, background, width, height)
    }
}

internal data class ReaderBackgroundPlane(val width: Int, val height: Int, val x: Int, val y: Int)

internal fun readerBackgroundPlane(
    viewportWidth: Int, viewportHeight: Int, sourceWidth: Float, sourceHeight: Float,
    fit: ReaderBackgroundFit, zoom: Float, focusX: Float, focusY: Float
): ReaderBackgroundPlane {
    val sx = viewportWidth / sourceWidth.coerceAtLeast(1f)
    val sy = viewportHeight / sourceHeight.coerceAtLeast(1f)
    val scale = if (fit == ReaderBackgroundFit.CROP) maxOf(sx, sy) else minOf(sx, sy)
    val width = ((if (fit == ReaderBackgroundFit.FILL) viewportWidth.toFloat() else sourceWidth * scale) * zoom)
        .roundToInt().coerceAtLeast(1)
    val height = ((if (fit == ReaderBackgroundFit.FILL) viewportHeight.toFloat() else sourceHeight * scale) * zoom)
        .roundToInt().coerceAtLeast(1)
    return ReaderBackgroundPlane(width, height,
        ((viewportWidth - width) * focusX).roundToInt(), ((viewportHeight - height) * focusY).roundToInt())
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun ReaderVideoTexture(
    file: File, background: ReaderBackground, sourceWidth: Float, sourceHeight: Float
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val legacyBlur = Build.VERSION.SDK_INT < 31
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Only the immutable resource path owns the player, never the editor parameters.
    val player = remember(file.absolutePath, context) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            if (legacyBlur) setVideoEffects(emptyList())
        }
    }
    val effects = remember(legacyBlur, background.blurDp, density, sourceWidth, sourceHeight) {
        buildList<androidx.media3.common.Effect> {
            if (legacyBlur && background.blurDp > 0f) {
                val radius = background.blurDp * density
                val sample = if (sourceWidth > 2 && sourceHeight > 2) ceil(radius / 25f).toInt().coerceAtLeast(1) else 1
                if (sample > 1) add(Presentation.createForWidthAndHeight(
                    (sourceWidth / sample).roundToInt().coerceAtLeast(2),
                    (sourceHeight / sample).roundToInt().coerceAtLeast(2), Presentation.LAYOUT_SCALE_TO_FIT))
                // Match the Android 11 intrinsic blur's approximate Gaussian sigma.
                add(GaussianBlur((radius / sample) * .4f + .6f))
            }
        }
    }
    LaunchedEffect(player, effects) { if (legacyBlur) player.setVideoEffects(effects) }
    SideEffect {
        player.repeatMode = if (background.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setPlaybackSpeed(background.videoSpeed.coerceIn(0.25f, 4f))
    }
    var ready by remember(player) { mutableStateOf(false) }
    var failed by remember(player) { mutableStateOf(false) }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true }
            override fun onPlayerError(error: PlaybackException) {
                failed = true
                Log.e("ReaderVideoBackground", "Background playback failed: ${file.name}", error)
            }
        }
        val observer = LifecycleEventObserver { _, _ ->
            player.playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        player.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
        player.prepare()
        player.playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    var viewport by remember(player) { mutableStateOf(IntSize.Zero) }
    key(player) { AndroidView(
        modifier = Modifier.fillMaxSize().onSizeChanged { viewport = it },
        factory = { TextureView(it).apply { isOpaque = false; player.setVideoTextureView(this) } },
        update = { view ->
            view.alpha = if (ready && !failed) 1f else 0f
            // Keep the Surface/TextureView bounded to the display at every zoom level.
            if (viewport.width > 0 && viewport.height > 0) {
                val plane = readerBackgroundPlane(viewport.width, viewport.height, sourceWidth, sourceHeight,
                    background.fit, background.zoom, background.focusX, background.focusY)
                // Media3's effect pipeline fits the full frame into its output surface.
                // Account for that padding before applying the shared image/video geometry.
                val inputScale = minOf(viewport.width / sourceWidth, viewport.height / sourceHeight)
                val inputWidth = if (legacyBlur) sourceWidth * inputScale else viewport.width.toFloat()
                val inputHeight = if (legacyBlur) sourceHeight * inputScale else viewport.height.toFloat()
                val scaleX = plane.width / inputWidth
                val scaleY = plane.height / inputHeight
                view.setTransform(Matrix().apply {
                    setScale(scaleX, scaleY)
                    postTranslate(
                        plane.x - (viewport.width - inputWidth) / 2f * scaleX,
                        plane.y - (viewport.height - inputHeight) / 2f * scaleY
                    )
                })
            }
            val matrix = backgroundColorMatrix(background.brightness, background.saturation)
            view.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(matrix.values)
            })
        }
    ) }
}
