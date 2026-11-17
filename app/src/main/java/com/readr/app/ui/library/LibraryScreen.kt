package com.readr.app.ui.library

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readr.app.di.AppContainer
import com.readr.app.domain.model.DocumentFormat
import com.readr.app.domain.model.LibrarySort
import com.readr.app.domain.model.LibraryViewMode
import com.readr.app.domain.model.ReadrDocument
import com.readr.app.ui.components.ConfirmDialog
import com.readr.app.ui.components.CoverImage
import com.readr.app.ui.components.EmptyState
import com.readr.app.ui.components.FormatBadge
import com.readr.app.ui.components.SectionHeader
import com.readr.app.util.FileUtils
import com.readr.app.util.TimeAgo

private val importMimeTypes = arrayOf(
    "application/pdf",
    "application/epub+zip",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text/plain",
)

/**
 * Home / My Library: a personal digital bookshelf with Continue Reading,
 * Favorites, search, sorting and format filtering.
 */
@Composable
fun LibraryScreen(
    onOpenDocument: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
) {
    val container = AppContainer.Local.current
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory {
        initializer { LibraryViewModel(container) }
    })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val appPrefs by vm.appPrefs.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    val filter by vm.formatFilter.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var showSearch by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<ReadrDocument?>(null) }

    // System back on the home screen: close the search panel first; menus and
    // dialogs handle back themselves. Only a second press within two seconds
    // exits, so a stray back press never drops the user out of the app.
    val context = LocalContext.current
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler {
        when {
            showSearch -> {
                showSearch = false
                vm.setQuery("")
            }
            else -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackAt < 2000L) {
                    context.findActivity()?.finish()
                } else {
                    lastBackAt = now
                    Toast.makeText(context, "Press back again to exit READR", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.import(uris) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!state.loading) {
                ExtendedFloatingActionButton(
                    onClick = { importLauncher.launch(importMimeTypes) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Import") },
                )
            }
        },
    ) { fabPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("READR", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "A Better Way to Read.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) vm.setQuery("") }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
                ViewToggle(vm = vm, mode = appPrefs.libraryView)
            }

            if (showSearch) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.setQuery(it) },
                    placeholder = { Text("Search title or author") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { vm.setQuery(""); showSearch = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close search")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            if (importing) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            when {
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.documents.isEmpty() && query.isBlank() && filter == null ->
                    Box(
                        Modifier.fillMaxSize().padding(fabPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = "Your shelf is waiting",
                            message = "Import a PDF, EPUB, DOCX or TXT file and READR will place it on your bookshelf.",
                            action = {
                                OutlinedButton(onClick = { importLauncher.launch(importMimeTypes) }) {
                                    Text("Add your first document")
                                }
                            }
                        )
                    }

                appPrefs.libraryView == LibraryViewMode.GRID -> LibraryGrid(
                    vm = vm,
                    state = state,
                    filter = filter,
                    sort = appPrefs.librarySort,
                    onOpenDocument = onOpenDocument,
                    onOpenDetails = onOpenDetails,
                    onRemove = { removeTarget = it },
                    bottomPadding = fabPadding.calculateBottomPadding(),
                )

                else -> LibraryList(
                    vm = vm,
                    state = state,
                    filter = filter,
                    sort = appPrefs.librarySort,
                    onOpenDocument = onOpenDocument,
                    onOpenDetails = onOpenDetails,
                    onRemove = { removeTarget = it },
                    bottomPadding = fabPadding.calculateBottomPadding(),
                )
            }
        }
    }

    removeTarget?.let { doc ->
        ConfirmDialog(
            title = "Remove from library",
            text = "\"${doc.title}\" and its copy will be removed from READR. The original file is untouched.",
            confirmLabel = "Remove",
            danger = true,
            onConfirm = { vm.remove(doc.id) },
            onDismiss = { removeTarget = null },
        )
    }
}

@Composable
private fun ViewToggle(vm: LibraryViewModel, mode: LibraryViewMode) {
    IconButton(onClick = {
        vm.setView(
            if (mode == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID
        )
    }) {
        Icon(
            if (mode == LibraryViewMode.GRID) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
            contentDescription = "Toggle view"
        )
    }
}

@Composable
private fun FilterBar(
    vm: LibraryViewModel,
    filter: DocumentFormat?,
    sort: LibrarySort,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter == null,
            onClick = { vm.setFilter(null) },
            label = { Text("All") },
        )
        for (format in DocumentFormat.entries) {
            FilterChip(
                selected = filter == format,
                onClick = { vm.setFilter(format) },
                label = { Text(format.label) },
            )
        }
        Spacer(Modifier.weight(1f))
        SortMenu(vm = vm, sort = sort)
    }
}

