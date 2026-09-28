package com.arlix.shadowvault

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class E2EUserFlowTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private fun cleanDatabase(context: Context) {
        context.deleteDatabase("vault_primary.db")
        context.deleteDatabase("vault_secondary.db")
        context.getDatabasePath("vault_primary.salt").delete()
        context.getDatabasePath("vault_secondary.salt").delete()
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cleanDatabase(context)
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cleanDatabase(context)
    }

    /**
     * 1. The "Happy Path" Setup & Add Credential Flow
     * Launch app -> Setup Vault -> Add Credential -> Lock -> Unlock -> Verify Credential
     */
    @Test
    fun testHappyPathSetupAddCredentialAndLockUnlock() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Initial Setup Screen
            composeTestRule.onNodeWithText("Create your Master Passphrase").assertIsDisplayed()
            composeTestRule.onNodeWithText("New Master Passphrase").performTextInput("masterpass123")
            composeTestRule.onNodeWithText("Confirm Passphrase").performTextInput("masterpass123")
            composeTestRule.onNodeWithText("CREATE VAULT").performClick()

            // 2. Wait for Argon2id derivation & SQLCipher DB init -> Dashboard
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodesWithContentDescription("Add Credential").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Arlix").assertIsDisplayed()

            // 3. Add Credential
            composeTestRule.onNodeWithContentDescription("Add Credential").performClick()
            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithText("Save Credential").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Title").performTextInput("ProtonMail")
            composeTestRule.onNodeWithText("Username").performTextInput("alice@proton.me")
            composeTestRule.onNodeWithText("Password").performTextInput("secret12345")
            composeTestRule.onNodeWithText("Notes (Optional)").performTextInput("Recovery key stored offline")
            composeTestRule.onNodeWithText("Save Credential").performClick()

            // 4. Verify Credential in Dashboard
            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithText("ProtonMail").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("ProtonMail").assertIsDisplayed()
            composeTestRule.onNodeWithText("alice@proton.me").assertIsDisplayed()

            // 5. Lock Vault
            composeTestRule.onNodeWithContentDescription("Lock Vault").performClick()
            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithText("UNLOCK").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Enter your Master Passphrase").assertIsDisplayed()

            // 6. Unlock Vault
            composeTestRule.onNodeWithText("Master Passphrase").performTextInput("masterpass123")
            composeTestRule.onNodeWithText("UNLOCK").performClick()

            // 7. Verify Credential Still Present After Re-authentication
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodesWithText("ProtonMail").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("ProtonMail").assertIsDisplayed()
        }
    }

    /**
     * 2. Edge Case: Password Mismatch Routing (Bug 1 & 9 Regression Test)
     * Setup mode with mismatching passwords must show error and STAY on setup screen.
     */
    @Test
    fun testPasswordMismatchRoutingBugRegression() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithText("Create your Master Passphrase").assertIsDisplayed()
            composeTestRule.onNodeWithText("New Master Passphrase").performTextInput("password123")
            composeTestRule.onNodeWithText("Confirm Passphrase").performTextInput("mismatch456")
            composeTestRule.onNodeWithText("CREATE VAULT").performClick()

            // Assert error message
            composeTestRule.onNodeWithText("Passphrases do not match. Please try again.").assertIsDisplayed()

            // Assert it remains on Setup screen, NOT routed to Unlock screen
            composeTestRule.onNodeWithText("Confirm Passphrase").assertIsDisplayed()
            composeTestRule.onNodeWithText("CREATE VAULT").assertIsDisplayed()
            composeTestRule.onAllNodesWithText("UNLOCK").assertCountEquals(0)
        }
    }

    /**
     * 3. Edge Case: UI Overflow Limits (Bug 7 Regression Test)
     * Password fields must strictly cap input at 256 characters.
     */
    @Test
    fun testPasswordLengthLimit256Chars() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val inputField = composeTestRule.onAllNodes(hasSetTextAction())[0]

            // Fill up to the maximum 256 characters
            inputField.performTextInput("a".repeat(256))
            val textNode1 = inputField.fetchSemanticsNode()
            val editable1 = textNode1.config.getOrNull(SemanticsProperties.EditableText)
            assertEquals(256, editable1?.length)

            // Attempt to type additional characters beyond the 256-character limit
            inputField.performTextInput("b".repeat(50))
            val textNode2 = inputField.fetchSemanticsNode()
            val editable2 = textNode2.config.getOrNull(SemanticsProperties.EditableText)
            // Assert that the overflow input was strictly rejected and length remains capped at 256
            assertEquals(256, editable2?.length)
        }
    }

    /**
     * 4. Edge Case: Background Lock Race Condition (Bug 3 Regression Test)
     * Backgrounding the app during an in-flight unlock cancels the job and returns to Locked.
     */
    @Test
    fun testBackgroundLockRaceCondition() {
        // Setup vault first
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithText("New Master Passphrase").performTextInput("masterpass123")
            composeTestRule.onNodeWithText("Confirm Passphrase").performTextInput("masterpass123")
            composeTestRule.onNodeWithText("CREATE VAULT").performClick()

            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodesWithContentDescription("Lock Vault").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription("Lock Vault").performClick()
            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithText("UNLOCK").fetchSemanticsNodes().isNotEmpty()
            }
        }

        // Re-launch into Locked state and test backgrounding race
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.onNodeWithText("Master Passphrase").performTextInput("masterpass123")
            composeTestRule.onNodeWithText("UNLOCK").performClick()

            // Immediately simulate user pushing app to background while Argon2id is running
            scenario.moveToState(Lifecycle.State.CREATED)

            // Bring app back to foreground
            scenario.moveToState(Lifecycle.State.RESUMED)

            // Verify the app safely returned to Locked state rather than leaking Unlocked
            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithText("UNLOCK").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Enter your Master Passphrase").assertIsDisplayed()
        }
    }

    /**
     * 5. Edge Case: Cold Vault Duress Activation
     * Creating a Cold Vault from Hot Vault dashboard produces an empty, isolated database.
     */
    @Test
    fun testColdVaultSetupAndDataIsolation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Setup Hot Vault
            composeTestRule.onNodeWithText("New Master Passphrase").performTextInput("masterpass123")
            composeTestRule.onNodeWithText("Confirm Passphrase").performTextInput("masterpass123")
            composeTestRule.onNodeWithText("CREATE VAULT").performClick()

            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodesWithContentDescription("Add Credential").fetchSemanticsNodes().isNotEmpty()
            }

            // Add sensitive Hot Vault credential
            composeTestRule.onNodeWithContentDescription("Add Credential").performClick()
            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithText("Save Credential").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Title").performTextInput("Top Secret Bank")
            composeTestRule.onNodeWithText("Username").performTextInput("agent007")
            composeTestRule.onNodeWithText("Password").performTextInput("classified")
            composeTestRule.onNodeWithText("Save Credential").performClick()

            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithText("Top Secret Bank").fetchSemanticsNodes().isNotEmpty()
            }

            // Trigger stealth Cold Vault dialog via long-press on App Info decoy icon
            composeTestRule.onNodeWithContentDescription("App Info").performTouchInput { longClick() }

            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithText("Advanced Security").fetchSemanticsNodes().isNotEmpty()
            }

            // Setup Cold Vault with distinct passphrase
            composeTestRule.onNodeWithText("Set Cold Vault Passphrase").performTextInput("coldpass999")
            composeTestRule.onNodeWithText("Confirm Passphrase").performTextInput("coldpass999")
            composeTestRule.onNodeWithText("CREATE").performClick()

            // Wait for Cold Vault transition: Hot vault credentials ("Top Secret Bank") disappear
            composeTestRule.waitUntil(15_000) {
                composeTestRule.onAllNodesWithText("Top Secret Bank").fetchSemanticsNodes().isEmpty()
            }

            // Assert Hot Vault credentials are NOT accessible (0 instances)
            composeTestRule.onAllNodesWithText("Top Secret Bank").assertCountEquals(0)
            composeTestRule.onAllNodesWithText("agent007").assertCountEquals(0)
        }
    }
}
