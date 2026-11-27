package app.vndb.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
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
import app.vndb.ui.components.LocalGlassBackdrop
import app.vndb.ui.components.Os4DropdownPreference
import app.vndb.ui.components.progressiveBlurSource
import app.vndb.ui.components.rememberProgressiveBlurBackdrop
import app.vndb.ui.nav.LocalBottomBarClearance
import app.vndb.ui.nav.sides
import app.vndb.ui.nav.tabContentWindowInsets
import app.vndb.ui.nav.LocalTopBarCenterObstructionStart
import app.vndb.ui.nav.LocalTopBarTitleAlpha
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val tokenState = remember(showToken) { TextFieldState(settings.apiToken) }
    var tokenMessage by remember { mutableStateOf<String?>(null) }
    val barClearance = LocalBottomBarClearance.current

    val blurBackdrop = rememberProgressiveBlurBackdrop()
    Scaffold(
        contentWindowInsets = tabContentWindowInsets(),
        topBar = {
            GlassTopAppBar(
                title = "设置",
                largeTitle = "设置",
                scrollBehavior = scroll,
                progressiveBlur = true,
                backdrop = blurBackdrop,
                titleAlpha = LocalTopBarTitleAlpha.current,
                centerObstructionStart = LocalTopBarCenterObstructionStart.current,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .progressiveBlurSource(blurBackdrop)
                .nestedScroll(scroll.nestedScrollConnection)
                .padding(padding.sides()),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + barClearance + 16.dp,
            ),
        ) {
            item { SmallTitle("账号") }
            item {
                GlassCard(Modifier.padding(horizontal = 12.dp)) {
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
            item { SmallTitle("外观") }
            item {
                GlassCard(Modifier.padding(horizontal = 12.dp)) {
                    Os4DropdownPreference(
                        items = ColorMode.entries.map { it.label },
                        selectedIndex = settings.colorMode.ordinal,
                        title = "颜色模式",
                        onSelectedIndexChange = {
                            scope.launch { container.preferences.setColorMode(ColorMode.entries[it]) }
                        },
                    )
                    Os4DropdownPreference(
                        items = TitlePreference.entries.map { it.label },
                        selectedIndex = settings.titlePreference.ordinal,
                        title = "标题语言",
                        summary = "优先显示 titles 列表中对应语言的标题",
                        onSelectedIndexChange = {
                            scope.launch { container.preferences.setTitlePreference(TitlePreference.entries[it]) }
                        },
                    )
                    Os4DropdownPreference(
                        items = NsfwPolicy.entries.map { it.label },
                        selectedIndex = settings.nsfwPolicy.ordinal,
                        title = "敏感封面",
                        summary = "根据 image.sexual 标记过滤",
                        onSelectedIndexChange = {
                            scope.launch { container.preferences.setNsfwPolicy(NsfwPolicy.entries[it]) }
                        },
                    )
                    Os4DropdownPreference(
                        items = listOf("无剧透", "轻微剧透", "全部剧透"),
                        selectedIndex = settings.spoilerLevel,
                        title = "剧透等级",
                        onSelectedIndexChange = {
                            scope.launch { container.preferences.setSpoilerLevel(it) }
                        },
                    )
                }
            }
            item { SmallTitle("关于") }
            item {
                GlassCard(Modifier.padding(horizontal = 12.dp)) {
                    ArrowPreference(
                        title = "关于 VNDB",
                        summary = "v${app.vndb.BuildConfig.VERSION_NAME}",
                        onClick = onOpenAbout,
                    )
                }
            }
        }
    }

    GlassDialog(
        show = showToken,
        backdrop = LocalGlassBackdrop.current,
        title = "API Token",
        summary = tokenMessage ?: "格式类似 xxxx-xxxxx-xxxxx-xxxx-xxxxx-xxxxx-xxxx",
        onDismissRequest = { showToken = false },
    ) {
        val dismiss = LocalDismissState.current
        Column {
            GlassTextField(
                state = tokenState,
                label = "Token",
                modifier = Modifier.padding(bottom = 12.dp),
            )
            GlassDialogButton(
                onClick = {
                    scope.launch {
                        val token = tokenState.text.toString()
                        container.preferences.setApiToken(token)
                        if (token.isBlank()) {
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
                role = GlassDialogButtonRole.Primary,
            ) {
                Text("验证并保存", style = MiuixTheme.textStyles.button)
            }
        }
    }
}
