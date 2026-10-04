package com.arlix.shadowvault

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppConfigInstrumentedTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val pm = ctx.packageManager

    @Test fun noPlatformPermissionsAreRequested() {
        val perms = pm.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions?.toList() ?: emptyList()
        assertTrue("Unexpected permissions: $perms", perms.none { it.startsWith("android.permission.") })
    }

    @Test fun backupIsDisabled() =
        assertEquals(0, ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)

    @Test fun onlyTheLauncherActivityOfOurAppIsExported() {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        val info = pm.getPackageInfo(ctx.packageName, flags)
        val ours = { n: String -> n.startsWith("com.arlix.shadowvault") }
        val exported = buildList {
            info.activities?.filter { it.exported && ours(it.name) }?.forEach { add(it.name) }
            info.services?.filter { it.exported && ours(it.name) }?.forEach { add(it.name) }
            info.receivers?.filter { it.exported && ours(it.name) }?.forEach { add(it.name) }
            info.providers?.filter { it.exported && ours(it.name) }?.forEach { add(it.name) }
        }
        assertEquals(listOf("com.arlix.shadowvault.MainActivity"), exported)
    }

    @Test fun mainActivityWindowIsHardened() {
        assumeFalse("screenshot mode is on", SecurityConfig.screenshotMode)
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { a ->
                assertTrue((a.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)
                assertTrue(a.window.decorView.rootView.filterTouchesWhenObscured)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, a.window.decorView.importantForAutofill)
            }
        }
    }
}
