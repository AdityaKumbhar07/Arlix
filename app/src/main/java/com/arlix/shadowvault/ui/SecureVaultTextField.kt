package com.arlix.shadowvault.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import com.arlix.shadowvault.SecurityConfig
import com.arlix.shadowvault.ui.theme.HotVaultAccent
import com.arlix.shadowvault.ui.theme.HotVaultBorder
import com.arlix.shadowvault.ui.theme.TextMuted
import com.arlix.shadowvault.ui.theme.TextPrimary

/** Keyboard options for ordinary fields (title, username, notes): no autocorrect or suggestions. */
val PlainTextKeyboardOptions = KeyboardOptions(autoCorrectEnabled = false)

/**
 * Passphrase / password input. The state-based secure field gives: password keyboard (so
 * keyboards don't learn it), no autocorrect, cut/copy/drag disabled, password semantics.
 * Hidden unless [SecurityConfig.screenshotMode] is on.
 * Keep the state with `remember { TextFieldState() }` and NEVER rememberTextFieldState()
 * (that one saves its text into the saved-state Bundle).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurePassphraseField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedSecureTextField(
        state = state,
        // [T3] Hidden from accessibility services (also hides it from TalkBack, by design).
        modifier = modifier.semantics { hideFromAccessibility() },
        placeholder = { Text(placeholder, color = TextMuted) },
        textObfuscationMode = if (SecurityConfig.screenshotMode) TextObfuscationMode.Visible
        else TextObfuscationMode.Hidden,
        shape = RoundedCornerShape(27),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HotVaultAccent,
            unfocusedBorderColor = HotVaultBorder,
            cursorColor = HotVaultAccent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )
}

/** Copies the typed text into a CharArray. The caller must wipe the result after use. */
fun TextFieldState.toSecretChars(): CharArray {
    val t = text
    return CharArray(t.length) { t[it] }
}
