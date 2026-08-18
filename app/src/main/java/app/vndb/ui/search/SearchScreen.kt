package app.vndb.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vndb.AppContainer
import app.vndb.data.local.UserSettings
import app.vndb.data.model.Character
import app.vndb.data.model.Producer
import app.vndb.data.model.SearchFilters
import app.vndb.data.model.SearchKind
import app.vndb.data.model.Staff
import app.vndb.data.model.Tag
import app.vndb.data.model.VisualNovel
import app.vndb.ui.components.CharacterRowCard
import app.vndb.ui.components.EmptyState
import app.vndb.ui.components.ErrorState
import app.vndb.ui.components.LoadingBox
import app.vndb.ui.components.VnRowCard
import app.vndb.ui.nav.AppRoute
import app.vndb.ui.nav.LocalBottomBarClearance
import app.vndb.ui.nav.tabContentWindowInsets
import app.vndb.ui.vmFactory
import app.vndb.util.languageName
import app.vndb.util.platformName
import app.vndb.util.producerTypeName
import app.vndb.util.tagCategoryName
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

data class SearchUiState(
    val query: String = "",
    val kind: SearchKind = SearchKind.VN,
    val filters: SearchFilters = SearchFilters(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val more: Boolean = false,
    val vns: List<VisualNovel> = emptyList(),
    val characters: List<Character> = emptyList(),
    val producers: List<Producer> = emptyList(),
    val staff: List<Staff> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        scheduleSearch()
    }

    fun onKind(kind: SearchKind) {
        _state.update { it.copy(kind = kind, page = 1, more = false) }
        search(reset = true)
    }

    fun onFilters(filters: SearchFilters) {
        _state.update { it.copy(filters = filters) }
        search(reset = true)
    }

    fun submit() {
        searchJob?.cancel()
        search(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (!current.more || current.loading || current.loadingMore) return
        search(reset = false)
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(380)
            search(reset = true)
        }
    }

    private fun search(reset: Boolean) {
        viewModelScope.launch {
            val snapshot = _state.value
            val nextPage = if (reset) 1 else snapshot.page + 1
            _state.update {
                it.copy(
                    loading = reset,
                    loadingMore = !reset,
                    error = null,
                    page = nextPage,
                    vns = if (reset) emptyList() else it.vns,
                    characters = if (reset) emptyList() else it.characters,
                    producers = if (reset) emptyList() else it.producers,
                    staff = if (reset) emptyList() else it.staff,
                    tags = if (reset) emptyList() else it.tags,
                )
            }
            runCatching {
                when (snapshot.kind) {
                    SearchKind.VN -> {
                        val page = container.repository.searchVn(snapshot.query.trim(), nextPage, snapshot.filters)
                        _state.update { it.copy(vns = it.vns + page.results, more = page.more, loading = false, loadingMore = false) }
                    }
                    SearchKind.CHARACTER -> {
                        val page = container.repository.searchCharacters(snapshot.query.trim(), nextPage)
                        _state.update { it.copy(characters = it.characters + page.results, more = page.more, loading = false, loadingMore = false) }
                    }
                    SearchKind.PRODUCER -> {
                        val page = container.repository.searchProducers(snapshot.query.trim(), nextPage)
                        _state.update { it.copy(producers = it.producers + page.results, more = page.more, loading = false, loadingMore = false) }
                    }
                    SearchKind.STAFF -> {
                        val page = container.repository.searchStaff(snapshot.query.trim(), nextPage)
                        _state.update { it.copy(staff = it.staff + page.results, more = page.more, loading = false, loadingMore = false) }
                    }
                    SearchKind.TAG -> {
                        val page = container.repository.searchTags(snapshot.query.trim(), nextPage)
                        _state.update { it.copy(tags = it.tags + page.results, more = page.more, loading = false, loadingMore = false) }
                    }
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, loadingMore = false, error = e.message ?: "搜索失败") }
            }
        }
    }
}

