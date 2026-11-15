package com.readr.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.readr.app.R
import com.readr.app.domain.model.DocumentFormat
import com.readr.app.domain.model.ReadrDocument
import com.readr.app.domain.model.TextureIntensity
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Decodes the paper grain texture once per composition site. */
@Composable
fun rememberGrainBitmap(): ImageBitmap {
    val context = LocalContext.current
    return remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.paper_grain).asImageBitmap()
    }
}

/** Tiles the paper grain behind content; call inside a background-colored container. */
fun DrawScope.drawGrain(bitmap: ImageBitmap, alpha: Float) {
    val iw = bitmap.width
    val ih = bitmap.height
    var y = 0
    while (y < size.height.roundToInt()) {
        var x = 0
        while (x < size.width.roundToInt()) {
            val w = minOf(iw, size.width.roundToInt() - x)
            val h = minOf(ih, size.height.roundToInt() - y)
            drawImage(
                image = bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(w, h),
                dstOffset = IntOffset(x, y),
                dstSize = IntSize(w, h),
                alpha = alpha,
                filterQuality = FilterQuality.Low,
            )
            x += iw
        }
        y += ih
    }
}

/**
 * The reading page itself: paper colour + subtle grain texture behind the
 * text (the texture is drawn first, so text always stays on top).
 */
@Composable
fun PaperSurface(
    backgroundColor: Color,
    textColor: Color,
    intensity: TextureIntensity,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val grain = rememberGrainBitmap()
    Box(
        modifier
            .background(backgroundColor)
            .drawBehind { drawGrain(grain, intensity.alpha) }
    ) {
        content()
    }
}

/** Compact paper preview used in Paper Studio theme cards. */
@Composable
fun MiniPaperPreview(
    backgroundColor: Color,
    textColor: Color,
    intensity: TextureIntensity,
    modifier: Modifier = Modifier,
    sample: String = "Aa",
) {
    PaperSurface(backgroundColor, textColor, intensity, modifier) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                sample,
                color = textColor,
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun FormatBadge(format: DocumentFormat, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            format.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Library cover: generated/embedded artwork, or a neutral format placeholder. */
@Composable
fun CoverImage(document: ReadrDocument, modifier: Modifier = Modifier, cornerRadius: Dp = 10.dp) {
    val file = document.coverPath?.let { File(it) }?.takeIf { it.exists() }
    if (file != null) {
        AsyncImage(
            model = file,
            contentDescription = document.title,
            modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Surface(
            modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    Icons.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    document.format.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (trailing != null) trailing()
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(
                    confirmLabel,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipSelector(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier) {
        for (option in options) {
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
fun EmptyLibraryArt(modifier: Modifier = Modifier) {
    // Hand-composed bookshelf illustration in the app's sage palette.
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sage = Color(0xFF8FA47F)
        val sageDeep = Color(0xFF6E835F)
        val tan = Color(0xFFD9C7A7)
        val clay = Color(0xFFB96B4C)
        val line = Color(0xFF6E835F)

        fun shelf(y: Float) {
            drawLine(line, Offset(w * 0.1f, y), Offset(w * 0.9f, y), strokeWidth = h * 0.02f)
        }

        // Upper shelf books
        val bookH = h * 0.26f
        drawRect(sageDeep, Offset(w * 0.18f, h * 0.16f), Size(w * 0.07f, bookH))
        drawRect(tan, Offset(w * 0.27f, h * 0.16f), Size(w * 0.09f, bookH * 0.92f))
        drawRect(clay, Offset(w * 0.38f, h * 0.16f), Size(w * 0.07f, bookH))
        drawRect(sage, Offset(w * 0.47f, h * 0.18f), Size(w * 0.11f, bookH * 0.94f))
        shelf(h * 0.44f)

        // Lower shelf
        drawRect(tan, Offset(w * 0.2f, h * 0.56f), Size(w * 0.1f, bookH * 0.86f))
        drawRect(sage, Offset(w * 0.32f, h * 0.58f), Size(w * 0.08f, bookH * 0.82f))
        drawRect(sageDeep, Offset(w * 0.42f, h * 0.56f), Size(w * 0.1f, bookH * 0.86f))
        drawRect(clay, Offset(w * 0.54f, h * 0.58f), Size(w * 0.08f, bookH * 0.82f))
        shelf(h * 0.86f)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyLibraryArt(modifier = Modifier.size(160.dp, 130.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = 16.dp)) { action() }
        }
    }
}

/** Deterministic tint for list accents. */
fun stableTint(seed: Int): Color {
    val tints = listOf(
        Color(0xFF8FA47F), Color(0xFFD9C7A7), Color(0xFFB96B4C),
        Color(0xFF77808B), Color(0xFF63534A), Color(0xFF9C8AA5),
    )
    return tints[abs(seed) % tints.size]
}
