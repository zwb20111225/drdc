package me.weishu.kernelsu.ui.screen.files

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Theme-agnostic color set so the shared file-manager body works in both UI modes. */
data class FilesPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val outline: Color,
    val divider: Color,
    val error: Color,
    val onError: Color,
)

@Composable
private fun materialFilesPalette(): FilesPalette {
    val c = MaterialTheme.colorScheme
    return FilesPalette(
        background = c.background,
        surface = c.surface,
        surfaceVariant = c.surfaceVariant,
        onSurface = c.onSurface,
        onSurfaceVariant = c.onSurfaceVariant,
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.primaryContainer,
        onPrimaryContainer = c.onPrimaryContainer,
        secondaryContainer = c.secondaryContainer,
        onSecondaryContainer = c.onSecondaryContainer,
        outline = c.outline,
        divider = c.outlineVariant,
        error = c.error,
        onError = c.onError,
    )
}

@Composable
private fun miuixFilesPalette(): FilesPalette {
    val c = MiuixTheme.colorScheme
    return FilesPalette(
        background = c.background,
        surface = c.surface,
        surfaceVariant = c.surfaceVariant,
        onSurface = c.onSurface,
        onSurfaceVariant = c.onSurfaceVariantSummary,
        primary = c.primary,
        onPrimary = c.onPrimary,
        primaryContainer = c.primaryContainer,
        onPrimaryContainer = c.onPrimaryContainer,
        secondaryContainer = c.secondaryContainer,
        onSecondaryContainer = c.onSecondaryContainer,
        outline = c.outline,
        divider = c.dividerLine,
        error = c.error,
        onError = c.onError,
    )
}