@Composable
private fun SortMenu(vm: LibraryViewModel, sort: LibrarySort) {
    Box {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Sort, contentDescription = "Sort")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in LibrarySort.entries) {
                DropdownMenuItem(
                    text = { Text(option.label + if (option == sort) "  ✓" else "") },
                    onClick = {
                        expanded = false
                        vm.setSort(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun ContinueReadingRow(state: LibraryUiState, onOpenDocument: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.continueReading, key = { it.id }) { doc ->
            ContinueCard(doc, onClick = { onOpenDocument(doc.id) })
        }
    }
}

@Composable
private fun FavoritesRow(state: LibraryUiState, onOpenDocument: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.favorites, key = { it.id }) { doc ->
            ContinueCard(doc, onClick = { onOpenDocument(doc.id) })
        }
    }
}

@Composable
private fun LibraryGrid(
    vm: LibraryViewModel,
    state: LibraryUiState,
    filter: DocumentFormat?,
    sort: LibrarySort,
    onOpenDocument: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onRemove: (ReadrDocument) -> Unit,
    bottomPadding: Dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.continueReading.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    SectionHeader("Continue Reading")
                    ContinueReadingRow(state, onOpenDocument)
                }
            }
        }
        if (state.favorites.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    SectionHeader("Favorites")
                    FavoritesRow(state, onOpenDocument)
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Column {
                SectionHeader("All Documents")
                FilterBar(vm, filter, sort)
            }
        }
        items(state.documents, key = { it.id }) { doc ->
            DocumentCard(
                document = doc,
                onClick = { onOpenDocument(doc.id) },
                onDetails = { onOpenDetails(doc.id) },
                onFavorite = { vm.toggleFavorite(doc.id) },
                onRemove = { onRemove(doc) },
            )
        }
    }
}

@Composable
private fun LibraryList(
    vm: LibraryViewModel,
    state: LibraryUiState,
    filter: DocumentFormat?,
    sort: LibrarySort,
    onOpenDocument: (Long) -> Unit,
    onOpenDetails: (Long) -> Unit,
    onRemove: (ReadrDocument) -> Unit,
    bottomPadding: Dp,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.continueReading.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Continue Reading")
                    ContinueReadingRow(state, onOpenDocument)
                }
            }
        }
        if (state.favorites.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Favorites")
                    FavoritesRow(state, onOpenDocument)
                }
            }
        }
        item {
            Column {
                SectionHeader("All Documents")
                FilterBar(vm, filter, sort)
            }
        }
        items(state.documents, key = { it.id }) { doc ->
            DocumentRow(
                document = doc,
                onClick = { onOpenDocument(doc.id) },
                onDetails = { onOpenDetails(doc.id) },
                onFavorite = { vm.toggleFavorite(doc.id) },
                onRemove = { onRemove(doc) },
            )
        }
    }
}

@Composable
private fun ContinueCard(doc: ReadrDocument, onClick: () -> Unit) {
    Column(Modifier.width(104.dp).clickable(onClick = onClick)) {
        Box {
            CoverImage(doc, Modifier.fillMaxWidth().aspectRatio(0.7f))
            if (doc.favorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
        }
        Text(
            doc.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        LinearProgressIndicator(
            progress = { doc.progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(4.dp),
        )
        Text(
            "${(doc.progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun DocumentCard(
    document: ReadrDocument,
    onClick: () -> Unit,
    onDetails: () -> Unit,
    onFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box {
            CoverImage(
                document,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
            )
            Box(Modifier.align(Alignment.TopEnd)) {
                CardMenu(onDetails, onFavorite, document.favorite, onRemove)
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(
                document.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2,
            )
            Text(
                FileUtils.humanSize(document.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (document.progress > 0.01f) {
                LinearProgressIndicator(
                    progress = { document.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(4.dp),
                )
            }
        }
    }
}

@Composable
private fun DocumentRow(
    document: ReadrDocument,
    onClick: () -> Unit,
    onDetails: () -> Unit,
    onFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(document, Modifier.width(58.dp).height(80.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    FormatBadge(document.format)
                    Text(
                        FileUtils.humanSize(document.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    document.lastOpenedAt?.let {
                        Text(
                            TimeAgo.format(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (document.progress > 0.01f) {
                    LinearProgressIndicator(
                        progress = { document.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(4.dp),
                    )
                }
            }
            CardMenu(onDetails, onFavorite, document.favorite, onRemove)
        }
    }
}

@Composable
private fun CardMenu(
    onDetails: () -> Unit,
    onFavorite: () -> Unit,
    favorite: Boolean,
    onRemove: () -> Unit,
) {
    Box {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Details") },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = { expanded = false; onDetails() },
            )
            DropdownMenuItem(
                text = { Text(if (favorite) "Remove favorite" else "Add favorite") },
                leadingIcon = {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                    )
                },
                onClick = { expanded = false; onFavorite() },
            )
            DropdownMenuItem(
                text = { Text("Remove from library") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = { expanded = false; onRemove() },
            )
        }
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
