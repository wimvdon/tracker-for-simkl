package to.wim.watchlist.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import to.wim.watchlist.CrashReporter
import to.wim.watchlist.data.model.MediaType

class MainActivity : ComponentActivity() {
    /** A title requested through an intent (notification, simkl.com link), consumed once shown. */
    private val requested = mutableStateOf<Route.Detail?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) requested.value = detailFrom(intent)
        if (!CrashReporter.showIfCrashed(this, ::showApp)) showApp()
    }

    private fun showApp() {
        setContent {
            AppRoot(requested = requested.value, onRequestHandled = { requested.value = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requested.value = detailFrom(intent)
    }

    companion object {
        private const val EXTRA_TYPE = "to.wim.watchlist.extra.TYPE"
        private const val EXTRA_ID = "to.wim.watchlist.extra.ID"

        fun detailIntent(
            context: Context,
            type: MediaType,
            simklId: Long,
        ): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TYPE, type.name)
                .putExtra(EXTRA_ID, simklId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

        private val linkPattern = Regex("""simkl\.com/(tv|anime|movies?)/(\d+)""")

        private fun detailFrom(intent: Intent?): Route.Detail? {
            intent ?: return null
            intent.data?.toString()?.let { link ->
                val m = linkPattern.find(link) ?: return null
                val type = MediaType.fromApi(m.groupValues[1]) ?: return null
                return Route.Detail(type, m.groupValues[2].toLongOrNull() ?: return null)
            }
            val type = MediaType.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TYPE) } ?: return null
            val id = intent.getLongExtra(EXTRA_ID, 0).takeIf { it > 0 } ?: return null
            return Route.Detail(type, id)
        }
    }
}
