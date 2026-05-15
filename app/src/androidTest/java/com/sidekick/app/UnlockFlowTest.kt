package com.sidekick.app

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.app.data.model.SaveData
import com.sidekick.app.ui.gallery.GalleryActivity
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnlockFlowTest {

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
    fun testUnlockFlow() {
        // Simula 25 ore passate dall'ultimo sblocco
        val pastTimestamp = System.currentTimeMillis() - (25L * 60 * 60 * 1000)
        app.saveSystem.save(SaveData(
            firstLaunchTimestamp = pastTimestamp,
            lastTimerCheckTimestamp = pastTimestamp
        ))

        ActivityScenario.launch(GalleryActivity::class.java).use { scenario ->
            // La UI si ricarica. Il pulsante dovrebbe essere abilitato.
            onView(withId(R.id.btn_draw_card)).check(matches(isEnabled()))

            // Clicchiamo per sbloccare
            onView(withId(R.id.btn_draw_card)).perform(click())

            // Dopo aver sbloccato, il timer viene resettato, quindi il pulsante si disabilita
            onView(withId(R.id.btn_draw_card)).check(matches(not(isEnabled())))
        }
    }
}
