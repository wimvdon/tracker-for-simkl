package io.github.impca201.simklr

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * Saves the stack trace of a crash, and shows it (with a Copy button) the next time the app opens.
 * Uses plain views, so it works even when the crash is in Compose.
 */
object CrashReporter {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(context).writeText(
                    "SIMKLr ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}) · Android ${Build.VERSION.RELEASE} " +
                        "(API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Thread: ${thread.name}\n\n" + error.stackTraceToString(),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Shows the saved crash and returns true, or returns false when there is none. */
    fun showIfCrashed(
        activity: Activity,
        onContinue: () -> Unit,
    ): Boolean {
        val file = file(activity)
        val report = runCatching { file.readText() }.getOrNull() ?: return false
        file.delete()
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val text =
            TextView(activity).apply {
                this.text = report
                typeface = Typeface.MONOSPACE
                textSize = 11f
                setTextIsSelectable(true)
            }
        val copy =
            Button(activity).apply {
                this.text = activity.getString(R.string.data_crash_copy)
                setOnClickListener {
                    activity.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("SIMKLr crash", report))
                }
            }
        val next = Button(activity).apply { this.text = activity.getString(R.string.data_crash_continue); setOnClickListener { onContinue() } }
        val title = TextView(activity).apply { this.text = activity.getString(R.string.data_crash_title); textSize = 20f }
        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding * 3, padding, padding * 3)
                addView(title)
                addView(copy)
                addView(next)
                addView(text)
            }
        activity.setContentView(ScrollView(activity).apply { addView(content) })
        return true
    }
}
