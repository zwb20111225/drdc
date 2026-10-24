package me.weishu.kernelsu.ui.screen.files

import java.util.Locale

/**
 * One entry of a directory listing, parsed from `ls -laZ` output.
 */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    /** Raw date column from `ls`, e.g. `2024-01-01 12:00` or `Jan  1  2023`. */
    val date: String,
    /** The 10-char permission column, e.g. `-rw-r--r--` (may carry a trailing `.`/`+`). */
    val perm: String,
    val owner: String,
    val group: String,
    /** SELinux context, `?` when unknown, null when the tool did not print it. */
    val context: String?,
) {
    val isApk: Boolean
        get() = !isDirectory && name.endsWith(".apk", ignoreCase = true)

    val isArchive: Boolean
        get() = !isDirectory && archiveKind(name) != null

    /** Octal permission, e.g. `755` or `4755` when setuid/setgid/sticky bits are set. */
    val octal: String
        get() = permToOctal(perm)
}

enum class ArchiveKind { Zip, SevenZ, Tar, TarGz, TarXz, TarBz2 }

fun archiveKind(name: String): ArchiveKind? {
    val n = name.lowercase(Locale.ROOT)
    return when {
        n.endsWith(".zip") -> ArchiveKind.Zip
        n.endsWith(".7z") -> ArchiveKind.SevenZ
        n.endsWith(".tar.gz") || n.endsWith(".tgz") -> ArchiveKind.TarGz
        n.endsWith(".tar.xz") || n.endsWith(".txz") -> ArchiveKind.TarXz
        n.endsWith(".tar.bz2") || n.endsWith(".tbz") || n.endsWith(".tbz2") -> ArchiveKind.TarBz2
        n.endsWith(".tar") -> ArchiveKind.Tar
        else -> null
    }
}

/** Strips a known archive suffix to build the extraction folder name. */
fun archiveBaseName(name: String): String {
    val n = name.lowercase(Locale.ROOT)
    val suffixes = listOf(".tar.gz", ".tar.xz", ".tar.bz2", ".tbz2", ".tgz", ".txz", ".tbz", ".zip", ".7z", ".tar")
    val suffix = suffixes.firstOrNull { n.endsWith(it) }
    val base = if (suffix != null) name.dropLast(suffix.length) else name
    return base.ifBlank { name }.ifBlank { "extracted" }
}

private const val TEXT_EXTENSIONS =
    "txt,log,conf,cfg,ini,md,json,xml,html,htm,css,js,ts,kt,kts,java,sh,prop,rc,yml,yaml,csv,list,rule,rules,toml,patch,diff,service,bash,zsh,env,gitignore,gitconfig,gradle,kts,properties"

/** Files the built-in text editor is offered for without asking. */
fun isTextCandidate(entry: FileEntry): Boolean {
    if (entry.isDirectory) return false
    val ext = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext in TEXT_EXTENSIONS.split(',')
}

fun formatFileSize(bytes: Long): String = when {
    bytes < 0 -> "?"
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}

/** `-rwxr-xr-x` → `755` (keeps setuid/setgid/sticky as the 4th leading digit). */
fun permToOctal(perm: String): String {
    val p = perm.trim()
    if (p.length < 10) return "???"
    var mode = 0
    for (i in 0 until 9) {
        val c = p[i + 1]
        val bit = when (i % 3) {
            0 -> if (c == 'r' || c == 'R') 4 else 0
            1 -> if (c == 'w' || c == 'W') 2 else 0
            else -> if (c == 'x' || c == 's' || c == 't' || c == 'S' || c == 'T') 1 else 0
        }
        mode = (mode shl 3) or bit
    }
    var special = 0
    if (p.getOrNull(3) == 's' || p.getOrNull(3) == 'S') special = special or 4
    if (p.getOrNull(6) == 's' || p.getOrNull(6) == 'S') special = special or 2
    if (p.getOrNull(9) == 't' || p.getOrNull(9) == 'T') special = special or 1
    val digits = String.format(Locale.US, "%03o", mode)
    return if (special != 0) "$special$digits" else digits
}