@Composable
fun SearchScreen(
    container: AppContainer,
    settings: UserSettings,
    onOpen: (AppRoute) -> Unit,
) {
    val vm: SearchViewModel = viewModel(factory = vmFactory { SearchViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (last != null && total > 4 && last >= total - 3) vm.loadMore()
            }
    }

    val barClearance = LocalBottomBarClearance.current
    Scaffold(contentWindowInsets = tabContentWindowInsets()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = barClearance),
        ) {
            SearchBar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                inputField = {
                    InputField(
                        query = state.query,
                        onQueryChange = vm::onQueryChange,
                        onSearch = {
                            expanded = false
                            vm.submit()
                        },
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        label = "搜索 VNDB",
                    )
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                outsideEndAction = {
                    Text(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clickable {
                                expanded = false
                                vm.onQueryChange("")
                            },
                        text = "取消",
                        color = MiuixTheme.colorScheme.primary,
                    )
                },
            ) {}

            TabRow(
                tabs = SearchKind.entries.map { it.label },
                selectedTabIndex = state.kind.ordinal,
                onTabSelected = { vm.onKind(SearchKind.entries[it]) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )

            if (state.kind == SearchKind.VN) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("高级筛选对应 POST /vn filters", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(
                        "筛选",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showFilters = true },
                    )
                }
            }

            when {
                state.loading -> LoadingBox(Modifier.weight(1f))
                state.error != null && isEmpty(state) -> ErrorState(state.error ?: "", onRetry = vm::submit, modifier = Modifier.weight(1f))
                isEmpty(state) -> EmptyState(if (state.query.isBlank()) "试试搜索作品、角色或制作组" else "没有匹配结果", Modifier.weight(1f))
                else -> {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        when (state.kind) {
                            SearchKind.VN -> items(state.vns, key = { it.id }) { vn ->
                                VnRowCard(vn, settings, onClick = { onOpen(AppRoute.Vn(vn.id)) })
                            }
                            SearchKind.CHARACTER -> items(state.characters, key = { it.id }) { ch ->
                                CharacterRowCard(ch, settings, onClick = { onOpen(AppRoute.Character(ch.id)) })
                            }
                            SearchKind.PRODUCER -> items(state.producers, key = { it.id }) { p ->
                                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), onClick = { onOpen(AppRoute.Producer(p.id)) }) {
                                    BasicComponent(
                                        title = p.name.orEmpty(),
                                        summary = listOfNotNull(p.original?.takeIf { it != p.name }, producerTypeName(p.type)).joinToString(" · "),
                                    )
                                }
                            }
                            SearchKind.STAFF -> items(state.staff, key = { it.id + (it.aid ?: 0) }) { s ->
                                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), onClick = { onOpen(AppRoute.Staff(s.id)) }) {
                                    BasicComponent(
                                        title = s.name.orEmpty(),
                                        summary = listOfNotNull(s.original?.takeIf { it != s.name }, s.lang).joinToString(" · "),
                                    )
                                }
                            }
                            SearchKind.TAG -> items(state.tags, key = { it.id }) { tag ->
                                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), onClick = { onOpen(AppRoute.Tag(tag.id)) }) {
                                    BasicComponent(
                                        title = tag.name.orEmpty(),
                                        summary = listOfNotNull(tagCategoryName(tag.category), tag.vnCount?.let { "$it 部作品" }).joinToString(" · "),
                                    )
                                }
                            }
                        }
                        if (state.loadingMore) {
                            item { LoadingBox(Modifier.fillMaxWidth().padding(16.dp)) }
                        }
                    }
                }
            }
        }

        FilterSheet(
            show = showFilters,
            filters = state.filters,
            onDismiss = { showFilters = false },
            onApply = {
                vm.onFilters(it)
                showFilters = false
            },
        )
    }
}

private fun isEmpty(state: SearchUiState): Boolean = when (state.kind) {
    SearchKind.VN -> state.vns.isEmpty()
    SearchKind.CHARACTER -> state.characters.isEmpty()
    SearchKind.PRODUCER -> state.producers.isEmpty()
    SearchKind.STAFF -> state.staff.isEmpty()
    SearchKind.TAG -> state.tags.isEmpty()
}

@Composable
private fun FilterSheet(
    show: Boolean,
    filters: SearchFilters,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    var draft by remember(show) { mutableStateOf(filters) }
    val langs = listOf("不限", "ja", "en", "zh-Hans", "zh-Hant", "ko", "ru")
    val platforms = listOf("不限", "win", "and", "swi", "psv", "ios", "web")
    val sorts = listOf("searchrank", "rating", "votecount", "released", "title")
    val sortLabels = listOf("相关度", "评分", "票数", "发售日", "标题")
    val lengths = listOf("不限", "极短", "短", "中等", "长", "极长")

    WindowBottomSheet(
        show = show,
        title = "作品筛选",
        onDismissRequest = onDismiss,
        endAction = {
            IconButton(onClick = { onApply(draft) }) {
                Icon(MiuixIcons.Ok, contentDescription = "应用", tint = MiuixTheme.colorScheme.primary)
            }
        },
    ) {
        Column {
            SmallTitle("对应 VNDB POST /vn 的 filters / sort")
            Card(Modifier.padding(12.dp)) {
                WindowDropdownPreference(
                    items = langs.map { if (it == "不限") it else languageName(it) },
                    selectedIndex = langs.indexOf(draft.language ?: "不限").coerceAtLeast(0),
                    title = "可用语言",
                    onSelectedIndexChange = {
                        draft = draft.copy(language = langs[it].takeIf { v -> v != "不限" })
                    },
                )
                HorizontalDivider()
                WindowDropdownPreference(
                    items = platforms.map { if (it == "不限") it else platformName(it) },
                    selectedIndex = platforms.indexOf(draft.platform ?: "不限").coerceAtLeast(0),
                    title = "平台",
                    onSelectedIndexChange = {
                        draft = draft.copy(platform = platforms[it].takeIf { v -> v != "不限" })
                    },
                )
                HorizontalDivider()
                WindowDropdownPreference(
                    items = listOf("不限", "7.0", "7.5", "8.0", "8.5", "9.0"),
                    selectedIndex = listOf(null, 70, 75, 80, 85, 90).indexOf(draft.minRating).coerceAtLeast(0),
                    title = "最低评分",
                    onSelectedIndexChange = {
                        draft = draft.copy(minRating = listOf(null, 70, 75, 80, 85, 90)[it])
                    },
                )
                HorizontalDivider()
                WindowDropdownPreference(
                    items = lengths,
                    selectedIndex = draft.length ?: 0,
                    title = "时长",
                    onSelectedIndexChange = {
                        draft = draft.copy(length = it.takeIf { v -> v > 0 })
                    },
                )
                HorizontalDivider()
                WindowDropdownPreference(
                    items = sortLabels,
                    selectedIndex = sorts.indexOf(draft.sort).coerceAtLeast(0),
                    title = "排序",
                    onSelectedIndexChange = {
                        draft = draft.copy(sort = sorts[it], reverse = sorts[it] != "title" && sorts[it] != "searchrank")
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    checked = draft.finishedOnly,
                    onCheckedChange = { draft = draft.copy(finishedOnly = it) },
                    title = "仅已完成",
                    summary = "devstatus = 0",
                )
                HorizontalDivider()
                SwitchPreference(
                    checked = draft.hasDescription,
                    onCheckedChange = { draft = draft.copy(hasDescription = it) },
                    title = "需要简介",
                    summary = "has_description = 1",
                )
            }
        }
    }
}
