package com.countflow.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.countflow.widget.engine.refresh.WidgetRefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * CountFlow's [Application].
 *
 * Three responsibilities, all of which have to live here:
 *
 * 1. `@HiltAndroidApp` generates the application-level Hilt component every other injection
 *    point resolves against.
 * 2. Implementing [Configuration.Provider] hands WorkManager a [HiltWorkerFactory], which is
 *    what lets `@HiltWorker` classes take constructor dependencies. This requires WorkManager's
 *    default initializer to be removed from the merged manifest — see `AndroidManifest.xml`.
 *    Without that removal WorkManager initializes itself first with the default factory and
 *    every injected worker fails at runtime.
 * 3. Starting [WidgetRefreshScheduler]. This is the Milestone 4 seam, not the Milestone 8
 *    strategy (ARCHITECTURE.md D-008) — see the injected implementation's own documentation for
 *    what it actually does and does not cover.
 *
 * Otherwise deliberately empty. Work done in `onCreate` is charged directly to cold start, and
 * CountFlow targets under 700 ms; initialization belongs in the component that needs it.
 */
@HiltAndroidApp
class CountFlowApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var widgetRefreshScheduler: WidgetRefreshScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        widgetRefreshScheduler.start()
    }
}
