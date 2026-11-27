package app.vndb.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * 顶栏渐进模糊采样工具。页面内容通过 [layerBackdrop] 录制为 backdrop；
 * 官方 GlassTopAppBar 再对这块 backdrop 做渐进模糊。内容层在透明底上录制会被着色器
 * 归一化成不透明光晕，因此录制时先铺一层不透明的页面底色。RuntimeShader 不可用
 * （API < 33）时返回 null，由 GlassTopAppBar 使用渐变/纯色兜底。
 */

/**
 * 创建供 GlassTopAppBar 渐进模糊采样的页面 backdrop。
 *
 * @param color 录制内容时垫在下方的不透明底色，一般为页面背景色。
 */
@Composable
fun rememberProgressiveBlurBackdrop(
    color: Color = MiuixTheme.colorScheme.surface,
): LayerBackdrop? {
    if (!isRuntimeShaderSupported()) return null
    return rememberLayerBackdrop {
        drawRect(color)
        drawContent()
    }
}

/**
 * 把页面内容录制进 [backdrop]；[backdrop] 为 `null` 时不做任何处理。
 *
 * @param backdrop [rememberProgressiveBlurBackdrop] 返回的 backdrop。
 */
fun Modifier.progressiveBlurSource(backdrop: LayerBackdrop?): Modifier =
    if (backdrop != null) this.layerBackdrop(backdrop) else this
