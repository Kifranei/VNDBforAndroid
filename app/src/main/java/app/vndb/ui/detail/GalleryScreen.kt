package app.vndb.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import app.vndb.ui.nav.LocalTopBarCenterObstructionStart
import app.vndb.ui.nav.LocalTopBarTitleAlpha
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GalleryScreen(
    urls: List<String>,
    start: Int,
    onBack: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) { urls.size }
    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = if (urls.isEmpty()) "截图" else "${pager.currentPage + 1} / ${urls.size}",
                expandable = false,
                titleAlpha = LocalTopBarTitleAlpha.current,
                centerObstructionStart = LocalTopBarCenterObstructionStart.current,
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(MiuixIcons.Glass.ChevronBackward, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (urls.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("没有可显示的图片", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) { page ->
                AsyncImage(
                    model = urls[page],
                    contentDescription = "截图 ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            }
        }
    }
}
