package com.fieldreport.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CargoBoxTest {
    private val s = cargoBoxScale(1000, 1000, 1000)

    @Test fun draggingAHandleAlongItsAxisChangesThatSizeIn50mmSteps() {
        // Height handle: straight up grows, straight down shrinks.
        assertTrue(dragCargoSize('h', 1000, 0f, -40f, s) > 1000); assertTrue(dragCargoSize('h', 1000, 0f, 40f, s) < 1000)
        // Width handle runs up-right, depth handle up-left.
        assertTrue(dragCargoSize('w', 1000, 40f, -23f, s) > 1000); assertTrue(dragCargoSize('d', 1000, -40f, -23f, s) > 1000)
        assertEquals(0, dragCargoSize('w', 1000, 37f, -11f, s) % 50)
    }

    @Test fun sizeStaysWithinLimitsAndNoMoveMeansNoChange() {
        assertEquals(1000, dragCargoSize('h', 1000, 0f, 0f, s))
        assertEquals(50, dragCargoSize('h', 1000, 0f, 5000f, s)); assertEquals(12000, dragCargoSize('h', 1000, 0f, -50000f, s))
    }

    @Test fun aBiggerBoxIsDrawnAtASmallerScaleSoItStillFits() = assertTrue(cargoBoxScale(4000, 2000, 2500) < s)
}
