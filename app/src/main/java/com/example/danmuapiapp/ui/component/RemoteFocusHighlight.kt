package com.example.danmuapiapp.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp

/** Draw above the component's glass/tint. Do not add a second focus target. */
fun Modifier.remoteFocusHighlight(shape: Shape, enabled: Boolean = true): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (focused && enabled) {
                // Keep the whole ring inside clipped surfaces. The two-tone ring
                // stays visible on bright, dark and translucent backgrounds.
                inset(3.dp.toPx()) {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, Color.Black.copy(alpha = 0.9f), style = Stroke(6.dp.toPx()))
                    drawOutline(outline, Color.White, style = Stroke(3.dp.toPx()))
                }
            }
        }
}
