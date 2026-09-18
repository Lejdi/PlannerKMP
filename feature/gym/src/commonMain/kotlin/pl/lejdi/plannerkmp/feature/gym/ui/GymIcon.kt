package pl.lejdi.plannerkmp.feature.gym.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A dumbbell, vendored.
 *
 * `material-icons-core` is the only icon set on this build's classpath and ships 49 glyphs, none of
 * which is a dumbbell or anything close enough to read as one — CLAUDE.md's rule for that case is
 * to vendor the icon rather than to add `material-icons-extended`, which would put several
 * thousand unused vectors into every binary.
 *
 * The path data is Material Symbols' own "fitness center" outline, on the same 24dp/24-unit grid
 * every generated icon in that library uses, so it sits at the same optical weight as the three
 * tabs beside it.
 *
 * Built once and cached, as the generated icons are: an `ImageVector` is immutable and rebuilding
 * it on every recomposition of the bottom bar buys nothing.
 */
val GymIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "FitnessCenter",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20.57f, 14.86f)
            lineTo(22f, 13.43f)
            lineTo(20.57f, 12f)
            lineTo(17f, 15.57f)
            lineTo(8.43f, 7f)
            lineTo(12f, 3.43f)
            lineTo(10.57f, 2f)
            lineTo(9.14f, 3.43f)
            lineTo(7.71f, 2f)
            lineTo(5.57f, 4.14f)
            lineTo(4.14f, 2.71f)
            lineTo(2.71f, 4.14f)
            lineTo(4.14f, 5.57f)
            lineTo(2f, 7.71f)
            lineTo(3.43f, 9.14f)
            lineTo(2f, 10.57f)
            lineTo(3.43f, 12f)
            lineTo(7f, 8.43f)
            lineTo(15.57f, 17f)
            lineTo(12f, 20.57f)
            lineTo(13.43f, 22f)
            lineTo(14.86f, 20.57f)
            lineTo(16.29f, 22f)
            lineTo(18.43f, 19.86f)
            lineTo(19.86f, 21.29f)
            lineTo(21.29f, 19.86f)
            lineTo(19.86f, 18.43f)
            lineTo(22f, 16.29f)
            close()
        }
    }.build()
}
