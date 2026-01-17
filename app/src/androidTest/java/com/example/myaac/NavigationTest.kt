package com.example.myaac

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    private val permissionRule = androidx.test.rule.GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: org.junit.rules.RuleChain = org.junit.rules.RuleChain
        .outerRule(permissionRule)
        .around(composeRule)

    @Test
    fun navigateThroughDrawer() {
        // Wait for content to stabilize and Splash Screen to dismiss
        // Simple sleep to allow async initialization
        Thread.sleep(5000)
        composeRule.waitForIdle()

        // Get strings from resources to match correctly
        val homeBoardResult = composeRule.activity.getString(R.string.board_home_name)
        val iWantBoardResult = composeRule.activity.getString(R.string.board_i_want_name)
        val menuDescription = composeRule.activity.getString(R.string.menu)

        // 1. Initial State: Should be on Home Board
        // Check TopAppBar title (use onFirst since "Home Board" might appear in drawer too)
        composeRule.onAllNodesWithText(homeBoardResult).onFirst().assertIsDisplayed()

        // 2. Open Drawer and navigate to "I Want"
        composeRule.onNodeWithContentDescription(menuDescription).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(iWantBoardResult).onFirst().performClick()
        Thread.sleep(1500) // Wait for drawer to close and navigation to complete
        composeRule.waitForIdle()
        
        // 3. Navigate back to Home using Drawer
        composeRule.onNodeWithContentDescription(menuDescription).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(homeBoardResult).onFirst().performClick()
        Thread.sleep(1500) // Wait for drawer to close and navigation to complete
        
        // 4. Verify back on Home
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(homeBoardResult).onFirst().assertIsDisplayed()
    }
}
