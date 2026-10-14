package codeshield.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartsTest {

    @Test
    fun barFractionsNormalizesByMax() {
        assertEquals(listOf(0.25f, 0.5f, 1f), barFractions(listOf(1, 2, 4)))
    }

    @Test
    fun barFractionsHandlesEmptyAndZeros() {
        assertTrue(barFractions(emptyList()).isEmpty())
        assertEquals(listOf(0f, 0f), barFractions(listOf(0, 0)))
    }

    @Test
    fun sparkPointsHandlesEmptyAndSingle() {
        assertTrue(sparkPoints(emptyList(), 100f, 40f).isEmpty())
        assertTrue(sparkPoints(listOf(1f), 0f, 40f).isEmpty())
        val single = sparkPoints(listOf(5f), 100f, 40f)
        assertEquals(1, single.size)
        assertEquals(50f, single[0].x)
        assertEquals(20f, single[0].y)
    }

    @Test
    fun sparkPointsStaysWithinBounds() {
        val pts = sparkPoints(listOf(3f, 1f, 4f, 1f, 5f), 120f, 50f)
        assertEquals(5, pts.size)
        assertEquals(0f, pts.first().x)
        assertEquals(120f, pts.last().x)
        for (p in pts) {
            assertTrue(p.x in 0f..120f, "x=$p")
            assertTrue(p.y in 0f..50f, "y=$p")
        }
    }

    @Test
    fun equalValuesDrawMidLine() {
        val pts = sparkPoints(listOf(2f, 2f, 2f), 90f, 30f)
        for (p in pts) assertEquals(15f, p.y)
    }
}
