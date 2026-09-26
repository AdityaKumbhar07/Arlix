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
            // [T3] Hides node from Accessibility tree to block screen scrapers.
            .semantics { hideFromAccessibility() },

        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false
        ).apply {
            // [T5] Triggers standard Android IME password-mode to suppress personalized learning.
        }
    )
}
