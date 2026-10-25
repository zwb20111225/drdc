package me.weishu.kernelsu.ui.screen.files

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ui.util.createRootShell
import me.weishu.kernelsu.ui.util.getRootShell
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Progress callback: fraction (null = indeterminate) plus a human readable hint. */
typealias FileProgress = suspend (Float?, String?) -> Unit

private const val TAG = "FilesShell"

/** Text files larger than this are opened in chunked read-only mode. */
const val TEXT_EDIT_MAX_BYTES = 1024L * 1024L

/** Chunk size for the read-only viewer of huge files. */
const val TEXT_CHUNK_BYTES = 256 * 1024

data class ShellResult(val code: Int, val out: List<String>, val err: List<String>) {
    val ok: Boolean get() = code == 0
    val errorText: String
        get() = err.joinToString("\n").ifBlank { out.joinToString("\n") }.trim().take(300)
}

data class ListResult(val entries: List<FileEntry>, val error: String?)

data class ApkInfo(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val signerSha256: String?,
)

data class ZipItem(val absPath: String, val relPath: String, val isDirectory: Boolean)

/**
 * All file operations run through the drdcd/root shell - never through Java NIO, otherwise
 * `/data` and other protected trees are unreachable.
 *
 * Three shells are used so a long-running job does not block quick commands:
 * - the shared main shell ([getRootShell]) for listings and small mutations,
 * - a dedicated job shell for the currently running long operation,
 * - a monitor shell to poll progress from the job's log file.
 */
object FilesShell {

    const val TMP_DIR = "/data/adb/drdc/tmp"
    const val TRASH_DIR = "/data/adb/drdc/trash"

    /** Paths the product promised never to write to. */
    private val PROTECTED_PREFIXES = listOf(
        "/data/adb/modules",
        "/data/adb/post-fs-data.d",
        "/data/adb/service.d",
    )

    fun isProtectedPath(path: String): Boolean {
        val normalized = path.trimEnd('/').ifEmpty { "/" }
        return PROTECTED_PREFIXES.any { normalized == it || normalized.startsWith("$it/") }
    }

    /** POSIX single quote escaping. */
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    // ---------------------------------------------------------------- shells

    private var jobShell: Shell? = null
    private var monitorShell: Shell? = null

    private fun job(): Shell = synchronized(this) { jobShell ?: createRootShell().also { jobShell = it } }
    private fun monitor(): Shell = synchronized(this) { monitorShell ?: createRootShell().also { monitorShell = it } }

    // ---------------------------------------------------------------- basics

