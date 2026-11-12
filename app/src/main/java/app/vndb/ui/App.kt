package app.vndb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vndb.AppContainer
import app.vndb.ui.about.AboutScreen
import app.vndb.ui.components.LocalGlassBackdrop
import app.vndb.ui.components.LocalProgressiveTopBarBlur
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
import app.vndb.ui.nav.LocalBottomBarClearance
import app.vndb.ui.nav.MainTab
import app.vndb.ui.nav.asMainTab
import app.vndb.ui.nav.shouldShowNavigationRail
import app.vndb.ui.nav.toRoute
import app.vndb.ui.search.SearchScreen
import app.vndb.ui.settings.SettingsScreen
import app.vndb.ui.theme.VndbTheme
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
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
        val wide = shouldShowNavigationRail()

        if (wide) {
            // Wide windows swap the bottom bar for a side rail that stays visible on detail pages too;
            // it steps aside for the full-screen About page and gallery.
            val showRail = route !is AppRoute.Gallery && route !is AppRoute.About
            val navBottom = with(LocalDensity.current) {
                WindowInsets.navigationBars.getBottom(this).toDp()
            }
            // Tab screens exclude the bottom inset from their own padding, so hand it over as clearance.
            val clearance = if (showBottomBar) navBottom else 0.dp
            // The rail already pads the start cutout / side navigation bar; keep pages from padding it again.
            val railInsets = WindowInsets.displayCutout.union(WindowInsets.navigationBars)
                .only(WindowInsetsSides.Start)
            Scaffold(modifier = Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
                    if (showRail) {
                        MainRail(selectedTab) { push(it.toRoute()) }
                    }
                    CompositionLocalProvider(LocalBottomBarClearance provides clearance) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(if (showRail) Modifier.consumeWindowInsets(railInsets) else Modifier),
                        ) {
                            AppScreens(container, settings, route, ::push, ::pop)
                        }
                    }
                }
            }
        } else if (useLiquid) {
            // OS4 风格液态玻璃底栏（移植自 Halcyon 的 Miuix GlassNavigationBar）：页面录制进
            // miuix-blur 的 LayerBackdrop，底栏胶囊采样其模糊；RuntimeShader 不可用时退化为纯色胶囊。
            val backdrop = rememberLayerBackdrop()
            val glassBackdrop = if (isRuntimeShaderSupported()) backdrop else null
            val density = LocalDensity.current
            val navBottom = with(density) {
                WindowInsets.navigationBars.getBottom(this).toDp()
            }
            var barHeight by remember(navBottom) {
                mutableStateOf(GlassNavigationBarDefaults.Height + if (navBottom == 0.dp) 32.dp else 16.dp + navBottom)
            }
            val clearance = barHeight + 12.dp
            // Keep overlays above both the backdrop layer and the floating bottom bar.
            Scaffold(modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalBottomBarClearance provides clearance,
                        LocalGlassBackdrop provides glassBackdrop,
                    ) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier),
                        ) {
                            AppScreens(container, settings, route, ::push, ::pop)
                        }
                    }
                    GlassNavigationBar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { barHeight = with(density) { it.height.toDp() } }
                            .padding(horizontal = 16.dp),
                        items = MainTab.entries.map { tab ->
                            GlassNavigationItem(
                                icon = tab.icon(),
                                label = tab.label,
                                contentDescription = tab.label,
                            )
                        },
                        selectedIndex = selectedTab.ordinal,
                        onSelect = { index ->
                            val tab = MainTab.entries[index]
                            if (tab != selectedTab || stack.size > 1) push(tab.toRoute())
                        },
                        backdrop = glassBackdrop,
                    )
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
    CompositionLocalProvider(LocalProgressiveTopBarBlur provides settings.progressiveTopBarBlur) {
        AppScreen(container, settings, route, push, pop)
    }
}

@Composable
private fun AppScreen(
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

@Composable
private fun MainRail(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val state = rememberNavigationRailState(NavigationRailValue.Expanded)
    NavigationRail(
        state = state,
        defaultWindowInsetsPadding = true,
        color = MiuixTheme.colorScheme.surface,
        expandContentDescription = "展开导航",
        collapseContentDescription = "收起导航",
    ) {
        MainTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = tab.icon(),
                label = tab.label,
            )
        }
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
