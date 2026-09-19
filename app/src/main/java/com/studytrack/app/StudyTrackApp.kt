package com.studytrack.app

import android.app.Application

/**
 * Application entry point. Initializes the service locator (manual DI container)
 * so ViewModels and repositories can resolve their dependencies.
 */
class StudyTrackApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
