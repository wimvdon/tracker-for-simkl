package to.wim.watchlist.data

import android.content.res.Resources
import to.wim.watchlist.R
import to.wim.watchlist.data.model.UserError

/** The message a screen shows for a [UserError], in the user's language. */
fun UserError.message(resources: Resources): String =
    when (this) {
        UserError.Offline -> resources.getString(R.string.data_error_offline)
        UserError.SignedOut -> resources.getString(R.string.data_error_login_expired)
        UserError.NotFound -> resources.getString(R.string.data_error_not_found)
        UserError.Busy -> resources.getString(R.string.data_error_busy)
        is UserError.ServerDown -> resources.getString(R.string.data_error_server, code)
        is UserError.Http -> resources.getString(R.string.data_error_http, code)
        is UserError.Unexpected ->
            detail?.let { resources.getString(R.string.data_error_unexpected_detail, it) }
                ?: resources.getString(R.string.data_error_unexpected)
    }

/** What to tell the user about a failed request (see [UserError]). */
fun Throwable.userMessage(resources: Resources): String = UserError.of(this).message(resources)
