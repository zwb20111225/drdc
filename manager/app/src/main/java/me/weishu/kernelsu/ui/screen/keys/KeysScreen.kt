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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.key.KeyApi
import me.weishu.kernelsu.data.key.KeyGuard
import me.weishu.kernelsu.data.key.KeyState
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun KeysPager(
    bottomInnerPadding: Dp,
    @Suppress("UNUSED_PARAMETER") isCurrentPage: Boolean = true,
) {
    val palette = keysPalette()
    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            MiuixScaffold(
                topBar = { MiuixTopAppBar(title = stringResource(R.string.keys_title)) },
                contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal),
            ) { innerPadding ->
                KeysBody(palette, innerPadding, bottomInnerPadding)
            }
        }

        UiMode.Material -> {
            ExpressiveScaffold(
                topBar = {
                    LargeFlexibleTopAppBar(
                        title = { MaterialText(stringResource(R.string.keys_title)) },
                        colors = expressiveTopAppBarColors(),
                    )
                },
            ) { innerPadding ->
                KeysBody(palette, innerPadding, bottomInnerPadding)
            }
        }
    }
}

@Composable
private fun KeysBody(palette: KeysPalette, innerPadding: PaddingValues, bottomInnerPadding: Dp) {
    val state by KeyGuard.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(bottom = bottomInnerPadding)
            .background(palette.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            val active = state as? KeyState.Active
            val statusText = when {
                state is KeyState.Checking -> stringResource(R.string.keys_checking)
                active != null && active.grace -> stringResource(R.string.keys_status_grace)
                active != null -> stringResource(R.string.keys_status_active)
                else -> stringResource(R.string.keys_status_inactive)
            }
            val statusColor = when {
                active != null && active.grace -> palette.error
                active != null -> palette.primary
                else -> palette.onSurfaceVariant
            }
            val statusIcon = when {
                active != null && active.grace -> Icons.Rounded.CloudOff
                active != null -> Icons.Rounded.CheckCircle
                else -> Icons.Rounded.VpnKey
            }

            KeysCard(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MaterialIcon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        MaterialText(
                            text = statusText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onSurface,
                        )
                        if (active != null) {
                            MaterialText(
                                text = if (active.cardExpireAt == 0L) {
                                    stringResource(R.string.keys_expire_never)
                                } else {
                                    stringResource(
                                        R.string.keys_expire_value,
                                        formatDate(active.cardExpireAt),
                                    )
                                },
                                fontSize = 12.sp,
                                color = palette.onSurfaceVariant,
                            )
                        }
                    }
                    MaterialIconButton(onClick = { KeyGuard.refresh(force = true) }) {
                        MaterialIcon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.keys_refresh),
                            tint = palette.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (active != null && active.grace) {
                    Spacer(Modifier.height(8.dp))
                    MaterialText(
                        text = stringResource(R.string.keys_grace_hint),
                        fontSize = 12.sp,
                        color = palette.error,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            KeysCard(palette) {
                InfoRow(palette, stringResource(R.string.keys_type)) {
                    MaterialText(
                        text = if (active?.type == "days") {
                            stringResource(R.string.keys_type_days)
                        } else {
                            stringResource(R.string.keys_type_permanent)
                        },
                        fontSize = 13.sp,
                        color = palette.onSurface,
                    )
                }
                InfoRow(palette, stringResource(R.string.keys_machine)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MaterialText(
                            text = KeyGuard.prettyMachineId(),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
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
                                        ClipData.newPlainText(
                                            "machine_id",
                                            KeyGuard.currentMachineId(),
                                        ),
                                    )
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.keys_machine_id_copied),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                .padding(4.dp)
                                .size(16.dp),
                        )
                    }
                }
                InfoRow(palette, stringResource(R.string.keys_server)) {
                    MaterialText(
                        text = KeyApi.serverUrl,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeysButton(
                    palette = palette,
                    label = stringResource(R.string.keys_refresh),
                    primary = true,
                    modifier = Modifier.weight(1f),
                ) { KeyGuard.refresh(force = true) }
                KeysButton(
                    palette = palette,
                    label = stringResource(R.string.keys_sign_out),
                    primary = false,
                    modifier = Modifier.weight(1f),
                ) { confirmSignOut = true }
            }
            Spacer(Modifier.height(18.dp))
        }

        if (confirmSignOut) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { confirmSignOut = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surface)
                        .clickable(enabled = false) {}
                        .padding(20.dp),
                ) {
                    MaterialText(
                        text = stringResource(R.string.keys_sign_out),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    MaterialText(
                        text = stringResource(R.string.keys_sign_out_confirm),
                        fontSize = 13.sp,
                        color = palette.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KeysButton(
                            palette = palette,
                            label = stringResource(android.R.string.cancel),
                            primary = false,
                            modifier = Modifier.weight(1f),
                        ) { confirmSignOut = false }
                        KeysButton(
                            palette = palette,
                            label = stringResource(R.string.keys_sign_out),
                            primary = true,
                            modifier = Modifier.weight(1f),
                        ) {
                            confirmSignOut = false
                            KeyGuard.signOut()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(palette: KeysPalette, label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialText(
            text = label,
            fontSize = 12.sp,
            color = palette.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Box(Modifier.weight(1f)) { value() }
    }
}

@Composable
private fun KeysButton(
    palette: KeysPalette,
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) palette.primary else palette.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        MaterialText(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
            color = if (primary) palette.onPrimary else palette.onSecondaryContainer,
        )
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

internal fun formatDate(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(DATE_FORMAT)
