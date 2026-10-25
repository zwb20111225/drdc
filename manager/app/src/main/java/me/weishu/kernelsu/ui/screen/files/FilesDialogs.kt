package me.weishu.kernelsu.ui.screen.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.weishu.kernelsu.R

@Composable
internal fun FilesDialogHost(viewModel: FilesViewModel, palette: FilesPalette) {
    when (val dialog = viewModel.dialog) {
        FilesDialog.None -> Unit
        FilesDialog.More -> MoreDialog(viewModel, palette)
        is FilesDialog.Input -> InputDialog(viewModel, palette, dialog)
        is FilesDialog.Jump -> JumpDialog(viewModel, palette, dialog)
        is FilesDialog.Chmod -> ChmodDialog(viewModel, palette, dialog)
        is FilesDialog.Chown -> ChownDialog(viewModel, palette, dialog)
        is FilesDialog.Properties -> PropertiesDialog(viewModel, palette, dialog)
        is FilesDialog.Delete -> DeleteDialog(viewModel, palette, dialog)
        FilesDialog.ClearTrash -> ClearTrashDialog(viewModel, palette)
        is FilesDialog.Conflict -> ConflictDialog(viewModel, palette, dialog)
        is FilesDialog.Compress -> CompressDialog(viewModel, palette, dialog)
        is FilesDialog.Extract -> ExtractDialog(viewModel, palette, dialog)
        is FilesDialog.Apk -> ApkDialog(viewModel, palette, dialog)
        is FilesDialog.Editor -> EditorDialog(viewModel, palette, dialog)
        is FilesDialog.Exec -> ExecDialog(viewModel, palette, dialog)
    }
    TaskOverlay(palette)
}

// ------------------------------------------------------------------ primitives

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun OverlayScrim(
    palette: FilesPalette,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 440.dp)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            content()
        }
    }
}

@Composable
internal fun DialogCard(
    palette: FilesPalette,
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface)
            .padding(20.dp),
    ) {
        if (!title.isNullOrBlank()) {
            MaterialText(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.onSurface,
            )
            Spacer(Modifier.height(14.dp))
        }
        content()
    }
}

@Composable
internal fun FilesButton(
    palette: FilesPalette,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) palette.primary else palette.secondaryContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        MaterialText(
            text = label,
            fontSize = 14.sp,
            color = (if (primary) palette.onPrimary else palette.onSecondaryContainer)
                .copy(alpha = if (enabled) 1f else 0.4f),
            maxLines = 1,
        )
    }
}

@Composable
internal fun FilesInput(
    palette: FilesPalette,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surfaceVariant)
            .padding(12.dp),
    ) {
        if (value.isEmpty() && placeholder != null) {
            MaterialText(
                text = placeholder,
                fontSize = 14.sp,
                color = palette.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = palette.onSurface, fontSize = 14.sp),
            cursorBrush = SolidColor(palette.primary),
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun FilesCheckRow(
    palette: FilesPalette,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) palette.primary else Color.Transparent)
                .border(1.dp, if (checked) palette.primary else palette.outline, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                MaterialIcon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = palette.onPrimary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        MaterialText(text = label, fontSize = 14.sp, color = palette.onSurface)
    }
}

@Composable
private fun DialogButtons(
    palette: FilesPalette,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        FilesButton(palette, stringResource(android.R.string.cancel), onCancel, Modifier.padding(end = 8.dp))
        FilesButton(palette, confirmLabel, onConfirm, primary = true, enabled = confirmEnabled)
    }
}

@Composable
private fun DialogHint(palette: FilesPalette, text: String) {
    MaterialText(
        text = text,
        fontSize = 13.sp,
        color = palette.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun InputDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Input) {
    var text by remember(spec) { mutableStateOf(spec.initial) }
    val title = when (spec.kind) {
        FilesDialog.InputKind.NewFolder -> stringResource(R.string.files_new_folder)
        FilesDialog.InputKind.NewFile -> stringResource(R.string.files_new_file)
        FilesDialog.InputKind.Rename -> stringResource(R.string.files_rename)
    }
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, title) {
            if (spec.entry != null) {
                DialogHint(palette, spec.entry.path)
                Spacer(Modifier.height(10.dp))
            }
            FilesInput(
                palette = palette,
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.files_input_name),
            )
            Spacer(Modifier.height(16.dp))
            DialogButtons(
                palette = palette,
                confirmLabel = stringResource(R.string.files_apply),
                onConfirm = { viewModel.submitInput(spec, text) },
                onCancel = viewModel::dismissDialog,
            )
        }
    }
}

