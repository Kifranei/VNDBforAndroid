package app.vndb

import android.content.Context
import app.vndb.data.api.VndbClient
import app.vndb.data.local.PreferencesRepository
import app.vndb.data.repo.VndbRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val preferences = PreferencesRepository(context.applicationContext)
    val settings = preferences.settings.stateIn(
        scope,
        SharingStarted.Eagerly,
        app.vndb.data.local.UserSettings(),
    )
    val client = VndbClient { settings.value.apiToken.ifBlank { null } }
    val repository = VndbRepository(client)
}

class VndbApplication : android.app.Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

fun Context.appContainer(): AppContainer =
    (applicationContext as VndbApplication).container
