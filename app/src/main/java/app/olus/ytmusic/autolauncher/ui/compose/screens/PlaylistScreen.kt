package app.olus.ytmusic.autolauncher.ui.compose.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.olus.ytmusic.autolauncher.YTMusicAutoLauncherApp
import app.olus.ytmusic.autolauncher.domain.model.Playlist
import app.olus.ytmusic.autolauncher.R
import app.olus.ytmusic.autolauncher.ui.compose.theme.YTRed
import app.olus.ytmusic.autolauncher.ui.compose.theme.YTRedSoft
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ──────────────────────────────────────────────────────────────────────────────
// Main Screen
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(viewModel: PlaylistViewModel) {
    val playlists by viewModel.playlists.collectAsState()
    val addState by viewModel.addPlaylistState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Playlist?>(null) }
    var showEditDialog by remember { mutableStateOf<Playlist?>(null) }

    val context = LocalContext.current
    val app = context.applicationContext as YTMusicAutoLauncherApp

    // Handle shared URL from intent
    LaunchedEffect(Unit) {
        app.sharedUrlToProcess?.let { url ->
            viewModel.handleSharedUrl(url)
            app.sharedUrlToProcess = null
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.yt_playlists),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        if (playlists.isNotEmpty()) {
                            Text(
                                if (playlists.size == 1) stringResource(R.string.playlist_count_singular, playlists.size) else stringResource(R.string.playlist_count_plural, playlists.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = YTRed,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .padding(
                        bottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()
                    )
                    .shadow(12.dp, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_playlist_title))
            }
        }
    ) { paddingValues ->
        val isRefreshing by viewModel.isRefreshing.collectAsState()

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshAll() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (playlists.isEmpty()) {
                EmptyState()
            } else {
                DraggablePlaylistList(
                    playlists = playlists,
                    paddingValues = PaddingValues(0.dp),
                    onSaveOrder = { viewModel.savePlaylistOrder(it) },
                    onDelete = { showDeleteDialog = it },
                    onEditTitle = { showEditDialog = it },
                    onRefreshMetadata = { viewModel.refreshPlaylistMetadata(it) }
                )
            }
        }
    }

    // Dialogs
    if (showAddDialog) {
        AddPlaylistDialog(
            viewModel = viewModel,
            onDismiss = {
                showAddDialog = false
                viewModel.resetAddPlaylistState()
            },
            onSave = {
                viewModel.addPlaylistAndFetch(addState.url)
                showAddDialog = false
            }
        )
    }

    showDeleteDialog?.let { playlist ->
        DeleteConfirmationDialog(
            playlistTitle = playlist.title,
            onConfirm = {
                viewModel.deletePlaylist(playlist)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    showEditDialog?.let { playlist ->
        EditTitleDialog(
            currentTitle = playlist.title,
            onConfirm = { newTitle ->
                viewModel.updatePlaylistTitle(playlist, newTitle)
                showEditDialog = null
            },
            onDismiss = { showEditDialog = null }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Empty State
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                YTRed.copy(alpha = 0.15f),
                                YTRed.copy(alpha = 0.05f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = YTRed.copy(alpha = 0.6f)
                )
            }
            Text(
                text = stringResource(R.string.no_playlists),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.add_playlist_instruction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Draggable Playlist List
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun DraggablePlaylistList(
    playlists: List<Playlist>,
    paddingValues: PaddingValues,
    onSaveOrder: (List<Playlist>) -> Unit,
    onDelete: (Playlist) -> Unit,
    onEditTitle: (Playlist) -> Unit,
    onRefreshMetadata: (Playlist) -> Unit
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Drag state
    var displayList by remember(playlists) { mutableStateOf(playlists) }
    var draggedItemIndex by remember { mutableIntStateOf(-1) }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .pointerInput(displayList) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val item = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { info ->
                                offset.y.toInt() in info.offset..(info.offset + info.size)
                            }
                        if (item != null) {
                            draggedItemIndex = item.index
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (draggedItemIndex >= 0) {
                            val currentY = change.position.y
                            val targetItem = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { info ->
                                    currentY.toInt() in info.offset..(info.offset + info.size)
                                }

                            if (targetItem != null && targetItem.index != draggedItemIndex) {
                                val list = displayList.toMutableList()
                                val item = list.removeAt(draggedItemIndex)
                                list.add(targetItem.index, item)
                                displayList = list
                                draggedItemIndex = targetItem.index
                            }

                            // Auto-scroll near edges
                            val layoutInfo = listState.layoutInfo
                            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                            val scrollThreshold = viewportHeight * 0.15f
                            val itemCenter = currentY.toInt()

                            scope.launch {
                                when {
                                    itemCenter < layoutInfo.viewportStartOffset + scrollThreshold -> {
                                        listState.scrollToItem(maxOf(0, listState.firstVisibleItemIndex - 1))
                                    }
                                    itemCenter > layoutInfo.viewportEndOffset - scrollThreshold -> {
                                        listState.scrollToItem(minOf(displayList.size - 1, listState.firstVisibleItemIndex + 1))
                                    }
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggedItemIndex = -1
                        onSaveOrder(displayList)
                    },
                    onDragCancel = {
                        draggedItemIndex = -1
                        displayList = playlists
                    }
                )
            },
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 96.dp // Extra space for FAB
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = displayList,
            key = { _, playlist -> playlist.id }
        ) { index, playlist ->
            val isBeingDragged = index == draggedItemIndex

            val elevation by animateDpAsState(
                targetValue = if (isBeingDragged) 16.dp else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "elevation"
            )
            val scale by animateFloatAsState(
                targetValue = if (isBeingDragged) 1.03f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "scale"
            )

            // Staggered entrance animation
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(index * 50L)
                visible = true
            }

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically { it / 3 }
            ) {
                PlaylistItem(
                    playlist = playlist,
                    onDelete = { onDelete(playlist) },
                    onEditTitle = { onEditTitle(playlist) },
                    isDragging = isBeingDragged,
                    modifier = Modifier
                        .animateItem()
                        .zIndex(if (isBeingDragged) 1f else 0f)
                        .scale(scale)
                        .shadow(elevation, RoundedCornerShape(16.dp))
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Playlist Item Card
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onDelete: () -> Unit,
    onEditTitle: () -> Unit,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.drag_to_reorder),
                modifier = Modifier
                    .size(24.dp)
                    .alpha(0.4f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Thumbnail with gradient overlay
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (playlist.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = playlist.imageUrl,
                        contentDescription = playlist.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Subtle gradient overlay at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.3f)
                                    ),
                                    startY = 0.5f * 72f // Start gradient at 50%
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        YTRed.copy(alpha = 0.2f),
                                        YTRedSoft.copy(alpha = 0.1f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = YTRed.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val metaText = listOfNotNull(playlist.trackCount, playlist.duration)
                    .joinToString(" • ")
                if (metaText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onEditTitle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(18.dp),
                        tint = YTRed.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Dialogs
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun EditTitleDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTitle by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(R.string.edit_playlist_title), fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                label = { Text(stringResource(R.string.playlist_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = YTRed,
                    focusedLabelColor = YTRed,
                    cursorColor = YTRed
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(newTitle) }) {
                Text(stringResource(R.string.save), color = YTRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun AddPlaylistDialog(
    viewModel: PlaylistViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val state by viewModel.addPlaylistState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(R.string.add_playlist_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = state.url,
                    onValueChange = { viewModel.updateUrl(it) },
                    label = { Text(stringResource(R.string.enter_url_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YTRed,
                        focusedLabelColor = YTRed,
                        cursorColor = YTRed
                    )
                )

            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = state.url.isNotEmpty()
            ) {
                Text(
                    stringResource(R.string.add),
                    color = if (state.url.isNotEmpty()) YTRed else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    playlistTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(R.string.delete_playlist_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Text(stringResource(R.string.delete_confirmation_text, playlistTitle))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = YTRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
