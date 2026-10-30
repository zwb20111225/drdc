package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Three live stealth indicators shown under the home hero ring. Uses explicit colors so
 * the same composable works in both Material and Miuix themes.
 *
 * When the kernel driver is not installed, Natives calls can return stale/default true
 * values.  Gate every indicator on [kernelReady] so a non-rooted install shows
 * "not installed" instead of claiming SELinux/mounts/AVC are already hidden.
 */
@Composable
fun ProtectionChips(kernelReady: Boolean, modifier: Modifier = Modifier) {
    val selinux = remember(kernelReady) {
        kernelReady && runCatching { Natives.isSelinuxHideEnabled() }.getOrDefault(false)
    }
    val mount = remember(kernelReady) {
        kernelReady && runCatching {
            Natives.isKernelUmountEnabled() && Natives.isDefaultUmountModules()
        }.getOrDefault(false)
    }
    val material = LocalUiMode.current == UiMode.Material
    val scheme = MiuixTheme.colorScheme
    val onContainer = if (material) MaterialTheme.colorScheme.primaryContainer else scheme.primaryContainer
    val onContent = if (material) MaterialTheme.colorScheme.onPrimaryContainer else scheme.onPrimaryContainer
    val offContainer = if (material) MaterialTheme.colorScheme.errorContainer else scheme.errorContainer
    val offContent = if (material) MaterialTheme.colorScheme.onErrorContainer else scheme.onErrorContainer
    val unavailableContainer =
        if (material) MaterialTheme.colorScheme.surfaceContainerHigh else scheme.surfaceContainerHigh
    val unavailableContent =
        if (material) MaterialTheme.colorScheme.onSurfaceVariant else scheme.onSurfaceVariantSummary
    val unavailableText = stringResource(R.string.home_not_installed)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProtectionChip(
            icon = Icons.Rounded.Security,
            text = if (kernelReady) stringResource(R.string.home_hidden_selinux) else unavailableText,
            enabled = selinux,
            available = kernelReady,
            container = if (selinux) onContainer else offContainer,
            content = if (selinux) onContent else offContent,
            unavailableContainer = unavailableContainer,
            unavailableContent = unavailableContent,
        )
        ProtectionChip(
            icon = Icons.Rounded.Layers,
            text = if (kernelReady) stringResource(R.string.home_hidden_mount) else unavailableText,
            enabled = mount,
            available = kernelReady,
            container = if (mount) onContainer else offContainer,
            content = if (mount) onContent else offContent,
            unavailableContainer = unavailableContainer,
            unavailableContent = unavailableContent,
        )
        ProtectionChip(
            icon = Icons.Rounded.VisibilityOff,
            text = if (kernelReady) stringResource(R.string.home_hidden_avc) else unavailableText,
            enabled = kernelReady,
            available = kernelReady,
            container = onContainer,
            content = onContent,
            unavailableContainer = unavailableContainer,
            unavailableContent = unavailableContent,
        )
    }
}

@Composable
private fun ProtectionChip(
    icon: ImageVector,
    text: String,
    enabled: Boolean,
    available: Boolean,
    container: Color,
    content: Color,
    unavailableContainer: Color,
    unavailableContent: Color,
) {
    val bg = if (available) container else unavailableContainer
    val fg = if (available) content else unavailableContent
    Row(
        modifier = Modifier
            .background(bg.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            color = fg,
            fontSize = 10.5.sp,
            maxLines = 1,
        )
        if (available && !enabled) {
            Text(text = "!", color = fg, fontSize = 10.5.sp)
        }
    }
}
