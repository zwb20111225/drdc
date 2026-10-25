package me.weishu.kernelsu

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.UserManager
import android.system.Os
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import me.weishu.kernelsu.data.GrantBackup
import me.weishu.kernelsu.data.BootRepair
import me.weishu.kernelsu.data.key.KeyGuard
import me.weishu.kernelsu.ui.util.AntiLpe
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.util.Locale

lateinit var ksuApp: KernelSUApplication

class KernelSUApplication : Application(), ViewModelStoreOwner {

    companion object {
        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val applicationInfoClass = ApplicationInfo::class.java
                val method = applicationInfoClass.getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }

    lateinit var okhttpClient: OkHttpClient
    private val appViewModelStore by lazy { ViewModelStore() }

    private fun isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked == true

    override fun onCreate() {
        super.onCreate()
        // Capture crashes to the public Downloads folder so users can send them
        // without root or a PC (installed before the user-unlocked check).
        Thread.setDefaultUncaughtExceptionHandler(
            CrashLogger(this, Thread.getDefaultUncaughtExceptionHandler())
        )
        ksuApp = this

        // Keep a trace of how far startup gets, and report the previous run's native
        // crash / logcat crash buffer on the next launch.
        Diagnostics.start(this)

        if (!isUserUnlocked()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val enable = SettingsRepositoryImpl().enablePredictiveBack
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")
            setEnableOnBackInvokedCallback(applicationInfo, enable)
        }

        // Keep the hidden real launcher entry in sync with the persisted setting.
        SettingsRepositoryImpl().syncLauncherIconState()

        // The superuser list is loaded when its page opens; preloading it here meant hundreds
        // of JNI profile lookups during startup, which is both slow and risky on kernels that
        // are not DRDC (it is the first thing that runs once root is granted).

        val webroot = File(dataDir, "webroot")
        if (!webroot.exists()) {
            webroot.mkdir()
        }

        // Provide working env for rust's temp_dir()
        Os.setenv("TMPDIR", cacheDir.absolutePath, true)

        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "DRDC/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }.build()

        KeyGuard.init(this)
        Diagnostics.mark(this, "application.ready")

        // Some kernels lose the allowlist across reboots; re-apply the grants we saved.
        // Restore grants before the UI loads so the superuser list does not appear empty.
        // These are Natives ioctls (no su), so this is cheap enough for onCreate.
        runCatching { GrantBackup.restore(this) }

        // Recover modules that were stuck in modules_update / never got their .remove
        // marker processed because post-fs-data did not run at boot.
        Thread({ runCatching { BootRepair.maybeRun() } }, "drdc-boot-repair").apply { isDaemon = true }.start()

        // Apply the saved PI-futex filter preference to the running kernel.
        Thread({
            runCatching {
                if (Natives.isManager && AntiLpe.isSupported()) {
                    AntiLpe.apply(SettingsRepositoryImpl().antiLpeEnabled)
                }
            }
        }, "drdc-antilpe").apply { isDaemon = true }.start()
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}
