package com.nazcarbon.music

import android.content.Context
import android.graphics.Canvas
import android.graphics.Movie
import android.os.SystemClock
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun PipesLoading(modifier: Modifier = Modifier) {
    AnimatedAssetGif("loading.gif", "Loading music", modifier.size(64.dp))
}

@Composable
internal fun AnimatedAssetGif(assetName: String, description: String?, modifier: Modifier = Modifier) {
    androidx.compose.runtime.key(assetName) {
        AndroidView(
            factory = { AssetGifView(it, assetName) },
            update = { it.contentDescription = description },
            modifier = modifier
        )
    }
}

/** Movie supports animated GIFs on every supported Android version, without another dependency. */
@Suppress("DEPRECATION")
private class AssetGifView(context: Context, assetName: String) : View(context) {
    private val movie = context.assets.open(assetName).use { Movie.decodeStream(it) }
    private val started = SystemClock.uptimeMillis()
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val animation = movie ?: return
        animation.setTime(((SystemClock.uptimeMillis() - started) % (animation.duration().takeIf { it > 0 } ?: 1000)).toInt())
        val scale = minOf(width.toFloat() / animation.width(), height.toFloat() / animation.height())
        canvas.save()
        canvas.translate((width - animation.width() * scale) / 2, (height - animation.height() * scale) / 2)
        canvas.scale(scale, scale)
        animation.draw(canvas, 0f, 0f)
        canvas.restore()
        if (isShown) postInvalidateOnAnimation()
    }
}