    suspend fun run(cmd: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val shell = getRootShell()
            val result = shell.newJob().add(cmd).to(ArrayList(), ArrayList()).exec()
            ShellResult(result.code, result.out, result.err)
        } catch (e: Throwable) {
            Log.w(TAG, "shell command failed: $cmd", e)
            ShellResult(-1, emptyList(), listOf(e.message ?: "shell error"))
        }
    }

    suspend fun probeRoot(): Boolean {
        val result = run("id")
        return result.ok && result.out.any { it.contains("uid=0") }
    }

    /**
     * Runs a command and forwards every output line as it arrives. Used by the file
     * manager's "execute" action so long-running scripts show progress instead of
     * blocking until they finish.
     */
    suspend fun runStreaming(command: String, onLine: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        try {
            val shell = getRootShell()
            val out = object : CallbackList<String?>() {
                override fun onAddElement(s: String?) {
                    s?.let(onLine)
                }
            }
            val err = object : CallbackList<String?>() {
                override fun onAddElement(s: String?) {
                    s?.let(onLine)
                }
            }
            shell.newJob().add(command).to(out, err).exec().code
        } catch (e: Throwable) {
            Log.w(TAG, "stream command failed: $command", e)
            onLine(e.message ?: "shell error")
            -1
        }
    }

    suspend fun pathExists(path: String): Boolean = run("test -e ${q(path)}").ok

    suspend fun isDirectoryPath(path: String): Boolean = run("test -d ${q(path)}").ok

    suspend fun listDir(path: String): ListResult {
        var result = run("LC_ALL=C ls -laZ ${q(path)}")
        if (!result.ok && result.errorText.contains("option", ignoreCase = true)) {
            result = run("LC_ALL=C ls -la ${q(path)}")
        }
        if (!result.ok) {
            return ListResult(emptyList(), result.errorText.ifBlank { "ls failed" })
        }
        return ListResult(FileEntryParser.parse(path, result.out), null)
    }

    /** Total descendant count (including the roots) of the given paths, used for progress. */
    suspend fun countUnits(paths: List<String>): Int {
        if (paths.isEmpty()) return 0
        val result = run("find ${paths.joinToString(" ") { q(it) }} 2>/dev/null | wc -l")
        return result.out.lastOrNull()?.trim()?.toIntOrNull() ?: 0
    }

    /** Finds a non-clashing target name in [dir] (used by the "keep both" conflict policy). */
    suspend fun freeName(dir: String, name: String): String {
        if (!pathExists("$dir/$name")) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!pathExists("$dir/$candidate")) return candidate
            i++
        }
    }

    // ---------------------------------------------------------------- long tasks

    private suspend fun readLineCount(path: String): Int = withContext(Dispatchers.IO) {
        runCatching {
            ShellUtils.fastCmd(monitor(), "wc -l < ${q(path)} 2>/dev/null")
        }.getOrNull()?.trim()?.toIntOrNull() ?: 0
    }

    /**
     * Runs [cmds] sequentially in one shell job; stdout/stderr go to [logPath] and each command
     * appends an `RC:<code>` marker. Progress is polled from the growing log file.
     *
     * @return exit codes in command order (may be shorter than [cmds] if the shell died).
     */
    suspend fun runLong(
        logPath: String,
        cmds: List<String>,
        total: Int,
        onProgress: FileProgress,
    ): List<Int> = withContext(Dispatchers.IO) {
        if (cmds.isEmpty()) return@withContext emptyList()
        run("mkdir -p ${q(TMP_DIR)}; : > ${q(logPath)}")
        val script = buildString {
            for (c in cmds) {
                append("( ").append(c).append(" ) >> ").append(q(logPath)).append(" 2>&1\n")
                append("echo \"RC:\$?\" >> ").append(q(logPath)).append("\n")
            }
            append("echo DONE\n")
        }
        val future = runCatching {
            job().newJob().add(script).to(ArrayList(), ArrayList()).enqueue()
        }.getOrNull() ?: return@withContext listOf(-1)
        while (!future.isDone) {
            if (total > 0) {
                val lines = readLineCount(logPath)
                onProgress((lines.toFloat() / (total + cmds.size)).coerceIn(0f, 0.98f), null)
            } else {
                onProgress(null, null)
            }
            delay(400)
        }
        runCatching { future.get() }
        val codes = runCatching {
            run("sed -n 's/^RC://p' ${q(logPath)}").out.mapNotNull { it.trim().toIntOrNull() }
        }.getOrDefault(emptyList())
        runCatching { run("rm -f ${q(logPath)}") }
        if (total > 0) onProgress(1f, null)
        codes
    }

    /** `cp`/`mv` command for one transfer; `mv` falls back to copy+delete across filesystems. */
    fun transferCmd(move: Boolean, source: String, target: String, replaceExisting: Boolean): String {
        val prefix = if (replaceExisting) "rm -rf ${q(target)} && " else ""
        return if (move) {
            prefix + "( mv -fv ${q(source)} ${q(target)} || ( cp -av ${q(source)} ${q(target)} && rm -rf ${q(source)} ) )"
        } else {
            prefix + "cp -av ${q(source)} ${q(target)}"
        }
    }

    // ---------------------------------------------------------------- mutations

    suspend fun moveToTrash(entries: List<FileEntry>): List<String> {
        if (entries.isEmpty()) return emptyList()
        run("mkdir -p ${q(TRASH_DIR)}")
        val failed = ArrayList<String>()
        val stamp = System.currentTimeMillis()
        entries.forEachIndexed { index, entry ->
            val target = "$TRASH_DIR/${stamp}_${index}_${entry.name}"
            val result = run(transferCmd(move = true, source = entry.path, target = target, replaceExisting = false))
            if (!result.ok) failed.add(entry.name)
        }
        return failed
    }

    suspend fun clearTrash(): Boolean {
        val result = run("rm -rf ${q(TRASH_DIR)}; mkdir -p ${q(TRASH_DIR)}")
        return result.ok
    }

    suspend fun mkdir(path: String): ShellResult = run("mkdir -p ${q(path)}")

    suspend fun touch(path: String): ShellResult = run("touch ${q(path)}")

    suspend fun rename(from: String, to: String): ShellResult = run("mv -f ${q(from)} ${q(to)}")

    suspend fun chmod(path: String, octal: String, recursive: Boolean): ShellResult =
        run("chmod ${if (recursive) "-R " else ""}$octal ${q(path)}")

    suspend fun chown(path: String, spec: String, recursive: Boolean): ShellResult =
        run("chown ${if (recursive) "-R " else ""}$spec ${q(path)}")

    // ---------------------------------------------------------------- text io

    suspend fun readText(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            SuFileInputStream.open(path).use { input ->
                input.readBytes().toString(Charsets.UTF_8).replace("\r\n", "\n")
            }
        }.getOrNull()
    }

    suspend fun writeText(path: String, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            SuFileOutputStream.open(path).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            true
        }.getOrElse {
            Log.w(TAG, "write failed: $path", it)
            false
        }
    }

    fun chunkCount(size: Long): Int = ((size + TEXT_CHUNK_BYTES - 1) / TEXT_CHUNK_BYTES).toInt().coerceAtLeast(1)

    /** Reads one 256 KiB aligned chunk of a large text file (read-only viewer). */
    suspend fun readChunk(path: String, index: Int): List<String> {
        val result = run("dd if=${q(path)} bs=$TEXT_CHUNK_BYTES skip=$index count=1 2>/dev/null")
        return if (result.ok) result.out else emptyList()
    }

    // ---------------------------------------------------------------- archives

    /** Expands [sources] (files/dirs) into archive items with relative paths. */
    suspend fun collectItems(sources: List<FileEntry>): List<ZipItem> {
        val items = ArrayList<ZipItem>()
        for (source in sources) {
            if (!source.isDirectory) {
                items.add(ZipItem(source.path, source.name, false))
                continue
            }
            val parent = source.path.substringBeforeLast('/', "")
            val dirs = run("find ${q(source.path)} -type d").out.map { it.trim() }.filter { it.isNotEmpty() }
            val files = run("find ${q(source.path)} ! -type d").out.map { it.trim() }.filter { it.isNotEmpty() }
            fun rel(p: String): String = if (parent.isEmpty()) p.trimStart('/') else p.removePrefix("$parent/")
            dirs.forEach { items.add(ZipItem(it, rel(it), true)) }
            files.forEach { items.add(ZipItem(it, rel(it), false)) }
        }
        return items
    }

    /** Batched `stat -c '%s|%n'` for entry sizes (7z needs sizes upfront). */
    suspend fun statSizes(paths: List<String>): Map<String, Long> {
        val sizes = HashMap<String, Long>(paths.size)
        for (batch in paths.chunked(150)) {
            val result = run("LC_ALL=C stat -c '%s|%n' ${batch.joinToString(" ") { q(it) }}")
            for (line in result.out) {
                val sep = line.indexOf('|')
                if (sep <= 0) continue
                val size = line.substring(0, sep).trim().toLongOrNull() ?: continue
                sizes[line.substring(sep + 1).trim()] = size
            }
        }
        return sizes
    }

    /** Creates a zip at [dest] (any path readable by the root shell). Returns an error or null. */
    suspend fun createZip(sources: List<FileEntry>, dest: String, onProgress: FileProgress): String? =
        withContext(Dispatchers.IO) {
            var created = false
            try {
                val items = collectItems(sources)
                if (items.isEmpty()) return@withContext "empty"
                run("rm -f ${q(dest)}")
                SuFileOutputStream.open(dest).use { raw ->
                    ZipOutputStream(BufferedOutputStream(raw)).use { zos ->
                        items.forEachIndexed { index, item ->
                            onProgress(index.toFloat() / items.size, item.relPath)
                            if (item.isDirectory) {
                                zos.putNextEntry(ZipEntry(item.relPath.trimEnd('/') + "/"))
                                zos.closeEntry()
                            } else {
                                zos.putNextEntry(ZipEntry(item.relPath))
                                SuFileInputStream.open(item.absPath).use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }
                created = true
                null
            } catch (e: Throwable) {
                Log.w(TAG, "createZip failed", e)
                e.message ?: "zip failed"
            } finally {
                if (!created) runCatching { run("rm -f ${q(dest)}") }
            }
        }

    suspend fun extractZip(arc: String, destDir: String, onProgress: FileProgress): String? =
        withContext(Dispatchers.IO) {
            try {
                run("mkdir -p ${q(destDir)}")
                SuFileInputStream.open(arc).use { raw ->
                    ZipInputStream(BufferedInputStream(raw)).use { zis ->
                        while (true) {
                            val entry = zis.nextEntry ?: break
                            val name = entry.name.trimStart('/')
                            if (name.isEmpty() || name.split('/').any { it == ".." }) {
                                zis.closeEntry()
                                continue
                            }
                            val target = "$destDir/$name"
                            onProgress(null, name)
                            if (entry.isDirectory || name.endsWith("/")) {
                                run("mkdir -p ${q(target.trimEnd('/'))}")
                            } else {
                                run("mkdir -p ${q(target.substringBeforeLast('/'))}")
                                SuFileOutputStream.open(target).use { out -> zis.copyTo(out) }
                            }
                            zis.closeEntry()
                        }
                    }
                }
                null
            } catch (e: Throwable) {
                Log.w(TAG, "extractZip failed", e)
                e.message ?: "unzip failed"
            }
        }

    suspend fun createSevenZ(
        dest: String,
        sources: List<FileEntry>,
        cacheFile: File,
        onProgress: FileProgress,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val items = collectItems(sources)
            if (items.isEmpty()) return@withContext "empty"
            val sizes = statSizes(items.filter { !it.isDirectory }.map { it.absPath })
            cacheFile.parentFile?.mkdirs()
            cacheFile.delete()
            SevenZOutputFile(cacheFile).use { out ->
                out.setContentCompression(SevenZMethod.LZMA2)
                items.forEachIndexed { index, item ->
                    onProgress(index.toFloat() / items.size, item.relPath)
                    val entry = SevenZArchiveEntry()
                    if (item.isDirectory) {
                        entry.name = item.relPath.trimEnd('/') + "/"
                        entry.isDirectory = true
                        out.putArchiveEntry(entry)
                        out.closeArchiveEntry()
                    } else {
                        entry.name = item.relPath
                        entry.size = sizes[item.absPath] ?: 0L
                        out.putArchiveEntry(entry)
                        SuFileInputStream.open(item.absPath).use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                            }
                        }
                        out.closeArchiveEntry()
                    }
                }
            }
            val copy = run("cp -f ${q(cacheFile.absolutePath)} ${q(dest)}")
            if (!copy.ok) copy.errorText else null
        } catch (e: Throwable) {
            Log.w(TAG, "createSevenZ failed", e)
            e.message ?: "7z failed"
        } finally {
            cacheFile.delete()
        }
    }

    @Suppress("DEPRECATION")
    suspend fun extractSevenZ(
        arc: String,
        destDir: String,
        cacheFile: File,
        onProgress: FileProgress,
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (!copyToCache(arc, cacheFile)) return@withContext "copy failed"
            run("mkdir -p ${q(destDir)}")
            SevenZFile(cacheFile).use { sevenZ ->
                val total = runCatching { sevenZ.entries.toList().size }.getOrDefault(0)
                var index = 0
                while (true) {
                    val entry = sevenZ.nextEntry ?: break
                    index++
                    val name = entry.name.trimStart('/')
                    if (name.split('/').any { it == ".." }) continue
                    onProgress(if (total > 0) index.toFloat() / total else null, name)
                    val target = "$destDir/$name"
                    if (entry.isDirectory) {
                        run("mkdir -p ${q(target.trimEnd('/'))}")
                    } else {
                        run("mkdir -p ${q(target.substringBeforeLast('/'))}")
                        SuFileOutputStream.open(target).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = sevenZ.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Throwable) {
            Log.w(TAG, "extractSevenZ failed", e)
            e.message ?: "7z extract failed"
        } finally {
            cacheFile.delete()
        }
    }

    fun tarCompressFlag(kind: ArchiveKind): String? = when (kind) {
        ArchiveKind.Tar -> ""
        ArchiveKind.TarGz -> "z"
        ArchiveKind.TarXz -> "J"
        ArchiveKind.TarBz2 -> "j"
        else -> null
    }

    fun tarCreateCmd(sources: List<FileEntry>, dest: String, flag: String): String {
        val parent = sources.first().path.substringBeforeLast('/', "/")
        val names = sources.joinToString(" ") { q(it.name) }
        return "( cd ${q(parent)} && tar -c${flag}vf ${q(dest)} $names )"
    }

    fun tarExtractCmd(arc: String, destDir: String, flag: String): String =
        "mkdir -p ${q(destDir)} && tar -x${flag}vf ${q(arc)} -C ${q(destDir)}"

    suspend fun tarEntryCount(arc: String, flag: String): Int {
        val result = run("tar -t${flag}f ${q(arc)} 2>/dev/null | wc -l")
        return result.out.lastOrNull()?.trim()?.toIntOrNull() ?: 0
    }

    // ---------------------------------------------------------------- apk

    private suspend fun copyToCache(src: String, dest: File): Boolean {
        dest.parentFile?.mkdirs()
        runCatching { if (!dest.exists()) dest.createNewFile() }
        val result = run("cp -f ${q(src)} ${q(dest.absolutePath)} && chmod 644 ${q(dest.absolutePath)}")
        return result.ok && dest.exists()
    }

    /** Reads basic APK metadata; falls back to a root copy into the app cache when needed. */
    suspend fun parseApk(context: Context, path: String): ApkInfo? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        parseApkFile(pm, path)?.let { return@withContext it }
        val cacheDir = File(context.cacheDir, "apkinfo")
        val tmp = File(cacheDir, "tmp-${System.currentTimeMillis()}.apk")
        val copied = copyToCache(path, tmp)
        if (!copied) return@withContext null
        try {
            parseApkFile(pm, tmp.absolutePath)
        } finally {
            tmp.delete()
        }
    }

    private fun parseApkFile(pm: PackageManager, path: String): ApkInfo? {
        return try {
            @Suppress("DEPRECATION")
            val info = pm.getPackageArchiveInfo(
                path,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES,
            ) ?: return null
            val signer = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()?.let { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString(":") { String.format(Locale.US, "%02X", it) }
            }
            ApkInfo(
                packageName = info.packageName,
                versionName = info.versionName,
                versionCode = info.longVersionCode,
                minSdk = info.applicationInfo?.minSdkVersion ?: 0,
                targetSdk = info.applicationInfo?.targetSdkVersion ?: 0,
                permissions = info.requestedPermissions?.toList() ?: emptyList(),
                signerSha256 = signer,
            )
        } catch (e: Throwable) {
            Log.w(TAG, "parseApk failed: $path", e)
            null
        }
    }
}
