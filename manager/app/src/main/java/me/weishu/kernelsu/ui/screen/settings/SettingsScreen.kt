package me.weishu.kernelsu.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.Navigator
import me.weishu.kernelsu.ui.navigation3.Route
import me.weishu.kernelsu.ui.util.RootKey
import me.weishu.kernelsu.ui.viewmodel.SettingsViewModel

@Composable
fun SettingPager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true,
) {
    val viewModel = viewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val initialResumeHandled = rememberSaveable { mutableStateOf(false) }
    var showRootKey by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            viewModel.refresh()
        }
    }

    LifecycleResumeEffect(Unit) {
        if (initialResumeHandled.value && latestIsCurrentPage) {
            viewModel.refresh()
        }
        initialResumeHandled.value = true
        onPauseOrDispose { }
    }

    val actions = SettingsScreenActions(
        onSetCheckUpdate = viewModel::setCheckUpdate,
        onSetCheckModuleUpdate = viewModel::setCheckModuleUpdate,
        onOpenTheme = { navigator.push(Route.ColorPalette) },
        onSetUiModeIndex = { index ->
            viewModel.setUiMode(if (index == 0) UiMode.Miuix.value else UiMode.Material.value)
        },
        onOpenProfileTemplate = { navigator.push(Route.AppProfileTemplate) },
        onSetSuCompatMode = viewModel::setSuCompatMode,
        onSetKernelUmountEnabled = viewModel::setKernelUmountEnabled,
        onSetSelinuxHideEnabled = viewModel::setSelinuxHideEnabled,
        onSetSulogEnabled = viewModel::setSulogEnabled,
        onSetAdbRootEnabled = viewModel::setAdbRootEnabled,
        onSetShellSuAdbOnlyEnabled = viewModel::setShellSuAdbOnlyEnabled,
        onSetDefaultUmountModules = viewModel::setDefaultUmountModules,
        onSetEnableWebDebugging = viewModel::setEnableWebDebugging,
        onSetAutoJailbreak = viewModel::setAutoJailbreak,
        onSetUseSoftReboot = viewModel::setUseSoftReboot,
        onSetAntiLpeEnabled = viewModel::setAntiLpeEnabled,
        onOpenRootKey = { showRootKey = true },
        onSetDisguisePasscode = viewModel::setDisguisePasscode,
        onSetShowLauncherIcon = viewModel::setShowLauncherIcon,
        onOpenAbout = { navigator.push(Route.About) },
    )

    if (showRootKey) {
        RootKeyDialog(onDismiss = { showRootKey = false })
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingPagerMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> SettingPagerMaterial(uiState, actions, bottomInnerPadding)
    }
}

@Composable
private fun RootKeyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf<String?>(null) }
    var supported by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            supported = RootKey.isSupported()
            key = if (supported) RootKey.ensure() else RootKey.read()
        }
        loading = false
    }

    val copyLabel = stringResource(R.string.root_key_copy)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.root_key_title)) },
        text = {
            Column {
                Text(stringResource(R.string.root_key_desc))
                Spacer(Modifier.height(12.dp))
                when {
                    loading -> Text(stringResource(R.string.root_key_loading))
                    !supported -> Text(stringResource(R.string.root_key_need_kernel))
                    key == null -> Text(stringResource(R.string.root_key_missing))
                    else -> {
                        SelectionContainer {
                            Text(
                                text = key!!,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.root_key_usage, key!!))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = key != null,
                onClick = {
                    key?.let {
                        clipboard.setText(AnnotatedString(it))
                        Toast.makeText(context, copyLabel, Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text(copyLabel)
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = supported && !working,
                    onClick = {
                        scope.launch {
                            working = true
                            key = withContext(Dispatchers.IO) { RootKey.reset() }
                            working = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.root_key_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.root_key_close))
                }
            }
        },
    )
}
