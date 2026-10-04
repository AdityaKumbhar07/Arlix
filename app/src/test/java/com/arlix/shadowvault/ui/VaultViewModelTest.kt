package com.arlix.shadowvault.ui

import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.testutil.VaultViewModelTestBase
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test

class VaultViewModelTest : VaultViewModelTestBase() {

    private fun openHot(entries: List<VaultEntry> = emptyList(), pw: String = "hotpass") {
        seed(false, pw, entries); newVm(); unlockHot(pw); waitUnlocked()
    }

    private fun entry(id: String = "e1", title: String = "Bank", pw: String = "p@ss") =
        VaultEntry(id = id, title = title, username = "u", notes = "n", passwordSecret = pw.toCharArray())

    // ---------------- initial state ----------------
    @Test fun freshInstallStartsInSetup() { newVm(); assertTrue(state() is VaultUiState.Setup) }
    @Test fun existingVaultStartsLocked() { seed(false, "x12345"); newVm(); assertTrue(state() is VaultUiState.Locked) }

    // ---------------- create vault ----------------
    @Test fun createRejectsTooShortPassphrase() {
        newVm()
        val p = "abcd".toCharArray(); val c = "abcd".toCharArray()
        vm.createVault(p, c)
        val s = state() as VaultUiState.Error
        assertTrue(s.isSetupMode); assertTrue(s.message.contains("at least 5"))
        assertTrue(isZeroed(p)); assertTrue(isZeroed(c)); assertEquals(0, repo.openCalls)
    }

    @Test fun createRejectsMismatch() {
        newVm()
        val p = "abcdef".toCharArray(); val c = "abcdeg".toCharArray()
        vm.createVault(p, c)
        val s = state() as VaultUiState.Error
        assertTrue(s.isSetupMode); assertTrue(s.message.contains("do not match"))
        assertTrue(isZeroed(p)); assertTrue(isZeroed(c)); assertEquals(0, repo.openCalls)
    }

    @Test fun createAcceptsExactlyFiveCharactersAndWipesInputs() {
        newVm()
        val p = "abcde".toCharArray(); val c = "abcde".toCharArray()
        vm.createVault(p, c)
        waitUnlocked()
        assertTrue(repo.vaultExists(false)); assertEquals(0, unlocked().entries.size)
        assertTrue(isZeroed(p)); assertTrue(isZeroed(c))
    }

    @Test fun createThenLockThenUnlockWorksForAwkwardPassphrases() {
        listOf("pässwörd", "密码密码密码", "😀😀😀😀😀", "x".repeat(300), " spaced  out ").forEach { pw ->
            crypto = com.arlix.shadowvault.testutil.FakeCryptoProvider()
            repo = com.arlix.shadowvault.testutil.FakeVaultRepository()
            newVm()
            vm.createVault(pw.toCharArray(), pw.toCharArray()); waitUnlocked()
            vm.lock(); waitLocked()
            unlockHot(pw + "x"); waitError()
            unlockHot(pw); waitUnlocked()
        }
    }

    @Test fun coldCreateWithShortPassphraseReportsOnColdErrorOnly() {
        openHot()
        vm.createVault("abc".toCharArray(), "abc".toCharArray(), isColdVault = true)
        assertTrue(vm.coldVaultError.value!!.contains("at least 5"))
        assertFalse(unlocked().isColdVault)
    }

    @Test fun coldCreateMismatchReportsOnColdErrorOnly() {
        openHot()
        vm.createVault("coldpass1".toCharArray(), "coldpass2".toCharArray(), isColdVault = true)
        assertTrue(vm.coldVaultError.value!!.contains("do not match"))
        assertFalse(unlocked().isColdVault)
        vm.clearColdVaultError(); assertNull(vm.coldVaultError.value)
    }

    // ---------------- unlock ----------------
    @Test fun correctPasswordUnlocksAndLoadsEntries() {
        openHot(makeEntries(5))
        assertEquals(5, unlocked().entries.size); assertTrue(repo.isVaultOpen())
    }

    @Test fun wrongPasswordGivesGenericErrorAndVaultStaysClosed() {
        seed(false, "right-pass"); newVm()
        val pw = unlockHot("wrong-pass"); waitError()
        val s = state() as VaultUiState.Error
        assertEquals("Incorrect Password or Corrupted Vault.", s.message)
        assertFalse(s.isSetupMode); assertFalse(repo.isVaultOpen()); assertTrue(isZeroed(pw))
    }

    @Test fun retryAfterWrongPasswordSucceeds() {
        seed(false, "right-pass"); newVm()
        unlockHot("nope-nope"); waitError()
        unlockHot("right-pass"); waitUnlocked()
    }

    @Test fun passwordArrayIsZeroedAfterSuccessfulUnlock() {
        seed(false, "hotpass"); newVm()
        val pw = unlockHot("hotpass"); waitUnlocked()
        assertTrue(isZeroed(pw))
    }

