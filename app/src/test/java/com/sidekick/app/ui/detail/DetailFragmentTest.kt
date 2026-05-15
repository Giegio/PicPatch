package com.sidekick.app.ui.detail

import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sidekick.app.R
import com.sidekick.app.PicPatchApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = PicPatchApp::class)
class DetailFragmentTest {

    @Test
    fun `DetailFragment shows AR button only for AR cards`() {
        val args = android.os.Bundle().apply { putInt("card_id", 1) }
        androidx.fragment.app.testing.FragmentScenario.launchInContainer(
            DetailFragment::class.java, 
            args, 
            R.style.Theme_PicPatch
        ).onFragment { fragment ->
            val arButton = fragment.view?.findViewById<View>(R.id.btn_view_ar)
            assertNotNull("AR button should exist in layout", arButton)
            assertEquals("AR button should be GONE for TIMER card", View.GONE, arButton!!.visibility)
        }
    }
}
