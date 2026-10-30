package me.weishu.kernelsu.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.WarningLevel
import me.weishu.kernelsu.ui.component.dialog.rememberConfirmDialog
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.rebootlistpopup.RebootListPopup
import me.weishu.kernelsu.ui.component.statustag.StatusTag
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo

@Composable
fun HomePagerMaterial(
    state: HomeUiState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    ExpressiveScaffold(
        topBar = { TopBar(scrollBehavior = scrollBehavior) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            GreetingHeader()
            if (state.checkUpdateEnabled) {
                UpdateCard(state = state, actions = actions)
            }
            if (state.showManagerPrBuildWarning) {
                WarningCard(stringResource(id = R.string.home_pr_build_warning), level = WarningLevel.Notice)
            } else if (state.showKernelPrBuildWarning) {
                WarningCard(stringResource(id = R.string.home_pr_kernel_warning), level = WarningLevel.Notice)
            }
            if (state.showGkiWarning) {
                WarningCard(stringResource(id = R.string.home_gki_warning), level = WarningLevel.Notice)
            }
            if (state.requiresNewKernel) {
                WarningCard(
                    stringResource(
                        id = if (state.canInstallKernelUpdate) R.string.require_kernel_version else R.string.require_kernel_version_gki
                    ),
                    onClick = if (state.canInstallKernelUpdate) actions.onInstallClick else null
                )
            }
            if (state.requiresNewManager) {
                WarningCard(
                    stringResource(
                        id = R.string.require_manager_version
                    )
                )
            }
            if (state.showLkmUpdate) {
                WarningCard(
                    message = stringResource(R.string.home_lkm_update_available),
                    level = WarningLevel.Notice,
                    onClick = actions.onInstallClick,
                )
            }
            if (state.showRootWarning) {
                WarningCard(stringResource(id = R.string.grant_root_failed))
            }
            HeroCard(
                state = state,
                actions = actions,
            )
            HomeTiles(actions = actions)
            InfoCard(systemInfo = state.systemInfo)
            Spacer(
                Modifier.height(
                    bottomInnerPadding + if (!Natives.isFullFeatured())
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
                )
            )
        }
    }
}

