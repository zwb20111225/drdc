package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme as miuixColorScheme

@Composable
fun ProtectionCardMaterial(
    state: SettingsUiState,
    actions: SettingsScreenActions,
) {
    Card(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 13.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.settings_protection_center),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_protection_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            ProtectionRowMaterial(
                title = stringResource(R.string.settings_selinux_hide),
                summary = stringResource(R.string.settings_selinux_hide_summary),
                checked = state.isSelinuxHideEnabled,
                onCheckedChange = actions.onSetSelinuxHideEnabled,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ProtectionRowMaterial(
                title = stringResource(R.string.settings_kernel_umount),
                summary = stringResource(R.string.settings_umount_modules_default_summary),
                checked = state.isKernelUmountEnabled && state.isDefaultUmountModules,
                onCheckedChange = { enabled ->
                    actions.onSetKernelUmountEnabled(enabled)
                    actions.onSetDefaultUmountModules(enabled)
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ProtectionRowMaterial(
                title = stringResource(R.string.settings_avc_spoof),
                summary = stringResource(R.string.settings_avc_spoof_summary),
                checked = true,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun ProtectionRowMaterial(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onCheckedChange != null) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.settings_feature_enabled),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun ProtectionCardMiuix(
    state: SettingsUiState,
    actions: SettingsScreenActions,
) {
    MiuixCard(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 12.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiuixIcon(
                    Icons.Rounded.Security,
                    contentDescription = null,
                    tint = miuixColorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    MiuixText(
                        text = stringResource(R.string.settings_protection_center),
                        fontSize = 16.sp,
                    )
                    MiuixText(
                        text = stringResource(R.string.settings_protection_summary),
                        fontSize = 12.sp,
                        color = miuixColorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            SwitchPreference(
                title = stringResource(R.string.settings_selinux_hide),
                summary = stringResource(R.string.settings_selinux_hide_summary),
                checked = state.isSelinuxHideEnabled,
                onCheckedChange = actions.onSetSelinuxHideEnabled,
            )
            SwitchPreference(
                title = stringResource(R.string.settings_kernel_umount),
                summary = stringResource(R.string.settings_umount_modules_default_summary),
                checked = state.isKernelUmountEnabled && state.isDefaultUmountModules,
                onCheckedChange = { enabled ->
                    actions.onSetKernelUmountEnabled(enabled)
                    actions.onSetDefaultUmountModules(enabled)
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MiuixText(
                        text = stringResource(R.string.settings_avc_spoof),
                        fontSize = 16.sp,
                    )
                    MiuixText(
                        text = stringResource(R.string.settings_avc_spoof_summary),
                        fontSize = 12.sp,
                        color = miuixColorScheme.onSurfaceVariantSummary,
                    )
                }
                MiuixText(
                    text = stringResource(R.string.settings_feature_enabled),
                    fontSize = 12.sp,
                    color = miuixColorScheme.primary,
                )
            }
        }
    }
}
