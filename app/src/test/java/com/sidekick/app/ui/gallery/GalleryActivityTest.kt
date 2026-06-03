package com.sidekick.app.ui.gallery

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.sidekick.app.R
import com.sidekick.app.PicPatchApp
import com.sidekick.app.data.model.CardData
import com.sidekick.app.data.model.CardType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(application = PicPatchApp::class)
class GalleryActivityTest {

    private lateinit var app: PicPatchApp

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        // Reset the save state to ensure a clean start
        app.saveSystem.reset()
        
        // Wait, since we are using real CardRepository (it parses the real assets), 
        // we can just use the real repository and save system logic.
    }

    @Test
    fun `gallery displays all cards dynamically`() {
        ActivityScenario.launch(GalleryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val recyclerView = activity.findViewById<RecyclerView>(R.id.rv_gallery)
                assertNotNull(recyclerView)
                
                val adapter = recyclerView.adapter as GalleryAdapter
                assertEquals(app.cardRepository.cards.size, adapter.itemCount)
            }
        }
    }

    @Test
    fun `clicking locked card shows toast`() {
        ActivityScenario.launch(GalleryActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val recyclerView = activity.findViewById<RecyclerView>(R.id.rv_gallery)
                val adapter = recyclerView.adapter as GalleryAdapter
                
                // Assuming first card is TIMER and locked initially (since firstLaunch is just checked, or not checked)
                // Let's explicitly check the state
                val firstCardState = adapter.currentList[0]
                assertFalse(firstCardState.isUnlocked)
                
                // Simulate click on locked card
                val viewHolder = adapter.createViewHolder(recyclerView, adapter.getItemViewType(0))
                adapter.bindViewHolder(viewHolder, 0)
                viewHolder.itemView.performClick()
                
                val toast = ShadowToast.getTextOfLatestToast()
                assertNotNull("Toast should be shown for locked card", toast)
                assertTrue(toast.contains("svelerà"))
            }
        }
    }
}
