package com.arlix.shadowvault

object SecurityConfig {
    /**
     * Set to true ONLY when you need screenshots or screen recordings of the real UI.
     * It has an effect in DEBUG builds only, so a release build can never ship with it on.
     */
    private const val SCREENSHOT_MODE_REQUESTED = false

    val screenshotMode: Boolean = BuildConfig.DEBUG && SCREENSHOT_MODE_REQUESTED
}
