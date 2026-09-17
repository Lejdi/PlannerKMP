package pl.lejdi.plannerkmp.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * The app's type scale.
 *
 * [PlannerTheme] used to pass only a `colorScheme`, so `MaterialTheme.typography` was the library
 * default and every screen that wanted emphasis reached for
 * `LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)` instead — in three files, for three
 * different roles (a day heading, a task title, a grocery item name). Naming the roles here is what
 * lets those call sites ask for the role rather than for the weight.
 */
internal val plannerTypography: Typography = Typography().let { base ->
    base.copy(
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

/**
 * The style for the name of a thing in a list — a task title, a grocery item.
 *
 * An extension on [Typography] rather than a loose `val`, so it is reached the same way every other
 * text style is and cannot drift out of the theme.
 */
val Typography.itemTitle: TextStyle get() = titleMedium

/** The style for a date heading above a column of tasks. */
val Typography.dayHeading: TextStyle get() = titleSmall
