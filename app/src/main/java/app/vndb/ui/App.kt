package app.vndb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vndb.AppContainer
import app.vndb.ui.about.AboutScreen
import app.vndb.ui.detail.BrowseListScreen
import app.vndb.ui.detail.CharacterDetailScreen
import app.vndb.ui.detail.GalleryScreen
import app.vndb.ui.detail.ProducerDetailScreen
import app.vndb.ui.detail.StaffDetailScreen
import app.vndb.ui.detail.TagDetailScreen
import app.vndb.ui.detail.VnDetailScreen
import app.vndb.ui.discover.DiscoverScreen
import app.vndb.ui.library.LibraryScreen
import app.vndb.ui.nav.AppRoute
import app.vndb.ui.nav.BrowseMode
import app.vndb.ui.nav.FloatingBottomBar
import app.vndb.ui.nav.FloatingBottomBarItem
import app.vndb.ui.nav.FloatingBottomBarMode
import app.vndb.ui.nav.LocalBottomBarClearance
import app.vndb.ui.nav.LocalFloatingBottomBarContentColor
import app.vndb.ui.nav.MainTab
import app.vndb.ui.nav.asMainTab
import app.vndb.ui.nav.toRoute
import app.vndb.ui.search.SearchScreen
import app.vndb.ui.settings.SettingsScreen
import app.vndb.ui.theme.VndbTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VndbApp(container: AppContainer, initialRoute: AppRoute? = null) {
    val settings by container.settings.collectAsStateWithLifecycle()
    val stack = remember {
        mutableStateListOf(initialRoute ?: AppRoute.Discover)
    }
    var selectedTab by remember { mutableStateOf(MainTab.Discover) }

    fun current(): AppRoute = stack.last()

    fun push(route: AppRoute) {
        if (route is AppRoute.Discover || route is AppRoute.Search || route is AppRoute.Library || route is AppRoute.Settings) {
            selectedTab = route.asMainTab() ?: selectedTab
            stack.clear()
            stack.add(route)
        } else {
            stack.add(route)
        }
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    VndbTheme(settings.colorMode) {
        val route = current()
        val showBottomBar = route.asMainTab() != null
        val useLiquid = settings.liquidGlassBar && showBottomBar

        if (useLiquid) {
            val backdrop = rememberLayerBackdrop()
            val navBottom = with(LocalDensity.current) {
                WindowInsets.navigationBars.getBottom(this).toDp()
            }
            val clearance = 64.dp + 12.dp + navBottom
            // Keep overlays above both the backdrop layer and the floating bottom bar.
            Scaffold(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalBottomBarClearance provides clearance) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .layerBackdrop(backdrop),
                        ) {
                            AppScreens(container, settings, route, ::push, ::pop)
                        }
                    }
                    FloatingBottomBar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp)
                            .navigationBarsPadding()
                            .padding(bottom = 12.dp),
                        selectedIndex = { selectedTab.ordinal },
                        onSelected = { push(MainTab.entries[it].toRoute()) },
                        backdrop = backdrop,
                        tabsCount = 4,
                        mode = FloatingBottomBarMode.LiquidGlass,
                    ) {
                        MainTab.entries.forEach { tab ->
                            FloatingBottomBarItem(onClick = { push(tab.toRoute()) }) {
                                val tint = LocalFloatingBottomBarContentColor.current
                                Icon(
                                    imageVector = tab.icon(),
                                    contentDescription = tab.label,
                                    tint = tint,
                                    modifier = Modifier.size(26.dp),
                                )
                                Text(tab.label, color = tint, style = MiuixTheme.textStyles.footnote2)
                            }
                        }
                    }
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar {
                            MainTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { push(tab.toRoute()) },
                                    icon = tab.icon(),
                                    label = tab.label,
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                val clearance = if (showBottomBar) padding.calculateBottomPadding() else 0.dp
                CompositionLocalProvider(LocalBottomBarClearance provides clearance) {
                    Box(Modifier.fillMaxSize()) {
                        AppScreens(container, settings, route, ::push, ::pop)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppScreens(
    container: AppContainer,
    settings: app.vndb.data.local.UserSettings,
    route: AppRoute,
    push: (AppRoute) -> Unit,
    pop: () -> Unit,
) {
    when (val r = route) {
        AppRoute.Discover -> DiscoverScreen(
            container = container,
            settings = settings,
            onOpenVn = { push(AppRoute.Vn(it)) },
            onBrowse = { mode -> push(AppRoute.BrowseVn(mode.title(), mode, "")) },
        )
        AppRoute.Search -> SearchScreen(container, settings, onOpen = push)
        AppRoute.Library -> LibraryScreen(container, settings, onOpen = push)
        AppRoute.Settings -> SettingsScreen(container, settings, onOpenAbout = { push(AppRoute.About) })
        AppRoute.About -> AboutScreen(onBack = pop)
        is AppRoute.Vn -> VnDetailScreen(r.id, container, settings, onBack = pop, onOpen = push)
        is AppRoute.Character -> CharacterDetailScreen(r.id, container, settings, onBack = pop, onOpen = push)
        is AppRoute.Producer -> ProducerDetailScreen(r.id, container, settings, onBack = pop, onOpen = push)
        is AppRoute.Staff -> StaffDetailScreen(r.id, container, settings, onBack = pop, onOpen = push)
        is AppRoute.Tag -> TagDetailScreen(r.id, container, settings, onBack = pop, onOpen = push)
        is AppRoute.BrowseVn -> BrowseListScreen(
            title = r.title,
            mode = r.mode,
            targetId = r.targetId,
            container = container,
            settings = settings,
            onBack = pop,
            onOpen = push,
        )
        is AppRoute.Gallery -> GalleryScreen(r.urls, r.start, onBack = pop)
    }
}

private fun MainTab.icon() = when (this) {
    MainTab.Discover -> MiuixIcons.Home
    MainTab.Search -> MiuixIcons.Search
    MainTab.Library -> MiuixIcons.Favorites
    MainTab.Settings -> MiuixIcons.Settings
}

private fun BrowseMode.title(): String = when (this) {
    BrowseMode.TopRated -> "高分作品"
    BrowseMode.Recent -> "最近发售"
    BrowseMode.Popular -> "最多评分"
    BrowseMode.Developer -> "开发作品"
    BrowseMode.Staff -> "参与作品"
    BrowseMode.Tag -> "标签作品"
}