@Composable
private fun JumpDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Jump) {
    var text by remember(spec) { mutableStateOf(spec.initial) }
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, stringResource(R.string.files_jump)) {
            FilesInput(
                palette = palette,
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.files_jump_hint),
            )
            Spacer(Modifier.height(16.dp))
            DialogButtons(
                palette = palette,
                confirmLabel = stringResource(R.string.files_apply),
                onConfirm = {
                    viewModel.dismissDialog()
                    viewModel.jumpTo(text)
                },
                onCancel = viewModel::dismissDialog,
            )
        }
    }
}

@Composable
private fun ChmodDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Chmod) {
    val initial = spec.entry.octal.takeIf { it.matches(Regex("[0-7]{3,4}")) } ?: "644"
    var octal by remember(spec) { mutableStateOf(initial) }
    var bits by remember(spec) { mutableStateOf(octalToBits(initial) ?: BooleanArray(9)) }
    var recursive by remember(spec) { mutableStateOf(false) }

    fun updateOctal(value: String) {
        octal = value
        octalToBits(value)?.let { bits = it }
    }

    fun toggleBit(index: Int) {
        val next = bits.copyOf()
        next[index] = !next[index]
        bits = next
        octal = bitsToOctal(next, octal)
    }

    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, stringResource(R.string.files_permission)) {
            DialogHint(palette, spec.entry.name)
            Spacer(Modifier.height(10.dp))
            FilesInput(
                palette = palette,
                value = octal,
                onValueChange = { updateOctal(it) },
                placeholder = "755",
            )
            Spacer(Modifier.height(6.dp))
            MaterialText(
                text = octalToRwx(octal) ?: "?",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = palette.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val rowLabels = listOf(
                stringResource(R.string.files_perm_user),
                stringResource(R.string.files_perm_group),
                stringResource(R.string.files_perm_other),
            )
            val checkLabels = listOf(
                stringResource(R.string.files_perm_read),
                stringResource(R.string.files_perm_write),
                stringResource(R.string.files_perm_exec),
            )
            for (row in 0..2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MaterialText(
                        text = rowLabels[row],
                        fontSize = 13.sp,
                        color = palette.onSurfaceVariant,
                        modifier = Modifier.width(52.dp),
                    )
                    for (col in 0..2) {
                        FilesCheckRow(
                            palette = palette,
                            label = checkLabels[col],
                            checked = bits[row * 3 + col],
                            onCheckedChange = { toggleBit(row * 3 + col) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FilesCheckRow(
                palette = palette,
                label = stringResource(R.string.files_chmod_recursive),
                checked = recursive,
                onCheckedChange = { recursive = it },
            )
            Spacer(Modifier.height(16.dp))
            DialogButtons(
                palette = palette,
                confirmLabel = stringResource(R.string.files_apply),
                onConfirm = { viewModel.applyChmod(spec.entry, octal, recursive) },
                onCancel = viewModel::dismissDialog,
            )
        }
    }
}

@Composable
private fun ChownDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Chown) {
    var text by remember(spec) { mutableStateOf("${spec.entry.owner}") }
    var recursive by remember(spec) { mutableStateOf(false) }
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, stringResource(R.string.files_chown)) {
            DialogHint(palette, spec.entry.name)
            Spacer(Modifier.height(10.dp))
            FilesInput(
                palette = palette,
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.files_chown_hint),
            )
            Spacer(Modifier.height(8.dp))
            FilesCheckRow(
                palette = palette,
                label = stringResource(R.string.files_chmod_recursive),
                checked = recursive,
                onCheckedChange = { recursive = it },
            )
            Spacer(Modifier.height(16.dp))
            DialogButtons(
                palette = palette,
                confirmLabel = stringResource(R.string.files_apply),
                onConfirm = { viewModel.applyChown(spec.entry, text, recursive) },
                onCancel = viewModel::dismissDialog,
            )
        }
    }
}

