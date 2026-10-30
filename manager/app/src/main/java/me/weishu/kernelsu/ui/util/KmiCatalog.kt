package me.weishu.kernelsu.ui.util

/**
 * KMI helpers that do not require a running root daemon.
 *
 * The install page used to ask drdcd for the current/supported KMIs only.  On a
 * not-yet-rooted device that command is unavailable, so the picker was empty
 * and showed "Unknown" even for a perfectly normal GKI kernel.  Keep a static
 * catalogue and derive the current KMI from the kernel release as a fallback.
 */
object KmiCatalog {
    val supported: List<String> = listOf(
        "android12-5.10",
        "android13-5.10",
        "android13-5.15",
        "android14-5.15",
        "android14-6.1",
        "android15-6.6",
        "android16-6.12",
        "android17-6.18",
    )

    private val kernelReleaseRegex = Regex("""^(\d+)\.(\d+)\..*?-android(\d+)""")

    /** "6.1.115-android14-11-o-gae..." -> "android14-6.1". */
    fun fromKernelRelease(release: String): String {
        val match = kernelReleaseRegex.find(release.trim()) ?: return ""
        val major = match.groupValues[1]
        val minor = match.groupValues[2]
        val android = match.groupValues[3]
        val kmi = "android$android-$major.$minor"
        return if (kmi in supported) kmi else ""
    }

    /** Best-effort detection without root; returns "" when unknown. */
    fun currentFromSystem(): String =
        fromKernelRelease(System.getProperty("os.version").orEmpty())
}
