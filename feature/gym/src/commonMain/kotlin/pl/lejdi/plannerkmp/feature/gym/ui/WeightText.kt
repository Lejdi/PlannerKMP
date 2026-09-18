package pl.lejdi.plannerkmp.feature.gym.ui

import kotlin.math.abs
import kotlin.math.round

/** Two decimals is plenty for a plate loaded on a bar, and more only reads as noise. */
private const val WEIGHT_DECIMALS_SCALE = 100.0
private const val WEIGHT_DECIMALS_SCALE_L = 100L

/**
 * The weight a user typed, or null when the field holds no number.
 *
 * A comma counts as a decimal separator. The inline editor is a decimal keypad, and on a Polish
 * layout its separator key types `,` — so without this the field rejected the number the user had
 * just typed with nothing on screen explaining why.
 *
 * Blank and unparseable both come back null on purpose, and the caller tells them apart by looking
 * at the text: an empty field means "no weight, this is a bodyweight exercise", while "heavy" means
 * "reject this edit". Collapsing the two here would have made one of them impossible to express.
 */
fun String.toWeightOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

/**
 * The weight as the list row and the form field show it: `80`, `82.5`, `82.57`, and `""` for none.
 *
 * Trailing zeros are dropped, because `80.0 kg` reads like a precision that was not measured.
 * Built from arithmetic rather than a platform formatter: `NumberFormat` is not in `commonMain`,
 * and `toString()` on a `Double` gives `80.0` and `82.566` — neither of which is wanted.
 */
fun Double?.toWeightText(): String {
    val value = this ?: return ""
    val scaled = round(abs(value) * WEIGHT_DECIMALS_SCALE).toLong()
    val whole = scaled / WEIGHT_DECIMALS_SCALE_L
    val hundredths = scaled % WEIGHT_DECIMALS_SCALE_L
    // Kept even though validation rejects a negative weight: a formatter that silently drops a
    // minus sign is the kind of thing that survives a later change to those bounds.
    val sign = if (value < 0) "-" else ""
    if (hundredths == 0L) return "$sign$whole"
    return "$sign$whole.${hundredths.toString().padStart(2, '0').trimEnd('0')}"
}
