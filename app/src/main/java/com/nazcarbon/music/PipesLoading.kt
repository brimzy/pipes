package com.nazcarbon.music

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nazcarbon.music.ui.theme.PipesPalette

@Composable
fun PipesLoading(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier.size(24.dp).semantics { contentDescription = "Loading music" },
        color = PipesPalette.Cyan, strokeWidth = 2.dp, trackColor = PipesPalette.Line)
}
