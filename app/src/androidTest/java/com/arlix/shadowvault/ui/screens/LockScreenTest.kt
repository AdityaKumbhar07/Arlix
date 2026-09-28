package com.arlix.shadowvault.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.arlix.shadowvault.ui.VaultUiState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LockScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSetupMode() {
        var created = false
        composeTestRule.setContent {
            LockScreen(
                uiState = VaultUiState.Setup(false),
                onUnlock = { },
                onCreateVault = { pwd, conf ->
                    assertArrayEquals(charArrayOf('1', '2', '3', '4', '5'), pwd)
                    assertArrayEquals(charArrayOf('1', '2', '3', '4', '5'), conf)
                    created = true
                }
            )
        }

        composeTestRule.onNodeWithText("New Master Passphrase").performTextInput("12345")
        composeTestRule.onNodeWithText("Confirm Passphrase").performTextInput("12345")
        composeTestRule.onNodeWithText("CREATE VAULT").performClick()

        assertTrue(created)
    }

    @Test
    fun testUnlockMode() {
        var unlocked = false
        composeTestRule.setContent {
            LockScreen(
                uiState = VaultUiState.Locked,
                onUnlock = { pwd ->
                    assertArrayEquals(charArrayOf('1', '2', '3', '4', '5'), pwd)
                    unlocked = true
                },
                onCreateVault = { _, _ -> }
            )
        }

        composeTestRule.onNodeWithText("Master Passphrase").performTextInput("12345")
        composeTestRule.onNodeWithText("UNLOCK").performClick()

        assertTrue(unlocked)
    }
}
