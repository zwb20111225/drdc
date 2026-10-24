package me.weishu.kernelsu.ui.screen.files

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Single-slot long-task coordinator. The heavy lifting happens in [FileTaskService] (a
 * foreground service so the OS does not kill the process); the UI observes [state] for its
 * progress overlay and [finishedEvents] for results.
 */
object FileTasks {

    data class TaskState(
        val generation: Long,
        val title: String,
        val progress: Float? = null,
        val message: String? = null,
        val finished: Boolean = false,
        val failed: Boolean = false,
    )

    val state = MutableStateFlow<TaskState?>(null)
    val finishedEvents = MutableSharedFlow<TaskState>(extraBufferCapacity = 16)

    private val generation = AtomicLong()
    private var pending: (suspend (FileProgress) -> Unit)? = null

    val isBusy: Boolean get() = state.value != null

    /** Returns false when another task is already running. */
    fun start(context: Context, title: String, block: suspend (FileProgress) -> Unit): Boolean {
        if (isBusy) return false
        val id = generation.incrementAndGet()
        pending = block
        state.value = TaskState(id, title)
        FileTaskService.start(context.applicationContext, id, title)
        return true
    }

    internal fun takePending(): (suspend (FileProgress) -> Unit)? {
        val block = pending
        pending = null
        return block
    }

    internal suspend fun report(id: Long, progress: Float?, message: String?) {
        val current = state.value ?: return
        if (current.generation != id) return
        state.value = current.copy(
            progress = progress ?: current.progress,
            message = message ?: current.message,
        )
    }

    internal suspend fun complete(id: Long, error: String?) {
        val current = state.value ?: return
        if (current.generation != id) return
        val done = current.copy(
            progress = 1f,
            finished = true,
            failed = error != null,
            message = error ?: current.message,
        )
        state.value = done
        finishedEvents.emit(done)
    }

    internal fun clear(id: Long) {
        if (state.value?.generation == id) state.value = null
    }
}