    @Test fun secondUnlockWhileUnlockingIsIgnored() {
        seed(false, "hotpass"); newVm()
        crypto.gate = CompletableDeferred()
        unlockHot("hotpass")
        waitFor { crypto.deriveCalls == 1 }
        val second = unlockHot("hotpass")
        assertTrue(isZeroed(second))
        crypto.gate!!.complete(Unit)
        waitUnlocked(); assertEquals(1, crypto.deriveCalls)
    }

    @Test fun outOfMemoryShowsMemoryMessage() {
        seed(false, "hotpass"); newVm()
        crypto.failWith = OutOfMemoryError("test")
        unlockHot("hotpass"); waitError()
        assertTrue((state() as VaultUiState.Error).message.contains("memory"))
    }

    @Test fun unexpectedFailureShowsGenericMessage() {
        seed(false, "hotpass"); newVm()
        crypto.failWith = RuntimeException("boom")
        unlockHot("hotpass"); waitError()
        assertEquals("Incorrect Password or Corrupted Vault.", (state() as VaultUiState.Error).message)
    }

    @Test fun readerFailureShowsReadError() {
        seed(false, "hotpass"); newVm()
        repo.failReads = true
        unlockHot("hotpass"); waitError()
        assertEquals("Vault read error. Please re-unlock.", (state() as VaultUiState.Error).message)
    }

    @Test fun failedFirstCreationReturnsToSetupScreen() {
        newVm()
        crypto.failWith = RuntimeException("boom")
        vm.createVault("abcdef".toCharArray(), "abcdef".toCharArray()); waitError()
        assertTrue((state() as VaultUiState.Error).isSetupMode)
    }

    // ---------------- lock ----------------
    @Test fun lockWipesEntriesClosesVaultAndResetsFilter() {
        openHot(makeEntries(6))
        vm.setCategoryFilter("Finance")
        val shown = unlocked().entries
        assertTrue(shown.all { hasSecret(it.passwordSecret) })
        vm.lock(); waitLocked()
        assertTrue(shown.all { isZeroed(it.passwordSecret) })
        assertFalse(repo.isVaultOpen()); assertEquals("All", vm.selectedCategory.value)
    }

    @Test fun lockDuringUnlockCancelsIt() {
        seed(false, "hotpass"); newVm()
        crypto.gate = CompletableDeferred()
        val pw = unlockHot("hotpass")
        waitFor { crypto.deriveCalls == 1 }
        vm.lock(); waitLocked()
        crypto.gate!!.complete(Unit); settle()
        assertTrue(state() is VaultUiState.Locked); assertEquals(0, repo.openCalls); assertTrue(isZeroed(pw))
    }

    @Test fun lockOnSetupScreenStaysOnSetup() {
        newVm(); vm.lock(); settle()
        assertTrue(state() is VaultUiState.Setup)
    }

    @Test fun rapidLockUnlockCyclesLeaveNothingOpen() {
        seed(false, "hotpass", makeEntries(20)); newVm()
        repeat(50) { unlockHot("hotpass"); waitUnlocked(); vm.lock(); waitLocked() }
        assertFalse(repo.isVaultOpen())
    }

    // ---------------- category filter ----------------
    @Test fun filterShowsOnlyMatchingCategory() {
        openHot(makeEntries(9))
        vm.setCategoryFilter("Finance")
        assertEquals(3, unlocked().entries.size)
        assertTrue(unlocked().entries.all { it.category == "Finance" })
        vm.setCategoryFilter("All"); assertEquals(9, unlocked().entries.size)
    }

    @Test fun filterIsIgnoredInColdVault() {
        seed(true, "coldpass", makeEntries(4).map { it.copy(category = "General") })
        openHot(makeEntries(6))
        vm.setCategoryFilter("Finance")
        unlockCold("coldpass"); waitUnlocked(cold = true)
        assertEquals(4, unlocked().entries.size)
    }

    @Test fun everyPublishGetsNewRevisionEvenForSameFilter() {
        openHot(makeEntries(3))
        val r1 = unlocked().revision
        vm.setCategoryFilter("Work"); vm.setCategoryFilter("Work")
        assertTrue(unlocked().revision > r1)
    }

    @Test fun filterChangeWhileLockedDoesNotUnlock() {
        seed(false, "hotpass"); newVm()
        vm.setCategoryFilter("Work")
        assertTrue(state() is VaultUiState.Locked)
    }

    // ---------------- credentials ----------------
    @Test fun addStoresEntryRefreshesListAndWipesTheCallersArray() {
        openHot()
        val e = entry()
        vm.addCredential(e)
        waitFor { unlocked().entries.size == 1 }
        waitFor { isZeroed(e.passwordSecret) }
        assertEquals("p@ss", String(repo.storedEntries(false).first().passwordSecret))
    }

    @Test fun editReplacesEntryWithSameId() {
        openHot()
        vm.addCredential(entry(title = "Old", pw = "one")); waitFor { unlocked().entries.size == 1 }
        vm.updateCredential(entry(title = "New", pw = "two"))
        waitFor { unlocked().entries.firstOrNull()?.title == "New" }
        assertEquals(1, unlocked().entries.size)
        assertEquals("two", String(repo.storedEntries(false).first().passwordSecret))
    }

