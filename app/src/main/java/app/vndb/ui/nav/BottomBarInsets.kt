package app.vndb.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalBottomBarClearance = compositionLocalOf { 0.dp }

@Composable
fun tabContentWindowInsets(): WindowInsets =
    WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

fun extraBottomPadding(scaffoldBottom: Dp, clearance: Dp): Dp =
    scaffoldBottom + clearance

/** Side part of a Scaffold padding, so landscape content clears the display cutout and side navigation bar. */
@Composable
fun PaddingValues.sides(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(start = calculateStartPadding(direction), end = calculateEndPadding(direction))
}
