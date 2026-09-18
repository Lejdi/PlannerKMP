package pl.lejdi.plannerkmp.feature.gym.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeightTextTest {

    @Test
    fun aPlainNumberParses() = assertEquals(80.0, "80".toWeightOrNull())

    @Test
    fun aDecimalPointParses() = assertEquals(82.5, "82.5".toWeightOrNull())

    /** The decimal key on a Polish keyboard types a comma. */
    @Test
    fun aDecimalCommaParses() = assertEquals(82.5, "82,5".toWeightOrNull())

    @Test
    fun surroundingSpaceIsIgnored() = assertEquals(80.0, "  80 ".toWeightOrNull())

    @Test
    fun textDoesNotParse() = assertNull("heavy".toWeightOrNull())

    @Test
    fun aBlankFieldDoesNotParse() = assertNull("   ".toWeightOrNull())

    @Test
    fun aWholeNumberIsShownWithoutDecimals() = assertEquals("80", 80.0.toWeightText())

    @Test
    fun oneDecimalIsKept() = assertEquals("82.5", 82.5.toWeightText())

    @Test
    fun aTrailingZeroIsDropped() = assertEquals("82.5", 82.50.toWeightText())

    @Test
    fun aLeadingZeroInTheFractionIsKept() = assertEquals("0.05", 0.05.toWeightText())

    @Test
    fun moreThanTwoDecimalsAreRounded() = assertEquals("82.57", 82.566.toWeightText())

    @Test
    fun noWeightIsShownAsNothing() = assertEquals("", null.toWeightText())

    /** What the row shows for a bodyweight exercise the user set to zero rather than cleared. */
    @Test
    fun zeroIsShownAsZero() = assertEquals("0", 0.0.toWeightText())

    @Test
    fun aRoundTripThroughBothKeepsTheValue() {
        assertEquals(82.5, 82.5.toWeightText().toWeightOrNull())
        assertEquals(80.0, 80.0.toWeightText().toWeightOrNull())
    }
}
