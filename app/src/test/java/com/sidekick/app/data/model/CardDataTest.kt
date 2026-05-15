package com.sidekick.app.data.model

import org.junit.Assert.*
import org.junit.Test

class CardDataTest {

    @Test
    fun `CardData fields default correctly`() {
        val card = CardData(
            id = 1,
            type = CardType.TIMER,
            title = "Test",
            dedication = "Test Dedication",
            photoPath = "path/to/photo.jpg"
        )
        
        assertEquals(50.0, card.gpsRadiusMeters, 0.001)
        assertNull(card.gpsLat)
        assertNull(card.gpsLon)
        assertNull(card.markerPath)
    }

    @Test
    fun `CardType enum coverage`() {
        val types = CardType.values()
        assertEquals(4, types.size)
        assertTrue(types.contains(CardType.TIMER))
        assertTrue(types.contains(CardType.GPS))
        assertTrue(types.contains(CardType.IMAGE_MARKER))
        assertTrue(types.contains(CardType.HYBRID))
    }
}
