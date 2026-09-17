package pl.lejdi.plannerkmp.core.navigation

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

/**
 * The one duration used by both the scene transition and any shared-element bounds transform.
 *
 * They have to agree. Navigation3's own default differs per platform (700ms on Android, 500ms on
 * iOS), so a hardcoded bounds transform that matched one platform left the other showing a
 * half-faded ghost of the outgoing screen after the shared element had already landed.
 */
object NavAnimation {
    const val DURATION_MILLIS: Int = 500

    /**
     * The scene transition, as a value rather than a number rebuilt at each call site.
     *
     * `NavDisplay` takes three of these — forward, pop and predictive pop — and they were written
     * out identically three times in the composition root, with a fourth copy of the duration in
     * each feature's bounds transform. Centralising the number but not the spec left three of the
     * four free to drift.
     */
    val fade: ContentTransform =
        fadeIn(tween(DURATION_MILLIS)) togetherWith fadeOut(tween(DURATION_MILLIS))

    /**
     * The matching bounds transform for a shared element.
     *
     * A `val`, so every `sharedBounds` gets the same instance. Built inline it was a new lambda on
     * every recomposition, which makes the modifier element unequal each pass and forces a node
     * update for an animation that has not changed.
     */
    @OptIn(ExperimentalSharedTransitionApi::class)
    val boundsTransform: BoundsTransform = BoundsTransform { _, _ -> tween(DURATION_MILLIS) }
}