@Composable
private fun PropertiesDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Properties) {
    val entry = spec.entry
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, stringResource(R.string.files_properties)) {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                PropertyRow(palette, stringResource(R.string.files_prop_name), entry.name)
                PropertyRow(palette, stringResource(R.string.files_prop_path), entry.path)
                PropertyRow(
                    palette,
                    stringResource(R.string.files_prop_type),
                    stringResource(
                        when {
                            entry.isDirectory -> R.string.files_prop_type_dir
                            entry.isSymlink -> R.string.files_prop_type_link
                            else -> R.string.files_prop_type_file
                        }
                    ),
                )
                PropertyRow(
                    palette,
                    stringResource(R.string.files_prop_size),
                    if (entry.isDirectory) "—" else formatFileSize(entry.size),
                )
                PropertyRow(palette, stringResource(R.string.files_prop_time), entry.date)
                PropertyRow(palette, stringResource(R.string.files_prop_owner), "${entry.owner}:${entry.group}")
                PropertyRow(
                    palette,
                    stringResource(R.string.files_prop_perm),
                    "${entry.perm} (${entry.octal})",
                )
                PropertyRow(
                    palette,
                    stringResource(R.string.files_prop_context),
                    entry.context ?: "?",
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilesButton(palette, stringResource(android.R.string.ok), viewModel::dismissDialog, primary = true)
            }
        }
    }
}

@Composable
private fun PropertyRow(palette: FilesPalette, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        MaterialText(
            text = label,
            fontSize = 13.sp,
            color = palette.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        MaterialText(
            text = value,
            fontSize = 13.sp,
            color = palette.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExecDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Exec) {
    val scroll = rememberScrollState()
    LaunchedEffect(spec.lines.size) {
        scroll.scrollTo(Int.MAX_VALUE)
    }
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, spec.entry.name) {
            MaterialText(
                text = "$ ${spec.command}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = palette.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.background)
                    .verticalScroll(scroll)
                    .padding(10.dp),
            ) {
                SelectionContainer {
                    Column {
                        if (spec.lines.isEmpty()) {
                            MaterialText(
                                text = stringResource(R.string.files_exec_running),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = palette.onSurfaceVariant,
                            )
                        }
                        spec.lines.forEach { line ->
                            MaterialText(
                                text = line,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = palette.onSurface,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val status = when {
                spec.running -> stringResource(R.string.files_exec_running)
                spec.exitCode == -1 -> stringResource(R.string.files_exec_stopped)
                else -> stringResource(R.string.files_exec_finished, spec.exitCode ?: 0)
            }
            MaterialText(
                text = status,
                fontSize = 12.sp,
                color = when {
                    spec.running -> palette.primary
                    spec.exitCode == 0 -> palette.primary
                    else -> palette.error
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (spec.running) {
                    FilesButton(palette, stringResource(R.string.files_exec_stop), viewModel::stopExec)
                    Spacer(Modifier.width(8.dp))
                }
                FilesButton(palette, stringResource(android.R.string.ok), viewModel::dismissDialog, primary = true)
            }
        }
    }
}

@Composable
private fun DeleteDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Delete) {
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, stringResource(R.string.files_delete)) {
            DialogHint(palette, stringResource(R.string.files_delete_confirm, spec.entries.size))
            Spacer(Modifier.height(6.dp))
            Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                spec.entries.take(20).forEach { entry ->
                    MaterialText(
                        text = entry.name,
                        fontSize = 13.sp,
                        color = palette.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            DialogButtons(
                palette = palette,
                confirmLabel = stringResource(R.string.files_delete),
                onConfirm = { viewModel.performDelete(spec.entries) },
                onCancel = viewModel::dismissDialog,
            )
        }
    }
}

@Composable
private fun ClearTrashDialog(viewModel: FilesViewModel, palette: FilesPalette) {
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, stringResource(R.string.files_trash_clear)) {
            DialogHint(palette, stringResource(R.string.files_clear_trash_confirm))
            Spacer(Modifier.height(16.dp))
            DialogButtons(
                palette = palette,
                confirmLabel = stringResource(R.string.files_trash_clear),
                onConfirm = viewModel::performClearTrash,
                onCancel = viewModel::dismissDialog,
            )
        }
    }
}

@Composable
private fun ConflictDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Conflict) {
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, stringResource(R.string.files_conflict_title)) {
            DialogHint(palette, stringResource(R.string.files_conflict_hint))
            Spacer(Modifier.height(6.dp))
            Column(Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
                spec.conflicts.take(20).forEach { name ->
                    MaterialText(
                        text = name,
                        fontSize = 13.sp,
                        color = palette.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilesButton(
                    palette,
                    stringResource(R.string.files_overwrite),
                    { viewModel.resolveConflict(spec.request, spec.conflicts, ConflictPolicy.Overwrite) },
                    Modifier.padding(end = 8.dp),
                    primary = true,
                )
                FilesButton(
                    palette,
                    stringResource(R.string.files_skip),
                    { viewModel.resolveConflict(spec.request, spec.conflicts, ConflictPolicy.Skip) },
                    Modifier.padding(end = 8.dp),
                )
                FilesButton(
                    palette,
                    stringResource(R.string.files_keep_both),
                    { viewModel.resolveConflict(spec.request, spec.conflicts, ConflictPolicy.KeepBoth) },
                    Modifier.padding(end = 8.dp),
                )
                FilesButton(
                    palette,
                    stringResource(android.R.string.cancel),
                    { viewModel.resolveConflict(spec.request, spec.conflicts, null) },
                )
            }
        }
    }
}

@Composable
private fun CompressDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Compress) {
    var kind by remember(spec) { mutableStateOf(ArchiveKind.Zip) }
    var name by remember(spec) {
        mutableStateOf(viewModel.defaultArchiveName(spec.entries.first().name, ArchiveKind.Zip))
    }
    val formats = listOf(
        ArchiveKind.Zip to "zip",
        ArchiveKind.SevenZ to "7z",
        ArchiveKind.Tar to "tar",
        ArchiveKind.TarGz to "tar.gz",
    )
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, stringResource(R.string.files_compress)) {
            DialogHint(palette, spec.entries.joinToString(", ") { it.name }.take(120))
            Spacer(Modifier.height(10.dp))
            Row {
                formats.forEach { (format, label) ->
                    val selected = kind == format
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) palette.primary else palette.secondaryContainer)
                            .clickable {
                                kind = format
                                name = viewModel.defaultArchiveName(name, format)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        MaterialText(
                            text = label,
                            fontSize = 13.sp,
                            color = if (selected) palette.onPrimary else palette.onSecondaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            FilesInput(
                palette = palette,
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.files_compress_name),
            )
            Spacer(Modifier.height(16.dp))
            DialogButtons(
                palette = palette,
                confirmLabel = stringResource(R.string.files_compress),
                onConfirm = { viewModel.performCompress(spec.entries, kind, name) },
                onCancel = viewModel::dismissDialog,
            )
        }
    }
}

