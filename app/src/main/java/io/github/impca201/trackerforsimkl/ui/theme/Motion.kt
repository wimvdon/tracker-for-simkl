package io.github.impca201.trackerforsimkl.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/** Material 3 motion tokens (easing and duration), shared by the app's transitions. */
object Motion {
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val SHORT_4 = 200
    const val MEDIUM_2 = 300
    const val MEDIUM_4 = 400
    const val LONG_2 = 500

    /** Fade-through for state switches such as loading, content and error. */
    fun <T> fadeSpec(
        durationMillis: Int = MEDIUM_2,
        delayMillis: Int = 0,
    ): FiniteAnimationSpec<T> = tween(durationMillis, delayMillis, Standard)

    /** Progress bars and other values that move to a new state. */
    fun <T> progressSpec(): FiniteAnimationSpec<T> = tween(LONG_2, easing = Emphasized)
}
