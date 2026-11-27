package app.vndb.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
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
import app.vndb.data.repo.VndbRepository
import app.vndb.ui.components.CharacterRowCard
import app.vndb.ui.components.EmptyState
import app.vndb.ui.components.ErrorState
import app.vndb.ui.components.LoadingBox
import app.vndb.ui.components.LocalGlassBackdrop
import app.vndb.ui.components.ScrollableGlassTabRow
import app.vndb.ui.components.Os4DropdownPreference
import app.vndb.ui.components.sheetHeaderAlwaysFloatingScrollOffset
import app.vndb.ui.components.VnRowCard
import app.vndb.ui.nav.AppRoute
import app.vndb.ui.vmFactory
import app.vndb.util.languageName
import app.vndb.util.platformName
import app.vndb.util.producerTypeName
import app.vndb.util.tagCategoryName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Filter
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.icon.glass.Reset
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

class SearchViewModel(private val repository: VndbRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        if (_state.value.query == value) return
        _state.update { it.copy(query = value) }
        search(reset = true, debounce = true)
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
        search(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (searchJob?.isActive == true || !current.more || current.loading || current.loadingMore) return
        search(reset = false)
    }

    private fun search(reset: Boolean, debounce: Boolean = false) {
        // The debounce and HTTP request share one job so submit also cancels an
        // automatic search that has already started.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(380)
            val snapshot = _state.value
            if (snapshot.query.isBlank()) {
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = null,
                        page = 1,
                        more = false,
                        vns = emptyList(),
                        characters = emptyList(),
                        producers = emptyList(),
                        staff = emptyList(),
                        tags = emptyList(),
                    )
                }
                return@launch
            }
            val nextPage = if (reset) 1 else snapshot.page + 1
            _state.update {
                it.copy(
                    loading = reset,
                    loadingMore = !reset,
                    error = null,
                    more = if (reset) false else it.more,
                    vns = if (reset) emptyList() else it.vns,
                    characters = if (reset) emptyList() else it.characters,
                    producers = if (reset) emptyList() else it.producers,
                    staff = if (reset) emptyList() else it.staff,
                    tags = if (reset) emptyList() else it.tags,
                )
            }
            try {
                when (snapshot.kind) {
                    SearchKind.VN -> {
                        val page = repository.searchVn(snapshot.query.trim(), nextPage, snapshot.filters)
                        ensureActive()
                        _state.update { it.copy(vns = (it.vns + page.results).distinctBy { it.id }, page = nextPage, more = page.more, loading = false, loadingMore = false) }
                    }
                    SearchKind.CHARACTER -> {
                        val page = repository.searchCharacters(snapshot.query.trim(), nextPage)
                        ensureActive()
                        _state.update { it.copy(characters = (it.characters + page.results).distinctBy { it.id }, page = nextPage, more = page.more, loading = false, loadingMore = false) }
                    }
                    SearchKind.PRODUCER -> {
                        val page = repository.searchProducers(snapshot.query.trim(), nextPage)
                        ensureActive()
                        _state.update { it.copy(producers = (it.producers + page.results).distinctBy { it.id }, page = nextPage, more = page.more, loading = false, loadingMore = false) }
                    }
                    SearchKind.STAFF -> {
                        val page = repository.searchStaff(snapshot.query.trim(), nextPage)
                        ensureActive()
                        _state.update { it.copy(staff = (it.staff + page.results).distinctBy { it.id to it.aid }, page = nextPage, more = page.more, loading = false, loadingMore = false) }
                    }
                    SearchKind.TAG -> {
                        val page = repository.searchTags(snapshot.query.trim(), nextPage)
                        ensureActive()
                        _state.update { it.copy(tags = (it.tags + page.results).distinctBy { it.id }, page = nextPage, more = page.more, loading = false, loadingMore = false) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                _state.update { it.copy(loading = false, loadingMore = false, error = e.message ?: "搜索失败") }
            }
        }
    }
}

