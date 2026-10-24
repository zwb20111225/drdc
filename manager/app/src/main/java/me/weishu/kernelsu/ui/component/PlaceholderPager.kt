package me.weishu.kernelsu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * Placeholder body for the milestone screens (files / terminal / keys) until the real
 * features land. It keeps the five-tab shell navigable in the meantime.
 */
@Composable
fun PlaceholderPager(
    title: String,
    description: String,
    icon: ImageVector,
    bottomInnerPadding: Dp,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> PlaceholderPagerMiuix(title, description, icon, bottomInnerPadding)
        UiMode.Material -> PlaceholderPagerMaterial(title, description, icon, bottomInnerPadding)
    }
}

@Composable
private fun PlaceholderPagerMiuix(
    title: String,
    description: String,
    icon: ImageVector,
    bottomInnerPadding: Dp,
) {
    MiuixScaffold(
        topBar = { MiuixTopAppBar(title = title) },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = bottomInnerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(colorScheme.secondaryContainer, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                MiuixIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            MiuixText(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(top = 20.dp),
            )
            MiuixText(
                text = description,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 320.dp),
            )
        }
    }
}

@Composable
private fun PlaceholderPagerMaterial(
    title: String,
    description: String,
    icon: ImageVector,
    bottomInnerPadding: Dp,
) {
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { MaterialText(title) },
                colors = expressiveTopAppBarColors(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(bottom = bottomInnerPadding)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                MaterialIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            MaterialText(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 20.dp),
            )
            MaterialText(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 320.dp),
            )
        }
    }
}
