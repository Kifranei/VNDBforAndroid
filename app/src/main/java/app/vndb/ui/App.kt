package app.vndb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vndb.AppContainer
import app.vndb.ui.about.AboutScreen
import app.vndb.ui.components.LocalGlassBackdrop
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
import app.vndb.ui.nav.LocalTopBarCenterObstructionStart
import app.vndb.ui.nav.LocalTopBarTitleAlpha
import app.vndb.ui.nav.MainTab
import app.vndb.ui.nav.asMainTab
import app.vndb.ui.nav.shouldExpandNavigationRail
import app.vndb.ui.nav.shouldShowNavigationRail
import app.vndb.ui.nav.toRoute
import app.vndb.ui.search.SearchOverlay
import app.vndb.ui.settings.SettingsScreen
import kotlin.math.roundToInt
import app.vndb.ui.theme.VndbTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.glass.rememberGlassNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.NotesOutline
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VndbApp(container: AppContainer, initialRoute: AppRoute? = null) {
    val settings by container.settings.collectAsStateWithLifecycle()
    val initialRoutes = if (initialRoute != null && initialRoute.asMainTab() == null) {
        listOf(AppRoute.Discover, initialRoute)
    } else {
        listOf(initialRoute ?: AppRoute.Discover)
    }
    val backStack = rememberNavBackStack<AppRoute>(*initialRoutes.toTypedArray())
    var searchExpanded by remember { mutableStateOf(false) }

    fun current(): AppRoute = backStack.last() as AppRoute

    fun push(route: AppRoute) {
        if (route.asMainTab() != null) {
            if (current() != route || backStack.size > 1) {
                backStack.clear()
                backStack.add(route)
            }
        } else if (backStack.lastOrNull() !== route) {
            backStack.add(route)
        }
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    VndbTheme(settings.colorMode) {
        val route = current()
        val selectedTab = (backStack.firstOrNull() as? AppRoute)?.asMainTab() ?: MainTab.Discover
        val showBottomBar = route.asMainTab() != null
        val wide = shouldShowNavigationRail()
        val backdrop = rememberLayerBackdrop()
        val glassBackdrop = if (isRuntimeShaderSupported()) backdrop else null

        CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
            if (wide) {
                // Wide windows use the OS4 glass rail. It stays visible on detail pages too;
                // it steps aside for the full-screen About page and gallery.
                val showRail = route !is AppRoute.Gallery && route !is AppRoute.About
                val navBottom = with(LocalDensity.current) {
                    WindowInsets.navigationBars.getBottom(this).toDp()
                }
                val clearance = if (showBottomBar) navBottom else 0.dp
                val expandRail = shouldExpandNavigationRail()
                val railState = rememberGlassNavigationRailState(initialExpanded = expandRail)
                LaunchedEffect(expandRail) { railState.expanded = expandRail }
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        val centerObstructionStart = if (
                            showRail && !railState.expanded && railState.collapsedWidth > 0.dp
                        ) {
                            railState.collapsedStart
                        } else {
                            Dp.Unspecified
                        }
                        val topBarTitleAlpha = remember(showRail, railState) {
                            if (showRail) {
                                { railState.progress }
                            } else {
                                { 1f }
                            }
                        }
                        CompositionLocalProvider(
                            LocalBottomBarClearance provides clearance,
                            LocalTopBarCenterObstructionStart provides centerObstructionStart,
                            LocalTopBarTitleAlpha provides topBarTitleAlpha,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)
                                    .background(MiuixTheme.colorScheme.surface),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .then(if (showRail) Modifier.glassRailContentPadding { railState.progress } else Modifier),
                                ) {
                                    AppNavDisplay(
                                        container = container,
                                        settings = settings,
                                        backStack = backStack,
                                        push = ::push,
                                        pop = ::pop,
                                    )
                                }
                                // Wide search is part of the recorded page so the persistent rail samples
                                // the active search surface rather than the stale page underneath it.
                                HostedSearchOverlay(
                                    container = container,
                                    settings = settings,
                                    expanded = searchExpanded,
                                    onDismissRequest = { searchExpanded = false },
                                    onOpen = {
                                        searchExpanded = false
                                        push(it)
                                    },
                                    horizontalInsetsModifier = if (showRail) {
                                        Modifier.glassRailContentPadding { railState.progress }
                                    } else {
                                        Modifier
                                    },
                                    searchTopPadding = {
                                        if (showRail) {
                                            (GlassNavigationRailDefaults.CollapsedTop +
                                                GlassNavigationRailDefaults.CollapsedHeight + 4.dp) * (1f - railState.progress)
                                        } else {
                                            0.dp
                                        }
                                    },
                                    recordOwnBackdrop = false,
                                )
                            }
                        }
                        if (showRail) {
                            MainRail(
                                selected = selectedTab,
                                backdrop = glassBackdrop,
                                state = railState,
                                onSelect = {
                                    searchExpanded = false
                                    push(it.toRoute())
                                },
                                onSearch = { searchExpanded = true },
                            )
                        }
                    }
                }
            } else {
                val density = LocalDensity.current
                val navBottom = with(density) {
                    WindowInsets.navigationBars.getBottom(this).toDp()
                }
                var barHeight by remember(navBottom) {
                    mutableStateOf(GlassNavigationBarDefaults.Height + if (navBottom == 0.dp) 32.dp else 16.dp + navBottom)
                }
                val clearance = if (showBottomBar) barHeight + 12.dp else 0.dp
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalBottomBarClearance provides clearance) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)
                                    .background(MiuixTheme.colorScheme.surface),
                            ) {
                                AppNavDisplay(
                                    container = container,
                                    settings = settings,
                                    backStack = backStack,
                                    push = ::push,
                                    pop = ::pop,
                                )
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
                                if (tab != selectedTab || backStack.size > 1) push(tab.toRoute())
                            },
                            backdrop = glassBackdrop,
                            visible = showBottomBar,
                            trailingAction = {
                                GlassIconButton(
                                    onClick = { searchExpanded = true },
                                    backdrop = glassBackdrop,
                                    size = GlassNavigationBarDefaults.Height,
                                    shadow = GlassShadows.Float,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Glass.Search,
                                        contentDescription = "搜索",
                                        tint = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            },
                        )
                        HostedSearchOverlay(
                            container = container,
                            settings = settings,
                            expanded = searchExpanded,
                            onDismissRequest = { searchExpanded = false },
                            onOpen = {
                                searchExpanded = false
                                push(it)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * When the search overlay cannot be part of the page recording (compact layouts, where it must
 * cover the bottom bar), records it independently so glass sheets inside search sample the results
 * rather than the unrelated main page. Sheets are hosted by the root Scaffold outside this layer,
 * so this does not self-sample. Wide layouts already include search in the page recording and pass
 * [recordOwnBackdrop] as false.
 */
@Composable
private fun HostedSearchOverlay(
    container: AppContainer,
    settings: app.vndb.data.local.UserSettings,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onOpen: (AppRoute) -> Unit,
    horizontalInsetsModifier: Modifier = Modifier,
    searchTopPadding: () -> Dp = { 0.dp },
    recordOwnBackdrop: Boolean = true,
) {
    if (!recordOwnBackdrop) {
        SearchOverlay(
            container = container,
            settings = settings,
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            onOpen = onOpen,
            horizontalInsetsModifier = horizontalInsetsModifier,
            searchTopPadding = searchTopPadding,
        )
        return
    }
    val backdrop = rememberLayerBackdrop()
    val searchBackdrop = if (expanded && isRuntimeShaderSupported()) backdrop else null
    CompositionLocalProvider(LocalGlassBackdrop provides searchBackdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (searchBackdrop != null) Modifier.layerBackdrop(searchBackdrop) else Modifier),
        ) {
            SearchOverlay(
                container = container,
                settings = settings,
                expanded = expanded,
                onDismissRequest = onDismissRequest,
                onOpen = onOpen,
                horizontalInsetsModifier = horizontalInsetsModifier,
                searchTopPadding = searchTopPadding,
            )
        }
    }
}