    @Test fun deleteRemovesEntryAndUnknownIdIsHarmless() {
        openHot(makeEntries(3))
        vm.deleteCredential("id-Site-1"); waitFor { unlocked().entries.size == 2 }
        vm.deleteCredential("does-not-exist"); settle()
        assertEquals(2, unlocked().entries.size)
    }

    @Test fun replacedListArraysAreWipedAndNewOnesStayIntact() {
        openHot(makeEntries(3))
        val old = unlocked().entries
        vm.addCredential(entry()); waitFor { unlocked().entries.size == 4 }
        assertTrue(old.all { isZeroed(it.passwordSecret) })
        assertTrue(unlocked().entries.all { hasSecret(it.passwordSecret) })
    }

    @Test fun addFailureShowsErrorAndStillWipesEntry() {
        openHot()
        repo.failNextAdd = java.io.IOException("disk full")
        val e = entry(); vm.addCredential(e); waitError()
        assertEquals("Failed to save credential.", (state() as VaultUiState.Error).message)
        assertTrue(isZeroed(e.passwordSecret))
    }

    @Test fun deleteFailureShowsError() {
        openHot(makeEntries(2))
        repo.failNextDelete = java.io.IOException("io"); vm.deleteCredential("id-Site-0"); waitError()
        assertEquals("Failed to delete credential.", (state() as VaultUiState.Error).message)
    }

    @Test fun addWhileLockedLeavesStateLocked() {
        seed(false, "hotpass"); newVm()
        val e = entry(); vm.addCredential(e); settle()
        assertTrue(state() is VaultUiState.Locked); assertTrue(isZeroed(e.passwordSecret))
    }

    // ---------------- cold vault ----------------
    @Test fun wrongColdKeyKeepsHotVaultOpenAndShowsError() {
        seed(true, "coldpass"); openHot(makeEntries(3))
        val before = unlocked().entries
        val wrong = unlockCold("not-it-123")
        waitFor { vm.coldVaultError.value != null }
        assertEquals("Incorrect Cold Vault Key or Corrupted Vault.", vm.coldVaultError.value)
        assertFalse(unlocked().isColdVault); assertEquals(3, unlocked().entries.size)
        assertTrue(before.all { hasSecret(it.passwordSecret) })
        assertEquals(false, repo.openIsCold); assertTrue(isZeroed(wrong))
    }

    @Test fun correctColdKeySwitchesToColdAndWipesHotEntries() {
        seed(true, "coldpass", makeEntries(2, "Cold")); openHot(makeEntries(3))
        val hot = unlocked().entries
        unlockCold("coldpass"); waitUnlocked(cold = true)
        assertEquals(2, unlocked().entries.size); assertEquals(true, repo.openIsCold)
        assertTrue(hot.all { isZeroed(it.passwordSecret) })
    }

    @Test fun coldVaultCanBeCreatedFromHotDashboard() {
        openHot()
        vm.createVault("coldpass1".toCharArray(), "coldpass1".toCharArray(), isColdVault = true)
        waitUnlocked(cold = true)
        assertTrue(repo.vaultExists(true)); assertTrue(vm.coldVaultExists())
    }

    @Test fun savingInColdVaultStaysOnColdScreen() {
        seed(true, "coldpass"); openHot()
        unlockCold("coldpass"); waitUnlocked(cold = true)
        vm.addCredential(entry(pw = "cold-secret"))
        waitFor { unlocked().entries.size == 1 }; settle()
        assertTrue(unlocked().isColdVault)
        assertEquals("cold-secret", String(repo.storedEntries(true).first().passwordSecret))
    }

    @Test fun secondColdUnlockWhileOneIsRunningIsIgnored() {
        seed(true, "coldpass"); openHot()
        crypto.gate = CompletableDeferred()
        unlockCold("coldpass")
        waitFor { crypto.deriveCalls == 1 }
        val second = unlockCold("coldpass")
        assertTrue(isZeroed(second))
        crypto.gate!!.complete(Unit); waitUnlocked(cold = true)
        assertEquals(1, crypto.deriveCalls)
    }

    @Test fun lockFromColdVaultWipesColdEntries() {
        seed(true, "coldpass", makeEntries(3, "Cold")); openHot()
        unlockCold("coldpass"); waitUnlocked(cold = true)
        val cold = unlocked().entries
        vm.lock(); waitLocked()
        assertTrue(cold.all { isZeroed(it.passwordSecret) }); assertFalse(repo.isVaultOpen())
    }

    @Test fun hotAndColdDataStaySeparate() {
        seed(true, "coldpass", makeEntries(2, "Cold")); openHot(makeEntries(5, "Hot"))
        assertTrue(unlocked().entries.all { it.title.startsWith("Hot") })
        unlockCold("coldpass"); waitUnlocked(cold = true)
        assertTrue(unlocked().entries.all { it.title.startsWith("Cold") })
    }
}
