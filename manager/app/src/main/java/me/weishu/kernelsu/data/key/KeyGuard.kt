package me.weishu.kernelsu.data.key

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.ksuApp
import java.io.IOException

sealed interface KeyState {
    data object Checking : KeyState
    data class NeedActivation(val reason: String? = null) : KeyState
    data class Active(
        val type: String,
        val cardExpireAt: Long,
        val grace: Boolean = false,
    ) : KeyState
}

/**
 * Single source of truth for activation state.
 *
 * Startup flow (SPEC §5.2): stored token that is still valid → main UI; expired token → try a
 * heartbeat; heartbeat unreachable → allow a 72h offline grace window; no token → activation.
 */
object KeyGuard {

    private const val GRACE_MS = 72L * 60 * 60 * 1000
    private const val HEARTBEAT_MS = 24L * 60 * 60 * 1000
    private const val RETRY_GAP_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    val state = MutableStateFlow<KeyState>(KeyState.Checking)

    @Volatile private var appContext: Context? = null
    @Volatile private var lastAttemptAt = 0L
    @Volatile private var cachedMachineId: String? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        refresh()
    }

    fun onForeground() {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        scope.launch { mutex.withLock { refreshLocked(force) } }
    }

    private suspend fun refreshLocked(force: Boolean) {
        try {
            refreshLockedInner(force)
        } catch (e: Throwable) {
            // Never let an unexpected error here kill the app; fall back to the local token.
            val stored = appContext?.let { TokenStore.load(it) }
            state.value = when {
                stored == null -> KeyState.NeedActivation()
                stored.tokenExpiresAt > System.currentTimeMillis() ->
                    KeyState.Active(stored.type, stored.cardExpireAt)

                System.currentTimeMillis() - stored.lastValidatedAt < GRACE_MS ->
                    KeyState.Active(stored.type, stored.cardExpireAt, grace = true)

                else -> KeyState.NeedActivation("offline_expired")
            }
        }
    }

    private suspend fun refreshLockedInner(force: Boolean) {
        val context = appContext ?: return
        val stored = TokenStore.load(context)
        if (stored == null) {
            state.value = KeyState.NeedActivation()
            return
        }
        val now = System.currentTimeMillis()
        val tokenValid = stored.tokenExpiresAt > now
        val due = force || !tokenValid || now - stored.lastValidatedAt >= HEARTBEAT_MS

        if (due && (force || now - lastAttemptAt >= RETRY_GAP_MS)) {
            lastAttemptAt = now
            try {
                val response = KeyApi.heartbeat(ksuApp.okhttpClient, stored.token, stored.machineId)
                TokenStore.save(context, response, stored.machineId)
                state.value = KeyState.Active(response.type ?: stored.type, response.expireAt)
                return
            } catch (e: KeyError) {
                if (isFatal(e.code)) {
                    TokenStore.clear(context)
                    state.value = KeyState.NeedActivation(e.code)
                    return
                }
                // Rate limit / transient server error: keep the local token and retry later.
            } catch (e: IOException) {
                // Offline: fall through to the local grace window.
            }
        }

        when {
            tokenValid -> state.value = KeyState.Active(stored.type, stored.cardExpireAt)
            now - stored.lastValidatedAt < GRACE_MS -> state.value = KeyState.Active(
                type = stored.type,
                cardExpireAt = stored.cardExpireAt,
                grace = true,
            )

            else -> state.value = KeyState.NeedActivation("offline_expired")
        }
    }

    suspend fun activate(cardKey: String, username: String): Result<Unit> = withContext(Dispatchers.IO) {
        val context = appContext
            ?: return@withContext Result.failure(IllegalStateException("KeyGuard is not initialized"))
        mutex.withLock {
            try {
                val machine = machineId(context)
                val response = KeyApi.activate(ksuApp.okhttpClient, cardKey, machine, username.trim())
                TokenStore.save(context, response, machine)
                UserProfile.setUsername(context, username)
                lastAttemptAt = System.currentTimeMillis()
                state.value = KeyState.Active(response.type ?: "permanent", response.expireAt)
                Result.success(Unit)
            } catch (e: KeyError) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }
    }

    fun signOut() {
        val context = appContext ?: return
        TokenStore.clear(context)
        state.value = KeyState.NeedActivation()
    }

    fun currentMachineId(): String {
        val context = appContext ?: return ""
        return machineId(context)
    }

    fun prettyMachineId(): String = MachineId.pretty(currentMachineId())

    private fun machineId(context: Context): String {
        cachedMachineId?.let { return it }
        val value = MachineId.compute(context)
        cachedMachineId = value
        return value
    }

    /** Codes that mean "this token/card can never work again" and require re-activation. */
    private fun isFatal(code: String): Boolean = code in FATAL_CODES

    private val FATAL_CODES = setOf(
        "invalid_card",
        "card_revoked",
        "card_expired",
        "card_inactive",
        "bound_other_device",
        "machine_mismatch",
        "bad_token",
    )
}
