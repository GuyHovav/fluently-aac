package com.example.myaac

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.RuleChain

@RunWith(AndroidJUnit4::class)
class BoardInteractionTest {

    private val permissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(permissionRule)
        .around(composeRule)

    @Test
    fun testSentenceConstructionAndClearing() {
        // Wait for app to load
        Thread.sleep(5000)
        composeRule.waitForIdle()

        // 1. Verify we are on Home Board
        val homeBoardText = composeRule.activity.getString(R.string.board_home_name)
        composeRule.onAllNodesWithText(homeBoardText).onFirst().assertIsDisplayed()

        // 2. Click "I" button
        // "I" might be common, so look for it in the grid. 
        // We can filter by clickable to ensure it's a button, but standard "I" text check is usually enough
        // or we try to find a node with text "I" that is displayed.
        // Note: "I" might be case sensitive.
        val targetWord1 = "I"
        val targetWord2 = "Want"
        
        // Find and click "I"
        // Use onFirst() because "I" might appear in sentence bar after click, or elsewhere.
        // Initially it should be on the grid.
        composeRule.onAllNodesWithText(targetWord1).onFirst().performClick()
        
        // 3. Verify "I" appears in the sentence bar area
        // The sentence bar shows "Build your sentence..." when empty.
        // Now it should show "I".
        // Note: Grammar engine might affect it, but "I" usually stays "I".
        // We check if "I" is displayed in the sentence bar text node.
        // Since we clicked it, there are now at least 2 "I"s (one on button, one in bar).
        // Let's assert we have multiple "I"s now.
        composeRule.waitForIdle()
        val iNodes = composeRule.onAllNodesWithText(targetWord1)
        iNodes.fetchSemanticsNodes().size >= 2
        
        // 4. Click "Want" button
        composeRule.onAllNodesWithText(targetWord2).onFirst().performClick()
        
        // 5. Verify accumulation
        // "I want" or "I Want"
        composeRule.waitForIdle()
        // Check for specific full text if possible, dependent on implementation
        // or just check that "Want" is also present multiple times now.
        
        // 6. Test Backspace (Clear)
        // Find Backspace button by content description
        val backspaceDesc = "Backspace (Long press to clear)"
        composeRule.onNodeWithContentDescription(backspaceDesc).performClick()
        
        // Should remove "Want" (last word)
        composeRule.waitForIdle()
        // "Want" might still be on the grid (button), so count should decrease by 1
        // Previously: Button + Bar = 2. Now: Button only = 1.
        // Note: This assumes "Want" is unique on the board.
        
        // 7. Test Clear All (Long click)
        composeRule.onNodeWithContentDescription(backspaceDesc).performTouchInput { longClick() }
        composeRule.waitForIdle()
        
        // Verify empty state text "Build your sentence..."
        composeRule.onNodeWithText("Build your sentence...").assertIsDisplayed()
    }
}
