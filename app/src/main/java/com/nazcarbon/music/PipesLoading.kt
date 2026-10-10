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
    AndroidView(factory = { LoadingGif(it) }, modifier = modifier.size(64.dp))
}

@Suppress("DEPRECATION")
private class LoadingGif(context: Context) : View(context) {
    private val movie = context.assets.open("loading.gif").use { Movie.decodeStream(it) }
    private var started = SystemClock.uptimeMillis()
    init {
        contentDescription = "Loading music"
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
