package app.olus.ytmusic.autolauncher.ui.compose.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import app.olus.ytmusic.autolauncher.util.AALogger
import app.olus.ytmusic.autolauncher.domain.model.Playlist
import app.olus.ytmusic.autolauncher.R
import app.olus.ytmusic.autolauncher.ui.compose.theme.YTRed
import app.olus.ytmusic.autolauncher.ui.compose.theme.YTRedSoft
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import org.json.JSONObject
import org.jsoup.Jsoup
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import kotlinx.coroutines.isActive
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Shadow
import app.olus.ytmusic.autolauncher.ui.compose.components.JellyfinBrowseDialog

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
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    var showJellyfinBrowse by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val app = context.applicationContext as YTMusicAutoLauncherApp

    // Handle shared URL from intent
    LaunchedEffect(Unit) {
        app.sharedUrlToProcess?.let { url ->
            viewModel.handleSharedUrl(url)
            app.sharedUrlToProcess = null
        }
    }

    // Auto-open lyrics when requested from background track change
    val lyricsTriggerCount by app.triggerLyricsDialog.collectAsState()
    LaunchedEffect(lyricsTriggerCount) {
        if (lyricsTriggerCount > 0) {
            showLyricsDialog = true
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val currentMetadata by viewModel.currentMetadata.collectAsState()
    val currentPlaybackState by viewModel.currentPlaybackState.collectAsState()
    val lyricsState by viewModel.lyricsState.collectAsState()

    val sheetState = androidx.compose.material3.rememberStandardBottomSheetState(
        initialValue = androidx.compose.material3.SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = androidx.compose.material3.rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.yt_playlists),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
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
                actions = {
                    AnimatedVisibility(visible = currentMetadata != null) {
                        IconButton(onClick = { showLyricsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = stringResource(R.string.lyrics_button),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
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
        // Permission checks
        val lifecycleOwner = LocalLifecycleOwner.current
        var hasNotificationAccess by remember { mutableStateOf(true) }
        var hasOverlayPermission by remember { mutableStateOf(true) }

        fun checkPermissions() {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            hasNotificationAccess = enabledPackages.contains(context.packageName)
            hasOverlayPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        LaunchedEffect(Unit) { checkPermissions() }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) { checkPermissions() }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Notification access banner
            AnimatedVisibility(visible = !hasNotificationAccess) {
                NotificationAccessBanner(
                    onGrantClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            // Overlay permission banner
            AnimatedVisibility(visible = hasNotificationAccess && !hasOverlayPermission) {
                OverlayPermissionBanner(
                    onGrantClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (playlists.isEmpty()) {
                    EmptyState()
                } else {
                    DraggablePlaylistList(
                        playlists = playlists,
                        paddingValues = PaddingValues(0.dp),
                        onSaveOrder = { viewModel.savePlaylistOrder(it) },
                        onDelete = { showDeleteDialog = it },
                        onEditPlaylist = { showEditDialog = it },
                        onRefreshMetadata = { viewModel.forceRefreshPlaylist(it) }
                    )
                }
            }
            // Compact Now Playing Bar
            if (currentMetadata != null) {
                CompactNowPlayingBar(
                    metadata = currentMetadata,
                    playbackState = currentPlaybackState,
                    modifier = Modifier.clickable { showLyricsDialog = true }
                )
            }
        } // End of Column
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
            },
            onJellyfinBrowse = {
                showAddDialog = false
                showJellyfinBrowse = true
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
        EditPlaylistDialog(
            playlist = playlist,
            onConfirm = { newTitle, newImageUrl ->
                viewModel.updatePlaylistDetails(playlist, newTitle, newImageUrl)
                showEditDialog = null
            },
            onDismiss = { showEditDialog = null }
        )
    }

    // Settings Dialog (Jellyfin + Diagnostics)
    if (showSettingsDialog) {
        SettingsDialog(
            jellyfinRepository = viewModel.jellyfinRepository,
            onJellyfinConnected = { /* connection saved internally */ },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Jellyfin Browse Dialog
    if (showJellyfinBrowse) {
        JellyfinBrowseDialog(
            jellyfinRepository = viewModel.jellyfinRepository,
            onItemSelected = { item ->
                viewModel.importJellyfinItem(item)
                showJellyfinBrowse = false
            },
            onDismiss = { showJellyfinBrowse = false }
        )
    }

    if (showLyricsDialog && currentMetadata != null) {
        LyricsDialog(
            metadata = currentMetadata,
            playbackState = currentPlaybackState,
            lyricsState = lyricsState,
            onDismiss = { showLyricsDialog = false }
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Notification Access Banner
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun NotificationAccessBanner(onGrantClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = YTRed.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = YTRed,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notification_access_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.notification_access_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onGrantClick) {
                Text(
                    text = stringResource(R.string.notification_access_button),
                    color = YTRed,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Compact Now Playing Bar
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun CompactNowPlayingBar(
    metadata: android.media.MediaMetadata?,
    playbackState: android.media.session.PlaybackState?,
    modifier: Modifier = Modifier
) {
    if (metadata == null) return
    
    val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unbekannt"
    val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "Unbekannt"
    val artUri = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ART_URI)
    val isPlaying = playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (artUri != null) {
                AsyncImage(
                    model = artUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isPlaying) YTRed else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) YTRed.copy(alpha = 0.2f) else Color.Transparent)
                    .padding(6.dp)
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Overlay Permission Banner
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun OverlayPermissionBanner(onGrantClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = YTRed.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = YTRed,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.overlay_permission_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.overlay_permission_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onGrantClick) {
                Text(
                    text = stringResource(R.string.notification_access_button),
                    color = YTRed,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
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
    onEditPlaylist: (Playlist) -> Unit,
    onRefreshMetadata: (Playlist) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var displayList by remember { mutableStateOf(playlists) }
    
    LaunchedEffect(playlists) {
        displayList = playlists
    }

    val state = rememberReorderableLazyListState(
        onMove = { from, to ->
            displayList = displayList.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onDragEnd = { startIndex, endIndex ->
            onSaveOrder(displayList)
        }
    )

    LazyColumn(
        state = state.listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .reorderable(state),
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
            ReorderableItem(state, key = playlist.id) { isDragging ->
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 16.dp else 0.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "elevation"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.03f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "scale"
                )

                PlaylistItem(
                    playlist = playlist,
                    onDelete = { onDelete(playlist) },
                    onEditTitle = { onEditPlaylist(playlist) },
                    onRefreshMetadata = { onRefreshMetadata(playlist) },
                    isDragging = isDragging,
                    dragModifier = Modifier.detectReorder(state),
                    modifier = Modifier
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
    onRefreshMetadata: () -> Unit,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
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
                modifier = dragModifier
                    .size(36.dp)
                    .padding(6.dp)
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

                val metaParts = mutableListOf<String>()
                if (playlist.source == "JELLYFIN") metaParts.add("JF")
                playlist.trackCount?.let { metaParts.add(it) }
                playlist.duration?.let { metaParts.add(it) }
                val metaText = metaParts.joinToString(" • ")
                if (metaText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (playlist.source == "JELLYFIN") 
                            MaterialTheme.colorScheme.secondary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
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
                    onClick = onRefreshMetadata,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
fun EditPlaylistDialog(
    playlist: Playlist,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTitle by remember { mutableStateOf(playlist.title) }
    var newImageUrl by remember { mutableStateOf(playlist.imageUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(R.string.edit_playlist_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                OutlinedTextField(
                    value = newImageUrl,
                    onValueChange = { newImageUrl = it },
                    label = { Text("Bild-URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YTRed,
                        focusedLabelColor = YTRed,
                        cursorColor = YTRed
                    )
                )

                var showSearchDialog by remember { mutableStateOf(false) }
                
                TextButton(
                    onClick = { showSearchDialog = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.search_cover_web), color = MaterialTheme.colorScheme.onSurface)
                }

                if (showSearchDialog) {
                    SearchCoverDialog(
                        initialQuery = newTitle,
                        onImageSelected = { url -> 
                            newImageUrl = url
                            showSearchDialog = false 
                        },
                        onDismiss = { showSearchDialog = false }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(newTitle, newImageUrl) }) {
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
    onSave: () -> Unit,
    onJellyfinBrowse: () -> Unit = {}
) {
    val state by viewModel.addPlaylistState.collectAsState()
    val jellyfinConfigured = viewModel.jellyfinRepository.isConfigured

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(stringResource(R.string.add_playlist_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                if (jellyfinConfigured) {
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            onDismiss()
                            onJellyfinBrowse()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Aus Jellyfin importieren", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
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

// ──────────────────────────────────────────────────────────────────────────────
// Search Cover Dialog (In-App iTunes API)
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SearchCoverDialog(
    initialQuery: String,
    onImageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun performSearch() {
        if (query.isBlank()) return
        isLoading = true
        hasSearched = true
        scope.launch(Dispatchers.IO) {
            try {
                val encodedQuery = android.net.Uri.encode(query)
                
                coroutineScope {
                val itunesAlbumDef = async {
                    val url = "https://itunes.apple.com/search?term=$encodedQuery&entity=album&limit=30"
                    val jsonStr = Jsoup.connect(url).ignoreContentType(true).execute().body()
                    val images = mutableListOf<String>()
                    val resultsArray = JSONObject(jsonStr).optJSONArray("results")
                    if (resultsArray != null) {
                        for (i in 0 until resultsArray.length()) {
                            val artwork = resultsArray.optJSONObject(i)?.optString("artworkUrl100")
                            if (!artwork.isNullOrEmpty()) images.add(artwork.replace("100x100bb.jpg", "1000x1000bb.jpg"))
                        }
                    }
                    images
                }

                val deezerDef = async {
                    val url = "https://api.deezer.com/search/album?q=$encodedQuery&limit=30"
                    val jsonStr = Jsoup.connect(url).ignoreContentType(true).execute().body()
                    val images = mutableListOf<String>()
                    val dataArray = JSONObject(jsonStr).optJSONArray("data")
                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val coverXl = dataArray.optJSONObject(i)?.optString("cover_xl")
                            if (!coverXl.isNullOrEmpty()) images.add(coverXl)
                        }
                    }
                    images
                }

                val itunesSongDef = async {
                    val url = "https://itunes.apple.com/search?term=$encodedQuery&entity=song&limit=30"
                    val jsonStr = Jsoup.connect(url).ignoreContentType(true).execute().body()
                    val images = mutableListOf<String>()
                    val resultsArray = JSONObject(jsonStr).optJSONArray("results")
                    if (resultsArray != null) {
                        for (i in 0 until resultsArray.length()) {
                            val artwork = resultsArray.optJSONObject(i)?.optString("artworkUrl100")
                            if (!artwork.isNullOrEmpty()) images.add(artwork.replace("100x100bb.jpg", "1000x1000bb.jpg"))
                        }
                    }
                    images
                }

                val allImages = mutableListOf<String>()
                try { allImages.addAll(itunesAlbumDef.await()) } catch (e: Exception) { }
                try { allImages.addAll(deezerDef.await()) } catch (e: Exception) { }
                try { allImages.addAll(itunesSongDef.await()) } catch (e: Exception) { }
                
                withContext(Dispatchers.Main) {
                    results = allImages.distinct()
                    isLoading = false
                }
                } // end coroutineScope
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (query.isNotBlank()) {
            performSearch()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Cover suchen", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = app.olus.ytmusic.autolauncher.ui.compose.theme.YTRed,
                            focusedLabelColor = app.olus.ytmusic.autolauncher.ui.compose.theme.YTRed,
                            cursorColor = app.olus.ytmusic.autolauncher.ui.compose.theme.YTRed
                        )
                    )
                    androidx.compose.material3.IconButton(onClick = { performSearch() }) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Search, 
                            contentDescription = "Suchen"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = app.olus.ytmusic.autolauncher.ui.compose.theme.YTRed)
                    }
                } else if (hasSearched && results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("Keine Ergebnisse", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(results.size) { index ->
                            val imageUrl = results[index]
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable {
                                        onImageSelected(imageUrl)
                                    },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(app.olus.ytmusic.autolauncher.R.string.cancel), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Diagnostics Dialog
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun DiagnosticsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var debugEnabled by remember { mutableStateOf(AALogger.isEnabled) }
    var logText by remember { mutableStateOf(AALogger.getLogs()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                stringResource(R.string.diagnostics_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Debug toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.debug_mode),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = debugEnabled,
                        onCheckedChange = {
                            debugEnabled = it
                            AALogger.isEnabled = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = YTRed
                        )
                    )
                }

                Text(
                    stringResource(R.string.debug_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { logText = AALogger.getLogs() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.refresh_logs), color = YTRed)
                    }
                    TextButton(
                        onClick = {
                            val logs = AALogger.getLogs()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "AA Debug Logs")
                                putExtra(Intent.EXTRA_TEXT, logs)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Logs teilen").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.share_logs), color = YTRed)
                    }
                    TextButton(
                        onClick = {
                            AALogger.clearLogs()
                            logText = AALogger.getLogs()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.clear_logs), color = MaterialTheme.colorScheme.error)
                    }
                }

                // Log viewer
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = logText,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Lyrics Dialog
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsDialog(
    metadata: android.media.MediaMetadata?,
    playbackState: android.media.session.PlaybackState?,
    lyricsState: app.olus.ytmusic.autolauncher.data.repository.LyricsState,
    onDismiss: () -> Unit
) {
    if (metadata == null) return

    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unbekannt"
    val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "Unbekannt"
    val artUri = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ART_URI)
    


    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
        ) {
            // Background Album Art with Blur
            if (artUri != null) {
                AsyncImage(
                    model = artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.6f)
                        .blur(50.dp)
                )
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.8f)))
                ))
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.padding(start = 8.dp)) {
                        Text(stringResource(R.string.close), color = Color.White)
                    }
                }

                // Lyrics Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (lyricsState) {
                        is app.olus.ytmusic.autolauncher.data.repository.LyricsState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = YTRed)
                            }
                        }
                        is app.olus.ytmusic.autolauncher.data.repository.LyricsState.Empty -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_lyrics), color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        is app.olus.ytmusic.autolauncher.data.repository.LyricsState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(lyricsState.message.ifEmpty { stringResource(R.string.error_fetch_failed) }, color = YTRed)
                            }
                        }
                        is app.olus.ytmusic.autolauncher.data.repository.LyricsState.Success -> {
                            val listState = rememberLazyListState()
                            var currentPositionMs by remember { mutableStateOf(playbackState?.position ?: 0L) }

                            if (lyricsState.isSynced) {
                                LaunchedEffect(playbackState) {
                                    if (playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) {
                                        val startPos = playbackState.position
                                        val startTime = playbackState.lastPositionUpdateTime
                                        while (isActive) {
                                            val timeDelta = android.os.SystemClock.elapsedRealtime() - startTime
                                            currentPositionMs = startPos + (timeDelta * playbackState.playbackSpeed).toLong()
                                            
                                            val activeIndex = lyricsState.lyrics.indexOfLast { it.timestampMs <= currentPositionMs }.coerceAtLeast(0)
                                            if (activeIndex >= 0 && activeIndex < lyricsState.lyrics.size) {
                                                val firstVisible = listState.firstVisibleItemIndex
                                                val lastVisible = firstVisible + listState.layoutInfo.visibleItemsInfo.size
                                                if (activeIndex < firstVisible || activeIndex > lastVisible - 3) {
                                                    listState.animateScrollToItem((activeIndex - 3).coerceAtLeast(0))
                                                }
                                            }
                                            kotlinx.coroutines.delay(100)
                                        }
                                    } else {
                                        currentPositionMs = playbackState?.position ?: 0L
                                    }
                                }
                            } else {
                                // Plain-text lyrics do not auto-scroll
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 120.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(lyricsState.lyrics) { index, line ->
                                    val isActive = if (lyricsState.isSynced) {
                                        currentPositionMs >= line.timestampMs && 
                                        (index == lyricsState.lyrics.lastIndex || currentPositionMs < lyricsState.lyrics[index + 1].timestampMs)
                                    } else {
                                        false
                                    }
                                                   
                                    val activeLineStyle = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        shadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f)
                                    )
                                    val inactiveLineStyle = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    val animationScale by animateFloatAsState(if (isActive) 1.05f else 1.0f)
                                    val animationAlpha by animateFloatAsState(if (isActive || !lyricsState.isSynced) 1.0f else 0.4f)

                                    Text(
                                        text = line.text.ifEmpty { "🎶" },
                                        style = if (isActive) activeLineStyle else inactiveLineStyle,
                                        color = Color.White.copy(alpha = animationAlpha),
                                        modifier = Modifier
                                            .padding(vertical = if (isActive) 16.dp else 10.dp)
                                            .scale(animationScale),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } // LazyColumn
                        }
                    } // When
                } // Box Lyrics Content
            } // Column
        } // Box Background
    }
}
