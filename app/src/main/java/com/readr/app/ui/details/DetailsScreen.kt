package com.readr.app.ui.details

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readr.app.data.db.BookmarkEntity
import com.readr.app.di.AppContainer
import com.readr.app.domain.model.ReadrDocument
import com.readr.app.ui.components.ConfirmDialog
import com.readr.app.ui.components.CoverImage
import com.readr.app.ui.components.FormatBadge
import com.readr.app.util.FileUtils
import com.readr.app.util.TimeAgo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailsViewModel(
    container: AppContainer,
    documentId: Long,
) : ViewModel() {
    private val repo = container.documentRepository

    val document: StateFlow<ReadrDocument?> = repo.observeById(documentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        repo.observeBookmarks(documentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(id, favorite) }
    }

    fun remove(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.remove(id)
            onDone()
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repo.deleteBookmark(id) }
    }
}

/**
 * Document details page: cover, metadata, progress, bookmarks and actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    documentId: Long,
    onOpen: () -> Unit,
    onBack: () -> Unit,
) {
    val container = AppContainer.Local.current
    val vm: DetailsViewModel = viewModel(
        key = "details_$documentId",
        factory = viewModelFactory { initializer { DetailsViewModel(container, documentId) } },
    )
    val doc by vm.document.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf(false) }

    val current = doc
    if (current == null) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Details", style = MaterialTheme.typography.titleMedium)
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CoverImage(
                current,
                Modifier
                    .width(200.dp)
                    .height(280.dp)
                    .padding(0.dp),
                cornerRadius = 14.dp,
            )
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                current.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 18.dp),
            )
            current.author?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 14.dp),
            ) {
                FormatBadge(current.format)
                MetaChip(FileUtils.humanSize(current.sizeBytes))
                current.lengthLabel?.let { MetaChip(it) }
                MetaChip("Added ${TimeAgo.format(current.addedAt)}")
                current.lastOpenedAt?.let { MetaChip("Opened ${TimeAgo.format(it)}") }
            }

            if (current.progress > 0.001f) {
                Text(
                    "Reading progress · ${(current.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                LinearProgressIndicator(
                    progress = { current.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(6.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text(if (current.progress > 0.001f) "Continue reading" else "Start reading")
                }
                IconButton(onClick = { vm.toggleFavorite(current.id, !current.favorite) }) {
                    Icon(
                        if (current.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (current.favorite) "Remove favorite" else "Add favorite",
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            OutlinedButton(
                onClick = { confirmRemove = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Remove from library")
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            Text("Bookmarks", style = MaterialTheme.typography.titleMedium)
            if (bookmarks.isEmpty()) {
                Text(
                    "No bookmarks yet. While reading, tap the bookmark icon to save the page you are on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                for (bookmark in bookmarks) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        ) {
                            Text(
                                bookmark.preview.ifBlank { "Bookmark" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                bookmarkLabel(bookmark),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.deleteBookmark(bookmark.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove from library",
            text = "\"${current.title}\" and its copy will be removed from READR. The original file is untouched.",
            confirmLabel = "Remove",
            danger = true,
            onConfirm = { vm.remove(current.id) { onBack() } },
            onDismiss = { confirmRemove = false },
        )
    }
}

private fun bookmarkLabel(bookmark: BookmarkEntity): String = when {
    bookmark.page != null -> "Page ${bookmark.page + 1}"
    bookmark.chapterIndex != null -> "Chapter ${bookmark.chapterIndex + 1}"
    else -> "Bookmark"
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
