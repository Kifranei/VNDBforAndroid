package app.vndb.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.vndb.AppContainer
import app.vndb.data.local.UserSettings
import app.vndb.data.model.ColorMode
import app.vndb.data.model.NsfwPolicy
import app.vndb.data.model.TitlePreference
import app.vndb.ui.nav.LocalBottomBarClearance
import app.vndb.ui.nav.tabContentWindowInsets
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun SettingsScreen(
    container: AppContainer,
    settings: UserSettings,
    onOpenAbout: () -> Unit,
) {
    val scroll = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var showToken by remember { mutableStateOf(false) }
    var tokenDraft by remember(showToken) { mutableStateOf(settings.apiToken) }
    var tokenMessage by remember { mutableStateOf<String?>(null) }
    val barClearance = LocalBottomBarClearance.current

    Scaffold(
        contentWindowInsets = tabContentWindowInsets(),
        topBar = { TopAppBar(title = "设置", largeTitle = "设置", scrollBehavior = scroll) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .nestedScroll(scroll.nestedScrollConnection)
                .padding(top = padding.calculateTopPadding()),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = padding.calculateBottomPadding() + barClearance + 16.dp,
            ),
        ) {
            item { SmallTitle("外观") }
            item {
                Card(Modifier.padding(horizontal = 12.dp)) {
                    WindowDropdownPreference(
                        items = ColorMode.entries.map { it.label },
                        selectedIndex = settings.colorMode.ordinal,
                        title = "颜色模式",
                        onSelectedIndexChange = {
                            scope.launch { container.preferences.setColorMode(ColorMode.entries[it]) }
                        },
                    )
                    WindowDropdownPreference(
                        items = TitlePreference.entries.map { it.label },
                        selectedIndex = settings.titlePreference.ordinal,
                        title = "标题语言",
                        summary = "优先显示 titles 列表中对应语言的标题",
                        onSelectedIndexChange = {
                            scope.launch { container.preferences.setTitlePreference(TitlePreference.entries[it]) }
                        },
                    )
                    WindowDropdownPreference(
                        items = NsfwPolicy.entries.map { it.label },
                        selectedIndex = settings.nsfwPolicy.ordinal,
                        title = "敏感封面",
                        summary = "根据 image.sexual 标记过滤",
                        onSelectedIndexChange = {
                            scope.launch { container.preferences.setNsfwPolicy(NsfwPolicy.entries[it]) }
                        },
                    )
                    WindowDropdownPreference(
                        items = listOf("无剧透", "轻微剧透", "全部剧透"),
                        selectedIndex = settings.spoilerLevel,
                        title = "剧透等级",
                        onSelectedIndexChange = {
                            scope.launch { container.preferences.setSpoilerLevel(it) }
                        },
                    )
                    SwitchPreference(
                        checked = settings.liquidGlassBar,
                        onCheckedChange = {
                            scope.launch { container.preferences.setLiquidGlassBar(it) }
                        },
                        title = "液态玻璃底栏",
                        summary = "关闭时使用普通 Miuix 底栏，开启后为悬浮液态玻璃",
                    )
                }
            }
            item { SmallTitle("账号") }
            item {
                Card(Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = if (settings.username.isBlank()) "VNDB API Token" else settings.username,
                        summary = if (settings.userId.isBlank()) "用于读取/写入个人列表，在 vndb.org/u/tokens 创建" else settings.userId,
                        onClick = { showToken = true },
                    )
                    ArrowPreference(
                        title = "申请 Token",
                        summary = "打开 VNDB Applications",
                        onClick = { runCatching { uri.openUri("https://vndb.org/u/tokens") } },
                    )
                }
            }
            item { SmallTitle("关于") }
            item {
                Card(Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "关于 VNDB",
                        summary = "v${app.vndb.BuildConfig.VERSION_NAME}",
                        onClick = onOpenAbout,
                    )
                }
            }
        }
    }

    WindowDialog(
        show = showToken,
        title = "API Token",
        summary = tokenMessage ?: "格式类似 xxxx-xxxxx-xxxxx-xxxx-xxxxx-xxxxx-xxxx",
        onDismissRequest = { showToken = false },
    ) {
        val dismiss = LocalDismissState.current
        Column {
            TextField(
                value = tokenDraft,
                onValueChange = { tokenDraft = it },
                label = "Token",
                modifier = Modifier.padding(bottom = 12.dp),
            )
            TextButton(
                text = "验证并保存",
                onClick = {
                    scope.launch {
                        container.preferences.setApiToken(tokenDraft)
                        if (tokenDraft.isBlank()) {
                            container.preferences.clearAuth()
                            tokenMessage = "已清除"
                            showToken = false
                            dismiss?.invoke()
                        } else {
                            runCatching { container.repository.authInfo() }
                                .onSuccess {
                                    container.preferences.setAuthUser(it.id, it.username.orEmpty())
                                    tokenMessage = "已绑定 ${it.username}"
                                    showToken = false
                                    dismiss?.invoke()
                                }
                                .onFailure {
                                    tokenMessage = "验证失败：${it.message}"
                                }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