@Composable
private fun UpdateCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val newVersion = state.latestVersionInfo
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)
    val updateDialog = rememberConfirmDialog(onConfirm = { actions.onOpenUrl(newVersion.downloadUrl) })

    AnimatedVisibility(
        visible = state.hasUpdate,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        WarningCard(
            message = stringResource(id = R.string.new_version_available, newVersion.versionCode),
            level = WarningLevel.Notice,
            onClick = {
                if (newVersion.changelog.isEmpty()) {
                    actions.onOpenUrl(newVersion.downloadUrl)
                } else {
                    updateDialog.showConfirm(
                        title = title,
                        content = newVersion.changelog,
                        markdown = true,
                        confirm = updateText
                    )
                }
            }
        )
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = { RebootListPopup() },
        colors = expressiveTopAppBarColors(),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun HeroCard(
    state: HomeUiState,
    actions: HomeActions,
) {
    val connected = state.ksuVersion != null
    val isGki = state.kernelVersion.isGKI()
    val notInstalled = !connected && isGki
    val canTap = !state.isLateLoadMode

    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = { if (canTap) actions.onInstallClick() },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal(size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        R.string.home_brand_subtitle,
                        state.systemInfo.kernelVersion.substringBefore("-")
                    ),
                    modifier = Modifier.weight(1f),
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val mode = workingMode(state)
                if (connected && mode != null) {
                    StatusTag(
                        label = mode,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (connected) "已安装" else "未安装",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "内核 " + state.systemInfo.kernelVersion.substringBefore("-") +
                        if (isGki) " · GKI" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!connected) {
                    Text(
                        text = "点击这里安装内核模块",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }

            ProtectionChips(kernelReady = connected, modifier = Modifier.padding(top = 10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_bubble),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(76.dp)
                        .rotate(-7f),
                )
            }

            val tags = buildList {
                if (connected && state.isSafeMode) add(stringResource(R.string.safe_mode) to true)
                if (connected && state.isLateLoadMode) add(stringResource(R.string.jailbreak_mode) to true)
                if (state.showCustomLkmBadge) add(stringResource(R.string.home_lkm_custom) to false)
            }
            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { (label, isAlert) ->
                        if (isAlert) {
                            StatusTag(
                                label = label,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                backgroundColor = MaterialTheme.colorScheme.errorContainer,
                            )
                        } else {
                            StatusTag(
                                label = label,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                            )
                        }
                    }
                }
            }

            if (notInstalled && state.isSELinuxPermissive) {
                TextButton(
                    onClick = actions.onJailbreakClick,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.home_jailbreak))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroStat(
                    value = state.systemInfo.managerVersion.substringBefore(" "),
                    label = stringResource(R.string.home_stat_manager),
                    modifier = Modifier.weight(1f),
                )
                StatDivider()
                HeroStat(
                    value = state.systemInfo.deviceModel,
                    label = stringResource(R.string.home_stat_device),
                    modifier = Modifier.weight(1f),
                )
                StatDivider()
                HeroStat(
                    value = selinuxDisplay(state.systemInfo.selinuxStatus),
                    label = stringResource(R.string.home_stat_selinux),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroRing(state: HomeUiState) {
    val connected = state.ksuVersion != null
    val isGki = state.kernelVersion.isGKI()
    val valueText = when {
        connected -> "100"
        isGki -> stringResource(R.string.home_not_installed)
        else -> stringResource(R.string.home_unsupported)
    }
    val mode = workingMode(state)
    val subText = when {
        connected && mode != null -> "drdc ${state.systemInfo.managerVersion.substringBefore(" ")} · $mode"
        connected -> "drdc ${state.systemInfo.managerVersion.substringBefore(" ")}"
        isGki -> "${state.systemInfo.kernelVersion.substringBefore("-")} · GKI"
        else -> state.systemInfo.kernelVersion.substringBefore("-")
    }
    val accent = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Box(modifier = Modifier.size(164.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val diameter = size.minDimension - stroke
            val arcTopLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = if (connected) 360f else 110f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (connected) "PROTECTED" else stringResource(R.string.home_status_title),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueText,
                modifier = Modifier.padding(vertical = 3.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
            )
            Text(
                text = subText,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = 3.dp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    )
}

@Composable
private fun Seal(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.app_seal),
            fontSize = (size.value * 0.46f).sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun HomeTiles(actions: HomeActions) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            HomeTile(
                icon = Icons.Rounded.Extension,
                title = stringResource(R.string.module),
                summary = stringResource(R.string.home_tile_module_desc),
                onClick = actions.onOpenModule,
                modifier = Modifier.weight(1f),
            )
            HomeTile(
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.superuser),
                summary = stringResource(R.string.home_tile_superuser_desc),
                onClick = actions.onOpenSuperUser,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HomeTile(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TonalCard(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(11.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WarningCard(
    message: String,
    level: WarningLevel = WarningLevel.Error,
    onClick: (() -> Unit)? = null
) {
    val containerColor = when (level) {
        WarningLevel.Error -> MaterialTheme.colorScheme.errorContainer
        WarningLevel.Notice -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.contentColorFor(containerColor)
            )
        }
    }
    if (onClick != null) {
        TonalCard(containerColor = containerColor, onClick = onClick, content = content)
    } else {
        TonalCard(containerColor = containerColor, content = content)
    }
}

@Composable
private fun InfoCard(
    systemInfo: SystemInfo,
    modifier: Modifier = Modifier,
) {
    @Composable
    fun InfoCardItem(
        icon: ImageVector,
        label: String,
        content: String,
        modifier: Modifier = Modifier,
    ) {
        SegmentedListItem(
            modifier = modifier,
            headlineContent = { Text(text = label, style = MaterialTheme.typography.bodyLarge) },
            leadingContent = { Icon(imageVector = icon, contentDescription = label) },
            supportingContent = {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }

    val selinuxDisplay = when (systemInfo.selinuxStatus) {
        "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
        "Permissive" -> stringResource(R.string.selinux_status_permissive)
        "Disabled" -> stringResource(R.string.selinux_status_disabled)
        else -> stringResource(R.string.selinux_status_unknown)
    }
    val seccompDisplay = when (systemInfo.seccompStatus) {
        -1 -> stringResource(R.string.seccomp_status_not_supported)
        0 -> stringResource(R.string.seccomp_status_disabled)
        1 -> stringResource(R.string.seccomp_status_strict)
        2 -> stringResource(R.string.seccomp_status_filter)
        else -> stringResource(R.string.seccomp_status_unknown)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                InfoCardItem(
                    icon = Icons.Filled.Tag,
                    label = stringResource(R.string.home_manager_version),
                    content = systemInfo.managerVersion,
                )
            }
            item {
                InfoCardItem(
                    icon = Icons.Filled.DeveloperBoard,
                    label = stringResource(R.string.home_kernel),
                    content = systemInfo.kernelVersion,
                )
            }
            item {
                InfoCardItem(
                    icon = Icons.Filled.Smartphone,
                    label = stringResource(R.string.home_device_model),
                    content = systemInfo.deviceModel,
                )
            }
            item {
                InfoCardItem(
                    icon = Icons.Filled.Fingerprint,
                    label = stringResource(R.string.home_fingerprint),
                    content = systemInfo.fingerprint,
                )
            }
        }
        SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                InfoCardItem(
                    icon = Icons.Filled.Security,
                    label = stringResource(R.string.home_selinux_status),
                    content = selinuxDisplay,
                )
            }
            item {
                InfoCardItem(
                    icon = Icons.Filled.FilterList,
                    label = stringResource(R.string.home_seccomp_status),
                    content = seccompDisplay,
                )
            }
        }
    }
}

private fun workingMode(state: HomeUiState): String? = when (state.lkmMode) {
    null -> null
    true -> "LKM"
    false -> "GKI"
}

@Composable
private fun selinuxDisplay(status: String): String = when (status) {
    "Enforcing" -> stringResource(R.string.selinux_status_enforcing)
    "Permissive" -> stringResource(R.string.selinux_status_permissive)
    "Disabled" -> stringResource(R.string.selinux_status_disabled)
    else -> stringResource(R.string.selinux_status_unknown)
}

@Preview(name = "Home Activated", showBackground = true)
@Composable
private fun HomeScreenActivatedPreview() {
    HomeScreenPreviewContent(ksuVersion = 12345, lkmMode = true)
}

@Preview(name = "Home Not Activated", showBackground = true)
@Composable
private fun HomeScreenNotActivatedPreview() {
    HomeScreenPreviewContent(ksuVersion = null, lkmMode = null)
}

@Preview(name = "Home Permissive", showBackground = true)
@Composable
private fun HomeScreenPermissivePreview() {
    HomeScreenPreviewContent(ksuVersion = null, lkmMode = null, selinuxStatus = "Permissive")
}

@Preview(name = "Home Jailbreak", showBackground = true)
@Composable
private fun HomeScreenJailbreakPreview() {
    HomeScreenPreviewContent(ksuVersion = 12345, lkmMode = true, isLateLoadMode = true)
}

private val previewSystemInfo = SystemInfo(
    kernelVersion = "6.12.23-android16-5-g123456789000-abogki123456789-4k",
    managerVersion = "3.0.0 (30000)",
    deviceModel = "Xiaomi 17 Pro Max",
    fingerprint = "Xiaomi/popsicle/popsicle:16/BQ2A.250705.001-BP2A.250605.031.A3/OS3.0.313.0.WPBCNXM:user/release-keys",
    selinuxStatus = "Enforcing",
    seccompStatus = 2
)

private val previewUriHandler = object : UriHandler {
    override fun openUri(uri: String) {}
}

@Composable
private fun HomeScreenPreviewContent(
    ksuVersion: Int?,
    lkmMode: Boolean?,
    isSafeMode: Boolean = false,
    isLateLoadMode: Boolean = false,
    selinuxStatus: String = "Enforcing",
) {
    CompositionLocalProvider(LocalUriHandler provides previewUriHandler) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val actions = HomeActions({}, {})
            HeroCard(
                state = previewHomeScreenState(
                    ksuVersion = ksuVersion,
                    lkmMode = lkmMode,
                    isSafeMode = isSafeMode,
                    isLateLoadMode = isLateLoadMode,
                    selinuxStatus = selinuxStatus,
                ),
                actions = actions
            )
            HomeTiles(actions = actions)
            InfoCard(previewSystemInfo.copy(selinuxStatus = selinuxStatus))
        }
    }
}

private fun previewHomeScreenState(
    ksuVersion: Int?,
    lkmMode: Boolean?,
    isSafeMode: Boolean = false,
    isLateLoadMode: Boolean = false,
    selinuxStatus: String = "Enforcing",
) = HomeUiState(
    kernelVersion = KernelVersion(6, 1, 0),
    ksuVersion = ksuVersion,
    lkmMode = lkmMode,
    isLkmBundled = lkmMode == true,
    isManager = true,
    isManagerPrBuild = false,
    isKernelPrBuild = false,
    requiresNewKernel = false,
    requiresNewManager = false,
    isRootAvailable = ksuVersion != null,
    isSafeMode = isSafeMode,
    isLateLoadMode = isLateLoadMode,
    checkUpdateEnabled = false,
    latestVersionInfo = LatestVersionInfo(),
    currentManagerVersionCode = 10000,
    systemInfo = previewSystemInfo.copy(selinuxStatus = selinuxStatus),
    kernelUAPIVersion = 1,
    managerUAPIVersion = 1,
)
