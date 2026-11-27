package app.vndb.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupSizing

/**
 * 页面级玻璃 backdrop（液态玻璃界面由 App 提供）。下拉弹窗用它采样模糊；
 * 为 null 时退化为不透明填充，避免采样到被遮住的页面。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * OS4 玻璃下拉偏好项。保留应用原有的调用形状，内部使用官方 [GlassDropdownPreference]。
 */
@Composable
fun Os4DropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    startAction: @Composable (() -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    GlassDropdownPreference(
        onSelectedIndexChange = { onSelectedIndexChange?.invoke(it) },
        items = items,
        selectedIndex = selectedIndex,
        title = title,
        backdrop = backdrop,
        modifier = modifier,
        enabled = enabled,
        showValue = showValue,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        insideMargin = insideMargin,
        sizing = GlassPopupSizing(maxHeight = maxHeight ?: GlassPopupDefaults.MaxHeight),
        onExpandedChange = onExpandedChange,
    )
}
