package com.sidekick.app.data.repository

import android.content.res.AssetManager
import com.sidekick.app.data.model.CardType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class CardRepositoryTest {

    private lateinit var assetManager: AssetManager
    private lateinit var repository: CardRepository

    @Before
    fun setup() {
        assetManager = mockk()
        
        val json = """
            {
              "cards": [
                {
                  "id": 1,
                  "type": "TIMER",
                  "title": "Title 1",
                  "dedication": "Dedication 1",
                  "photoPath": "cards/card_01/photo.jpg"
                },
                {
                  "id": 15,
                  "type": "GPS",
                  "title": "Special Location",
                  "dedication": "We met here",
                  "photoPath": "cards/card_15/photo.jpg",
                  "gpsLat": 45.0,
                  "gpsLon": 9.0,
                  "gpsRadiusMeters": 100.0
                }
              ]
            }
        """.trimIndent()
        
        every { assetManager.open("cards_config.json") } returns ByteArrayInputStream(json.toByteArray())
        
        repository = CardRepository(context = null, assetManager = assetManager)
    }

    @Test
    fun `cards are parsed correctly`() {
        val cards = repository.cards
        assertEquals(2, cards.size)
        
        val first = cards[0]
        assertEquals(1, first.id)
        assertEquals(CardType.TIMER, first.type)
        assertEquals("Title 1", first.title)
        assertNull(first.gpsLat)
        
        val second = cards[1]
        assertEquals(15, second.id)
        assertEquals(CardType.GPS, second.type)
        assertEquals(45.0, second.gpsLat)
        assertEquals(9.0, second.gpsLon)
        assertEquals(100.0, second.gpsRadiusMeters, 0.001)
    }

    @Test
    fun `timerCards filters correctly`() {
        val timerCards = repository.timerCards
        assertEquals(1, timerCards.size)
        assertEquals(1, timerCards.first().id)
    }

    @Test
    fun `getById returns correct card or null`() {
        assertNotNull(repository.getById(15))
        assertNull(repository.getById(99))
    }
}