@Composable
private fun ExtractDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Extract) {
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, stringResource(R.string.files_extract)) {
            DialogHint(palette, stringResource(R.string.files_extract_hint))
            Spacer(Modifier.height(6.dp))
            Column(Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
                spec.entries.forEach { entry ->
                    MaterialText(
                        text = "${entry.name}  →  ${archiveBaseName(entry.name)}/",
                        fontSize = 13.sp,
                        color = palette.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            DialogButtons(
                palette = palette,
                confirmLabel = stringResource(R.string.files_extract),
                onConfirm = { viewModel.performExtract(spec.entries) },
                onCancel = viewModel::dismissDialog,
            )
        }
    }
}

@Composable
private fun ApkDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Apk) {
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, stringResource(R.string.files_apk_info)) {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    spec.loading -> DialogHint(palette, stringResource(R.string.files_loading))
                    spec.error != null -> DialogHint(palette, spec.error)
                    spec.info != null -> {
                        val info = spec.info
                        PropertyRow(palette, stringResource(R.string.files_prop_name), spec.entry.name)
                        PropertyRow(palette, stringResource(R.string.files_apk_package), info.packageName)
                        PropertyRow(
                            palette,
                            stringResource(R.string.files_apk_version),
                            "${info.versionName ?: "?"} (${info.versionCode})",
                        )
                        PropertyRow(
                            palette,
                            stringResource(R.string.files_apk_sdk),
                            "${info.minSdk} / ${info.targetSdk}",
                        )
                        PropertyRow(palette, stringResource(R.string.files_prop_size), formatFileSize(spec.entry.size))
                        PropertyRow(
                            palette,
                            stringResource(R.string.files_apk_signer),
                            info.signerSha256 ?: "?",
                        )
                        Spacer(Modifier.height(6.dp))
                        MaterialText(
                            text = stringResource(R.string.files_apk_permissions, info.permissions.size),
                            fontSize = 13.sp,
                            color = palette.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        MaterialText(
                            text = info.permissions.joinToString("\n").ifEmpty { "—" },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilesButton(palette, stringResource(android.R.string.ok), viewModel::dismissDialog, primary = true)
            }
        }
    }
}

