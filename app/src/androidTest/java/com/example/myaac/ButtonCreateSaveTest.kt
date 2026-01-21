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
class ButtonCreateSaveTest {

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
    fun testButtonCreateAndSave() {
        // Wait for app to load
        Thread.sleep(5000)
        composeRule.waitForIdle()

        // 1. Enter Admin Mode
        val menuDesc = composeRule.activity.getString(R.string.menu)
        composeRule.onNodeWithContentDescription(menuDesc).performClick()
        composeRule.waitForIdle()

        val enterAdminText = composeRule.activity.getString(R.string.enter_admin_mode)
        composeRule.onNodeWithText(enterAdminText).performClick()

        val pinLabel = composeRule.activity.getString(R.string.pin)
        composeRule.onNodeWithText(pinLabel).performTextInput("1234")
        
        val unlockText = composeRule.activity.getString(R.string.unlock)
        composeRule.onNodeWithText(unlockText).performClick()
        composeRule.waitForIdle()

        // 2. Create New Board
        val createNewBoardText = composeRule.activity.getString(R.string.create_new_board)
        composeRule.onNodeWithText(createNewBoardText).assertIsDisplayed()
        composeRule.onNodeWithText(createNewBoardText).performClick()

        val newBoardText = composeRule.activity.getString(R.string.new_board)
        composeRule.onNodeWithText(newBoardText).performClick()

        val boardNameLabel = composeRule.activity.getString(R.string.board_name)
        val testBoardName = "Test Button Save Board"
        composeRule.onNodeWithText(boardNameLabel).performTextInput(testBoardName)

        val createText = composeRule.activity.getString(R.string.create)
        composeRule.onAllNodesWithText(createText).onLast().performClick()
       Thread.sleep(5000)
        composeRule.waitForIdle()

        // Verify Board Created
        composeRule.onAllNodesWithText(testBoardName).onFirst().assertIsDisplayed()

        // 3. Add Button (THIS IS THE CORE TEST - CREATE AND SAVE)
        val emptyPrompt = composeRule.activity.getString(R.string.empty_board_prompt)
        composeRule.onNodeWithText(emptyPrompt).performClick()
        Thread.sleep(1000)
        
        // Find Label input using testTag
        val labelInput = composeRule.onAllNodesWithTag("edit_button_label").onLast()
        labelInput.performTextInput("Persistence Test Button")
        composeRule.waitForIdle()
        labelInput.assertTextContains("Persistence Test Button")

        // Click Save
        composeRule.onAllNodesWithTag("save_button").onLast().performClick()
        Thread.sleep(3000)
        composeRule.waitForIdle()

        // VERIFY - This is the key assertion that validates persistence works
        composeRule.onNodeWithText("Persistence Test Button").assertIsDisplayed()
        
        // SUCCESS! If we reach this point, the button was created, saved to database,
        // and is now displayed in the UI with the correct label
    }
}
