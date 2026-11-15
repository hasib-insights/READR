package com.readr.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readr.app.di.AppContainer
import com.readr.app.domain.model.AppPrefs
import com.readr.app.domain.model.AppThemeMode
import com.readr.app.domain.model.LibrarySort
import com.readr.app.domain.model.LibraryViewMode
import com.readr.app.domain.model.PaperTheme
import com.readr.app.domain.model.ReaderFont
import com.readr.app.domain.model.ReadingPrefs
import com.readr.app.domain.model.TextureIntensity
import com.readr.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StorageInfo(
    val documents: Long = 0,
    val covers: Long = 0,
    val database: Long = 0,
) {
    val total: Long get() = documents + covers + database
}

/**
 * Settings: appearance, reading defaults, library preferences, storage
 * information and the About/Privacy section.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onOpenPaperStudio: () -> Unit) {
    val container = AppContainer.Local.current
    val prefsRepo = container.preferencesRepository
    val appPrefs by prefsRepo.appPrefs.collectAsStateWithLifecycle(initialValue = AppPrefs())
    val readingPrefs by prefsRepo.readingPrefs.collectAsStateWithLifecycle(initialValue = ReadingPrefs())
    val scope = rememberCoroutineScope()
    fun update(block: suspend () -> Unit) = scope.launch { block() }

    val context = LocalContext.current
    var showStorage by remember { mutableStateOf(false) }
    val storage by produceState(StorageInfo(), Unit) {
        value = withContext(Dispatchers.IO) {
            val dbFile = context.getDatabasePath("readr.db")
            StorageInfo(
                documents = FileUtils.directorySize(container.documentsDir),
                covers = FileUtils.directorySize(container.coversDir),
                database = if (dbFile.exists()) dbFile.length() else 0,
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )

        GroupCard("Appearance") {
            Text("App theme", style = MaterialTheme.typography.bodyLarge)
            FlowRow {
                for (mode in AppThemeMode.entries) {
                    FilterChip(
                        selected = appPrefs.appThemeMode == mode,
                        onClick = { update { prefsRepo.setAppThemeMode(mode) } },
                        label = { Text(mode.label) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    )
                }
            }
        }

        GroupCard("Reading defaults") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Default paper", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        readingPrefs.paperTheme.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenPaperStudio) { Text("Open Paper Studio") }
            }
            FlowRow {
                for (theme in PaperTheme.entries) {
                    FilterChip(
                        selected = readingPrefs.paperTheme == theme,
                        onClick = { update { prefsRepo.setPaperTheme(theme) } },
                        label = { Text(theme.label.substringBefore(" Paper").substringBefore(" Mode")) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    )
                }
            }
            Text("Texture intensity", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            FlowRow {
                for (intensity in TextureIntensity.entries) {
                    FilterChip(
                        selected = readingPrefs.textureIntensity == intensity,
                        onClick = { update { prefsRepo.setTextureIntensity(intensity) } },
                        label = { Text(intensity.label) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    )
                }
            }
            Text("Default font", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            FlowRow {
                for (font in ReaderFont.entries) {
                    FilterChip(
                        selected = readingPrefs.font == font,
                        onClick = { update { prefsRepo.setFont(font) } },
                        label = { Text(font.label) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    )
                }
            }
            Text(
                "Default font size · ${readingPrefs.fontSizeSp} sp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Slider(
                value = readingPrefs.fontSizeSp.toFloat(),
                onValueChange = { update { prefsRepo.setFontSize(it.toInt()) } },
                valueRange = 12f..30f,
                steps = 17,
            )
        }

        GroupCard("Library") {
            Text("Default view", style = MaterialTheme.typography.bodyLarge)
            FlowRow {
                FilterChip(
                    selected = appPrefs.libraryView == LibraryViewMode.GRID,
                    onClick = { update { prefsRepo.setLibraryView(LibraryViewMode.GRID) } },
                    label = { Text("Grid") },
                    modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                )
                FilterChip(
                    selected = appPrefs.libraryView == LibraryViewMode.LIST,
                    onClick = { update { prefsRepo.setLibraryView(LibraryViewMode.LIST) } },
                    label = { Text("List") },
                    modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                )
            }
            Text("Sort by", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
            FlowRow {
                for (sort in LibrarySort.entries) {
                    FilterChip(
                        selected = appPrefs.librarySort == sort,
                        onClick = { update { prefsRepo.setLibrarySort(sort) } },
                        label = { Text(sort.label) },
                        modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    )
                }
            }
        }

        GroupCard("Storage") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("READR uses ${FileUtils.humanSize(storage.total)}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Imported documents ${FileUtils.humanSize(storage.documents)} · Covers ${FileUtils.humanSize(storage.covers)} · Database ${FileUtils.humanSize(storage.database)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showStorage = true }) { Text("Manage") }
            }
        }

        GroupCard("About READR") {
            val version = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "1.0.0"
            }
            Text("READR $version", style = MaterialTheme.typography.bodyLarge)
            Text(
                "A Better Way to Read.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text(
                "READR is fully offline: no login, no ads, no analytics, no tracking and no " +
                    "internet permission. Your documents and reading data never leave this device. " +
                    "Imported files are copied into the app's private storage so your library keeps " +
                    "working even if the original file moves.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text(
                "Open source under the Apache License 2.0. Source code: github.com/hasib-insights/READR",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(90.dp))
    }

    if (showStorage) {
        AlertDialog(
            onDismissRequest = { showStorage = false },
            title = { Text("Manage storage") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Imported documents: ${FileUtils.humanSize(storage.documents)}")
                    Text("Generated covers: ${FileUtils.humanSize(storage.covers)}")
                    Text("Library database: ${FileUtils.humanSize(storage.database)}")
                    Text(
                        "Clearing covers frees space; covers regenerate when needed. " +
                            "To free the most space, remove documents you no longer read.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showStorage = false
                    scope.launch { withContext(Dispatchers.IO) { container.documentRepository.clearCovers() } }
                }) { Text("Clear generated covers") }
            },
            dismissButton = {
                TextButton(onClick = { showStorage = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun GroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}
