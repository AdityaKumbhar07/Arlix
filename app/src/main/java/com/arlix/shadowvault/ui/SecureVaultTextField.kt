package com.arlix.shadowvault.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.ui.res.painterResource
import com.arlix.shadowvault.R
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * Hidden unless [SecurityConfig.screenshotMode] is on or toggled via the eye icon.
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
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedSecureTextField(
        state = state,
        inputTransformation = InputTransformation.maxLength(256),
        // [T3] Hidden from accessibility services (also hides it from TalkBack, by design).
        modifier = modifier.semantics { hideFromAccessibility() },
        placeholder = { Text(placeholder, color = TextMuted) },
        textObfuscationMode = if (passwordVisible || SecurityConfig.screenshotMode) {
            TextObfuscationMode.Visible
        } else {
            TextObfuscationMode.Hidden
        },
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    painter = painterResource(if (passwordVisible) R.drawable.ic_eye_slash else R.drawable.ic_eye),
                    contentDescription = if (passwordVisible) "Hide passphrase" else "Show passphrase",
                    tint = TextMuted
                )
            }
        },
        shape = RoundedCornerShape(27),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
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
