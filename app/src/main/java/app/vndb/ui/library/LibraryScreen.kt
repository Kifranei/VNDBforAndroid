package app.vndb.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vndb.AppContainer
import app.vndb.data.local.UserSettings
import app.vndb.data.model.FavoriteItem
import app.vndb.data.model.UlistEntry
import app.vndb.data.model.UlistLabel
import app.vndb.ui.components.EmptyState
import app.vndb.ui.components.ErrorState
import app.vndb.ui.components.LoadingBox
import app.vndb.ui.components.VnRowCard
import app.vndb.ui.nav.AppRoute
import app.vndb.ui.nav.LocalBottomBarClearance
import app.vndb.ui.nav.tabContentWindowInsets
import app.vndb.ui.vmFactory
import app.vndb.util.ulistLabelName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class LibraryUiState(
    val local: List<FavoriteItem> = emptyList(),
    val history: List<FavoriteItem> = emptyList(),
    val labels: List<UlistLabel> = emptyList(),
    val remote: List<UlistEntry> = emptyList(),
    val selectedLabel: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.preferences.favorites.collect { list ->
                _state.update { it.copy(local = list) }
            }
        }
        viewModelScope.launch {
            container.preferences.history.collect { list ->
                _state.update { it.copy(history = list) }
            }
        }
        refreshRemote()
    }

    fun refreshRemote(label: Int? = _state.value.selectedLabel) {
        val user = container.settings.value.userId
        if (user.isBlank()) {
            _state.update { it.copy(remote = emptyList(), labels = emptyList(), loading = false, error = null, selectedLabel = label) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, selectedLabel = label) }
            runCatching {
                val labels = container.repository.userLabels(user)
                val page = container.repository.userList(user, label, 1)
                _state.update { it.copy(labels = labels, remote = page.results, loading = false) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "同步失败") }
            }
        }
    }

    fun removeHistory(item: FavoriteItem) {
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
    var tab by remember { mutableIntStateOf(2) }
    var confirmClear by remember { mutableStateOf(false) }
    val scroll = MiuixScrollBehavior()
    val tabs = listOf("本地收藏", "浏览记录", "VNDB 列表")
    val barClearance = LocalBottomBarClearance.current

    Scaffold(
        contentWindowInsets = tabContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = "收藏",
                largeTitle = "收藏",
                scrollBehavior = scroll,
                actions = {
                    if (tab == 1 && state.history.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = "清空浏览记录",
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            TabRow(
                tabs = tabs,
                selectedTabIndex = tab,
                onTabSelected = { tab = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            when (tab) {
                0 -> FavoriteList(state.local, onOpen, Modifier.weight(1f).nestedScroll(scroll.nestedScrollConnection), padding.calculateBottomPadding() + barClearance)
                1 -> FavoriteList(
                    items = state.history,
                    onOpen = onOpen,
                    modifier = Modifier.weight(1f).nestedScroll(scroll.nestedScrollConnection),
                    bottom = padding.calculateBottomPadding() + barClearance,
                    onRemove = vm::removeHistory,
                )
                else -> {
                    if (settings.userId.isBlank()) {
                        EmptyState("在设置里填入 API Token 后可同步 VNDB 列表", Modifier.weight(1f))
                    } else if (state.loading) {
                        LoadingBox(Modifier.weight(1f))
                    } else if (state.error != null) {
                        ErrorState(state.error ?: "", onRetry = { vm.refreshRemote() }, Modifier.weight(1f))
                    } else {
                        val labels = listOf("全部") + state.labels.map { ulistLabelName(it.id, it.label) }
                        val selected = if (state.selectedLabel == null) 0 else state.labels.indexOfFirst { it.id == state.selectedLabel } + 1
                        LazyColumn(
                            modifier = Modifier.weight(1f).nestedScroll(scroll.nestedScrollConnection),
                            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + barClearance + 8.dp),
                        ) {
                            item {
                                TabRow(
                                    tabs = labels.ifEmpty { listOf("全部") },
                                    selectedTabIndex = selected.coerceAtLeast(0),
                                    onTabSelected = { index ->
                                        vm.refreshRemote(if (index == 0) null else state.labels.getOrNull(index - 1)?.id)
                                    },
                                    minWidth = 88.dp,
                                    maxWidth = 160.dp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                            if (state.remote.isEmpty()) {
                                item { EmptyState("这个列表是空的") }
                            }
                            items(state.remote, key = { it.id }) { entry ->
                                val vn = entry.vn?.let { if (it.id.isBlank()) it.copy(id = entry.id) else it }
                                if (vn != null) {
                                    VnRowCard(vn, settings, onClick = { onOpen(AppRoute.Vn(vn.id.ifBlank { entry.id })) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    OverlayDialog(
        show = confirmClear,
        title = "清空浏览记录",
        summary = "将删除全部 ${state.history.size} 条记录，此操作无法撤销。",
        onDismissRequest = { confirmClear = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = "取消",
                onClick = { confirmClear = false },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = "清空",
                onClick = {
                    vm.clearHistory()
                    confirmClear = false
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun FavoriteList(
    items: List<FavoriteItem>,
    onOpen: (AppRoute) -> Unit,
    modifier: Modifier,
    bottom: androidx.compose.ui.unit.Dp,
    onRemove: ((FavoriteItem) -> Unit)? = null,
) {
    if (items.isEmpty()) {
        EmptyState("还没有内容", modifier)
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = bottom + 8.dp)) {
        items(items, key = { it.type + it.id }) { item ->
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)) {
                BasicComponent(
                    title = item.title,
                    summary = item.subtitle ?: item.id,
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
                    endActions = {
                        if (onRemove != null) {
                            IconButton(onClick = { onRemove(item) }) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = "删除这条记录",
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
