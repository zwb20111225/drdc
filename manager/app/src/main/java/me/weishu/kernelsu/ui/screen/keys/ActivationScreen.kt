package me.weishu.kernelsu.ui.screen.keys

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.key.KeyApi
import me.weishu.kernelsu.data.key.KeyError
import me.weishu.kernelsu.data.key.KeyGuard
import me.weishu.kernelsu.data.key.KeyState
import me.weishu.kernelsu.data.key.UserProfile
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.IOException
import java.util.Locale

/** Shared colors so the gate/status screens render correctly under both UI modes. */
internal data class KeysPalette(
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
internal fun keysPalette(): KeysPalette {
    return when (LocalUiMode.current) {
        UiMode.Miuix -> {
            val c = MiuixTheme.colorScheme
            KeysPalette(
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

        UiMode.Material -> {
            val c = MaterialTheme.colorScheme
            KeysPalette(
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
    }
}

/** Blocks the manager UI until a card is activated (SPEC §5.2). */
@Composable
fun KeyGate(content: @Composable () -> Unit) {
    val state by KeyGuard.state.collectAsStateWithLifecycle()
    when (state) {
        is KeyState.Active -> {
            val context = LocalContext.current
            var askUsername by remember {
                mutableStateOf(UserProfile.username(context).isBlank())
            }
            if (askUsername) {
                UsernamePrompt(
                    onSave = { name ->
                        KeyGuard.setUsername(name)
                        askUsername = false
                    },
                    onDismiss = { askUsername = false },
                )
            }
            content()
        }

        KeyState.Checking -> ActivationScreen(KeyState.Checking)
        is KeyState.NeedActivation -> ActivationScreen(state)
    }
}

@Composable
private fun UsernamePrompt(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val valid = name.trim().length in 1..20
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { MaterialText(stringResource(R.string.keys_username)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 20) name = it },
                label = { MaterialText(stringResource(R.string.keys_username_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(name.trim()) },
            ) {
                MaterialText(stringResource(R.string.keys_username_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                MaterialText(stringResource(R.string.keys_username_later))
            }
        },
    )
}

@Composable
private fun ActivationScreen(state: KeyState) {
    val palette = keysPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cardKey by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(UserProfile.username(context)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val reason = (state as? KeyState.NeedActivation)?.reason
    val checking = state is KeyState.Checking
    val keyReady = cardKey.count { it.isLetterOrDigit() } == 16
    val userReady = username.trim().length in 1..20
    val ready = keyReady && userReady

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                MaterialIcon(
                    imageVector = Icons.Rounded.VpnKey,
                    contentDescription = null,
                    tint = palette.onPrimaryContainer,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            MaterialText(
                text = stringResource(R.string.keys_gate_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            MaterialText(
                text = stringResource(R.string.keys_gate_subtitle),
                fontSize = 13.sp,
                color = palette.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            if (checking) {
                CircularProgressIndicator(color = palette.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(12.dp))
                MaterialText(
                    text = stringResource(R.string.keys_checking),
                    fontSize = 13.sp,
                    color = palette.onSurfaceVariant,
                )
                return@Column
            }

            KeysCard(palette) {
                MaterialText(
                    text = stringResource(R.string.keys_machine_id),
                    fontSize = 12.sp,
                    color = palette.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MaterialText(
                        text = KeyGuard.prettyMachineId(),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    MaterialIcon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.keys_machine_id_copy),
                        tint = palette.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("machine_id", KeyGuard.currentMachineId()),
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.keys_machine_id_copied),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            .padding(6.dp)
                            .size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            KeysCard(palette) {
                MaterialText(
                    text = stringResource(R.string.keys_username),
                    fontSize = 12.sp,
                    color = palette.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    if (username.isEmpty()) {
                        MaterialText(
                            text = stringResource(R.string.keys_username_hint),
                            fontSize = 15.sp,
                            color = palette.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    BasicTextField(
                        value = username,
                        onValueChange = { if (it.length <= 20) username = it },
                        textStyle = TextStyle(
                            color = palette.onSurface,
                            fontSize = 15.sp,
                        ),
                        cursorBrush = SolidColor(palette.primary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            KeysCard(palette) {
                MaterialText(
                    text = stringResource(R.string.keys_card_key),
                    fontSize = 12.sp,
                    color = palette.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    if (cardKey.isEmpty()) {
                        MaterialText(
                            text = stringResource(R.string.keys_card_key_hint),
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    BasicTextField(
                        value = cardKey,
                        onValueChange = { cardKey = formatCardKey(it) },
                        textStyle = TextStyle(
                            color = palette.onSurface,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(palette.primary),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val message = error ?: reasonText(reason)
            if (message != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MaterialIcon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = palette.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    MaterialText(
                        text = message,
                        fontSize = 13.sp,
                        color = palette.error,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (ready && !busy) palette.primary else palette.primary.copy(alpha = 0.35f))
                    .clickable(enabled = ready && !busy) {
                        busy = true
                        error = null
                        scope.launch {
                            val result = KeyGuard.activate(cardKey, username.trim())
                            busy = false
                            if (result.isFailure) {
                                error = errorMessage(context, result.exceptionOrNull())
                            }
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        color = palette.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    MaterialText(
                        text = stringResource(R.string.keys_activate),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.onPrimary.copy(alpha = if (ready) 1f else 0.6f),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            MaterialText(
                text = KeyApi.serverUrl + "  ·  v" + BuildConfig.VERSION_NAME,
                fontSize = 11.sp,
                color = palette.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun KeysCard(palette: KeysPalette, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .padding(14.dp),
        content = content,
    )
}

internal fun formatCardKey(input: String): String {
    val body = input.uppercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }
        .removePrefix("DRDC")
        .take(12)
    if (body.isEmpty()) return ""
    return buildString {
        append("DRDC")
        body.chunked(4).forEach { chunk ->
            if (chunk.isNotEmpty()) {
                append('-')
                append(chunk)
            }
        }
    }
}

@Composable
private fun reasonText(reason: String?): String? = when (reason) {
    null -> null
    "card_expired" -> stringResource(R.string.keys_error_expired)
    "card_revoked" -> stringResource(R.string.keys_error_revoked)
    "bound_other_device", "machine_mismatch" -> stringResource(R.string.keys_error_bound)
    "offline_expired" -> stringResource(R.string.keys_error_offline_expired)
    "invalid_card" -> stringResource(R.string.keys_error_invalid_card)
    else -> null
}

internal fun errorMessage(context: Context, error: Throwable?): String = when (error) {
    is KeyError -> when (error.code) {
        "invalid_card" -> context.getString(R.string.keys_error_invalid_card)
        "bound_other_device" -> context.getString(R.string.keys_error_bound)
        "card_expired" -> context.getString(R.string.keys_error_expired)
        "card_revoked" -> context.getString(R.string.keys_error_revoked)
        "rate_limited" -> context.getString(R.string.keys_error_rate)
        "bad_signature" -> context.getString(R.string.keys_error_signature)
        else -> context.getString(R.string.keys_error_generic, error.code)
    }

    is IOException -> {
        val detail = error.message?.replace('\n', ' ')?.take(90)
        if (detail.isNullOrBlank()) {
            context.getString(R.string.keys_error_network)
        } else {
            context.getString(R.string.keys_error_network_detail, detail)
        }
    }

    else -> context.getString(R.string.keys_error_generic, error?.message ?: "unknown")
}
