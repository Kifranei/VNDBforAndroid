package app.vndb

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.vndb.ui.VndbApp
import app.vndb.ui.nav.AppRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = intent.toAppRoute()
        setContent {
            VndbApp(container = appContainer(), initialRoute = initial)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

private fun Intent.toAppRoute(): AppRoute? {
    val data = data ?: return null
    val host = data.host ?: return null
    if (host != "vndb.org") return null
    val path = data.path?.trim('/') ?: return null
    val id = path.substringBefore('/')
    return when {
        id.startsWith("v") -> AppRoute.Vn(id)
        id.startsWith("c") -> AppRoute.Character(id)
        id.startsWith("p") -> AppRoute.Producer(id)
        id.startsWith("s") -> AppRoute.Staff(id)
        id.startsWith("g") -> AppRoute.Tag(id)
        else -> null
    }
}