/** `755` / `0755` / `4755` → `rwxr-xr-x` (returns null for invalid input). */
fun octalToRwx(octal: String): String? {
    val cleaned = octal.trim()
    if (!cleaned.matches(Regex("[0-7]{3,4}"))) return null
    val digits = cleaned.takeLast(3)
    val special = if (cleaned.length == 4) cleaned[0] - '0' else 0
    val out = StringBuilder()
    for (i in 0 until 3) {
        val v = digits[i] - '0'
        out.append(if (v and 4 != 0) 'r' else '-')
        out.append(if (v and 2 != 0) 'w' else '-')
        val exec = v and 1 != 0
        out.append(
            when {
                i == 0 && special and 4 != 0 -> if (exec) 's' else 'S'
                i == 1 && special and 2 != 0 -> if (exec) 's' else 'S'
                i == 2 && special and 1 != 0 -> if (exec) 't' else 'T'
                exec -> 'x'
                else -> '-'
            }
        )
    }
    return out.toString()
}

/** The lowest digit of an octal mode as 9 booleans (rwx × 3), for the checkbox editor. */
fun octalToBits(octal: String): BooleanArray? {
    val cleaned = octal.trim()
    if (!cleaned.matches(Regex("[0-7]{3,4}"))) return null
    val digits = cleaned.takeLast(3)
    val bits = BooleanArray(9)
    for (i in 0 until 3) {
        val v = digits[i] - '0'
        bits[i * 3] = v and 4 != 0
        bits[i * 3 + 1] = v and 2 != 0
        bits[i * 3 + 2] = v and 1 != 0
    }
    return bits
}

/** Keeps a 4th (setuid/setgid/sticky) digit from [octal] when rebuilding from [bits]. */
fun bitsToOctal(bits: BooleanArray, keepSpecialFrom: String): String {
    var mode = 0
    for (b in bits) {
        mode = (mode shl 1) or (if (b) 1 else 0)
    }
    val digits = String.format(Locale.US, "%03o", mode)
    val special = keepSpecialFrom.trim().takeIf { it.length == 4 }?.get(0)?.takeIf { it in '1'..'7' }
    return if (special != null) "$special$digits" else digits
}

object FileEntryParser {

    private const val CONTEXT = """(?:\?|[^\s]+:[^\s]+)"""

    private val LINE = Regex(
        """^([bcdlps-][rwxstST-]{9}[.+@]?)\s+(\d+)\s+(\S+)\s+(\S+)\s+($CONTEXT\s+)?(\d+)\s+""" +
            """(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}(?::\d{2})?|[A-Za-z]{3}\s+\d{1,2}\s+(?:\d{4}|\d{2}:\d{2}))\s+(.+)$"""
    )

    /**
     * Parses `ls -la` / `ls -laZ` output. Directory "-h" style prefixes and `total` lines are
     * skipped; `.` / `..` are dropped; `name -> target` symlink tails are stripped.
     */
    fun parse(dir: String, lines: List<String>): List<FileEntry> {
        val entries = ArrayList<FileEntry>(lines.size)
        for (line in lines) {
            val m = LINE.matchEntire(line) ?: continue
            val perm = m.groupValues[1]
            val owner = m.groupValues[3]
            val group = m.groupValues[4]
            val context = m.groupValues[5].trim().ifEmpty { null }
            val size = m.groupValues[6].toLongOrNull() ?: continue
            val date = m.groupValues[7].replace(Regex("\\s+"), " ")
            var name = m.groupValues[8]
            val isSymlink = perm.startsWith("l")
            if (isSymlink) {
                val arrow = name.lastIndexOf(" -> ")
                if (arrow > 0) name = name.substring(0, arrow)
            }
            if (name == "." || name == ".." || name.isEmpty()) continue
            val path = if (dir.endsWith("/")) "$dir$name" else "$dir/$name"
            entries.add(
                FileEntry(
                    name = name,
                    path = path,
                    isDirectory = perm.startsWith("d"),
                    isSymlink = isSymlink,
                    size = size,
                    date = date,
                    perm = perm.trimEnd('.', '+', '@'),
                    owner = owner,
                    group = group,
                    context = context,
                )
            )
        }
        return entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    }
}