@Composable
private fun AppNavDisplay(
    container: AppContainer,
    settings: app.vndb.data.local.UserSettings,
    backStack: NavBackStack,
    push: (AppRoute) -> Unit,
    pop: () -> Unit,
) {
    val navCornerRadius = rememberNavSystemCornerRadius()
    val backdropColor = MiuixTheme.colorScheme.surface
    val effects = remember(navCornerRadius, backdropColor) {
        NavDisplayEffects(
            cornerClipRadius = navCornerRadius,
            backdropColor = backdropColor,
        )
    }
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = pop,
        transition = NavTransitions.MiuixDefault,
        effects = effects,
    ) {
        entry<AppRoute.Discover> {
            DiscoverScreen(
                container = container,
                settings = settings,
                onOpenVn = { push(AppRoute.Vn(it)) },
                onBrowse = { mode -> push(AppRoute.BrowseVn(mode.title(), mode, "")) },
            )
        }
        entry<AppRoute.Library> {
            LibraryScreen(container, settings, onOpen = push)
        }
        entry<AppRoute.Settings> {
            SettingsScreen(container, settings, onOpenAbout = { push(AppRoute.About) })
        }
        entry<AppRoute.About> {
            AboutScreen(onBack = pop)
        }
        entry<AppRoute.Vn> { route ->
            VnDetailScreen(route.id, container, settings, onBack = pop, onOpen = push)
        }
        entry<AppRoute.Character> { route ->
            CharacterDetailScreen(route.id, container, settings, onBack = pop, onOpen = push)
        }
        entry<AppRoute.Producer> { route ->
            ProducerDetailScreen(route.id, container, settings, onBack = pop, onOpen = push)
        }
        entry<AppRoute.Staff> { route ->
            StaffDetailScreen(route.id, container, settings, onBack = pop, onOpen = push)
        }
        entry<AppRoute.Tag> { route ->
            TagDetailScreen(route.id, container, settings, onBack = pop, onOpen = push)
        }
        entry<AppRoute.BrowseVn> { route ->
            BrowseListScreen(
                title = route.title,
                mode = route.mode,
                targetId = route.targetId,
                container = container,
                settings = settings,
                onBack = pop,
                onOpen = push,
            )
        }
        entry<AppRoute.Gallery> { route ->
            GalleryScreen(route.urls, route.start, onBack = pop)
        }
    }
}

