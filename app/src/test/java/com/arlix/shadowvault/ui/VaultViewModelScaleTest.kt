package com.arlix.shadowvault.ui

import com.arlix.shadowvault.domain.VaultEntry
import com.arlix.shadowvault.testutil.VaultViewModelTestBase
import org.junit.Assert.*
import org.junit.Test

class VaultViewModelScaleTest : VaultViewModelTestBase() {

    private fun openWith(n: Int) {
        seed(false, "hotpass", makeEntries(n)); newVm()
        unlockHot("hotpass"); waitUnlocked(timeoutMs = 20_000)
    }

    private fun unlockAndLock(n: Int) {
        val t0 = System.nanoTime()
        openWith(n)
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("unlock with $n entries: $ms ms")
        assertEquals(n, unlocked().entries.size)
        assertTrue("took $ms ms", ms < 15_000)
        val all = unlocked().entries
        vm.lock(); waitLocked()
        assertTrue(all.all { isZeroed(it.passwordSecret) })
    }

    @Test fun unlock100Entries() = unlockAndLock(100)
    @Test fun unlock1000Entries() = unlockAndLock(1_000)
    @Test fun unlock10000Entries() = unlockAndLock(10_000)

    @Test fun categoryFilterOver3000Entries() {
        openWith(3_000)
        vm.setCategoryFilter("Finance"); assertEquals(1_000, unlocked().entries.size)
        vm.setCategoryFilter("Work"); assertEquals(1_000, unlocked().entries.size)
        vm.setCategoryFilter("All"); assertEquals(3_000, unlocked().entries.size)
    }

    @Test fun add300EntriesOneByOne() {
        seed(false, "hotpass"); newVm(); unlockHot("hotpass"); waitUnlocked()
        val first = unlocked().entries
        repeat(300) { i ->
            vm.addCredential(VaultEntry(title = "T$i", username = "u", notes = "n", passwordSecret = "p$i".toCharArray()))
            waitFor { unlocked().entries.size == i + 1 }
        }
        assertEquals(300, repo.storedEntries(false).size)
        assertTrue(first.all { isZeroed(it.passwordSecret) })
    }

    @Test fun randomisedAddEditDeleteMatchesReferenceModel() {
        seed(false, "hotpass"); newVm(); unlockHot("hotpass"); waitUnlocked()
        val rnd = kotlin.random.Random(42)
        val model = linkedMapOf<String, String>()
        fun e(id: String, t: String) = VaultEntry(id = id, title = t, username = "u", notes = "n", passwordSecret = "pw".toCharArray())
        repeat(500) { i ->
            when (rnd.nextInt(3)) {
                0 -> { val id = "n$i"; model[id] = "T$i"; vm.addCredential(e(id, "T$i")) }
                1 -> if (model.isNotEmpty()) {
                    val id = model.keys.elementAt(rnd.nextInt(model.size)); model[id] = "E$i"; vm.updateCredential(e(id, "E$i"))
                }
                else -> if (model.isNotEmpty()) {
                    val id = model.keys.elementAt(rnd.nextInt(model.size)); model.remove(id); vm.deleteCredential(id)
                }
            }
            pump()
        }
        settle()
        assertEquals(model, repo.storedEntries(false).associate { it.id to it.title })
        assertEquals(model.keys, unlocked().entries.map { it.id }.toSet())
    }

    @Test fun hugeFieldsRoundTrip() {
        val big = (0 until 50).map { i ->
            VaultEntry(id = "big$i", title = "Big-$i", username = "u".repeat(5_000), notes = "n".repeat(200_000),
                passwordSecret = "p".repeat(50_000).toCharArray())
        }
        seed(false, "hotpass", big); newVm(); unlockHot("hotpass"); waitUnlocked(timeoutMs = 20_000)
        assertEquals(50, unlocked().entries.size)
        assertTrue(unlocked().entries.all { it.notes.length == 200_000 && it.passwordSecret.size == 50_000 })
    }

    @Test fun repeatedLockUnlockWith1000EntriesLeavesNoPlaintext() {
        seed(false, "hotpass", makeEntries(1_000)); newVm()
        repeat(20) {
            unlockHot("hotpass"); waitUnlocked()
            val all = unlocked().entries
            vm.lock(); waitLocked()
            assertTrue(all.all { isZeroed(it.passwordSecret) })
        }
    }
}