@Composable
private fun EditorDialog(viewModel: FilesViewModel, palette: FilesPalette, spec: FilesDialog.Editor) {
    var text by remember(spec.entry.path, spec.loading) { mutableStateOf(spec.text) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = viewModel::dismissDialog)
                        .padding(8.dp),
                ) {
                    MaterialIcon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(android.R.string.cancel),
                        tint = palette.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                MaterialText(
                    text = spec.entry.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                if (spec.editable && !spec.loading && spec.error == null) {
                    FilesButton(
                        palette = palette,
                        label = stringResource(R.string.files_editor_save),
                        onClick = { viewModel.editorSave(text) },
                        primary = true,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                spec.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    MaterialText(
                        text = stringResource(R.string.files_loading),
                        fontSize = 13.sp,
                        color = palette.onSurfaceVariant,
                    )
                }

                spec.error != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    MaterialText(
                        text = spec.error,
                        fontSize = 13.sp,
                        color = palette.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }

                spec.editable -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceVariant)
                        .padding(8.dp),
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        textStyle = TextStyle(
                            color = palette.onSurface,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(palette.primary),
                    )
                }

                else -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    MaterialText(
                        text = stringResource(R.string.files_editor_readonly),
                        fontSize = 12.sp,
                        color = palette.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surfaceVariant)
                            .padding(8.dp),
                    ) {
                        MaterialText(
                            text = spec.chunkText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.onSurface,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val chunkCount = FilesShell.chunkCount(spec.entry.size)
                        FilesButton(
                            palette = palette,
                            label = stringResource(R.string.files_editor_prev),
                            onClick = { viewModel.editorLoadChunk(spec.chunkIndex - 1) },
                            enabled = spec.chunkIndex > 0,
                        )
                        MaterialText(
                            text = stringResource(R.string.files_editor_page, spec.chunkIndex + 1, chunkCount),
                            fontSize = 13.sp,
                            color = palette.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        FilesButton(
                            palette = palette,
                            label = stringResource(R.string.files_editor_next),
                            onClick = { viewModel.editorLoadChunk(spec.chunkIndex + 1) },
                            enabled = spec.chunkIndex < chunkCount - 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreDialog(viewModel: FilesViewModel, palette: FilesPalette) {
    OverlayScrim(palette, viewModel::dismissDialog) {
        DialogCard(palette, stringResource(R.string.files_more_actions)) {
            MenuRow(palette, Icons.Rounded.CreateNewFolder, stringResource(R.string.files_new_folder)) {
                viewModel.openNewFolder()
            }
            MenuRow(palette, Icons.AutoMirrored.Rounded.NoteAdd, stringResource(R.string.files_new_file)) {
                viewModel.openNewFile()
            }
            MenuRow(palette, Icons.Rounded.Search, stringResource(R.string.files_jump)) {
                viewModel.openJump()
            }
            MenuRow(palette, Icons.Rounded.SelectAll, stringResource(R.string.files_select_all)) {
                viewModel.dismissDialog()
                viewModel.selectAll(viewModel.activePane)
            }
            MenuRow(palette, Icons.Rounded.Deselect, stringResource(R.string.files_clear_selection)) {
                viewModel.dismissDialog()
                viewModel.clearAllSelections()
            }
            MenuRow(palette, Icons.Rounded.RestoreFromTrash, stringResource(R.string.files_trash_open)) {
                viewModel.dismissDialog()
                viewModel.openTrash()
            }
            MenuRow(palette, Icons.Rounded.DeleteSweep, stringResource(R.string.files_trash_clear)) {
                viewModel.openClearTrash()
            }
            MenuRow(palette, Icons.Rounded.Refresh, stringResource(R.string.files_refresh)) {
                viewModel.dismissDialog()
                viewModel.refreshAll()
            }
        }
    }
}

@Composable
private fun MenuRow(
    palette: FilesPalette,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialIcon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        MaterialText(text = label, fontSize = 15.sp, color = palette.onSurface)
    }
}

@Composable
private fun TaskOverlay(palette: FilesPalette) {
    val task by FileTasks.state.collectAsStateWithLifecycle()
    var hiddenGeneration by remember { mutableStateOf<Long?>(null) }
    val current = task ?: return
    if (current.generation == hiddenGeneration) return
    OverlayScrim(palette, onDismiss = { hiddenGeneration = current.generation }) {
        DialogCard(palette, current.title) {
            if (current.progress != null) {
                LinearProgressIndicator(
                    progress = { current.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.primary,
                    trackColor = palette.surfaceVariant,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.primary,
                    trackColor = palette.surfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MaterialText(
                    text = buildString {
                        val percent = current.progress?.let { "${(it * 100).toInt()}%  " } ?: ""
                        append(percent)
                        append(current.message ?: "")
                    },
                    fontSize = 13.sp,
                    color = palette.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                FilesButton(
                    palette = palette,
                    label = stringResource(R.string.files_hide),
                    onClick = { hiddenGeneration = current.generation },
                )
            }
        }
    }
}
