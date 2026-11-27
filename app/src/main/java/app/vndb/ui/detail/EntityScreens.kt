package app.vndb.ui.detail

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.vndb.AppContainer
import app.vndb.data.local.UserSettings
import app.vndb.data.model.Character
import app.vndb.data.model.FavoriteItem
import app.vndb.data.model.Producer
import app.vndb.data.model.Staff
import app.vndb.data.model.Tag
import app.vndb.data.model.VisualNovel
import app.vndb.ui.components.CoverImage
import app.vndb.ui.components.ErrorState
import app.vndb.ui.components.InfoChip
import app.vndb.ui.components.LoadingBox
import app.vndb.ui.components.VnRowCard
import app.vndb.ui.nav.AppRoute
import app.vndb.ui.vmFactory
import app.vndb.util.formatBirthday
import app.vndb.util.producerTypeName
import app.vndb.util.stripVndbMarkup
import app.vndb.util.tagCategoryName
import app.vndb.util.visibleUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private class SimpleState<T>(
    val loading: Boolean = true,
    val error: String? = null,
    val data: T? = null,
    val extra: List<VisualNovel> = emptyList(),
)

private class EntityVm<T>(
    private val load: suspend () -> Pair<T, List<VisualNovel>>,
) : ViewModel() {
    private val _state = MutableStateFlow(SimpleState<T>())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = SimpleState(loading = true)
            runCatching { load() }
                .onSuccess { (data, extra) ->
                    _state.value = SimpleState(loading = false, data = data, extra = extra)
                }
                .onFailure {
                    _state.value = SimpleState(loading = false, error = it.message ?: "加载失败")
                }
        }
    }
}

@Composable
fun CharacterDetailScreen(
    id: String,
    container: AppContainer,
    settings: UserSettings,
    onBack: () -> Unit,
    onOpen: (AppRoute) -> Unit,
) {
    val vm: EntityVm<Character> = viewModel(
        key = "c$id",
        factory = vmFactory {
            EntityVm {
                val ch = container.repository.characterDetail(id)
                container.preferences.addHistory(FavoriteItem(ch.id, "character", ch.name ?: ch.id, ch.original, ch.image?.url))
                ch to emptyList()
            }
        },
    )
    EntityScaffold(
        title = vm.state.collectAsStateWithLifecycle().value.data?.name ?: "角色",
        state = vm.state.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onRetry = vm::refresh,
    ) { ch ->
        item {
            Card(Modifier.padding(12.dp), insideMargin = PaddingValues(12.dp)) {
                Row {
                    CoverImage(ch.image.visibleUrl(settings.nsfwPolicy), Modifier.width(108.dp), ch.name, 0.85f)
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(ch.name.orEmpty(), style = MiuixTheme.textStyles.title3)
                        ch.original?.let { Text(it, color = MiuixTheme.colorScheme.onSurfaceSecondary) }
                        formatBirthday(ch.birthday)?.let { InfoChip("生日 $it") }
                        ch.age?.let { InfoChip("${it} 岁") }
                        ch.bloodType?.let { InfoChip("血型 ${it.uppercase()}") }
                    }
                }
            }
        }
        val measures = listOfNotNull(
            ch.height?.let { "身高 ${it}cm" },
            ch.weight?.let { "体重 ${it}kg" },
            ch.bust?.let { "B${it}" },
            ch.waist?.let { "W${it}" },
            ch.hips?.let { "H${it}" },
            ch.cup?.let { "罩杯 $it" },
        )
        if (measures.isNotEmpty()) {
            item {
                Card(Modifier.padding(horizontal = 12.dp), insideMargin = PaddingValues(16.dp)) {
                    Text(measures.joinToString(" · "))
                }
            }
        }
        val desc = stripVndbMarkup(ch.description)
        if (desc.isNotBlank()) {
            item { SmallTitle("简介") }
            item {
                Card(Modifier.padding(horizontal = 12.dp), insideMargin = PaddingValues(16.dp)) {
                    Text(desc, style = MiuixTheme.textStyles.paragraph)
                }
            }
        }
        val traits = ch.traits.filter { (it.spoiler ?: 0) <= settings.spoilerLevel }
            .filter { settings.nsfwPolicy != app.vndb.data.model.NsfwPolicy.HIDE || it.sexual != true }
        if (traits.isNotEmpty()) {
            item { SmallTitle("特征") }
            item {
                Card(Modifier.padding(horizontal = 12.dp)) {
                    traits.forEach { t ->
                        BasicComponent(
                            title = t.name.orEmpty(),
                            summary = listOfNotNull(t.groupName, if ((t.spoiler ?: 0) > 0) "剧透" else null).joinToString(" · "),
                        )
                    }
                }
            }
        }
        if (ch.vns.isNotEmpty()) {
            item { SmallTitle("登场作品") }
            items(ch.vns.distinctBy { it.id }, key = { it.id }) { vn ->
                Card(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), onClick = { onOpen(AppRoute.Vn(vn.id)) }) {
                    BasicComponent(title = vn.title.orEmpty(), summary = vn.role)
                }
            }
        }
    }
}

