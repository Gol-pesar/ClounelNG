package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app name matches Clounel`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Clounel", appName)
    }

    @Test
    fun `navigate across tunnel, configs with QR and clipboard import, and settings`() {
        composeTestRule.setContent {
            PulseProxyAppRoot()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top_theme_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_lang_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vpn_mode_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("proxy_mode_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_toggle_button").assertIsDisplayed()

        // Navigate to Configs tab (Python tab is hidden from main nav)
        composeTestRule.onNodeWithTag("nav_profiles").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("import_clipboard_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_qr_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_profile_fab").assertIsDisplayed()

        // Navigate to Settings & Logs tab
        composeTestRule.onNodeWithTag("nav_logs").performClick()
        composeTestRule.waitForIdle()
    }
}
