package com.fieldreport.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Ported from the source repository's geometry tests so the editor behaves the same here. */
class GeometryTest {
    private val box = Annotation("1", 1, KIND_RECTANGLE, "#ed4d4d", .2, .2, .5, .6)
    private fun near(a: Double, b: Double) = assertTrue("$a != $b", abs(a - b) < 1e-9)

    @Test fun pinchKeepsShapeWhenFingersLandThenScalesByGapRatio() {
        for (kind in listOf(KIND_CIRCLE, KIND_RECTANGLE, KIND_ARROW)) {
            val shape = box.copy(kind = kind); val state = TwoPointState()
            assertEquals(shape, pinchFromTouches(shape, listOf(TouchPoint(1, .0, .1), TouchPoint(2, 1.0, .9)), state, 800.0, 600.0))
            val smaller = pinchFromTouches(shape, listOf(TouchPoint(2, .9, .82), TouchPoint(1, .1, .18)), state, 800.0, 600.0)
            near((smaller.x2 - smaller.x1) / .3, .8); near((smaller.y2 - smaller.y1) / .4, .8); near((smaller.x1 + smaller.x2) / 2, .35)
            // One finger lifted: nothing changes, however the remaining finger moves.
            assertEquals(smaller, pinchFromTouches(smaller, listOf(TouchPoint(1, .2, .4)), state, 800.0, 600.0))
            assertTrue(state.finished)
            assertEquals(smaller, pinchFromTouches(smaller, listOf(TouchPoint(1, .7, .7), TouchPoint(3, .1, .1)), state, 800.0, 600.0))
        }
    }

    @Test fun widerGripScalesLessForSameTravel() {
        fun pinch(grip: Double): Annotation {
            val state = TwoPointState()
            pinchFromTouches(box, listOf(TouchPoint(1, .5 - grip / 2, .4), TouchPoint(2, .5 + grip / 2, .4)), state, 800.0, 600.0)
            return pinchFromTouches(box, listOf(TouchPoint(1, .5 - grip / 2 + .05, .4), TouchPoint(2, .5 + grip / 2 - .05, .4)), state, 800.0, 600.0)
        }
        val wide = pinch(.8); val narrow = pinch(.4)
        near((wide.x2 - wide.x1) / .3, .875); near((narrow.x2 - narrow.x1) / .3, .75)
    }

    @Test fun pinchAndTwoFingerMoveAreMeasuredFromTheStart() {
        val state = TwoPointState()
        pinchFromTouches(box, listOf(TouchPoint(1, .3, .3), TouchPoint(2, .5, .3)), state, 800.0, 600.0)
        val bigger = pinchFromTouches(box, listOf(TouchPoint(1, .2, .3), TouchPoint(2, .6, .3)), state, 800.0, 600.0)
        near(bigger.x2 - bigger.x1, .6)
        val moved = pinchFromTouches(bigger, listOf(TouchPoint(1, .4, .4), TouchPoint(2, .6, .4)), state, 800.0, 600.0)
        near(moved.x1, .3); near(moved.y1, .3)
    }

    @Test fun fingersOnOppositeSidesStretchOneAxisOnly() {
        val left = TouchPoint(1, .1, .4); val right = TouchPoint(2, .8, .4)
        assertEquals(PinchAxis.HORIZONTAL, pinchAxis(box, left, right))
        assertEquals(PinchAxis.VERTICAL, pinchAxis(box, TouchPoint(1, .3, .1), TouchPoint(2, .3, .9)))
        val state = TwoPointState()
        pinchFromTouches(box, listOf(left, right), state, 800.0, 600.0)
        val wider = pinchFromTouches(box, listOf(TouchPoint(1, .0, .4), TouchPoint(2, .9, .4)), state, 800.0, 600.0)
        assertTrue(wider.x2 - wider.x1 > .3 + 1e-6); near(wider.y2 - wider.y1, .4)
    }

    @Test fun longPressToleratesJitterButNotMovement() {
        assertEquals(false, movedPastHold(.3, .3, .303, .303, 800.0, 600.0))
        assertEquals(true, movedPastHold(.3, .3, .32, .3, 800.0, 600.0))
    }

    @Test fun laterShapeWinsWhenOverlapping() = assertEquals("2", hitShape(listOf(box, box.copy(id = "2")), .3, .3, 800.0, 600.0)?.id)

    @Test fun moveStaysInsidePhotoAndKeepsSize() {
        val moved = translateShape(box, 2.0, -2.0)
        assertEquals(1.0, moved.x2, 0.0); assertEquals(0.0, moved.y1, 0.0); assertTrue(abs((moved.x2 - moved.x1) - .3) < .00001)
    }

    @Test fun arrowIsSelectedOnlyNearItsLine() {
        val arrow = box.copy(kind = KIND_ARROW, x1 = .1, y1 = .1, x2 = .9, y2 = .9)
        assertNotNull(hitShape(listOf(arrow), .5, .5, 800.0, 600.0)); assertNull(hitShape(listOf(arrow), .1, .9, 800.0, 600.0))
    }

    @Test fun drawingWithTwoFingersFreezesWhenOneLifts() {
        for (kind in listOf(KIND_CIRCLE, KIND_RECTANGLE)) {
            val state = TwoPointState()
            val resized = resizeFromTouches(box.copy(kind = kind), listOf(TouchPoint(7, .1, .2), TouchPoint(9, .8, .9)), state)
            val lifted = resizeFromTouches(resized, listOf(TouchPoint(7, .4, .4)), state)
            assertEquals(resized, lifted); assertTrue(state.finished)
            assertEquals(resized, resizeFromTouches(lifted, listOf(TouchPoint(7, .5, .5), TouchPoint(10, .6, .6)), state))
        }
    }

    @Test fun eachFingerKeepsItsCornerWhateverTheTouchOrder() {
        val state = TwoPointState()
        resizeFromTouches(box, listOf(TouchPoint(7, .1, .2), TouchPoint(9, .8, .9)), state)
        val r = resizeFromTouches(box, listOf(TouchPoint(9, .7, .8), TouchPoint(7, .2, .3)), state)
        assertEquals(.2, r.x1, 0.0); assertEquals(.3, r.y1, 0.0); assertEquals(.7, r.x2, 0.0); assertEquals(.8, r.y2, 0.0)
    }

    @Test fun deletingAMiddleShapePullsLaterNumbersForward() {
        val shapes = listOf(box, box.copy(id = "2", number = 2), box.copy(id = "3", number = 3))
        assertEquals(listOf(1, 2), renumber(shapes.filter { it.id != "2" }).map { it.number })
        assertEquals(listOf("1", "3"), renumber(shapes.filter { it.id != "2" }).map { it.id })
        assertEquals(listOf(1, 2, 3), renumber(shapes).map { it.number })
    }
}
