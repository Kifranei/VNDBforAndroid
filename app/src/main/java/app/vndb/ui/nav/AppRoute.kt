package app.vndb.ui.nav

import java.util.UUID
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed class AppRoute : NavKey {
    @Serializable
    data object Discover : AppRoute()

    @Serializable
    data object Library : AppRoute()

    @Serializable
    data object Settings : AppRoute()

    @Serializable
    data object About : AppRoute()

    @Serializable
    data class Vn(val id: String, val instanceId: String = UUID.randomUUID().toString()) : AppRoute()

    @Serializable
    data class Character(val id: String, val instanceId: String = UUID.randomUUID().toString()) : AppRoute()

    @Serializable
    data class Producer(val id: String, val instanceId: String = UUID.randomUUID().toString()) : AppRoute()

    @Serializable
    data class Staff(val id: String, val instanceId: String = UUID.randomUUID().toString()) : AppRoute()

    @Serializable
    data class Tag(val id: String, val instanceId: String = UUID.randomUUID().toString()) : AppRoute()

    @Serializable
    data class Gallery(
        val urls: List<String>,
        val start: Int,
        val instanceId: String = UUID.randomUUID().toString(),
    ) : AppRoute()

    @Serializable
    data class BrowseVn(
        val title: String,
        val mode: BrowseMode,
        val targetId: String,
        val instanceId: String = UUID.randomUUID().toString(),
    ) : AppRoute()
}

enum class BrowseMode { Developer, Staff, Tag, TopRated, Recent, Popular }

enum class MainTab(val label: String) {
    Discover("发现"),
    Library("资料库"),
    Settings("设置"),
}

fun MainTab.toRoute(): AppRoute = when (this) {
    MainTab.Discover -> AppRoute.Discover
    MainTab.Library -> AppRoute.Library
    MainTab.Settings -> AppRoute.Settings
}

fun AppRoute.asMainTab(): MainTab? = when (this) {
    AppRoute.Discover -> MainTab.Discover
    AppRoute.Library -> MainTab.Library
    AppRoute.Settings -> MainTab.Settings
    else -> null
}
