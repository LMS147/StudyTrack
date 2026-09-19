package com.studytrack.app

import android.content.Context

/**
 * Minimal manual dependency container (chosen over a DI framework to keep the
 * dependency list exactly as specified). Initialized once from [StudyTrackApp].
 *
 * Feature repositories are added to this container as the networking layer and
 * screens come online in later build steps.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
