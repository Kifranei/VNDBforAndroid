package app.vndb.ui.detail

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vndb.AppContainer
import app.vndb.data.local.UserSettings
import app.vndb.data.model.VisualNovel
import app.vndb.ui.components.EmptyState
import app.vndb.ui.components.ErrorState
import app.vndb.ui.components.LoadingBox
import app.vndb.ui.components.VnRowCard
import app.vndb.ui.nav.AppRoute
import app.vndb.ui.nav.BrowseMode
import app.vndb.ui.vmFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

data class BrowseState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val items: List<VisualNovel> = emptyList(),
    val page: Int = 1,
    val more: Boolean = false,
)

class BrowseViewModel(
    private val mode: BrowseMode,
    private val targetId: String,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = load(reset = true)

    fun loadMore() {
        val s = _state.value
        if (!s.more || s.loading || s.loadingMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        viewModelScope.launch {
            val next = if (reset) 1 else _state.value.page + 1
            _state.update {
                it.copy(
                    loading = reset,
                    loadingMore = !reset,
                    error = null,
                    page = next,
                    items = if (reset) emptyList() else it.items,
                )
            }
            runCatching { fetch(next) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            items = it.items + page.results,
                            more = page.more,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, loadingMore = false, error = e.message ?: "加载失败") }
                }
        }
    }

    private suspend fun fetch(page: Int) = when (mode) {
        BrowseMode.Developer -> container.repository.vnByDeveloper(targetId, page)
        BrowseMode.Staff -> container.repository.vnByStaff(targetId, page)
        BrowseMode.Tag -> container.repository.vnByTag(targetId, page)
        BrowseMode.TopRated -> container.repository.topRated(page, 20)
        BrowseMode.Recent -> container.repository.recentlyReleased(page, 20)
        BrowseMode.Popular -> container.repository.mostVoted(page, 20)
    }
}

@Composable
fun BrowseListScreen(
    title: String,
    mode: BrowseMode,
    targetId: String,
    container: AppContainer,
    settings: UserSettings,
    onBack: () -> Unit,
    onOpen: (AppRoute) -> Unit,
) {
    val vm: BrowseViewModel = viewModel(
        key = "browse-$mode-$targetId",
        factory = vmFactory { BrowseViewModel(mode, targetId, container) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val scroll = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (last != null && total > 4 && last >= total - 3) vm.loadMore()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                scrollBehavior = scroll,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error != null && state.items.isEmpty() ->
                ErrorState(state.error ?: "", onRetry = vm::refresh, Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState("暂无作品", Modifier.padding(padding))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scroll.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                items(state.items, key = { it.id }) { vn ->
                    VnRowCard(vn, settings, onClick = { onOpen(AppRoute.Vn(vn.id)) })
                }
                if (state.loadingMore) {
                    item { LoadingBox(Modifier.padding(16.dp)) }
                }
            }
        }
    }
}
