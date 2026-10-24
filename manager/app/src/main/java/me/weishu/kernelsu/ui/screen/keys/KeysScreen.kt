package me.weishu.kernelsu.ui.screen.keys

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.PlaceholderPager

@Composable
fun KeysPager(
    bottomInnerPadding: Dp,
    @Suppress("UNUSED_PARAMETER") isCurrentPage: Boolean = true,
) {
    PlaceholderPager(
        title = stringResource(R.string.keys_title),
        description = stringResource(R.string.keys_placeholder),
        icon = Icons.Rounded.Key,
        bottomInnerPadding = bottomInnerPadding,
    )
}
