package app.vndb.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vndb.AppContainer
import app.vndb.data.local.UserSettings
import app.vndb.data.model.Character
import app.vndb.data.model.FavoriteItem
import app.vndb.data.model.Release
import app.vndb.data.model.UlistEntry
import app.vndb.data.model.VisualNovel
import app.vndb.ui.components.CoverImage
import app.vndb.ui.components.ErrorState
import app.vndb.ui.components.InfoChip
import app.vndb.ui.components.LoadingBox
import app.vndb.ui.components.PosterCard
import app.vndb.ui.nav.AppRoute
import app.vndb.ui.vmFactory
import app.vndb.util.characterRoleName
import app.vndb.util.displayTitle
import app.vndb.util.formatDevStatus
import app.vndb.util.formatLength
import app.vndb.util.formatRating
import app.vndb.util.formatVotes
import app.vndb.util.languageName
import app.vndb.util.platformName
import app.vndb.util.staffRoleName
import app.vndb.util.stripVndbMarkup
import app.vndb.util.tagCategoryName
import app.vndb.util.ulistLabelName
import app.vndb.util.vndbSiteUrl
import app.vndb.util.voicedName
import app.vndb.util.visibleUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

data class VnDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val vn: VisualNovel? = null,
    val characters: List<Character> = emptyList(),
    val releases: List<Release> = emptyList(),
    val favorited: Boolean = false,
    val ulist: UlistEntry? = null,
    val listSaving: Boolean = false,
    val listMessage: String? = null,
    val listIndex: Int = 0,
    val listOffset: Int = 0,
    val pageTab: Int = 0,
)