@Composable
fun FilesPager(
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
    viewModel: FilesViewModel = viewModel(),
) {
    val context = LocalContext.current
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val dialog = viewModel.dialog
    val selection = viewModel.activePaneState().selection

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isCurrentPage && (dialog != FilesDialog.None || selection.isNotEmpty()),
        onBackCompleted = {
            when {
                viewModel.dialog != FilesDialog.None -> viewModel.dismissDialog()
                else -> viewModel.clearAllSelections()
            }
        },
    )

    LaunchedEffect(Unit) {
        for (message in viewModel.messages) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            val palette = miuixFilesPalette()
            MiuixScaffold(
                topBar = {
                    MiuixTopAppBar(
                        title = stringResource(R.string.files_title),
                        actions = {
                            MiuixIconButton(onClick = { viewModel.refreshAll() }) {
                                MiuixIcon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.files_refresh),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            MiuixIconButton(onClick = { viewModel.showMore() }) {
                                MiuixIcon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.files_more_actions),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                },
                contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal),
            ) { innerPadding ->
                FilesBody(viewModel, palette, innerPadding, bottomInnerPadding)
            }
        }

        UiMode.Material -> {
            val palette = materialFilesPalette()
            ExpressiveScaffold(
                topBar = {
                    LargeFlexibleTopAppBar(
                        title = { MaterialText(stringResource(R.string.files_title)) },
                        colors = expressiveTopAppBarColors(),
                        actions = {
                            MaterialIconButton(onClick = { viewModel.refreshAll() }) {
                                MaterialIcon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.files_refresh),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            MaterialIconButton(onClick = { viewModel.showMore() }) {
                                MaterialIcon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.files_more_actions),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                FilesBody(viewModel, palette, innerPadding, bottomInnerPadding)
            }
        }
    }
}

@Composable
private fun FilesBody(
    viewModel: FilesViewModel,
    palette: FilesPalette,
    innerPadding: PaddingValues,
    bottomInnerPadding: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(bottom = bottomInnerPadding)
            .background(palette.background),
    ) {
        when (viewModel.rootStatus) {
            FilesViewModel.RootStatus.Checking -> RootGate(
                palette = palette,
                message = stringResource(R.string.files_root_checking),
            )

            FilesViewModel.RootStatus.Denied -> RootGate(
                palette = palette,
                message = stringResource(R.string.files_root_denied),
                retryLabel = stringResource(R.string.files_root_retry),
                onRetry = viewModel::retryRoot,
            )

            FilesViewModel.RootStatus.Granted -> {
                Column(Modifier.fillMaxSize()) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        val sideBySide = maxWidth >= 600.dp
                        if (sideBySide) {
                            Row(Modifier.fillMaxSize()) {
                                FilesPane(viewModel, 0, palette, Modifier.weight(1f).fillMaxHeight())
                                Box(Modifier.fillMaxHeight().width(1.dp).background(palette.divider))
                                FilesPane(viewModel, 1, palette, Modifier.weight(1f).fillMaxHeight())
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                FilesPane(viewModel, 0, palette, Modifier.weight(1f).fillMaxWidth())
                                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
                                FilesPane(viewModel, 1, palette, Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                    SelectionBar(viewModel, palette)
                }
            }
        }

        FilesDialogHost(viewModel, palette)
    }
}

@Composable
private fun RootGate(
    palette: FilesPalette,
    message: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (onRetry == null) {
            CircularProgressIndicator(color = palette.primary)
        } else {
            MaterialIcon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = palette.error,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        MaterialText(
            text = message,
            fontSize = 14.sp,
            color = palette.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        if (retryLabel != null && onRetry != null) {
            Spacer(Modifier.height(16.dp))
            FilesButton(palette, retryLabel, onClick = onRetry, primary = true)
        }
    }
}

@Composable
private fun FilesPane(
    viewModel: FilesViewModel,
    index: Int,
    palette: FilesPalette,
    modifier: Modifier,
) {
    val pane = viewModel.panes[index]
    val isActive = viewModel.activePane == index
    Column(modifier.background(if (isActive) palette.surface else palette.background)) {
        BreadcrumbBar(viewModel, index, palette, pane.path, isActive)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                pane.loading && pane.entries.isEmpty() ->
                    CenterText(palette, stringResource(R.string.files_loading))

                pane.error != null && pane.entries.isEmpty() ->
                    CenterText(palette, pane.error, isError = true)

                pane.entries.isEmpty() ->
                    CenterText(palette, stringResource(R.string.files_empty_dir))

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    // No explicit key: a directory listing can contain duplicate names
                    // (bind mounts, case-insensitive views) and LazyColumn throws on a
                    // repeated key, which used to kill the app when opening this page.
                    items(pane.entries) { entry ->
                        FileRow(viewModel, index, entry, pane, palette)
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterText(palette: FilesPalette, text: String, isError: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        MaterialText(
            text = text,
            fontSize = 13.sp,
            color = if (isError) palette.error else palette.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BreadcrumbBar(
    viewModel: FilesViewModel,
    index: Int,
    palette: FilesPalette,
    path: String,
    isActive: Boolean,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(path) {
        scroll.scrollTo(Int.MAX_VALUE.coerceAtMost(1 shl 20))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) palette.primaryContainer.copy(alpha = 0.35f) else palette.surfaceVariant.copy(alpha = 0.35f))
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Crumb("/", palette, isActive) {
                viewModel.activatePane(index)
                viewModel.refreshPane(index, "/")
            }
            val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
            var accumulated = ""
            segments.forEach { segment ->
                accumulated += "/$segment"
                val target = accumulated
                Crumb(segment, palette, isActive) {
                    viewModel.activatePane(index)
                    viewModel.refreshPane(index, target)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    viewModel.activatePane(index)
                    viewModel.openJump()
                }
                .padding(4.dp),
        ) {
            MaterialIcon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.files_jump),
                tint = if (isActive) palette.onPrimaryContainer else palette.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun Crumb(
    text: String,
    palette: FilesPalette,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    MaterialText(
        text = text,
        fontSize = 13.sp,
        color = if (isActive) palette.onPrimaryContainer else palette.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun FileRow(
    viewModel: FilesViewModel,
    index: Int,
    entry: FileEntry,
    pane: FilesViewModel.PaneState,
    palette: FilesPalette,
) {
    val selected = entry.name in pane.selection
    val selectionMode = pane.selection.isNotEmpty()
    val subtitle = buildString {
        if (!entry.isDirectory) {
            append(formatFileSize(entry.size))
            append(" · ")
        }
        append(entry.perm)
        append(" · ")
        append(entry.date)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) palette.primary.copy(alpha = 0.14f) else Color.Transparent)
            .combinedClickable(
                onClick = { viewModel.openEntry(index, entry) },
                onLongClick = { viewModel.toggleSelect(index, entry.name) },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialIcon(
            imageVector = entryIcon(entry),
            contentDescription = null,
            tint = when {
                entry.isDirectory -> palette.primary
                entry.isSymlink -> palette.onSurfaceVariant
                else -> palette.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            MaterialText(
                text = entry.name,
                fontSize = 14.sp,
                color = palette.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MaterialText(
                text = subtitle,
                fontSize = 11.sp,
                color = palette.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selectionMode) {
            Spacer(Modifier.width(8.dp))
            MaterialIcon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) palette.primary else palette.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal fun entryIcon(entry: FileEntry): ImageVector = when {
    entry.isDirectory -> Icons.Rounded.Folder
    entry.isSymlink -> Icons.Rounded.Link
    entry.isApk -> Icons.Rounded.Android
    entry.isArchive -> Icons.Rounded.FolderZip
    isTextCandidate(entry) -> Icons.Rounded.Description
    else -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

@Composable
private fun SelectionBar(viewModel: FilesViewModel, palette: FilesPalette) {
    val selected = viewModel.selectedEntries()
    if (selected.isEmpty()) return
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionChip(palette, Icons.Rounded.ContentCopy, stringResource(R.string.files_copy_to)) {
                viewModel.startTransfer(move = false)
            }
            ActionChip(palette, Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.files_move_to)) {
                viewModel.startTransfer(move = true)
            }
            ActionChip(palette, Icons.Rounded.Delete, stringResource(R.string.files_delete)) {
                viewModel.openDelete()
            }
            if (selected.size == 1) {
                ActionChip(palette, Icons.Rounded.DriveFileRenameOutline, stringResource(R.string.files_rename)) {
                    viewModel.openRename()
                }
                ActionChip(palette, Icons.Rounded.Lock, stringResource(R.string.files_permission)) {
                    viewModel.openChmod()
                }
                ActionChip(palette, Icons.Rounded.Person, stringResource(R.string.files_chown)) {
                    viewModel.openChown()
                }
            }
            ActionChip(palette, Icons.Rounded.FolderZip, stringResource(R.string.files_compress)) {
                viewModel.openCompress()
            }
            if (selected.any { it.isArchive }) {
                ActionChip(palette, Icons.Rounded.Unarchive, stringResource(R.string.files_extract)) {
                    viewModel.openExtract()
                }
            }
            if (selected.size == 1) {
                ActionChip(palette, Icons.Rounded.Info, stringResource(R.string.files_properties)) {
                    viewModel.openProperties(selected.first())
                }
            }
            ActionChip(palette, Icons.Rounded.Close, stringResource(R.string.files_clear_selection)) {
                viewModel.clearAllSelections()
            }
        }
    }
}

@Composable
private fun ActionChip(
    palette: FilesPalette,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialIcon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.onSecondaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        MaterialText(
            text = label,
            fontSize = 12.sp,
            color = palette.onSecondaryContainer,
            maxLines = 1,
        )
    }
}
