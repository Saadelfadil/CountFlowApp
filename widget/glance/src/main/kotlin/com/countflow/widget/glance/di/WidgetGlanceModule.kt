package com.countflow.widget.glance.di

import com.countflow.widget.engine.refresh.WidgetRefreshScheduler
import com.countflow.widget.glance.refresh.GlanceWidgetRefreshScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the widget engine's refresh-scheduling seam to this milestone's implementation. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetGlanceModule {

    @Binds
    @Singleton
    abstract fun bindsWidgetRefreshScheduler(
        impl: GlanceWidgetRefreshScheduler,
    ): WidgetRefreshScheduler
}
