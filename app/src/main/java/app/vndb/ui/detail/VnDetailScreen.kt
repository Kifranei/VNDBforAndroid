package app.vndb.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vndb.AppContainer
import app.vndb.data.local.UserSettings
import app.vndb.data.model.Character
import app.vndb.data.model.HistoryItem
import app.vndb.data.model.Release
import app.vndb.data.model.UlistEntry
import app.vndb.data.model.VisualNovel
import app.vndb.ui.components.CoverImage
import app.vndb.ui.components.ErrorState
import app.vndb.ui.components.InfoChip
import app.vndb.ui.components.LoadingBox
import app.vndb.ui.components.LocalGlassBackdrop
import app.vndb.ui.components.Os4DropdownPreference
import app.vndb.ui.components.PosterCard
import app.vndb.ui.components.progressiveBlurSource
import app.vndb.ui.components.rememberProgressiveBlurBackdrop
import app.vndb.ui.components.sheetHeaderAlwaysFloatingScrollOffset
import app.vndb.ui.nav.AppRoute
import app.vndb.ui.nav.sides
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
import app.vndb.util.visibleUrl
import app.vndb.util.vndbSiteUrl
import app.vndb.util.voicedName
import app.vndb.ui.nav.LocalTopBarCenterObstructionStart
import app.vndb.ui.nav.LocalTopBarTitleAlpha
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.icon.glass.Copy
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.icon.glass.Share
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class VnDetailState(
    val loading: Boolean = true,
    val error: String? = null,
    val vn: VisualNovel? = null,
    val characters: List<Character> = emptyList(),
    val releases: List<Release> = emptyList(),
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
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val vn = container.repository.vnDetail(id)
                container.preferences.addHistory(
                    HistoryItem(vn.id, "vn", vn.title ?: vn.id, vn.alttitle, vn.image.visibleUrl(container.settings.value.nsfwPolicy)),
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

    val blurBackdrop = rememberProgressiveBlurBackdrop()
    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = state.vn?.displayTitle(settings.titlePreference).orEmpty().ifBlank { "作品" },
                scrollBehavior = scroll,
                progressiveBlur = true,
                backdrop = blurBackdrop,
                titleAlpha = LocalTopBarTitleAlpha.current,
                centerObstructionStart = LocalTopBarCenterObstructionStart.current,
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(MiuixIcons.Glass.ChevronBackward, contentDescription = "返回")
                    }
                },
                actions = {
                    GlassIconButton(onClick = { showList = true }) {
                        Icon(MiuixIcons.Glass.Edit, contentDescription = "列表")
                    }
                    GlassIconButton(onClick = { uri.openUri(vndbSiteUrl(id)) }) {
                        Icon(MiuixIcons.Glass.Share, contentDescription = "打开网站")
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
                        .progressiveBlurSource(blurBackdrop)
                        .padding(padding.sides())
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
                        GlassTabRow(
                            tabs = listOf("简介", "角色", "发行", "相关"),
                            selectedIndex = tab,
                            onSelect = {
                                tab = it
                                vm.saveTab(it)
                            },
                            backdrop = null,
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

private data class CopyableName(
    val label: String,
    val value: String,
)

private fun copyableNames(vn: VisualNovel, settings: UserSettings): List<CopyableName> {
    val entries = mutableListOf<CopyableName>()
    val seenValues = mutableSetOf<String>()

    fun add(label: String, value: String?) {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty() || !seenValues.add(normalized)) return
        entries += CopyableName(label, normalized)
    }

    add("当前显示名", vn.displayTitle(settings.titlePreference))
    val original = vn.titles.firstOrNull { it.main == true }
        ?: vn.titles.firstOrNull { it.lang != null && it.lang == vn.olang }
    add("原名", original?.title)
    add("原名罗马音", original?.latin)

    vn.titles.forEach { localized ->
        if (localized === original) return@forEach
        val language = localized.lang?.let(::languageName) ?: "其他语言"
        val translationKind = if (localized.official == false) "非官方译名" else "译名"
        add("$language$translationKind", localized.title)
        add("${language}罗马音", localized.latin)
    }

    add("VNDB 主标题", vn.title)
    add("VNDB 替代标题", vn.alttitle)
    return entries
}

private fun copyName(context: Context, item: CopyableName) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(item.label, item.value))
    Toast.makeText(context, "已复制${item.label}", Toast.LENGTH_SHORT).show()
}

@Composable
private fun Header(vn: VisualNovel, settings: UserSettings) {
    var showCopySheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val names = remember(vn, settings.titlePreference) { copyableNames(vn, settings) }

    GlassCard(
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
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(vn.id) {
                        detectTapGestures(
                            onLongPress = { showCopySheet = true },
                        )
                    },
            ) {
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

    GlassBottomSheet(
        show = showCopySheet,
        backdrop = LocalGlassBackdrop.current,
        title = "复制名称",
        onDismissRequest = { showCopySheet = false },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            GlassCard(Modifier.padding(12.dp)) {
                names.forEachIndexed { index, item ->
                    BasicComponent(
                        title = item.value,
                        summary = item.label,
                        onClick = {
                            copyName(context, item)
                            showCopySheet = false
                        },
                        endActions = {
                            Icon(
                                MiuixIcons.Glass.Copy,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        },
                    )
                    if (index != names.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Overview(vn: VisualNovel, settings: UserSettings, onOpen: (AppRoute) -> Unit) {
    Column {
        if (vn.developers.isNotEmpty()) {
            SmallTitle("开发")
            GlassCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
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
            GlassCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
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
            GlassCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), insideMargin = PaddingValues(16.dp)) {
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
            GlassCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
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
        GlassCard(Modifier.padding(12.dp), insideMargin = PaddingValues(16.dp)) {
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
        GlassCard(Modifier.padding(12.dp), insideMargin = PaddingValues(16.dp)) {
            Text("没有发行记录")
        }
        return
    }
    GlassCard(Modifier.padding(12.dp)) {
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
        GlassCard(Modifier.padding(12.dp), insideMargin = PaddingValues(16.dp)) {
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
    GlassBottomSheet(
        show = show,
        backdrop = LocalGlassBackdrop.current,
        progressiveBlur = true,
        scrollOffset = ::sheetHeaderAlwaysFloatingScrollOffset,
        title = "同步到 VNDB 列表",
        onDismissRequest = onDismiss,
        endAction = {
            GlassIconButton(
                onClick = {
                    if (!state.listSaving) {
                        val vote = if (voteIndex == 0) null else (11 - voteIndex) * 10
                        onSave(vote, selected.toList(), current?.notes)
                        onDismiss()
                    }
                },
            ) {
                Icon(MiuixIcons.Glass.Ok, contentDescription = "保存", tint = MiuixTheme.colorScheme.onBackground)
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                GlassCard(Modifier.padding(12.dp)) {
                    Os4DropdownPreference(
                        // 位于底部弹窗内：页面 backdrop 被弹窗遮住，使用不透明填充
                        backdrop = null,
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
}
