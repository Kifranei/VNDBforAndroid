package app.vndb.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.vndb.ui.nav.LocalBottomBarClearance
import app.vndb.ui.nav.tabContentWindowInsets
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vndb.AppContainer
import app.vndb.data.local.UserSettings
import app.vndb.data.model.Quote
import app.vndb.data.model.VisualNovel
import app.vndb.ui.components.EmptyState
import app.vndb.ui.components.ErrorState
import app.vndb.ui.components.LoadingBox
import app.vndb.ui.components.PosterCard
import app.vndb.ui.nav.BrowseMode
import app.vndb.ui.vmFactory
import app.vndb.util.displayTitle
import app.vndb.util.formatRating
import app.vndb.util.visibleUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class DiscoverUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val quote: Quote? = null,
    val topRated: List<VisualNovel> = emptyList(),
    val recent: List<VisualNovel> = emptyList(),
    val popular: List<VisualNovel> = emptyList(),
    val statsText: String? = null,
)

class DiscoverViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverUiState())
    val state = _state.asStateFlow()

    init {
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = initial, refreshing = !initial, error = null)
            runCatching {
                val quote = async { runCatching { container.repository.randomQuote() }.getOrNull() }
                val top = async { container.repository.topRated() }
                val recent = async { container.repository.recentlyReleased() }
                val popular = async { container.repository.mostVoted() }
                val stats = async { runCatching { container.repository.stats() }.getOrNull() }
                DiscoverUiState(
                    loading = false,
                    refreshing = false,
                    quote = quote.await(),
                    topRated = top.await().results,
                    recent = recent.await().results,
                    popular = popular.await().results,
                    statsText = stats.await()?.let { "收录 ${it.vn} 部作品 · ${it.chars} 位角色" },
                )
            }.onSuccess {
                _state.value = it
            }.onFailure {
                _state.value = _state.value.copy(loading = false, refreshing = false, error = it.message ?: "加载失败")
            }
        }
    }
}

@Composable
fun DiscoverScreen(
    container: AppContainer,
    settings: UserSettings,
    onOpenVn: (String) -> Unit,
    onBrowse: (BrowseMode) -> Unit,
) {
    val vm: DiscoverViewModel = viewModel(factory = vmFactory { DiscoverViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val scroll = MiuixScrollBehavior()
    val pull = rememberPullToRefreshState()

    val barClearance = LocalBottomBarClearance.current
    Scaffold(
        contentWindowInsets = tabContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = "发现",
                largeTitle = "发现",
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error != null && state.topRated.isEmpty() -> ErrorState(
                message = state.error ?: "加载失败",
                onRetry = { vm.refresh(true) },
                modifier = Modifier.padding(padding),
            )
            else -> {
                PullToRefresh(
                    isRefreshing = state.refreshing,
                    onRefresh = { vm.refresh(false) },
                    pullToRefreshState = pull,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scroll.nestedScrollConnection),
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + barClearance + 12.dp,
                        ),
                    ) {
                        state.statsText?.let {
                            item { SmallTitle(it) }
                        }
                        state.quote?.let { quote ->
                            item {
                                QuoteCard(quote, settings, onOpenVn)
                            }
                        }
                        item {
                            Rail(
                                title = "高分作品",
                                items = state.topRated,
                                settings = settings,
                                onOpenVn = onOpenVn,
                                onMore = { onBrowse(BrowseMode.TopRated) },
                            )
                        }
                        item {
                            Rail(
                                title = "最近发售",
                                items = state.recent,
                                settings = settings,
                                onOpenVn = onOpenVn,
                                onMore = { onBrowse(BrowseMode.Recent) },
                            )
                        }
                        item {
                            Rail(
                                title = "最多评分",
                                items = state.popular,
                                settings = settings,
                                onOpenVn = onOpenVn,
                                onMore = { onBrowse(BrowseMode.Popular) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteCard(quote: Quote, settings: UserSettings, onOpenVn: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        insideMargin = PaddingValues(16.dp),
        onClick = { quote.vn?.id?.let(onOpenVn) },
    ) {
        Text("今日摘录", style = MiuixTheme.textStyles.subtitle, color = MiuixTheme.colorScheme.primary)
        Text(
            text = "“${quote.quote.orEmpty()}”",
            style = MiuixTheme.textStyles.paragraph,
            modifier = Modifier.padding(top = 8.dp),
        )
        val who = listOfNotNull(quote.character?.name, quote.vn?.displayTitle(settings.titlePreference))
            .joinToString(" · ")
        if (who.isNotBlank()) {
            Text(
                who,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Rail(
    title: String,
    items: List<VisualNovel>,
    settings: UserSettings,
    onOpenVn: (String) -> Unit,
    onMore: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallTitle(title, modifier = Modifier.weight(1f))
            IconButton(onClick = onMore) {
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = "更多",
                    tint = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
        if (items.isEmpty()) {
            EmptyState("暂无数据")
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { vn ->
                    PosterCard(
                        title = vn.displayTitle(settings.titlePreference),
                        subtitle = vn.released,
                        imageUrl = vn.image.visibleUrl(settings.nsfwPolicy),
                        rating = formatRating(vn.rating),
                        onClick = { onOpenVn(vn.id) },
                    )
                }
            }
        }
    }
}
