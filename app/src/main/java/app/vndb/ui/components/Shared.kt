package app.vndb.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vndb.data.local.UserSettings
import app.vndb.data.model.Character
import app.vndb.data.model.VisualNovel
import app.vndb.util.displaySubtitle
import app.vndb.util.displayTitle
import app.vndb.util.formatLength
import app.vndb.util.formatRating
import app.vndb.util.formatVotes
import app.vndb.util.visibleUrl
import coil3.compose.AsyncImage
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassTabRow
import top.yukonga.miuix.kmp.glass.GlassTabRowDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType


internal fun sheetHeaderAlwaysFloatingScrollOffset(): Float = 2f

/**
 * A scrolling OS4 glass tab row using the rc01 [GlassTabRow] API.
 *
 * GlassTabRow distributes its tabs by weight, so it needs a finite width even when the row can
 * scroll. Each tab is sized from its label, with the standard Miuix tab width range as bounds.
 */
@Composable
fun ScrollableGlassTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    minTabWidth: Dp = 76.dp,
    maxTabWidth: Dp = 160.dp,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 8.dp,
) {
    if (tabs.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MiuixTheme.textStyles.subtitle
    val tabWidth = remember(tabs, labelStyle, density) {
        val maxLabelWidth = tabs.maxOf { textMeasurer.measure(it, labelStyle).size.width }
        with(density) { maxLabelWidth.toDp() } + GlassTabRowDefaults.TabPaddingHorizontal * 2
    }.coerceIn(minTabWidth, maxTabWidth)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gapWidth = GlassTabRowDefaults.TabGap * (tabs.size - 1).coerceAtLeast(0)
        val contentWidth = horizontalPadding * 2 + tabWidth * tabs.size + gapWidth
        val viewportWidth = if (maxWidth.value.isFinite()) maxWidth else 0.dp
        val rowWidth = maxOf(viewportWidth, contentWidth)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            GlassTabRow(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                backdrop = backdrop,
                modifier = Modifier
                    .width(rowWidth)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            )
        }
    }
}

@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    aspectRatio: Float = 0.7f,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text("无封面", style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun VnRowCard(
    vn: VisualNovel,
    settings: UserSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        insideMargin = PaddingValues(10.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(
                url = vn.image.visibleUrl(settings.nsfwPolicy),
                modifier = Modifier.width(72.dp),
                contentDescription = vn.title,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = vn.displayTitle(settings.titlePreference),
                    style = MiuixTheme.textStyles.headline1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                vn.displaySubtitle(settings.titlePreference)?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = listOfNotNull(
                        vn.released,
                        formatLength(vn.lengthMinutes, vn.length),
                    ).joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatRating(vn.rating),
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.primary,
                )
                Text(
                    text = formatVotes(vn.votecount) + " 票",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
fun CharacterRowCard(
    character: Character,
    settings: UserSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        insideMargin = PaddingValues(10.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverImage(
                url = character.image.visibleUrl(settings.nsfwPolicy),
                modifier = Modifier.width(64.dp),
                aspectRatio = 0.85f,
                contentDescription = character.name,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(character.name.orEmpty(), style = MiuixTheme.textStyles.headline1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                character.original?.takeIf { it != character.name }?.let {
                    Text(it, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
                }
                character.vns.firstOrNull()?.title?.let {
                    Text(it, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    SmallTitle(text = text)
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = MiuixTheme.colorScheme.error)
        GlassDialogButton(onClick = onRetry, role = GlassDialogButtonRole.Primary) {
            Text("重试", style = MiuixTheme.textStyles.button)
        }
    }
}

@Composable
fun InfoChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
fun HorizontalSpacer(height: Int = 8) {
    Spacer(Modifier.height(height.dp))
}

@Composable
fun PosterCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    rating: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.width(132.dp),
        insideMargin = PaddingValues(0.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = onClick,
    ) {
        Column {
            CoverImage(url = imageUrl, modifier = Modifier.fillMaxWidth(), contentDescription = title)
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MiuixTheme.textStyles.body2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
                }
                if (!rating.isNullOrBlank()) {
                    Text(rating, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.primary)
                }
            }
        }
    }
}
