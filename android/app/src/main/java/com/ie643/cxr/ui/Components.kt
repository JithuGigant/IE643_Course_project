@file:OptIn(ExperimentalMaterial3Api::class)

package com.ie643.cxr.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ie643.cxr.CropRect
import kotlin.math.abs
import kotlin.math.min

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun Kv(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.42f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End, modifier = Modifier.weight(0.58f))
    }
}

fun levelColor(level: String): Color = when (level.uppercase()) {
    "BLOCK" -> BlockColor
    "WARN" -> WarnColor
    else -> HintColor
}

fun levelLabel(level: String): String = when (level.uppercase()) {
    "BLOCK" -> "Blocking"
    "WARN" -> "Warning"
    else -> "Hint"
}

/** One input-quality finding with a coloured edge. [actionLabel] adds a button (invert, rotate). */
@Composable
fun FindingCard(level: String, title: String, detail: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val col = levelColor(level)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = col.copy(alpha = 0.10f))) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(col))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${levelLabel(level)}: $title", fontWeight = FontWeight.SemiBold, color = col, fontSize = 15.sp)
                Text(detail, style = MaterialTheme.typography.bodySmall)
                if (actionLabel != null && onAction != null) OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp)) { Text(text, fontSize = 16.sp) }
}

private const val EDGE_L = 1
private const val EDGE_T = 2
private const val EDGE_R = 4
private const val EDGE_B = 8
private const val MOVE = 16

private fun hit(x: Float, y: Float, c: CropRect, w: Float, h: Float, m: Float): Int {
    val lx = c.l * w; val rx = c.r * w; val ty = c.t * h; val by = c.b * h
    var mode = 0
    val inY = y > ty - m && y < by + m
    val inX = x > lx - m && x < rx + m
    if (inY && abs(x - lx) < m) mode = mode or EDGE_L
    if (inY && abs(x - rx) < m) mode = mode or EDGE_R
    if (inX && abs(y - ty) < m) mode = mode or EDGE_T
    if (inX && abs(y - by) < m) mode = mode or EDGE_B
    if (mode == 0 && x in lx..rx && y in ty..by) mode = MOVE
    return mode
}

private fun applyDrag(c: CropRect, mode: Int, dx: Float, dy: Float): CropRect {
    var l = c.l; var t = c.t; var r = c.r; var b = c.b
    val minS = 0.12f
    if (mode == MOVE) {
        val w = r - l; val h = b - t
        l = (l + dx).coerceIn(0f, 1f - w); t = (t + dy).coerceIn(0f, 1f - h)
        r = l + w; b = t + h
    } else {
        if ((mode and EDGE_L) != 0) l = (l + dx).coerceIn(0f, r - minS)
        if ((mode and EDGE_R) != 0) r = (r + dx).coerceIn(l + minS, 1f)
        if ((mode and EDGE_T) != 0) t = (t + dy).coerceIn(0f, b - minS)
        if ((mode and EDGE_B) != 0) b = (b + dy).coerceIn(t + minS, 1f)
    }
    return CropRect(l, t, r, b)
}

/** Image with a draggable crop frame (drag edges or corners; drag inside to move). */
@Composable
fun CropBox(bitmap: Bitmap, crop: CropRect, onCrop: (CropRect) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val img = remember(bitmap) { bitmap.asImageBitmap() }
    val cropNow by rememberUpdatedState(crop)
    val onCropNow by rememberUpdatedState(onCrop)
    val onDoneNow by rememberUpdatedState(onDone)
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().height(380.dp), contentAlignment = Alignment.Center) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val scale = min(maxW / bitmap.width, maxH / bitmap.height)
        val dw = with(density) { (bitmap.width * scale).toDp() }
        val dh = with(density) { (bitmap.height * scale).toDp() }
        val mode = remember { IntArray(1) }
        Box(
            Modifier.size(dw, dh).pointerInput(bitmap) {
                detectDragGestures(
                    onDragStart = { o -> mode[0] = hit(o.x, o.y, cropNow, size.width.toFloat(), size.height.toFloat(), 28.dp.toPx()) },
                    onDragEnd = { onDoneNow() },
                    onDragCancel = { onDoneNow() },
                    onDrag = { change, delta ->
                        change.consume()
                        if (mode[0] != 0) onCropNow(applyDrag(cropNow, mode[0], delta.x / size.width, delta.y / size.height))
                    },
                )
            },
        ) {
            Image(bitmap = img, contentDescription = "Image to analyse", modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val l = crop.l * w; val t = crop.t * h; val r = crop.r * w; val b = crop.b * h
                val dim = Color.Black.copy(alpha = 0.55f)
                drawRect(dim, Offset(0f, 0f), Size(w, t))
                drawRect(dim, Offset(0f, b), Size(w, h - b))
                drawRect(dim, Offset(0f, t), Size(l, b - t))
                drawRect(dim, Offset(r, t), Size(w - r, b - t))
                drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(width = 2.dp.toPx()))
                val rad = 8.dp.toPx()
                for (p in listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))) {
                    drawCircle(Color.White, rad, p)
                    drawCircle(Color(0xFF0B6E6E), rad - 3.dp.toPx(), p)
                }
            }
        }
    }
}
