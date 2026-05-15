package com.sidekick.app

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.app.data.model.SaveData
import com.sidekick.app.ui.gallery.GalleryActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavePersistenceTest {

    private lateinit var app: PicPatchApp

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.saveSystem.reset()
    }

    @After
    fun teardown() {
        app.saveSystem.reset()
    }

    @Test
    fun testUnlockPersistsAfterRecreation() {
        // Unlock card ID 1 explicitly via SaveSystem
        val now = System.currentTimeMillis()
        val save = SaveData(
            firstLaunchTimestamp = now - 10000,
            lastTimerCheckTimestamp = now - 10000,
            unlockTimestamps = mutableMapOf(1 to now)
        )
        app.saveSystem.save(save)

        ActivityScenario.launch(GalleryActivity::class.java).use { scenario ->
            assertEquals(1, app.unlockManager.unlockedCount())
            
            // Recreate activity to simulate process kill and restart logic testing UI binding
            scenario.recreate()
            
            assertEquals(1, app.unlockManager.unlockedCount())
        }
    }
}
