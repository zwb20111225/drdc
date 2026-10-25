package me.weishu.kernelsu.ui.screen.files

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import java.io.File

sealed interface FilesDialog {
    data object None : FilesDialog
    data object More : FilesDialog

    enum class InputKind { NewFolder, NewFile, Rename }

    data class Input(val kind: InputKind, val initial: String = "", val entry: FileEntry? = null) : FilesDialog
    data class Jump(val initial: String) : FilesDialog
    data class Chmod(val entry: FileEntry) : FilesDialog
    data class Chown(val entry: FileEntry) : FilesDialog
    data class Properties(val entry: FileEntry) : FilesDialog
    data class Delete(val entries: List<FileEntry>) : FilesDialog
    data object ClearTrash : FilesDialog
    data class Conflict(val request: TransferRequest, val conflicts: List<String>) : FilesDialog
    data class Compress(val entries: List<FileEntry>) : FilesDialog
    data class Extract(val entries: List<FileEntry>) : FilesDialog
    data class Apk(
        val entry: FileEntry,
        val loading: Boolean = true,
        val info: ApkInfo? = null,
        val error: String? = null,
    ) : FilesDialog

    data class Editor(
        val entry: FileEntry,
        val editable: Boolean,
        val loading: Boolean = true,
        val text: String = "",
        val chunkIndex: Int = 0,
        val chunkText: String = "",
        val error: String? = null,
    ) : FilesDialog

    /** Output of running a file through the root shell. */
    data class Exec(
        val entry: FileEntry,
        val command: String,
        val lines: List<String> = emptyList(),
        val running: Boolean = true,
        val exitCode: Int? = null,
    ) : FilesDialog
}

data class TransferRequest(val move: Boolean, val sources: List<FileEntry>, val destDir: String)

