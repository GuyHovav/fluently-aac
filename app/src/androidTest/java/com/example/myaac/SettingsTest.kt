package com.example.myaac

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: androidx.test.rule.GrantPermissionRule = androidx.test.rule.GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Test
    fun testSettingsNavigation() {
        // Open Navigation Drawer if not visible (standard check involves checking if menu button exists)
        // Note: On tablets/wide screens, drawer might be permanent. 
        // We look for "Enter Admin Mode" directly first, if not found, try opening drawer.
        
        val adminModeText = composeRule.activity.getString(R.string.enter_admin_mode)
        
        // Try to find the button. If it's in a closed drawer, we won't find it.
        // Simple heuristic: If "MyAAC" (home title) is visible and Menu button is visible, click Menu.
        val menuContentDescription = composeRule.activity.getString(R.string.menu)
        val menuNode = composeRule.onAllNodesWithContentDescription(menuContentDescription).onFirst()
        
        if (menuNode.isDisplayed()) {
            menuNode.performClick()
            composeRule.waitForIdle()
        }

        // 1. Enter Admin Mode
        composeRule.onNodeWithText(adminModeText).performClick()
        
        // 2. Enter PIN
        val pinLabel = composeRule.activity.getString(R.string.pin)
        composeRule.onNodeWithText(pinLabel).performTextInput("1234")
        
        val unlockText = composeRule.activity.getString(R.string.unlock)
        composeRule.onNodeWithText(unlockText).performClick()
        composeRule.waitForIdle()

        // 3. Click Settings
        val settingsText = composeRule.activity.getString(R.string.settings)
        composeRule.onNodeWithText(settingsText).performClick()
        composeRule.waitForIdle()

        // 4. Verify Settings Screen
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Account").assertIsDisplayed()
        composeRule.onNodeWithText("Display").assertIsDisplayed()
    }

    @Test
    fun testDisplaySettingsInteraction() {
        // Navigate to Settings first
        navigateToSettings()

        // Toggle "Show Horizontal Navigation"
        val horizontalNavTag = "horizontal_nav_checkbox"
        composeRule.onNodeWithTag(horizontalNavTag).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun navigateToSettings() {
        val adminModeText = composeRule.activity.getString(R.string.enter_admin_mode)
        val menuContentDescription = composeRule.activity.getString(R.string.menu)
        val menuNode = composeRule.onAllNodesWithContentDescription(menuContentDescription).onFirst()
        
        if (menuNode.isDisplayed()) {
            menuNode.performClick()
            composeRule.waitForIdle()
        }

        // Check if we are already unlocked (Settings button visible)
        val settingsText = composeRule.activity.getString(R.string.settings)
        val settingsNode = composeRule.onAllNodesWithText(settingsText).onFirst()
        
        if (settingsNode.isDisplayed()) {
            settingsNode.performClick()
        } else {
            // Unlock first
            composeRule.onNodeWithText(adminModeText).performClick()
            val pinLabel = composeRule.activity.getString(R.string.pin)
            composeRule.onNodeWithText(pinLabel).performTextInput("1234")
            val unlockText = composeRule.activity.getString(R.string.unlock)
            composeRule.onNodeWithText(unlockText).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText(settingsText).performClick()
        }
        composeRule.waitForIdle()
    }
}
