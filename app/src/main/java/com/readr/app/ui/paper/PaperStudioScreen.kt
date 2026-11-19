package com.readr.app.ui.paper

import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readr.app.di.AppContainer
import com.readr.app.domain.model.PaperTheme
import com.readr.app.domain.model.ReadingPrefs
import com.readr.app.domain.model.TextureIntensity
import com.readr.app.ui.components.PaperSurface
import kotlinx.coroutines.launch

private const val SAMPLE_TITLE = "The Reading Room"
private const val SAMPLE_BODY =
    "READR prints every book you open on the paper you choose. The grain sits behind the text, " +
        "never over it, so letters stay sharp and the page stays calm. Choose Warm Cream for long " +
        "evenings, Parchment for essays, and Dark Reading Mode for the last train home."

/**
 * Paper Studio: dedicated section for reading background customization with a
 * live preview of the exact reading experience.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaperStudioScreen() {
    val container = AppContainer.Local.current
    val prefsRepo = container.preferencesRepository
    val prefs by prefsRepo.readingPrefs.collectAsStateWithLifecycle(initialValue = ReadingPrefs())
    val scope = rememberCoroutineScope()
    fun update(block: suspend () -> Unit) = scope.launch { block() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Paper Studio",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Choose the paper your books are printed on. Your selection is remembered across all documents.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )

        // ---- live reading preview ----
        val bg = Color(prefs.customBackground ?: prefs.paperTheme.bg)
        val fg = Color(prefs.customTextColor ?: prefs.paperTheme.text)
        Card(
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PaperSurface(
                backgroundColor = bg,
                textColor = fg,
                intensity = prefs.textureIntensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        SAMPLE_TITLE,
                        color = fg,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        SAMPLE_BODY,
                        color = fg,
                        fontSize = prefs.fontSizeSp.sp,
                        lineHeight = (prefs.fontSizeSp * prefs.lineSpacing).sp,
                    )
                }
            }
        }

        Text(
            "Paper",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
        )
        PaperGrid(
            selected = prefs.paperTheme,
            intensity = prefs.textureIntensity,
            onSelect = { update { prefsRepo.applyPaperTheme(it) } },
        )

        Text(
            "Texture intensity",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
        )
        FlowRow {
            for (intensity in TextureIntensity.entries) {
                FilterChip(
                    selected = prefs.textureIntensity == intensity,
                    onClick = { update { prefsRepo.setTextureIntensity(intensity) } },
                    label = { Text(intensity.label) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Text(
            "Warm Cream Paper with a Low grain is selected automatically on first launch. " +
                "The paper applies to EPUB, DOCX and TXT documents; PDF keeps its original appearance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Spacer(Modifier.height(90.dp))
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
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(84.dp)
                        ) {
                            PaperSurface(
                                backgroundColor = Color(theme.bg),
                                textColor = Color(theme.text),
                                intensity = intensity,
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(12.dp),
                                onClick = { onSelect(theme) },
                            ) {
                                PreviewCenteredLabel(theme)
                            }
                            // selection outline drawn above the paper so it stays visible
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .border(
                                        if (selected == theme) 2.dp else 1.dp,
                                        if (selected == theme) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    ),
                            )
                        }
                        Text(
                            theme.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected == theme) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreviewCenteredLabel(theme: PaperTheme) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            theme.label.substringBefore(" Paper").substringBefore(" Mode"),
            color = Color(theme.text),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
