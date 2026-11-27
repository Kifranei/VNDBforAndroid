package app.vndb.ui.nav

sealed class AppRoute {
    data object Discover : AppRoute()
    data object Search : AppRoute()
    data object Library : AppRoute()
    data object Settings : AppRoute()
    data object About : AppRoute()
    data class Vn(val id: String) : AppRoute()
    data class Character(val id: String) : AppRoute()
    data class Producer(val id: String) : AppRoute()
    data class Staff(val id: String) : AppRoute()
    data class Tag(val id: String) : AppRoute()
    data class Gallery(val urls: List<String>, val start: Int) : AppRoute()
    data class BrowseVn(val title: String, val mode: BrowseMode, val targetId: String) : AppRoute()
}

enum class BrowseMode { Developer, Staff, Tag, TopRated, Recent, Popular }

enum class MainTab(val label: String) {
    Discover("发现"),
    Search("搜索"),
    Library("收藏"),
    Settings("设置"),
}

fun MainTab.toRoute(): AppRoute = when (this) {
    MainTab.Discover -> AppRoute.Discover
    MainTab.Search -> AppRoute.Search
    MainTab.Library -> AppRoute.Library
    MainTab.Settings -> AppRoute.Settings
}

fun AppRoute.asMainTab(): MainTab? = when (this) {
    AppRoute.Discover -> MainTab.Discover
    AppRoute.Search -> MainTab.Search
    AppRoute.Library -> MainTab.Library
    AppRoute.Settings -> MainTab.Settings
    else -> null
}
