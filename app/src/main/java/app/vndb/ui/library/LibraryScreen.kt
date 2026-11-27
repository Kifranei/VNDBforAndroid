package app.vndb.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vndb.AppContainer
import app.vndb.data.local.UserSettings
import app.vndb.data.model.HistoryItem
import app.vndb.data.model.UlistEntry
import app.vndb.data.model.UlistLabel
import app.vndb.ui.components.CoverImage
import app.vndb.ui.components.EmptyState
import app.vndb.ui.components.ErrorState
import app.vndb.ui.components.LoadingBox
import app.vndb.ui.components.LocalGlassBackdrop
import app.vndb.ui.components.ScrollableGlassTabRow
import app.vndb.ui.components.VnRowCard
import app.vndb.ui.components.progressiveBlurSource
import app.vndb.ui.components.rememberProgressiveBlurBackdrop
import app.vndb.ui.nav.AppRoute
import app.vndb.ui.nav.LocalBottomBarClearance
import app.vndb.ui.nav.LocalTopBarCenterObstructionStart
import app.vndb.ui.nav.LocalTopBarTitleAlpha
import app.vndb.ui.nav.sides
import app.vndb.ui.nav.tabContentWindowInsets
import app.vndb.ui.vmFactory
import app.vndb.util.ulistLabelName
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

data class LibraryUiState(
    val history: List<HistoryItem> = emptyList(),
    val labels: List<UlistLabel> = emptyList(),
    val remote: List<UlistEntry> = emptyList(),
    val selectedLabel: Int? = null,
    val page: Int = 1,
    val more: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            container.preferences.history.collect { list ->
                _state.update { it.copy(history = list) }
            }
        }
        refreshRemote()
    }

    fun refreshRemote(label: Int? = _state.value.selectedLabel) {
        loadRemote(label = label, reset = true)
    }

    fun loadMore() {
        val s = _state.value
        if (!s.more || s.loading || s.loadingMore) return
        loadRemote(label = s.selectedLabel, reset = false)
    }

    private fun loadRemote(label: Int?, reset: Boolean) {
        val user = container.settings.value.userId
        if (user.isBlank()) {
            loadJob?.cancel()
            _state.update {
                it.copy(
                    remote = emptyList(),
                    labels = emptyList(),
                    loading = false,
                    loadingMore = false,
                    more = false,
                    page = 1,
                    error = null,
                    selectedLabel = label,
                )
            }
            return
        }
        if (reset) loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val next = if (reset) 1 else _state.value.page + 1
            _state.update {
                it.copy(
                    loading = reset,
                    loadingMore = !reset,
                    error = null,
                    selectedLabel = label,
                    page = next,
                    remote = if (reset) emptyList() else it.remote,
                )
            }
            try {
                val labels = if (reset) container.repository.userLabels(user) else _state.value.labels
                val page = container.repository.userList(user, label, next)
                _state.update {
                    it.copy(
                        labels = labels,
                        remote = it.remote + page.results,
                        more = page.more,
                        loading = false,
                        loadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadingMore = false, error = e.message ?: "同步失败") }
            }
        }
    }

    fun removeHistory(item: HistoryItem) {
        viewModelScope.launch { container.preferences.removeHistory(item) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.preferences.clearHistory() }
    }
}

