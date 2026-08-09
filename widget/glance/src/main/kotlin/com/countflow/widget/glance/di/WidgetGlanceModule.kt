package com.countflow.widget.glance.di

import com.countflow.widget.engine.refresh.AlarmScheduler
import com.countflow.widget.engine.refresh.WidgetRedrawer
import com.countflow.widget.engine.refresh.WidgetRefreshScheduler
import com.countflow.widget.glance.refresh.AndroidAlarmScheduler
import com.countflow.widget.glance.refresh.GlanceWidgetRedrawer
import com.countflow.widget.glance.refresh.GlanceWidgetRefreshScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the widget engine's refresh-scheduling seams to their `:widget:glance` implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetGlanceModule {

    @Binds
    @Singleton
    abstract fun bindsWidgetRefreshScheduler(
        impl: GlanceWidgetRefreshScheduler,
    ): WidgetRefreshScheduler

    @Binds
    @Singleton
    abstract fun bindsAlarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler

    @Binds
    @Singleton
    abstract fun bindsWidgetRedrawer(impl: GlanceWidgetRedrawer): WidgetRedrawer
}
