package app.vndb.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/** Wide windows (landscape phones, tablets) swap the bottom bar for a side navigation rail. */
@Composable
fun shouldShowNavigationRail(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    return with(density) {
        val width = windowInfo.containerSize.width.toDp()
        val height = windowInfo.containerSize.height.toDp()
        val ratio = if (width.value == 0f) 1f else height / width
        width >= 840.dp || (width >= 600.dp && ratio < 1.2f)
    }
}