@Composable
fun LibraryScreen(
    container: AppContainer,
    settings: UserSettings,
    onOpen: (AppRoute) -> Unit,
) {
    val vm: LibraryViewModel = viewModel(factory = vmFactory { LibraryViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val scroll = MiuixScrollBehavior()
    val tabs = listOf("VNDB 列表", "浏览记录")
    val barClearance = LocalBottomBarClearance.current
    val blurBackdrop = rememberProgressiveBlurBackdrop()
    val remoteListState = rememberLazyListState()
    val historyListState = rememberLazyListState()
    val activeListState = if (tab == 0) remoteListState else historyListState
    val signedIn = settings.userId.isNotBlank()
    val showSecondaryTabs = tab == 0 && signedIn
    val labels = listOf("全部") + state.labels.map { ulistLabelName(it.id, it.label) }
    val selectedLabelIndex = if (state.selectedLabel == null) {
        0
    } else {
        state.labels.indexOfFirst { it.id == state.selectedLabel } + 1
    }

    LaunchedEffect(remoteListState) {
        snapshotFlow { remoteListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = remoteListState.layoutInfo.totalItemsCount
                if (last != null && total > 4 && last >= total - 3) vm.loadMore()
            }
    }

    Scaffold(
        contentWindowInsets = tabContentWindowInsets(),
        topBar = {
            GlassTopAppBar(
                title = "资料库",
                largeTitle = "资料库",
                scrollBehavior = scroll,
                progressiveBlur = true,
                backdrop = blurBackdrop,
                titleAlpha = LocalTopBarTitleAlpha.current,
                centerObstructionStart = LocalTopBarCenterObstructionStart.current,
                actions = {
                    if (tab == 1 && state.history.isNotEmpty()) {
                        GlassIconButton(onClick = { confirmClear = true }) {
                            Icon(
                                imageVector = MiuixIcons.Glass.Delete,
                                contentDescription = "清空浏览记录",
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
                bottomContent = {
                    Column(Modifier.fillMaxWidth()) {
                        GlassTabRow(
                            tabs = tabs,
                            selectedIndex = tab,
                            onSelect = { tab = it },
                            backdrop = blurBackdrop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        if (showSecondaryTabs) {
                            ScrollableGlassTabRow(
                                tabs = labels,
                                selectedIndex = selectedLabelIndex.coerceAtLeast(0),
                                onSelect = { index ->
                                    vm.refreshRemote(if (index == 0) null else state.labels.getOrNull(index - 1)?.id)
                                },
                                backdrop = blurBackdrop,
                                verticalPadding = 4.dp,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding.sides()),
        ) {
            LazyColumn(
                state = activeListState,
                modifier = Modifier
                    .fillMaxSize()
                    .progressiveBlurSource(blurBackdrop)
                    .nestedScroll(scroll.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + barClearance + 8.dp,
                ),
            ) {
                if (tab == 1) {
                    if (state.history.isEmpty()) {
                        item { EmptyState("还没有内容", Modifier.fillParentMaxSize()) }
                    } else {
                        items(state.history, key = { it.type + it.id }) { item ->
                            HistoryRowCard(
                                item = item,
                                onOpen = onOpen,
                                onRemove = vm::removeHistory,
                            )
                        }
                    }
                } else {
                    when {
                        !signedIn -> {
                            item { EmptyState("在设置里填入 API Token 后可同步 VNDB 列表", Modifier.fillParentMaxSize()) }
                        }
                        state.loading -> {
                            item { LoadingBox(Modifier.fillParentMaxSize()) }
                        }
                        state.error != null && state.remote.isEmpty() -> {
                            item {
                                ErrorState(
                                    message = state.error ?: "",
                                    onRetry = { vm.refreshRemote() },
                                    modifier = Modifier.fillParentMaxSize(),
                                )
                            }
                        }
                        else -> {
                            if (state.remote.isEmpty()) {
                                item { EmptyState("这个列表是空的", Modifier.fillParentMaxSize()) }
                            }
                            items(state.remote, key = { it.id }) { entry ->
                                val vn = entry.vn?.let { if (it.id.isBlank()) it.copy(id = entry.id) else it }
                                if (vn != null) {
                                    VnRowCard(vn, settings, onClick = { onOpen(AppRoute.Vn(vn.id.ifBlank { entry.id })) })
                                }
                            }
                            if (state.loadingMore) {
                                item { LoadingBox(Modifier.padding(16.dp)) }
                            }
                        }
                    }
                }
            }

        }
    }

    GlassDialog(
        show = confirmClear,
        backdrop = LocalGlassBackdrop.current,
        title = "清空浏览记录",
        summary = "将删除全部 ${state.history.size} 条记录，此操作无法撤销。",
        onDismissRequest = { confirmClear = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GlassDialogButton(
                onClick = { confirmClear = false },
                modifier = Modifier.weight(1f),
            ) {
                Text("取消", style = MiuixTheme.textStyles.button)
            }
            Spacer(Modifier.width(20.dp))
            GlassDialogButton(
                onClick = {
                    vm.clearHistory()
                    confirmClear = false
                },
                modifier = Modifier.weight(1f),
                role = GlassDialogButtonRole.Destructive,
            ) {
                Text("清空", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

@Composable
private fun HistoryRowCard(
    item: HistoryItem,
    onOpen: (AppRoute) -> Unit,
    onRemove: (HistoryItem) -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        insideMargin = PaddingValues(10.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = {
            onOpen(
                when (item.type) {
                    "character" -> AppRoute.Character(item.id)
                    "producer" -> AppRoute.Producer(item.id)
                    "staff" -> AppRoute.Staff(item.id)
                    "tag" -> AppRoute.Tag(item.id)
                    else -> AppRoute.Vn(item.id)
                },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.type == "vn" || item.type == "character") {
                CoverImage(
                    url = item.imageUrl,
                    modifier = Modifier.width(72.dp),
                    contentDescription = item.title,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = item.title,
                    style = MiuixTheme.textStyles.headline1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle ?: item.id,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = historyTypeName(item.type),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            GlassIconButton(onClick = { onRemove(item) }, size = 36.dp) {
                Icon(
                    imageVector = MiuixIcons.Glass.Delete,
                    contentDescription = "删除这条记录",
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}

private fun historyTypeName(type: String): String = when (type) {
    "character" -> "角色"
    "producer" -> "制作"
    "staff" -> "职员"
    "tag" -> "标签"
    else -> "作品"
}
