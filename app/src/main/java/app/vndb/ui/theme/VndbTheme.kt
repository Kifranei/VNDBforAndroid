package app.vndb.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.vndb.data.model.ColorMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun VndbTheme(
    colorMode: ColorMode,
    content: @Composable () -> Unit,
) {
    val controller = remember(colorMode) {
        ThemeController(
            when (colorMode) {
                ColorMode.LIGHT -> ColorSchemeMode.Light
                ColorMode.DARK -> ColorSchemeMode.Dark
                ColorMode.MONET_SYSTEM -> ColorSchemeMode.MonetSystem
                ColorMode.MONET_LIGHT -> ColorSchemeMode.MonetLight
                ColorMode.MONET_DARK -> ColorSchemeMode.MonetDark
                ColorMode.SYSTEM -> ColorSchemeMode.System
            },
        )
    }
    MiuixTheme(controller = controller, content = content)
}
