package com.countflow.widget.glance.di

import com.countflow.widget.engine.lifecycle.WidgetLifecycleCoordinator
import com.countflow.widget.engine.provider.WidgetRenderModelProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * How code that Hilt cannot inject reaches the dependency graph.
 *
 * [androidx.glance.appwidget.GlanceAppWidget] is not an Android component — it is instantiated
 * by Glance's own runtime, not by Hilt — so `provideGlance` cannot take an `@Inject` constructor
 * the way a ViewModel or a `BroadcastReceiver` can (KNOWN_ISSUES.md LIM-005). This is the one
 * place that gap is bridged: `EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)`
 * resolves it, using the application [Context] every `provideGlance` call already receives.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {

    fun widgetRenderModelProvider(): WidgetRenderModelProvider

    fun widgetLifecycleCoordinator(): WidgetLifecycleCoordinator
}
