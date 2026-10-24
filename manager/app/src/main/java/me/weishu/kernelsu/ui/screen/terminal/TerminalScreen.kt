package me.weishu.kernelsu.ui.screen.terminal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.PlaceholderPager

@Composable
fun TerminalPager(
    bottomInnerPadding: Dp,
    @Suppress("UNUSED_PARAMETER") isCurrentPage: Boolean = true,
) {
    PlaceholderPager(
        title = stringResource(R.string.terminal_title),
        description = stringResource(R.string.terminal_placeholder),
        icon = Icons.Rounded.Terminal,
        bottomInnerPadding = bottomInnerPadding,
    )
}
