package com.arlix.svault.ui

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

@OptIn(ExperimentalComposeUiApi::class)
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
            // This hides the exact physical layout node from rogue accessibility apps
            .semantics { invisibleToUser() },

        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
        ).apply {
            // [T5: Rogue Keyboard Defense]
            // We cannot inject EditorInfo flags directly into Compose KeyboardOptions yet without
            // a custom ImeAction, but setting KeyboardType.Password naturally applies
            // TYPE_TEXT_VARIATION_PASSWORD to the OS keyboard, which intrinsically blocks
            // legitimate keyboards (like Gboard) from saving the words to their dictionary.
        }
    )
}
