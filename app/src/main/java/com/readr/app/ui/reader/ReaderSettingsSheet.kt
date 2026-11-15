package com.readr.app.ui.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.readr.app.domain.model.PaperTheme
import com.readr.app.domain.model.ReaderFont
import com.readr.app.domain.model.ReadingPrefs
import com.readr.app.domain.model.TextureIntensity
import com.readr.app.ui.components.MiniPaperPreview
import kotlinx.coroutines.launch

private val SageBorder = Color(0xFF5B7B58)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsSheet(
    prefs: ReadingPrefs,
    vm: ReaderViewModel,
    isPdf: Boolean,
    brightness: Float?,
    onBrightnessChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun update(block: suspend () -> Unit) = scope.launch { block() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            if (!isPdf) {
                SheetTitle("Paper Studio")
                PaperGrid(
                    selected = prefs.paperTheme,
                    intensity = prefs.textureIntensity,
                    onSelect = { theme -> update { vm.prefsRepo.setPaperTheme(theme) } },
                )
                Spacer(Modifier.height(12.dp))
                SheetLabel("Texture intensity")
                FlowRow {
                    for (intensity in TextureIntensity.entries) {
                        FilterChip(
                            selected = prefs.textureIntensity == intensity,
                            onClick = { update { vm.prefsRepo.setTextureIntensity(intensity) } },
                            label = { Text(intensity.label) },
                            modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            SheetTitle("Typography")
            SheetLabel("Font size · ${prefs.fontSizeSp} sp")
            Slider(
                value = prefs.fontSizeSp.toFloat(),
                onValueChange = { value -> update { vm.prefsRepo.setFontSize(value.toInt()) } },
                valueRange = 12f..30f,
                steps = 17,
            )
            SheetLabel("Font family")
            FlowRow {
                for (font in ReaderFont.entries) {
                    FilterChip(
                        selected = prefs.font == font,
                        onClick = { update { vm.prefsRepo.setFont(font) } },
                        label = { Text(font.label) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    )
                }
            }
            SheetLabel("Line spacing · ${String.format("%.2f", prefs.lineSpacing)}×")
            Slider(
                value = prefs.lineSpacing,
                onValueChange = { value ->
                    update { vm.prefsRepo.setLineSpacing((value * 20).toInt() / 20f) }
                },
                valueRange = 1.0f..2.0f,
            )
            SheetLabel("Reading margins · ${prefs.marginDp} dp")
            Slider(
                value = prefs.marginDp.toFloat(),
                onValueChange = { value -> update { vm.prefsRepo.setMarginDp(value.toInt()) } },
                valueRange = 8f..48f,
                steps = 9,
            )
            Spacer(Modifier.height(20.dp))

            SheetTitle("Brightness")
            SheetLabel(
                if (brightness == null) "Screen brightness · System"
                else "Screen brightness · ${(brightness * 100).toInt()}%"
            )
            Slider(
                value = brightness ?: 0.5f,
                onValueChange = { onBrightnessChange(it) },
                valueRange = 0.02f..1f,
            )
            SwitchRow(
                "Adjust system-wide brightness",
                "Needs WRITE SETTINGS permission; otherwise only READR's window changes.",
                checked = prefs.systemBrightnessMode,
                onChecked = { update { vm.prefsRepo.setSystemBrightnessMode(it) } },
            )
            Spacer(Modifier.height(20.dp))

            SheetTitle("Eye Comfort")
            SwitchRow(
                "Warm reading mode",
                "Adds a soft warm tint over the page.",
                checked = prefs.warmFilter,
                onChecked = { update { vm.prefsRepo.setWarmFilter(it) } },
            )
            SwitchRow(
                "Edge brightness gesture",
                "Swipe up or down along the left edge to adjust brightness.",
                checked = prefs.brightnessGestureEnabled,
                onChecked = { update { vm.prefsRepo.setGestureEnabled(it) } },
            )
            SheetLabel("Gesture sensitivity · ${String.format("%.1f", prefs.gestureSensitivity)}×")
            Slider(
                value = prefs.gestureSensitivity,
                onValueChange = { value ->
                    update { vm.prefsRepo.setGestureSensitivity((value * 10).toInt() / 10f) }
                },
                valueRange = 0.4f..2.5f,
            )

            if (isPdf) {
                Spacer(Modifier.height(20.dp))
                SheetTitle("PDF options")
                SwitchRow(
                    "Dark rendering",
                    "Inverts page colours for night reading; the original layout is untouched.",
                    checked = prefs.pdfDarkFilter,
                    onChecked = { update { vm.prefsRepo.setPdfDarkFilter(it) } },
                )
                SheetLabel("Page fit")
                FlowRow {
                    FilterChip(
                        selected = prefs.pdfFitWidth,
                        onClick = { update { vm.prefsRepo.setPdfFitWidth(true) } },
                        label = { Text("Fit width") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = !prefs.pdfFitWidth,
                        onClick = { update { vm.prefsRepo.setPdfFitWidth(false) } },
                        label = { Text("Whole page") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaperGrid(
    selected: PaperTheme,
    intensity: TextureIntensity,
    onSelect: (PaperTheme) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in PaperTheme.entries.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (theme in row) {
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MiniPaperPreview(
                            backgroundColor = Color(theme.bg),
                            textColor = Color(theme.text),
                            intensity = intensity,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .border(
                                    if (selected == theme) 2.dp else 1.dp,
                                    if (selected == theme) SageBorder else Color.Transparent,
                                    RoundedCornerShape(10.dp),
                                ),
                        )
                        Text(
                            theme.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
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
