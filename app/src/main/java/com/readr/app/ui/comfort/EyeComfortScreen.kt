package com.readr.app.ui.comfort

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readr.app.di.AppContainer
import com.readr.app.domain.model.PaperTheme
import com.readr.app.domain.model.ReaderFont
import com.readr.app.domain.model.ReadingPrefs
import com.readr.app.ui.components.PaperSurface
import com.readr.app.ui.reader.fontFamilyOf
import kotlinx.coroutines.launch

private val customBackgrounds = listOf(
    0xFFFAF3E3L, 0xFFFEFEFCL, 0xFFF6E7C8L, 0xFFEFDFBBL, 0xFFE9E6D9L, 0xFFE8E8E4L,
    0xFFE7EFE2L, 0xFFF6E3E0L, 0xFFE3EDF3L, 0xFFE6F0E6L, 0xFFECE8F3L, 0xFF201E1AL,
)

private val customTextColors = listOf(
    0xFF3A3128L, 0xFF5B4632L, 0xFF1C1B18L, 0xFF4E4E46L, 0xFF2F4030L,
    0xFFD8CFBCL, 0xFFC9BBA9L, 0xFF5C4E6EL,
)

/**
 * Eye Comfort: warm/dark modes, custom page and text colours, typography and
 * brightness controls — all shared with the Reading Room through the same
 * preference store.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EyeComfortScreen() {
    val container = AppContainer.Local.current
    val prefsRepo = container.preferencesRepository
    val prefs by prefsRepo.readingPrefs.collectAsStateWithLifecycle(initialValue = ReadingPrefs())
    val scope = rememberCoroutineScope()
    fun update(block: suspend () -> Unit) = scope.launch { block() }

    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    var brightness by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
    val applyBrightness: (Float) -> Unit = { raw ->
        val value = raw.coerceIn(0.02f, 1f)
        brightness = value
        activity?.window?.attributes = activity?.window?.attributes?.apply { screenBrightness = value }
    }
    val writeSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Eye Comfort",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Tune the page, the type and the light until your eyes forget there is a screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )

        // Live preview
        val bg = Color(prefs.customBackground ?: prefs.paperTheme.bg)
        val fg = Color(prefs.customTextColor ?: prefs.paperTheme.text)
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PaperSurface(bg, fg, prefs.textureIntensity, Modifier.fillMaxWidth().height(160.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "The Reading Room",
                        color = fg,
                        fontSize = (prefs.fontSizeSp * 1.4).sp,
                        fontFamily = fontFamilyOf(prefs.font),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sample text rendered with your current comfort settings.",
                        color = fg,
                        fontSize = prefs.fontSizeSp.sp,
                        lineHeight = (prefs.fontSizeSp * prefs.lineSpacing).sp,
                        fontFamily = fontFamilyOf(prefs.font),
                    )
                }
            }
        }

        Text("Reading modes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
        SwitchRow(
            "Warm reading mode",
            "Soft warm tint over any paper.",
            prefs.warmFilter,
        ) { update { prefsRepo.setWarmFilter(it) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            FilterChip(
                selected = prefs.paperTheme == PaperTheme.DARK,
                onClick = { update { prefsRepo.setPaperTheme(PaperTheme.DARK) } },
                label = { Text("Dark Reading Mode") },
            )
            FilterChip(
                selected = prefs.paperTheme == PaperTheme.WARM_CREAM,
                onClick = { update { prefsRepo.setPaperTheme(PaperTheme.WARM_CREAM) } },
                label = { Text("Warm Cream") },
            )
        }

        Text("Custom page colour", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        FlowRow {
            FilterChip(
                selected = prefs.customBackground == null,
                onClick = { update { prefsRepo.setCustomBackground(null) } },
                label = { Text("Paper default") },
                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
            )
            for (c in customBackgrounds) {
                val selected = prefs.customBackground == c
                Box(
                    Modifier
                        .padding(end = 8.dp, bottom = 4.dp)
                        .size(44.dp)
                        .background(Color(c), RoundedCornerShape(10.dp))
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { update { prefsRepo.setCustomBackground(c) } }
                )
            }
        }

        Text("Custom text colour", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        FlowRow {
            FilterChip(
                selected = prefs.customTextColor == null,
                onClick = { update { prefsRepo.setCustomTextColor(null) } },
                label = { Text("Paper default") },
                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
            )
            for (c in customTextColors) {
                val selected = prefs.customTextColor == c
                Box(
                    Modifier
                        .padding(end = 8.dp, bottom = 4.dp)
                        .size(44.dp)
                        .background(Color(c), RoundedCornerShape(10.dp))
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { update { prefsRepo.setCustomTextColor(c) } }
                )
            }
        }

        Text("Typography", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
        Text(
            "Font size · ${prefs.fontSizeSp} sp",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = prefs.fontSizeSp.toFloat(),
            onValueChange = { update { prefsRepo.setFontSize(it.toInt()) } },
            valueRange = 12f..30f,
            steps = 17,
        )
        FlowRow {
            for (font in ReaderFont.entries) {
                FilterChip(
                    selected = prefs.font == font,
                    onClick = { update { prefsRepo.setFont(font) } },
                    label = { Text(font.label) },
                    modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                )
            }
        }
        Text(
            "Line spacing · ${String.format("%.2f", prefs.lineSpacing)}× · Margins ${prefs.marginDp} dp",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = prefs.lineSpacing,
            onValueChange = { update { prefsRepo.setLineSpacing((it * 20).toInt() / 20f) } },
            valueRange = 1.0f..2.0f,
        )
        Slider(
            value = prefs.marginDp.toFloat(),
            onValueChange = { update { prefsRepo.setMarginDp(it.toInt()) } },
            valueRange = 8f..48f,
            steps = 9,
        )

        Text("Brightness", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
        val currentBrightness = brightness
        Text(
            if (currentBrightness == null) "Screen brightness · System"
            else "Screen brightness · ${(currentBrightness * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = brightness ?: 0.5f,
            onValueChange = applyBrightness,
            valueRange = 0.02f..1f,
        )
        SwitchRow(
            "Edge brightness gesture",
            "Swipe up/down along the left edge while reading.",
            prefs.brightnessGestureEnabled,
        ) { update { prefsRepo.setGestureEnabled(it) } }
        Text(
            "Gesture sensitivity · ${String.format("%.1f", prefs.gestureSensitivity)}×",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = prefs.gestureSensitivity,
            onValueChange = { update { prefsRepo.setGestureSensitivity((it * 10).toInt() / 10f) } },
            valueRange = 0.4f..2.5f,
        )
        SwitchRow(
            "Adjust system-wide brightness",
            "Requires WRITE SETTINGS; otherwise only READR's window is dimmed.",
            prefs.systemBrightnessMode,
        ) { enabled ->
            if (!enabled) {
                update { prefsRepo.setSystemBrightnessMode(false) }
            } else if (Settings.System.canWrite(context)) {
                update { prefsRepo.setSystemBrightnessMode(true) }
            } else {
                writeSettingsLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
