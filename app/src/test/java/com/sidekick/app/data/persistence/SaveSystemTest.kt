package com.sidekick.app.data.persistence

import android.content.Context
import com.sidekick.app.data.model.SaveData
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SaveSystemTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var saveSystem: SaveSystem

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("savesystem_test").toFile()
        mockContext = mockk()
        every { mockContext.filesDir } returns tempDir
        
        saveSystem = SaveSystem(mockContext)
        // Ensure clean state before each test
        saveSystem.reset()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `load returns default SaveData when file is missing`() {
        val save = saveSystem.load()
        assertEquals(-1L, save.firstLaunchTimestamp)
        assertTrue(save.unlockTimestamps.isEmpty())
        assertFalse(saveSystem.hasSaveFile())
    }

    @Test
    fun `save and load serialize and deserialize correctly`() {
        val original = SaveData(
            firstLaunchTimestamp = 12345L,
            lastTimerCheckTimestamp = 54321L,
            unlockedCardIds = mutableSetOf(1, 5),
            unlockTimestamps = mutableMapOf(1 to 1000L, 5 to 2000L)
        )
        
        saveSystem.save(original)
        assertTrue(saveSystem.hasSaveFile())
        
        val loaded = saveSystem.load()
        assertEquals(12345L, loaded.firstLaunchTimestamp)
        assertEquals(54321L, loaded.lastTimerCheckTimestamp)
        assertEquals(2, loaded.unlockedCount)
        assertTrue(loaded.isUnlocked(1))
        assertTrue(loaded.isUnlocked(5))
        assertFalse(loaded.isUnlocked(2))
    }

    @Test
    fun `load recovers from corrupt JSON by returning clean state`() {
        val file = File(tempDir, "save_data.json")
        file.writeText("{ invalid_json ]")
        
        val save = saveSystem.load()
        assertEquals(-1L, save.firstLaunchTimestamp)
        assertTrue(save.unlockTimestamps.isEmpty())
    }

    @Test
    fun `reset deletes the save file`() {
        saveSystem.save(SaveData(firstLaunchTimestamp = 1000L))
        assertTrue(saveSystem.hasSaveFile())
        
        saveSystem.reset()
        assertFalse(saveSystem.hasSaveFile())
        
        val loaded = saveSystem.load()
        assertEquals(-1L, loaded.firstLaunchTimestamp)
    }
}