@Composable
fun SearchOverlay(
    container: AppContainer,
    settings: UserSettings,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onOpen: (AppRoute) -> Unit,
    horizontalInsetsModifier: Modifier = Modifier,
    searchTopPadding: () -> Dp = { 0.dp },
) {
    val vm: SearchViewModel = viewModel(factory = vmFactory { SearchViewModel(container.repository) })
    val state by vm.state.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (last != null && total > 4 && last >= total - 3) vm.loadMore()
            }
    }

    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val searchBarPadding = GlassSearchOverlayDefaults.VerticalPadding
    val searchResultTopPadding = 8.dp
    val searchContentTop = statusBarTop + GlassSearchOverlayDefaults.BarHeight + searchBarPadding * 2 + searchResultTopPadding
    val horizontalSafetyInsets = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal)
    val combinedHorizontalInsetsModifier = Modifier
        .windowInsetsPadding(horizontalSafetyInsets)
        .then(horizontalInsetsModifier)

    GlassSearchOverlay(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        scrollMaterialThreshold = searchBarPadding + searchResultTopPadding,
        horizontalInsetsModifier = combinedHorizontalInsetsModifier,
        label = "搜索 VNDB",
        searchIcon = MiuixIcons.Glass.Search,
        closeIcon = MiuixIcons.Glass.Close,
        searchTopPadding = searchTopPadding,
        scrollOffset = {
            when {
                listState.layoutInfo.totalItemsCount == 0 -> 0f
                listState.firstVisibleItemIndex > 0 -> Float.MAX_VALUE
                else -> listState.firstVisibleItemScrollOffset.toFloat()
            }
        },
    ) { query ->
        // GlassSearchOverlay owns the query field. Mirror it into the existing
        // debounced search ViewModel; duplicate initial values are ignored there.
        LaunchedEffect(query) { vm.onQueryChange(query) }

        Column(
            Modifier
                .fillMaxSize()
                .then(combinedHorizontalInsetsModifier)
                .additionalSearchTopPadding(searchTopPadding),
        ) {
            Spacer(Modifier.height(searchContentTop))

            ScrollableGlassTabRow(
                tabs = SearchKind.entries.map { it.label },
                selectedIndex = state.kind.ordinal,
                onSelect = { vm.onKind(SearchKind.entries[it]) },
                backdrop = null,
                verticalPadding = 4.dp,
            )

            if (state.kind == SearchKind.VN) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "高级筛选对应 POST /vn filters",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    GlassIconButton(onClick = { showFilters = true }) {
                        Icon(
                            MiuixIcons.Glass.Filter,
                            contentDescription = "筛选",
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }

            when {
                state.loading -> LoadingBox(Modifier.weight(1f))
                state.error != null && isEmpty(state) -> ErrorState(
                    state.error ?: "",
                    onRetry = vm::submit,
                    modifier = Modifier.weight(1f),
                )
                isEmpty(state) -> EmptyState(
                    if (state.query.isBlank()) "试试搜索作品、角色或制作组" else "没有匹配结果",
                    Modifier.weight(1f),
                )
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        when (state.kind) {
                            SearchKind.VN -> items(state.vns, key = { it.id }) { vn ->
                                VnRowCard(vn, settings, onClick = { onOpen(AppRoute.Vn(vn.id)) })
                            }
                            SearchKind.CHARACTER -> items(state.characters, key = { it.id }) { ch ->
                                CharacterRowCard(ch, settings, onClick = { onOpen(AppRoute.Character(ch.id)) })
                            }
                            SearchKind.PRODUCER -> items(state.producers, key = { it.id }) { producer ->
                                GlassCard(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                    onClick = { onOpen(AppRoute.Producer(producer.id)) },
                                ) {
                                    BasicComponent(
                                        title = producer.name.orEmpty(),
                                        summary = listOfNotNull(
                                            producer.original?.takeIf { it != producer.name },
                                            producerTypeName(producer.type),
                                        ).joinToString(" · "),
                                    )
                                }
                            }
                            SearchKind.STAFF -> items(state.staff, key = { "${it.id}:${it.aid}" }) { staff ->
                                GlassCard(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                    onClick = { onOpen(AppRoute.Staff(staff.id)) },
                                ) {
                                    BasicComponent(
                                        title = staff.name.orEmpty(),
                                        summary = listOfNotNull(
                                            staff.original?.takeIf { it != staff.name },
                                            staff.lang,
                                        ).joinToString(" · "),
                                    )
                                }
                            }
                            SearchKind.TAG -> items(state.tags, key = { it.id }) { tag ->
                                GlassCard(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                    onClick = { onOpen(AppRoute.Tag(tag.id)) },
                                ) {
                                    BasicComponent(
                                        title = tag.name.orEmpty(),
                                        summary = listOfNotNull(
                                            tagCategoryName(tag.category),
                                            tag.vnCount?.let { "$it 部作品" },
                                        ).joinToString(" · "),
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
}

/** Applies the search bar's animated top clearance during layout instead of recomposing each rail frame. */
private fun Modifier.additionalSearchTopPadding(searchTopPadding: () -> Dp): Modifier = layout { measurable, constraints ->
    val top = searchTopPadding().roundToPx().coerceIn(0, constraints.maxHeight)
    val placeable = measurable.measure(
        constraints.copy(
            minHeight = 0,
            maxHeight = (constraints.maxHeight - top).coerceAtLeast(0),
        ),
    )
    layout(placeable.width, (placeable.height + top).coerceAtMost(constraints.maxHeight)) {
        placeable.placeRelative(0, top)
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

    GlassBottomSheet(
        show = show,
        backdrop = LocalGlassBackdrop.current,
        progressiveBlur = true,
        scrollOffset = ::sheetHeaderAlwaysFloatingScrollOffset,
        title = "作品筛选",
        startAction = {
            GlassIconButton(onClick = { draft = SearchFilters() }) {
                Icon(
                    MiuixIcons.Glass.Reset,
                    contentDescription = "重置筛选",
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
        endAction = {
            GlassIconButton(onClick = { onApply(draft) }) {
                Icon(MiuixIcons.Glass.Ok, contentDescription = "应用", tint = MiuixTheme.colorScheme.onBackground)
            }
        },
        headerBottomContent = {
            Text(
                "对应 VNDB POST /vn 的 filters / sort",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        },
        onDismissRequest = onDismiss,
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                GlassCard(Modifier.padding(horizontal = 12.dp)) {
                Os4DropdownPreference(
                    // 位于底部弹窗内：页面 backdrop 被弹窗遮住，使用不透明填充
                    backdrop = null,
                    items = langs.map { if (it == "不限") it else languageName(it) },
                    selectedIndex = langs.indexOf(draft.language ?: "不限").coerceAtLeast(0),
                    title = "可用语言",
                    onSelectedIndexChange = {
                        draft = draft.copy(language = langs[it].takeIf { v -> v != "不限" })
                    },
                )
                HorizontalDivider()
                Os4DropdownPreference(
                    // 位于底部弹窗内：页面 backdrop 被弹窗遮住，使用不透明填充
                    backdrop = null,
                    items = platforms.map { if (it == "不限") it else platformName(it) },
                    selectedIndex = platforms.indexOf(draft.platform ?: "不限").coerceAtLeast(0),
                    title = "平台",
                    onSelectedIndexChange = {
                        draft = draft.copy(platform = platforms[it].takeIf { v -> v != "不限" })
                    },
                )
                HorizontalDivider()
                Os4DropdownPreference(
                    // 位于底部弹窗内：页面 backdrop 被弹窗遮住，使用不透明填充
                    backdrop = null,
                    items = listOf("不限", "7.0", "7.5", "8.0", "8.5", "9.0"),
                    selectedIndex = listOf(null, 70, 75, 80, 85, 90).indexOf(draft.minRating).coerceAtLeast(0),
                    title = "最低评分",
                    onSelectedIndexChange = {
                        draft = draft.copy(minRating = listOf(null, 70, 75, 80, 85, 90)[it])
                    },
                )
                HorizontalDivider()
                Os4DropdownPreference(
                    // 位于底部弹窗内：页面 backdrop 被弹窗遮住，使用不透明填充
                    backdrop = null,
                    items = lengths,
                    selectedIndex = draft.length ?: 0,
                    title = "时长",
                    onSelectedIndexChange = {
                        draft = draft.copy(length = it.takeIf { v -> v > 0 })
                    },
                )
                HorizontalDivider()
                Os4DropdownPreference(
                    // 位于底部弹窗内：页面 backdrop 被弹窗遮住，使用不透明填充
                    backdrop = null,
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
}
