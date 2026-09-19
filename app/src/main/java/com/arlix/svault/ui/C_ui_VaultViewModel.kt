package com.arlix.svault.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.arlix.svault.E_ui_VaultState

// Different security chamgers segregating credential according to sensitivity
enum class E_ui_VaultChamber {
    HOT,
    COLD
}

// Sub sections under HOT(casual)
enum class E_ui_HotSection(val v_ui_label: String) {
    GENERAL("🌐 General"),
    PERSONAL("💼 Personal"),
    FINANCE("💳 Finance")
}

data class C_ui_CredentialItem(
    val v_ui_id: Long,
    val v_ui_title: String,
    val v_ui_account: String,
    val v_ui_secret: String,
    val v_ui_section: E_ui_HotSection
)

class C_ui_VaultViewModel : ViewModel() {

    var v_ui_vaultState by mutableStateOf(E_ui_VaultState.LOCKED)
    var v_ui_passwordInput by mutableStateOf("")
    var v_ui_isPasswordVisible by mutableStateOf(false)
    var v_ui_statusMessage by mutableStateOf("")
    var v_ui_isLoading by mutableStateOf(false)
    var v_ui_selectedChamber by mutableStateOf(E_ui_VaultChamber.HOT)
    var v_ui_selectedHotSection by mutableStateOf(E_ui_HotSection.GENERAL)

    //testing variables
    var v_ui_credentialsList by mutableStateOf(
        listOf(
            C_ui_CredentialItem(1, "Netflix", "user@personal.com", "N3tfl!xP@ss2026", E_ui_HotSection.GENERAL),
            C_ui_CredentialItem(2, "Amazon Prime", "user@personal.com", "Amz0n#Pr1me99", E_ui_HotSection.GENERAL),
            C_ui_CredentialItem(3, "GitHub Enterprise", "user", "ghp_secureToken998877", E_ui_HotSection.PERSONAL),
            C_ui_CredentialItem(4, "ProtonMail", "user@proton.me", "Pr0t0n_Vault_K3y!", E_ui_HotSection.PERSONAL),
            C_ui_CredentialItem(5, "HDFC NetBanking", "user_hdfc_usr", "Hdfc@SecurePin2026", E_ui_HotSection.FINANCE),
            C_ui_CredentialItem(6, "UPI PIN Backup", "Primary Bank Account", "984251", E_ui_HotSection.FINANCE)
        )
    )

    fun f_ui_onUnlockedClicked() {
        if(v_ui_passwordInput.isBlank()) {
            v_ui_statusMessage = "Passphrase Can't be empty my friend"
            return
        }
        v_ui_statusMessage = ""
        v_ui_vaultState = E_ui_VaultState.UNLOCKED
        v_ui_passwordInput = ""
    }

    fun f_ui_lockImmediate() {
        v_ui_vaultState = E_ui_VaultState.LOCKED
        v_ui_passwordInput = ""
        v_ui_statusMessage = ""
        v_ui_selectedChamber = E_ui_VaultChamber.HOT
        v_ui_selectedHotSection = E_ui_HotSection.GENERAL
    }
}