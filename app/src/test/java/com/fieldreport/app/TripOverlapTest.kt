package com.fieldreport.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripOverlapTest {
    private val h = 3_600_000L
    private val existing = listOf(Trip("a", start = 10 * h, end = 13 * h))

    @Test fun overlapInsideAcrossAndAroundIsFound() {
        assertEquals("a", overlappingTrip(existing, 11 * h, 12 * h, null)?.id)
        assertEquals("a", overlappingTrip(existing, 9 * h, 11 * h, null)?.id)
        assertEquals("a", overlappingTrip(existing, 12 * h, 15 * h, null)?.id)
        assertEquals("a", overlappingTrip(existing, 8 * h, 20 * h, null)?.id)
    }

    @Test fun backToBackTripsAreAllowed() {
        assertNull(overlappingTrip(existing, 13 * h, 15 * h, null))
        assertNull(overlappingTrip(existing, 7 * h, 10 * h, null))
    }

    @Test fun editingATripDoesNotClashWithItself() = assertNull(overlappingTrip(existing, 10 * h, 13 * h, "a"))
}
