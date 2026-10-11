# =====================================================================
#  Arlix — ShadowVault  |  ProGuard / R8 rules
#  Keep rules are listed only for libraries that need them.
#  R8 full mode is active (via proguard-android-optimize.txt), so
#  everything not reachable from entry-points is removed by default.
# =====================================================================

# ── BouncyCastle ──────────────────────────────────────────────────────
# Reflection-heavy internals that R8 can't trace statically.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── SQLCipher ─────────────────────────────────────────────────────────
# JNI-loaded native library: keep the Java-side wrapper intact.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.**

# ── Room (ORM) ────────────────────────────────────────────────────────
# Room reads annotations at runtime via reflection; keep entity fields.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ── Kotlin coroutines ─────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**

# ── Kotlin serialization (not used, but suppress any residual warns) ──
-dontwarn kotlinx.serialization.**

# ── JNI (native memory_sanitizer library) ─────────────────────────────
# The JNI method name must survive mangling; keep the calling Kotlin class.
-keep class com.arlix.shadowvault.crypto.MemorySanitizer { *; }

# ── General Android / Compose ─────────────────────────────────────────
# Compose generates synthetic classes; R8 handles these via the Compose
# compiler plugin — no extra rules needed here.

# Remove all logging in release builds.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

