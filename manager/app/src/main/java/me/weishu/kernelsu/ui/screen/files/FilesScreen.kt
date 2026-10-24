package me.weishu.kernelsu.ui.screen.files

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.PlaceholderPager

@Composable
fun FilesPager(
    bottomInnerPadding: Dp,
    @Suppress("UNUSED_PARAMETER") isCurrentPage: Boolean = true,
) {
    PlaceholderPager(
        title = stringResource(R.string.files_title),
        description = stringResource(R.string.files_placeholder),
        icon = Icons.Rounded.Folder,
        bottomInnerPadding = bottomInnerPadding,
    )
}
