package com.nazcarbon.music

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

@Composable
internal fun ArtworkImage(
    model: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
) {
    val context = LocalContext.current
    val url = remember(model) { normalizeArtworkUrl(model) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url, failed, attempt) {
        if (failed && attempt < 2 && url.isNotEmpty()) {
            delay(1_000L * (attempt + 1))
            failed = false
            attempt++
        }
    }
    val request = remember(url, attempt, context) {
        ImageRequest.Builder(context).data(url.takeIf { it.isNotEmpty() })
            .setHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36")
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .fallback(android.R.drawable.ic_menu_gallery)
            .build()
    }
    key(url, attempt) {
        AsyncImage(request, contentDescription, modifier, contentScale = contentScale, alpha = alpha,
            onError = { failed = true })
    }
}

internal fun normalizeArtworkUrl(value: String): String {
    val trimmed = value.trim().replace("&amp;", "&")
    return when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("http://") -> "https:" + trimmed.removePrefix("http:")
        else -> trimmed
    }
}
