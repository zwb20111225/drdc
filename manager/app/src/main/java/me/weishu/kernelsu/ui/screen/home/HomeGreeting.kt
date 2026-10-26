package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.key.UserProfile
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalTime

@Composable
fun GreetingHeader(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val name = remember {
        UserProfile.username(context).ifBlank { context.getString(R.string.app_name) }
    }
    val hour = remember { LocalTime.now().hour }
    val greeting = when (hour) {
        in 5..11 -> R.string.home_greeting_morning
        in 12..17 -> R.string.home_greeting_afternoon
        in 18..22 -> R.string.home_greeting_evening
        else -> R.string.home_greeting_night
    }
    val color = if (LocalUiMode.current == UiMode.Material) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MiuixTheme.colorScheme.onBackground
    }
    val muted = if (LocalUiMode.current == UiMode.Material) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(greeting, name),
            color = color,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.home_greeting_subtitle),
            color = muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