class VnDetailViewModel(
    private val id: String,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(VnDetailState())
    val state = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            container.preferences.favorites.collect { list ->
                _state.update { it.copy(favorited = list.any { f -> f.id == id && f.type == "vn" }) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val vn = container.repository.vnDetail(id)
                container.preferences.addHistory(
                    FavoriteItem(vn.id, "vn", vn.title ?: vn.id, vn.alttitle, vn.image?.thumbnail),
                )
                val chars = runCatching { container.repository.charactersForVn(id) }.getOrDefault(emptyList())
                val releases = runCatching { container.repository.releasesForVn(id) }.getOrDefault(emptyList())
                val user = container.settings.value.userId
                val ulist = if (user.isNotBlank()) {
                    runCatching { container.repository.ulistEntry(user, id) }.getOrNull()
                } else {
                    null
                }
                _state.update { it.copy(loading = false, vn = vn, characters = chars, releases = releases, ulist = ulist) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    fun saveScroll(index: Int, offset: Int) {
        _state.update { it.copy(listIndex = index, listOffset = offset) }
    }

    fun saveTab(tab: Int) {
        _state.update { it.copy(pageTab = tab) }
    }

    fun toggleFavorite() {
        val vn = _state.value.vn ?: return
        viewModelScope.launch {
            container.preferences.toggleFavorite(
                FavoriteItem(vn.id, "vn", vn.title ?: vn.id, vn.alttitle, vn.image?.thumbnail),
            )
        }
    }

    fun saveUlist(vote: Int?, labels: List<Int>, notes: String?) {
        if (container.settings.value.apiToken.isBlank()) {
            _state.update { it.copy(listMessage = "请先在设置里填写 Token") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(listSaving = true, listMessage = null) }
            runCatching { container.repository.updateUlist(id, vote, labels, notes) }
                .onSuccess {
                    val user = container.settings.value.userId
                    val ulist = if (user.isNotBlank()) {
                        runCatching { container.repository.ulistEntry(user, id) }.getOrNull()
                    } else {
                        null
                    }
                    _state.update { it.copy(listSaving = false, ulist = ulist, listMessage = "已同步到 VNDB") }
                }
                .onFailure { e ->
                    _state.update { it.copy(listSaving = false, listMessage = e.message ?: "同步失败") }
                }
        }
    }
}

@Composable
fun VnDetailScreen(
    id: String,
    container: AppContainer,
    settings: UserSettings,
    onBack: () -> Unit,
    onOpen: (AppRoute) -> Unit,
) {
    val vm: VnDetailViewModel = viewModel(key = id, factory = vmFactory { VnDetailViewModel(id, container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val scroll = MiuixScrollBehavior()
    val uri = LocalUriHandler.current
    var tab by remember { mutableStateOf(state.pageTab) }
    var showList by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(state.listIndex, state.listOffset)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> vm.saveScroll(index, offset) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = state.vn?.displayTitle(settings.titlePreference).orEmpty().ifBlank { "作品" },
                scrollBehavior = scroll,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showList = true }) {
                        Icon(MiuixIcons.Edit, contentDescription = "列表")
                    }
                    IconButton(onClick = { vm.toggleFavorite() }) {
                        Icon(
                            if (state.favorited) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                            contentDescription = "收藏",
                            tint = if (state.favorited) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(onClick = { uri.openUri(vndbSiteUrl(id)) }) {
                        Icon(MiuixIcons.Share, contentDescription = "打开网站")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error ?: "", onRetry = vm::refresh, Modifier.padding(padding))
            else -> {
                val vn = state.vn ?: return@Scaffold
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scroll.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                ) {
                    item {
                        Header(vn, settings)
                    }
                    item {
                        TabRow(
                            tabs = listOf("简介", "角色", "发行", "相关"),
                            selectedTabIndex = tab,
                            onTabSelected = {
                                tab = it
                                vm.saveTab(it)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    when (tab) {
                        0 -> item { Overview(vn, settings, onOpen) }
                        1 -> item { Characters(vn.id, state.characters, settings, onOpen) }
                        2 -> item { Releases(state.releases, onOpen) }
                        else -> item { Related(vn, settings, onOpen) }
                    }
                }
            }
        }
    }

    UlistSheet(
        show = showList,
        state = state,
        onDismiss = { showList = false },
        onSave = { vote, labels, notes -> vm.saveUlist(vote, labels, notes) },
    )
}

@Composable
private fun Header(vn: VisualNovel, settings: UserSettings) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        insideMargin = PaddingValues(12.dp),
    ) {
        Row {
            CoverImage(
                url = vn.image.visibleUrl(settings.nsfwPolicy),
                modifier = Modifier.width(112.dp),
                contentDescription = vn.title,
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text(vn.displayTitle(settings.titlePreference), style = MiuixTheme.textStyles.title3)
                vn.alttitle?.takeIf { it != vn.title }?.let {
                    Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
                Text(
                    "${formatRating(vn.rating)}  ·  ${formatVotes(vn.votecount)} 票",
                    style = MiuixTheme.textStyles.headline2,
                    color = MiuixTheme.colorScheme.primary,
                )
                Text(
                    listOfNotNull(vn.released, formatLength(vn.lengthMinutes, vn.length), formatDevStatus(vn.devstatus)).joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vn.languages.take(8).forEach { InfoChip(languageName(it)) }
            vn.platforms.take(6).forEach { InfoChip(platformName(it)) }
        }
    }
}

@Composable
private fun Overview(vn: VisualNovel, settings: UserSettings, onOpen: (AppRoute) -> Unit) {
    Column {
        if (vn.developers.isNotEmpty()) {
            SmallTitle("开发")
            Card(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                vn.developers.forEach { dev ->
                    BasicComponent(
                        title = dev.name.orEmpty(),
                        summary = dev.original,
                        onClick = { onOpen(AppRoute.Producer(dev.id)) },
                    )
                }
            }
        }
        val tags = vn.tags
            .filter { (it.spoiler ?: 0) <= settings.spoilerLevel }
            .filter { settings.nsfwPolicy != app.vndb.data.model.NsfwPolicy.HIDE || it.category != "ero" }
            .sortedByDescending { it.rating ?: 0.0 }
        if (tags.isNotEmpty()) {
            SmallTitle("标签")
            Card(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                tags.take(24).forEach { tag ->
                    BasicComponent(
                        title = tag.name.orEmpty(),
                        summary = listOfNotNull(
                            tagCategoryName(tag.category),
                            tag.rating?.let { String.format("%.1f", it) },
                            if ((tag.spoiler ?: 0) > 0) "剧透" else null,
                        ).joinToString(" · "),
                        onClick = { onOpen(AppRoute.Tag(tag.id)) },
                    )
                }
            }
        }
        val desc = stripVndbMarkup(vn.description)
        if (desc.isNotBlank()) {
            SmallTitle("简介")
            Card(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), insideMargin = PaddingValues(16.dp)) {
                Text(desc, style = MiuixTheme.textStyles.paragraph)
            }
        }
        val shots = vn.screenshots.mapNotNull { it.visibleUrl(settings.nsfwPolicy, preferFull = true) }
        if (shots.isNotEmpty()) {
            SmallTitle("截图")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shots.size) { index ->
                    CoverImage(
                        url = shots[index],
                        modifier = Modifier
                            .width(180.dp)
                            .height(110.dp)
                            .clickable { onOpen(AppRoute.Gallery(shots, index)) },
                        aspectRatio = 16f / 10f,
                    )
                }
            }
        }
        if (vn.staff.isNotEmpty()) {
            SmallTitle("制作人员")
            Card(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                vn.staff.take(20).forEach { staff ->
                    BasicComponent(
                        title = staff.name.orEmpty(),
                        summary = listOfNotNull(staffRoleName(staff.role), staff.note).joinToString(" · "),
                        onClick = { onOpen(AppRoute.Staff(staff.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Characters(vnId: String, characters: List<Character>, settings: UserSettings, onOpen: (AppRoute) -> Unit) {
    if (characters.isEmpty()) {
        Card(Modifier.padding(12.dp), insideMargin = PaddingValues(16.dp)) {
            Text("没有角色数据")
        }
        return
    }
    val grouped = characters.groupBy { ch ->
        ch.vns.firstOrNull { it.id == vnId }?.role
            ?: ch.vns.firstOrNull()?.role
            ?: "side"
    }
    Column {
        listOf("main", "primary", "side", "appears").forEach { role ->
            val list = grouped[role].orEmpty()
            if (list.isEmpty()) return@forEach
            SmallTitle(characterRoleName(role))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.id }) { ch ->
                    PosterCard(
                        title = ch.name.orEmpty(),
                        subtitle = ch.original,
                        imageUrl = ch.image.visibleUrl(settings.nsfwPolicy),
                        rating = null,
                        onClick = { onOpen(AppRoute.Character(ch.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Releases(releases: List<Release>, onOpen: (AppRoute) -> Unit) {
    if (releases.isEmpty()) {
        Card(Modifier.padding(12.dp), insideMargin = PaddingValues(16.dp)) {
            Text("没有发行记录")
        }
        return
    }
    Card(Modifier.padding(12.dp)) {
        releases.forEach { rel ->
            BasicComponent(
                title = rel.title.orEmpty(),
                summary = listOfNotNull(
                    rel.released,
                    rel.platforms.joinToString("/") { platformName(it) }.ifBlank { null },
                    rel.minage?.let { "C$it" },
                    if (rel.patch == true) "补丁" else null,
                    if (rel.official == false) "非官方" else null,
                    voicedName(rel.voiced).takeIf { rel.voiced != null },
                ).joinToString(" · "),
            )
        }
    }
}

@Composable
private fun Related(vn: VisualNovel, settings: UserSettings, onOpen: (AppRoute) -> Unit) {
    if (vn.relations.isEmpty()) {
        Card(Modifier.padding(12.dp), insideMargin = PaddingValues(16.dp)) {
            Text("没有相关作品")
        }
        return
    }
    Column {
        SmallTitle("相关作品")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vn.relations, key = { it.id }) { rel ->
                PosterCard(
                    title = rel.title.orEmpty(),
                    subtitle = rel.relation,
                    imageUrl = rel.image.visibleUrl(settings.nsfwPolicy),
                    rating = null,
                    onClick = { onOpen(AppRoute.Vn(rel.id)) },
                )
            }
        }
    }
}

private val DefaultLabels = listOf(1, 2, 3, 4, 5, 6)

@Composable
private fun UlistSheet(
    show: Boolean,
    state: VnDetailState,
    onDismiss: () -> Unit,
    onSave: (vote: Int?, labels: List<Int>, notes: String?) -> Unit,
) {
    val current = state.ulist
    var selected by remember(show, current) {
        mutableStateOf(current?.labels?.map { it.id }?.filter { it in DefaultLabels }?.toSet() ?: emptySet())
    }
    val voteOptions = listOf("不评分") + (10 downTo 1).map { "$it.0" }
    var voteIndex by remember(show, current) {
        mutableStateOf(
            current?.vote?.let { ((100 - it) / 10 + 1).coerceIn(1, 10) } ?: 0,
        )
    }
    WindowBottomSheet(
        show = show,
        title = "同步到 VNDB 列表",
        onDismissRequest = onDismiss,
        endAction = {
            IconButton(
                onClick = {
                    val vote = if (voteIndex == 0) null else (11 - voteIndex) * 10
                    onSave(vote, selected.toList(), current?.notes)
                    onDismiss()
                },
                enabled = !state.listSaving,
            ) {
                Icon(MiuixIcons.Ok, contentDescription = "保存", tint = MiuixTheme.colorScheme.primary)
            }
        },
    ) {
        Column {
            Card(Modifier.padding(12.dp)) {
                WindowDropdownPreference(
                    items = voteOptions,
                    selectedIndex = voteIndex,
                    title = "评分",
                    summary = current?.vote?.let { "当前 ${it / 10.0}" } ?: "未评分",
                    onSelectedIndexChange = { voteIndex = it },
                )
                DefaultLabels.forEach { labelId ->
                    SwitchPreference(
                        checked = labelId in selected,
                        onCheckedChange = {
                            selected = if (it) selected + labelId else selected - labelId
                        },
                        title = ulistLabelName(labelId, null),
                    )
                }
            }
            state.listMessage?.let {
                Text(it, modifier = Modifier.padding(horizontal = 16.dp), color = MiuixTheme.colorScheme.primary)
            }
        }
    }
}