enum class ConflictPolicy { Overwrite, Skip, KeepBoth }

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    enum class RootStatus { Checking, Granted, Denied }

    data class PaneState(
        val path: String,
        val entries: List<FileEntry> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
        val selection: Set<String> = emptySet(),
    )

    var rootStatus by mutableStateOf(RootStatus.Checking)
        private set
    val panes = mutableStateListOf(PaneState(DEFAULT_PANE_PRIMARY), PaneState(DEFAULT_PANE_SECONDARY))
    var activePane by mutableIntStateOf(0)
        private set
    var dialog by mutableStateOf<FilesDialog>(FilesDialog.None)
        private set

    /** Text messages to be shown as toasts by the UI. */
    val messages = Channel<String>(Channel.BUFFERED)

    private val paneJobs = arrayOfNulls<Job>(2)
    private var execJob: Job? = null

    init {
        viewModelScope.launch { probeRoot() }
        viewModelScope.launch {
            FileTasks.finishedEvents.collect { task ->
                val text = if (task.failed) {
                    getString(R.string.files_task_failed, task.message.orEmpty())
                } else {
                    task.message ?: getString(R.string.files_task_done)
                }
                messages.trySend(text)
                if (!task.failed) {
                    clearAllSelections()
                    refreshAll()
                }
            }
        }
    }

    private fun getString(res: Int, vararg args: Any): String =
        getApplication<Application>().getString(res, *args)

    fun toast(res: Int, vararg args: Any) {
        messages.trySend(getString(res, *args))
    }

    // ------------------------------------------------------------ root / panes

    suspend fun probeRoot() {
        rootStatus = RootStatus.Checking
        val granted = FilesShell.probeRoot()
        if (granted) {
            rootStatus = RootStatus.Granted
            refreshPane(0, DEFAULT_PANE_PRIMARY)
            refreshPane(1, DEFAULT_PANE_SECONDARY)
        } else {
            rootStatus = RootStatus.Denied
        }
    }

    fun retryRoot() {
        viewModelScope.launch { probeRoot() }
    }

    fun activatePane(index: Int) {
        activePane = index
    }

    fun activePaneState(): PaneState = panes.getOrElse(activePane) { panes[0] }

    fun refreshAll() {
        refreshPane(0)
        refreshPane(1)
    }

    fun refreshPane(index: Int, newPath: String? = null) {
        val pane = panes.getOrNull(index) ?: return
        val path = newPath ?: pane.path
        panes[index] = pane.copy(
            path = path,
            loading = true,
            error = null,
            selection = if (newPath != null && newPath != pane.path) emptySet() else pane.selection,
        )
        paneJobs[index]?.cancel()
        paneJobs[index] = viewModelScope.launch {
            val result = FilesShell.listDir(path)
            val current = panes.getOrNull(index) ?: return@launch
            if (current.path != path) return@launch
            panes[index] = current.copy(entries = result.entries, loading = false, error = result.error)
        }
    }

    fun goUp(index: Int) {
        val pane = panes.getOrNull(index) ?: return
        val parent = parentOf(pane.path)
        if (parent != pane.path) refreshPane(index, parent)
    }

    fun jumpTo(path: String) {
        val normalized = if (path.startsWith("/")) path else "/$path"
        refreshPane(activePane, normalized.trimEnd('/').ifEmpty { "/" })
    }

    fun openTrash() {
        viewModelScope.launch {
            FilesShell.mkdir(FilesShell.TRASH_DIR)
            refreshPane(activePane, FilesShell.TRASH_DIR)
        }
    }

    fun showMore() {
        dialog = FilesDialog.More
    }

    fun dismissDialog() {
        dialog = FilesDialog.None
    }

    // ------------------------------------------------------------ selection

    fun toggleSelect(index: Int, name: String) {
        val pane = panes.getOrNull(index) ?: return
        val selection = pane.selection.toMutableSet()
        if (!selection.add(name)) selection.remove(name)
        panes[index] = pane.copy(selection = selection)
        activePane = index
    }

    fun selectAll(index: Int) {
        val pane = panes.getOrNull(index) ?: return
        panes[index] = pane.copy(selection = pane.entries.map { it.name }.toSet())
        activePane = index
    }

    fun clearSelection(index: Int) {
        val pane = panes.getOrNull(index) ?: return
        panes[index] = pane.copy(selection = emptySet())
    }

    fun clearAllSelections() {
        for (index in 0..1) clearSelection(index)
    }

    fun selectedEntries(): List<FileEntry> {
        val pane = activePaneState()
        return pane.entries.filter { it.name in pane.selection }
    }

    private fun isProtected(path: String): Boolean = FilesShell.isProtectedPath(path)

    // ------------------------------------------------------------ opening

    fun openEntry(index: Int, entry: FileEntry) {
        val pane = panes.getOrNull(index) ?: return
        activePane = index
        if (pane.selection.isNotEmpty()) {
            toggleSelect(index, entry.name)
            return
        }
        if (entry.isDirectory) {
            refreshPane(index, entry.path)
            return
        }
        if (entry.isSymlink) {
            viewModelScope.launch {
                if (FilesShell.isDirectoryPath(entry.path)) refreshPane(index, entry.path) else openFile(entry)
            }
            return
        }
        openFile(entry)
    }

    private fun openFile(entry: FileEntry) {
        when {
            entry.isApk -> openApk(entry)
            isTextCandidate(entry) || entry.size <= 64 * 1024 -> openEditor(entry)
            else -> dialog = FilesDialog.Properties(entry)
        }
    }

    fun openProperties(entry: FileEntry) {
        dialog = FilesDialog.Properties(entry)
    }

    /** Opens the single selected entry, ignoring selection mode. */
    fun openSelected() {
        val entry = selectedEntries().singleOrNull() ?: return
        clearAllSelections()
        openEntry(activePane, entry)
    }

    /**
     * Runs the selected file through the root shell and streams its output.
     * Executables are started directly; everything else is passed to sh, so scripts
     * without the executable bit still work.
     */
    fun runFile(entry: FileEntry) {
        if (entry.isDirectory) return
        val dir = entry.path.substringBeforeLast('/', "/").ifEmpty { "/" }
        val executable = entry.perm.length >= 10 &&
            listOf(3, 6, 9).any { entry.perm[it] == 'x' }
        val command = if (executable) {
            "cd ${FilesShell.q(dir)} && ./${FilesShell.q(entry.name)}"
        } else {
            "cd ${FilesShell.q(dir)} && sh ${FilesShell.q(entry.name)}"
        }
        executeFile(entry, command)
    }

    private fun executeFile(entry: FileEntry, command: String) {
        execJob?.cancel()
        val lines = ArrayList<String>()
        dialog = FilesDialog.Exec(entry = entry, command = command)
        execJob = viewModelScope.launch {
            val code = FilesShell.runStreaming(command) { line ->
                lines.add(line)
                while (lines.size > 2000) lines.removeAt(0)
                val current = dialog
                if (current is FilesDialog.Exec && current.entry.path == entry.path) {
                    dialog = current.copy(lines = ArrayList(lines))
                }
            }
            val current = dialog
            if (current is FilesDialog.Exec && current.entry.path == entry.path) {
                dialog = current.copy(lines = ArrayList(lines), running = false, exitCode = code)
            }
            refreshAll()
        }
    }

    fun stopExec() {
        val current = dialog as? FilesDialog.Exec ?: return
        execJob?.cancel()
        dialog = current.copy(running = false, exitCode = -1)
        viewModelScope.launch {
            FilesShell.run("pkill -f ${FilesShell.q(current.entry.path)}")
        }
    }

    fun openApk(entry: FileEntry) {
        dialog = FilesDialog.Apk(entry)
        viewModelScope.launch {
            val info = FilesShell.parseApk(getApplication(), entry.path)
            val current = dialog
            if (current is FilesDialog.Apk && current.entry.path == entry.path) {
                dialog = if (info != null) {
                    current.copy(loading = false, info = info)
                } else {
                    current.copy(loading = false, error = getString(R.string.files_apk_parse_failed))
                }
            }
        }
    }

    fun openEditor(entry: FileEntry) {
        val editable = entry.size <= TEXT_EDIT_MAX_BYTES
        dialog = FilesDialog.Editor(entry = entry, editable = editable)
        viewModelScope.launch {
            val current = dialog as? FilesDialog.Editor ?: return@launch
            if (current.entry.path != entry.path) return@launch
            if (editable) {
                val text = FilesShell.readText(entry.path)
                if (text == null) {
                    dialog = current.copy(loading = false, error = getString(R.string.files_editor_open_failed))
                } else if (text.contains('\u0000')) {
                    dialog = FilesDialog.Properties(entry)
                    toast(R.string.files_not_text)
                } else {
                    dialog = current.copy(loading = false, text = text)
                }
            } else {
                val lines = FilesShell.readChunk(entry.path, 0)
                dialog = current.copy(loading = false, chunkText = lines.joinToString("\n"), chunkIndex = 0)
            }
        }
    }

    fun editorLoadChunk(index: Int) {
        val current = dialog as? FilesDialog.Editor ?: return
        if (current.editable) return
        val count = FilesShell.chunkCount(current.entry.size)
        if (index !in 0 until count) return
        dialog = current.copy(loading = true, chunkIndex = index)
        viewModelScope.launch {
            val lines = FilesShell.readChunk(current.entry.path, index)
            val now = dialog as? FilesDialog.Editor ?: return@launch
            if (now.entry.path == current.entry.path && !now.editable) {
                dialog = now.copy(loading = false, chunkText = lines.joinToString("\n"), chunkIndex = index)
            }
        }
    }

    fun editorSave(text: String) {
        val current = dialog as? FilesDialog.Editor ?: return
        if (!current.editable) return
        if (isProtected(current.entry.path)) {
            toast(R.string.files_protected)
            return
        }
        viewModelScope.launch {
            if (FilesShell.writeText(current.entry.path, text)) {
                dialog = FilesDialog.None
                toast(R.string.files_editor_saved)
                refreshAll()
            } else {
                toast(R.string.files_op_failed, getString(R.string.files_editor_open_failed))
            }
        }
    }

    // ------------------------------------------------------------ create / rename

    fun openNewFolder() {
        dialog = FilesDialog.Input(FilesDialog.InputKind.NewFolder)
    }

    fun openNewFile() {
        dialog = FilesDialog.Input(FilesDialog.InputKind.NewFile)
    }

    fun openJump() {
        dialog = FilesDialog.Jump(activePaneState().path)
    }

    fun openRename() {
        val entry = selectedEntries().singleOrNull() ?: return
        if (isProtected(entry.path)) {
            toast(R.string.files_protected)
            return
        }
        dialog = FilesDialog.Input(FilesDialog.InputKind.Rename, initial = entry.name, entry = entry)
    }

    fun submitInput(spec: FilesDialog.Input, text: String) {
        val name = text.trim()
        if (name.isEmpty() || name.contains('/') || name == "." || name == "..") {
            toast(R.string.files_invalid_name)
            return
        }
        dialog = FilesDialog.None
        val pane = activePaneState()
        viewModelScope.launch {
            val result = when (spec.kind) {
                FilesDialog.InputKind.Rename -> {
                    val entry = spec.entry ?: return@launch
                    if (name == entry.name) return@launch
                    if (isProtected(entry.path)) {
                        toast(R.string.files_protected)
                        return@launch
                    }
                    FilesShell.rename(entry.path, "${parentOf(entry.path)}/$name")
                }

                FilesDialog.InputKind.NewFolder -> {
                    if (isProtected(pane.path)) {
                        toast(R.string.files_protected)
                        return@launch
                    }
                    FilesShell.mkdir("${pane.path}/$name")
                }

                FilesDialog.InputKind.NewFile -> {
                    if (isProtected(pane.path)) {
                        toast(R.string.files_protected)
                        return@launch
                    }
                    FilesShell.touch("${pane.path}/$name")
                }
            }
            if (!result.ok) toast(R.string.files_op_failed, result.errorText)
            refreshAll()
        }
    }

    // ------------------------------------------------------------ transfer

    fun startTransfer(move: Boolean) {
        val source = activePaneState()
        val dest = panes[1 - activePane]
        val selected = selectedEntries()
        if (selected.isEmpty()) return
        if (source.path == dest.path) {
            toast(R.string.files_same_dir)
            return
        }
        if (selected.any { isProtected(it.path) } || isProtected(dest.path)) {
            toast(R.string.files_protected)
            return
        }
        viewModelScope.launch {
            val conflicts = selected.filter { FilesShell.pathExists("${dest.path}/${it.name}") }.map { it.name }
            val request = TransferRequest(move, selected, dest.path)
            if (conflicts.isEmpty()) {
                runTransfer(request, ConflictPolicy.Overwrite, emptyList())
            } else {
                dialog = FilesDialog.Conflict(request, conflicts)
            }
        }
    }

    fun resolveConflict(request: TransferRequest, conflicts: List<String>, policy: ConflictPolicy?) {
        if (policy == null) {
            dialog = FilesDialog.None
            return
        }
        runTransfer(request, policy, conflicts)
    }

    private fun runTransfer(request: TransferRequest, policy: ConflictPolicy, conflicts: List<String>) {
        dialog = FilesDialog.None
        val app = getApplication<Application>()
        val title = getString(if (request.move) R.string.files_moving else R.string.files_copying, request.sources.size)
        val started = FileTasks.start(app, title) { report ->
            val conflictSet = conflicts.toHashSet()
            val commands = ArrayList<String>(request.sources.size)
            for (entry in request.sources) {
                report(null, entry.name)
                val hasConflict = entry.name in conflictSet
                if (hasConflict && policy == ConflictPolicy.Skip) {
                    continue
                }
                val targetName = if (hasConflict && policy == ConflictPolicy.KeepBoth) {
                    FilesShell.freeName(request.destDir, entry.name)
                } else {
                    entry.name
                }
                val target = "${request.destDir}/$targetName"
                commands.add(
                    FilesShell.transferCmd(
                        move = request.move,
                        source = entry.path,
                        target = target,
                        replaceExisting = hasConflict && policy == ConflictPolicy.Overwrite,
                    )
                )
            }
            if (commands.isEmpty()) {
                report(1f, getString(R.string.files_nothing_to_do))
                return@start
            }
            val total = FilesShell.countUnits(request.sources.map { it.path })
            val codes = FilesShell.runLong("${FilesShell.TMP_DIR}/transfer.log", commands, total, report)
            val failed = codes.count { it != 0 } + (commands.size - codes.size).coerceAtLeast(0)
            val done = commands.size - failed
            when {
                failed > 0 -> throw IllegalStateException(getString(R.string.files_partial_failed, done, failed))
                request.move -> report(1f, getString(R.string.files_move_done, done))
                else -> report(1f, getString(R.string.files_copy_done, done))
            }
        }
        if (!started) toast(R.string.files_busy)
    }

    // ------------------------------------------------------------ delete / trash

    fun openDelete() {
        val selected = selectedEntries()
        if (selected.isEmpty()) return
        if (selected.any { isProtected(it.path) }) {
            toast(R.string.files_protected)
            return
        }
        dialog = FilesDialog.Delete(selected)
    }

    fun performDelete(entries: List<FileEntry>) {
        dialog = FilesDialog.None
        viewModelScope.launch {
            val failed = FilesShell.moveToTrash(entries)
            if (failed.isEmpty()) {
                toast(R.string.files_deleted, entries.size)
                clearSelection(activePane)
            } else {
                toast(R.string.files_partial_failed, entries.size - failed.size, failed.size)
            }
            refreshAll()
        }
    }

    fun openClearTrash() {
        dialog = FilesDialog.ClearTrash
    }

    fun performClearTrash() {
        dialog = FilesDialog.None
        viewModelScope.launch {
            if (FilesShell.clearTrash()) toast(R.string.files_trash_cleared) else toast(R.string.files_op_failed, "trash")
            refreshAll()
        }
    }

    // ------------------------------------------------------------ permissions

    fun openChmod() {
        val entry = selectedEntries().singleOrNull() ?: return
        if (isProtected(entry.path)) {
            toast(R.string.files_protected)
            return
        }
        dialog = FilesDialog.Chmod(entry)
    }

    fun applyChmod(entry: FileEntry, octal: String, recursive: Boolean) {
        if (!octal.trim().matches(Regex("[0-7]{3,4}"))) {
            toast(R.string.files_invalid_mode)
            return
        }
        dialog = FilesDialog.None
        viewModelScope.launch {
            val result = FilesShell.chmod(entry.path, octal.trim(), recursive)
            if (!result.ok) toast(R.string.files_op_failed, result.errorText)
            refreshAll()
        }
    }

    fun openChown() {
        val entry = selectedEntries().singleOrNull() ?: return
        if (isProtected(entry.path)) {
            toast(R.string.files_protected)
            return
        }
        dialog = FilesDialog.Chown(entry)
    }

    fun applyChown(entry: FileEntry, spec: String, recursive: Boolean) {
        if (!spec.trim().matches(Regex("[A-Za-z0-9_.-]+(:[A-Za-z0-9_.-]+)?"))) {
            toast(R.string.files_invalid_owner)
            return
        }
        dialog = FilesDialog.None
        viewModelScope.launch {
            val result = FilesShell.chown(entry.path, spec.trim(), recursive)
            if (!result.ok) toast(R.string.files_op_failed, result.errorText)
            refreshAll()
        }
    }

    // ------------------------------------------------------------ archives

    fun openCompress() {
        val selected = selectedEntries()
        if (selected.isEmpty()) return
        if (selected.any { isProtected(it.path) } || isProtected(activePaneState().path)) {
            toast(R.string.files_protected)
            return
        }
        dialog = FilesDialog.Compress(selected)
    }

    fun openExtract() {
        val archives = selectedEntries().filter { it.isArchive }
        if (archives.isEmpty()) return
        if (archives.any { isProtected(it.path) } || isProtected(activePaneState().path)) {
            toast(R.string.files_protected)
            return
        }
        dialog = FilesDialog.Extract(archives)
    }

    fun performCompress(entries: List<FileEntry>, kind: ArchiveKind, name: String) {
        dialog = FilesDialog.None
        val dir = activePaneState().path
        val fileName = name.trim().ifEmpty { defaultArchiveName(entries.first().name, kind) }
        val dest = "$dir/$fileName"
        if (isProtected(dest)) {
            toast(R.string.files_protected)
            return
        }
        val app = getApplication<Application>()
        val started = FileTasks.start(app, getString(R.string.files_compressing, fileName)) { report ->
            val error = when (kind) {
                ArchiveKind.Zip -> FilesShell.createZip(entries, dest, report)
                ArchiveKind.SevenZ -> FilesShell.createSevenZ(
                    dest,
                    entries,
                    File(getApplication<Application>().cacheDir, "seven/archive.7z"),
                    report,
                )

                else -> {
                    val flag = FilesShell.tarCompressFlag(kind).orEmpty()
                    val total = FilesShell.countUnits(entries.map { it.path })
                    val codes = FilesShell.runLong(
                        "${FilesShell.TMP_DIR}/tar-create.log",
                        listOf(FilesShell.tarCreateCmd(entries, dest, flag)),
                        total,
                        report,
                    )
                    if (codes.firstOrNull() != 0) "tar failed" else null
                }
            }
            if (error != null) throw IllegalStateException(error)
            report(1f, getString(R.string.files_compressed, fileName))
        }
        if (!started) toast(R.string.files_busy)
    }

    fun performExtract(entries: List<FileEntry>) {
        dialog = FilesDialog.None
        val app = getApplication<Application>()
        val started = FileTasks.start(app, getString(R.string.files_extracting, entries.size)) { report ->
            for ((index, entry) in entries.withIndex()) {
                val kind = archiveKind(entry.name) ?: continue
                val destDir = "${parentOf(entry.path)}/${archiveBaseName(entry.name)}"
                if (isProtected(destDir)) {
                    toast(R.string.files_protected)
                    continue
                }
                val scoped: FileProgress = { progress, message ->
                    report((index + (progress ?: 0f)) / entries.size, message ?: entry.name)
                }
                report(index.toFloat() / entries.size, entry.name)
                val error = when (kind) {
                    ArchiveKind.Zip -> FilesShell.extractZip(entry.path, destDir, scoped)

                    ArchiveKind.SevenZ -> FilesShell.extractSevenZ(
                        entry.path,
                        destDir,
                        File(getApplication<Application>().cacheDir, "seven/archive.7z"),
                        scoped,
                    )

                    else -> {
                        val flag = FilesShell.tarCompressFlag(kind).orEmpty()
                        val total = FilesShell.tarEntryCount(entry.path, flag)
                        val codes = FilesShell.runLong(
                            "${FilesShell.TMP_DIR}/tar-extract.log",
                            listOf(FilesShell.tarExtractCmd(entry.path, destDir, flag)),
                            total,
                            scoped,
                        )
                        if (codes.firstOrNull() != 0) "tar failed" else null
                    }
                }
                if (error != null) throw IllegalStateException("${entry.name}: $error")
            }
            report(1f, getString(R.string.files_extract_done, entries.size))
        }
        if (!started) toast(R.string.files_busy)
    }

    fun defaultArchiveName(base: String, kind: ArchiveKind): String {
        val stripped = archiveBaseName(base).ifBlank { "archive" }
        return stripped + when (kind) {
            ArchiveKind.Zip -> ".zip"
            ArchiveKind.SevenZ -> ".7z"
            ArchiveKind.Tar -> ".tar"
            ArchiveKind.TarGz -> ".tar.gz"
            ArchiveKind.TarXz -> ".tar.xz"
            ArchiveKind.TarBz2 -> ".tar.bz2"
        }
    }

    companion object {
        const val DEFAULT_PANE_PRIMARY = "/sdcard"
        const val DEFAULT_PANE_SECONDARY = "/data/adb"
    }
}

fun parentOf(path: String): String {
    val trimmed = path.trimEnd('/')
    if (trimmed.isEmpty()) return "/"
    val index = trimmed.lastIndexOf('/')
    return if (index <= 0) "/" else trimmed.substring(0, index)
}
