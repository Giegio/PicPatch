package com.sidekick.app.unlock

import android.content.Context
import com.sidekick.app.data.model.CardData
import com.sidekick.app.data.model.CardType
import com.sidekick.app.data.persistence.SaveSystem
import com.sidekick.app.data.repository.CardRepository
import com.sidekick.app.util.FakeClock
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class UnlockManagerTest {

    private lateinit var tempDir: File
    private lateinit var saveSystem: SaveSystem
    private lateinit var repository: CardRepository
    private lateinit var clock: FakeClock
    private lateinit var unlockManager: UnlockManager

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("unlock_test").toFile()
        val mockContext = mockk<Context>()
        every { mockContext.filesDir } returns tempDir
        
        saveSystem = SaveSystem(mockContext)
        saveSystem.reset()
        
        repository = mockk()
        
        // Mock a simple repository with 3 TIMER cards and 1 GPS card
        val fakeCards = listOf(
            CardData(1, CardType.TIMER, "T1", "", ""),
            CardData(2, CardType.TIMER, "T2", "", ""),
            CardData(3, CardType.TIMER, "T3", "", ""),
            CardData(4, CardType.GPS, "G1", "", "", 0.0, 0.0)
        )
        every { repository.cards } returns fakeCards
        every { repository.timerCards } returns fakeCards.filter { it.type == CardType.TIMER }
        every { repository.specialCards } returns fakeCards.filter { it.type != CardType.TIMER }
        every { repository.getById(any()) } answers { 
            val id = firstArg<Int>()
            fakeCards.firstOrNull { it.id == id }
        }

        clock = FakeClock()
        unlockManager = UnlockManager(repository, saveSystem, clock)
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `checkFirstLaunch initializes correctly and grants immediate draw`() {
        unlockManager.checkFirstLaunch()
        val save = saveSystem.load()
        assertEquals(clock.currentTimeMillis(), save.firstLaunchTimestamp)
        
        // Time to next draw should be 0 because of initialization logic (-24h)
        assertEquals(0L, unlockManager.getTimeToNextDrawMs())
        assertEquals(1, unlockManager.getAvailableDraws())
    }

    @Test
    fun `drawTimerCard unlocks a card and resets timer`() {
        unlockManager.checkFirstLaunch()
        
        val card = unlockManager.drawTimerCard()
        assertNotNull(card)
        assertEquals(CardType.TIMER, card?.type)
        
        // Timer is reset, so 0 draws available and ~24h to next draw
        assertEquals(0, unlockManager.getAvailableDraws())
        val windowMs = 24L * 60 * 60 * 1000
        assertEquals(windowMs, unlockManager.getTimeToNextDrawMs())
        
        val save = saveSystem.load()
        assertTrue(save.isUnlocked(card!!.id))
    }

    @Test
    fun `drawTimerCard returns null if time has not passed`() {
        unlockManager.checkFirstLaunch()
        unlockManager.drawTimerCard() // use the first draw
        
        clock.advanceBy(1000L) // advance 1 second
        val card = unlockManager.drawTimerCard()
        assertNull(card)
    }

    @Test
    fun `drawTimerCard returns null if pool exhausted`() {
        unlockManager.checkFirstLaunch()
        
        // Draw 3 times, advancing time each time
        for (i in 1..3) {
            assertNotNull(unlockManager.drawTimerCard())
            clock.advanceBy(24L * 60 * 60 * 1000)
        }
        
        // 4th draw should be null because all timer cards are unlocked
        assertNull(unlockManager.drawTimerCard())
        assertEquals(0, unlockManager.getAvailableDraws())
        assertEquals(0L, unlockManager.getTimeToNextDrawMs())
    }

    @Test
    fun `unlockSpecialCard unlocks card only once`() {
        assertTrue(unlockManager.unlockSpecialCard(4)) // G1
        assertFalse(unlockManager.unlockSpecialCard(4)) // already unlocked
        
        val save = saveSystem.load()
        assertTrue(save.isUnlocked(4))
    }
}
