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
 */
@Composable
fun ProtectionChips(modifier: Modifier = Modifier) {
    val selinux = remember { runCatching { Natives.isSelinuxHideEnabled() }.getOrDefault(false) }
    val mount = remember {
        runCatching { Natives.isKernelUmountEnabled() && Natives.isDefaultUmountModules() }
            .getOrDefault(false)
    }
    val material = LocalUiMode.current == UiMode.Material
    val scheme = MiuixTheme.colorScheme
    val onContainer = if (material) MaterialTheme.colorScheme.primaryContainer else scheme.primaryContainer
    val onContent = if (material) MaterialTheme.colorScheme.onPrimaryContainer else scheme.onPrimaryContainer
    val offContainer = if (material) MaterialTheme.colorScheme.errorContainer else scheme.errorContainer
    val offContent = if (material) MaterialTheme.colorScheme.onErrorContainer else scheme.onErrorContainer

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProtectionChip(
            icon = Icons.Rounded.Security,
            text = stringResource(R.string.home_hidden_selinux),
            enabled = selinux,
            container = if (selinux) onContainer else offContainer,
            content = if (selinux) onContent else offContent,
        )
        ProtectionChip(
            icon = Icons.Rounded.Layers,
            text = stringResource(R.string.home_hidden_mount),
            enabled = mount,
            container = if (mount) onContainer else offContainer,
            content = if (mount) onContent else offContent,
        )
        ProtectionChip(
            icon = Icons.Rounded.VisibilityOff,
            text = stringResource(R.string.home_hidden_avc),
            enabled = true,
            container = onContainer,
            content = onContent,
        )
    }
}

@Composable
private fun ProtectionChip(
    icon: ImageVector,
    text: String,
    enabled: Boolean,
    container: Color,
    content: Color,
) {
    Row(
        modifier = Modifier
            .background(container.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            color = content,
            fontSize = 10.5.sp,
            maxLines = 1,
        )
        if (!enabled) {
            Text(text = "!", color = content, fontSize = 10.5.sp)
        }
    }
}