@Composable
fun ProducerDetailScreen(
    id: String,
    container: AppContainer,
    settings: UserSettings,
    onBack: () -> Unit,
    onOpen: (AppRoute) -> Unit,
) {
    val vm: EntityVm<Producer> = viewModel(
        key = "p$id",
        factory = vmFactory {
            EntityVm {
                val p = container.repository.producerDetail(id)
                val vns = container.repository.vnByDeveloper(id, 1).results
                container.preferences.addHistory(FavoriteItem(p.id, "producer", p.name ?: p.id, p.original, null))
                p to vns
            }
        },
    )
    val state = vm.state.collectAsStateWithLifecycle().value
    EntityScaffold(
        title = state.data?.name ?: "制作组",
        state = state,
        onBack = onBack,
        onRetry = vm::refresh,
    ) { p ->
        item {
            Card(Modifier.padding(12.dp), insideMargin = PaddingValues(16.dp)) {
                Text(p.name.orEmpty(), style = MiuixTheme.textStyles.title3)
                p.original?.let { Text(it, color = MiuixTheme.colorScheme.onSurfaceSecondary) }
                Text(listOfNotNull(producerTypeName(p.type), p.lang).joinToString(" · "), modifier = Modifier.padding(top = 6.dp))
            }
        }
        val desc = stripVndbMarkup(p.description)
        if (desc.isNotBlank()) {
            item { SmallTitle("简介") }
            item {
                Card(Modifier.padding(horizontal = 12.dp), insideMargin = PaddingValues(16.dp)) {
                    Text(desc, style = MiuixTheme.textStyles.paragraph)
                }
            }
        }
        if (state.extra.isNotEmpty()) {
            item { SmallTitle("开发作品") }
            items(state.extra, key = { it.id }) { vn ->
                VnRowCard(vn, settings, onClick = { onOpen(AppRoute.Vn(vn.id)) })
            }
        }
    }
}

@Composable
fun StaffDetailScreen(
    id: String,
    container: AppContainer,
    settings: UserSettings,
    onBack: () -> Unit,
    onOpen: (AppRoute) -> Unit,
) {
    val vm: EntityVm<Staff> = viewModel(
        key = "s$id",
        factory = vmFactory {
            EntityVm {
                val s = container.repository.staffDetail(id)
                val vns = container.repository.vnByStaff(id, 1).results
                container.preferences.addHistory(FavoriteItem(s.id, "staff", s.name ?: s.id, s.original, null))
                s to vns
            }
        },
    )
    val state = vm.state.collectAsStateWithLifecycle().value
    EntityScaffold(
        title = state.data?.name ?: "职员",
        state = state,
        onBack = onBack,
        onRetry = vm::refresh,
    ) { s ->
        item {
            Card(Modifier.padding(12.dp), insideMargin = PaddingValues(16.dp)) {
                Text(s.name.orEmpty(), style = MiuixTheme.textStyles.title3)
                s.original?.let { Text(it, color = MiuixTheme.colorScheme.onSurfaceSecondary) }
                Text(listOfNotNull(s.lang, s.gender).joinToString(" · "), modifier = Modifier.padding(top = 6.dp))
            }
        }
        val desc = stripVndbMarkup(s.description)
        if (desc.isNotBlank()) {
            item { SmallTitle("简介") }
            item {
                Card(Modifier.padding(horizontal = 12.dp), insideMargin = PaddingValues(16.dp)) {
                    Text(desc, style = MiuixTheme.textStyles.paragraph)
                }
            }
        }
        if (s.aliases.isNotEmpty()) {
            item { SmallTitle("别名") }
            item {
                Card(Modifier.padding(horizontal = 12.dp)) {
                    s.aliases.forEach { alias ->
                        BasicComponent(title = alias.latin ?: alias.name.orEmpty(), summary = alias.name)
                    }
                }
            }
        }
        if (state.extra.isNotEmpty()) {
            item { SmallTitle("参与作品") }
            items(state.extra, key = { it.id }) { vn ->
                VnRowCard(vn, settings, onClick = { onOpen(AppRoute.Vn(vn.id)) })
            }
        }
    }
}

@Composable
fun TagDetailScreen(
    id: String,
    container: AppContainer,
    settings: UserSettings,
    onBack: () -> Unit,
    onOpen: (AppRoute) -> Unit,
) {
    val vm: EntityVm<Tag> = viewModel(
        key = "g$id",
        factory = vmFactory {
            EntityVm {
                val tag = container.repository.tagDetail(id)
                val vns = container.repository.vnByTag(id, 1).results
                container.preferences.addHistory(FavoriteItem(tag.id, "tag", tag.name ?: tag.id, tagCategoryName(tag.category), null))
                tag to vns
            }
        },
    )
    val state = vm.state.collectAsStateWithLifecycle().value
    EntityScaffold(
        title = state.data?.name ?: "标签",
        state = state,
        onBack = onBack,
        onRetry = vm::refresh,
    ) { tag ->
        item {
            Card(Modifier.padding(12.dp), insideMargin = PaddingValues(16.dp)) {
                Text(tag.name.orEmpty(), style = MiuixTheme.textStyles.title3)
                Text(
                    listOfNotNull(tagCategoryName(tag.category), tag.vnCount?.let { "$it 部作品" }).joinToString(" · "),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        val desc = stripVndbMarkup(tag.description)
        if (desc.isNotBlank()) {
            item { SmallTitle("说明") }
            item {
                Card(Modifier.padding(horizontal = 12.dp), insideMargin = PaddingValues(16.dp)) {
                    Text(desc, style = MiuixTheme.textStyles.paragraph)
                }
            }
        }
        if (state.extra.isNotEmpty()) {
            item { SmallTitle("作品") }
            items(state.extra, key = { it.id }) { vn ->
                VnRowCard(vn, settings, onClick = { onOpen(AppRoute.Vn(vn.id)) })
            }
        }
    }
}

@Composable
private fun <T> EntityScaffold(
    title: String,
    state: SimpleState<T>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.(T) -> Unit,
) {
    val scroll = MiuixScrollBehavior()
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
            state.error != null -> ErrorState(state.error, onRetry, Modifier.padding(padding))
            state.data == null -> LoadingBox(Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scroll.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                content = { content(state.data) },
            )
        }
    }
}