/** Adds the width of the expanded glass rail as layout-time start padding without recomposing every morph frame. */
private fun Modifier.glassRailContentPadding(progress: () -> Float): Modifier = layout { measurable, constraints ->
    val clearance = GlassNavigationRailDefaults.ExpandedWidth + GlassNavigationRailDefaults.Margin
    val start = (clearance.toPx() * progress()).roundToInt().coerceIn(0, constraints.maxWidth)
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth - start).coerceAtLeast(0),
            maxWidth = (constraints.maxWidth - start).coerceAtLeast(0),
        ),
    )
    layout((placeable.width + start).coerceAtMost(constraints.maxWidth), placeable.height) {
        placeable.placeRelative(start, 0)
    }
}

@Composable
private fun MainRail(
    selected: MainTab,
    backdrop: Backdrop?,
    state: top.yukonga.miuix.kmp.glass.GlassNavigationRailState,
    onSelect: (MainTab) -> Unit,
    onSearch: () -> Unit,
) {
    GlassNavigationRail(
        items = MainTab.entries.map { tab ->
            GlassNavigationItem(
                icon = tab.icon(),
                label = tab.label,
                contentDescription = tab.label,
            )
        },
        selectedIndex = selected.ordinal,
        onSelect = { index ->
            if (index < MainTab.entries.size) {
                onSelect(MainTab.entries[index])
            } else {
                onSearch()
            }
        },
        secondaryItems = listOf(
            GlassNavigationItem(
                icon = MiuixIcons.Glass.Search,
                label = "搜索",
                contentDescription = "搜索",
            ),
        ),
        backdrop = backdrop,
        state = state,
        expandContentDescription = "展开导航",
        collapseContentDescription = "收起导航",
    )
}

private fun MainTab.icon() = when (this) {
    MainTab.Discover -> MiuixIcons.Glass.HomeOutline
    MainTab.Library -> MiuixIcons.Glass.NotesOutline
    MainTab.Settings -> MiuixIcons.Glass.SettingsOutline
}

private fun BrowseMode.title(): String = when (this) {
    BrowseMode.TopRated -> "高分作品"
    BrowseMode.Recent -> "最近发售"
    BrowseMode.Popular -> "最多评分"
    BrowseMode.Developer -> "开发作品"
    BrowseMode.Staff -> "参与作品"
    BrowseMode.Tag -> "标签作品"
}
