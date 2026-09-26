package com.arlix.shadowvault.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun SecureVaultTextField(
    value: String, // Note: We use String here only for Compose rendering, but backend keeps CharArray
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = modifier
            // [T3: Accessibility Blinding]
            // Hides this node from the Accessibility tree so rogue accessibility scrapers
            // (a narrow class of Android banking trojans) cannot read the field content via
            // AccessibilityNodeInfo traversal. FLAG_SECURE handles pixel-level capture (T1).
            .semantics { hideFromAccessibility() },

        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false
        ).apply {
            // [T5: Rogue Keyboard Defense]
            // KeyboardType.Password triggers standard Android IME password-mode behavior,
            // which suppresses personalized-learning dictionary insertion on compliant keyboards
            // (Gboard and most AOSP-derived IMEs). No additional explicit IME_FLAG_NO_PERSONALIZED_LEARNING
            // injection — not exposed by current Compose KeyboardOptions API without disproportionate
            // interop complexity for the marginal gain.
        }
    )
}
