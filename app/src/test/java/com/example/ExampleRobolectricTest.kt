package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.ProxyDatabase
import com.example.data.ProxyProfileRepository
import com.example.proxy.ConfigShareCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    fun `navigate across tabs, verify caution note, empty default configs, and python password lock`() {
        composeTestRule.setContent {
            PulseProxyAppRoot()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("caution_note_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_theme_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_lang_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vpn_mode_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("proxy_mode_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_toggle_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_add_config_button").performScrollTo().assertIsDisplayed()

        // Navigate to Configs tab (no default config seeded)
        composeTestRule.onNodeWithTag("nav_profiles").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("caution_note_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_configs_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_clipboard_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_qr_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_profile_fab").assertIsDisplayed()

        // Navigate to Settings & Logs tab and test Python password gate
        composeTestRule.onNodeWithTag("nav_logs").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("caution_note_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_python_studio_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("python_password_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("python_password_input").performTextInput("amirali")
        composeTestRule.onNodeWithTag("python_password_confirm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("caution_note_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("python_studio_list").performScrollToIndex(2)
        composeTestRule.onNodeWithTag("python_code_editor").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `user imported configs persist across restarts even with custom worker host`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = ProxyDatabase.getInstance(context)
        val repo = ProxyProfileRepository(db.profileDao())

        val imported = ConfigShareCodec.decodeFromText(
            "amir://cl2?uuid=63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd&url=wss%3A%2F%2Ftwilight-dream-9e3c.aliahmadiphotoofficial.workers.dev%2F&cleanIp=172.67.70.244&name=My+Worker"
        ).getOrThrow().copy(isDefault = false)

        repo.saveProfile(imported)
        repo.purgeLegacyDefaultConfigs()

        val saved = repo.profiles.first()
        assertEquals(1, saved.size)
        assertEquals("My Worker", saved.first().name)
        assertEquals("63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd", saved.first().uuid)
        repo.deleteProfile(saved.first())
    }
}
