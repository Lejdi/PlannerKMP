package pl.lejdi.plannerkmp.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's spacing scale.
 *
 * Every screen used to write its own `dp` literals — `4`, `8`, `12`, `16`, `24`, `32`, `40` and a
 * few one-off widths — so "the padding inside a card" was a number repeated in three files with
 * nothing tying the copies together. A scale means a spacing decision is made once and named, and
 * that a screen which wants something off-scale has to say so explicitly rather than by typing a
 * different number.
 *
 * A plain object rather than a `CompositionLocal`: these values do not vary by theme, and paying
 * for a local read at every call site buys nothing. The moment they do need to vary — a compact
 * layout, say — this becomes the default of a local without any call site changing.
 */
object Spacing {
    /** Hairline gaps: between a label and the value it belongs to. */
    val xs: Dp = 4.dp

    /** The default gap between two related controls. */
    val sm: Dp = 8.dp

    /** Padding inside a card or row. */
    val md: Dp = 12.dp

    /** Screen-edge padding, and the gap between unrelated blocks. */
    val lg: Dp = 16.dp

    /** Generous separation: above an empty-state message, below a section. */
    val xl: Dp = 24.dp

    /** Page-level inset, e.g. the dashboard pager's peek margin. */
    val xxl: Dp = 32.dp

    /** The indent that lines a nested control up under its parent radio button. */
    val nestedIndent: Dp = 40.dp
}

/** Fixed control widths that several screens share. */
object Sizing {
    /** A tap-to-open date field. Wide enough for `dd-mm-yyyy` at the default type scale. */
    val dateFieldWidth: Dp = 150.dp

    /** A tap-to-open time field. */
    val timeFieldWidth: Dp = 100.dp

    /** The days-interval entry box, sized for two or three digits. */
    val intervalFieldWidth: Dp = 64.dp

    /** Minimum height of an inline editor row, so it does not jump as lines are added. */
    val inlineEditorMinHeight: Dp = 84.dp

    /**
     * Bottom padding a scrolling list needs to clear a floating action button.
     *
     * `Scaffold` positions its FAB over the content and reserves no room for it, so a list without
     * this leaves its last item permanently half-covered.
     */
    val fabClearance: Dp = 88.dp

    /**
     * The minimum touch target Material asks for.
     *
     * Named because two controls here have to ask for it explicitly: `RadioButton(onClick = null)`
     * skips `minimumInteractiveComponentSize()` (the selectable parent owns the click), and
     * `TextFieldDefaults.DecorationBox` applies none of the height the `TextField` composable does.
     */
    val minimumTouchTarget: Dp = 48.dp

    /** The width of a card inside a list, leaving a margin either side. */
    const val CARD_WIDTH_FRACTION: Float = 0.9f
}
