package com.countflow.feature.settings.di

import com.countflow.feature.settings.about.AndroidAppVersionProvider
import com.countflow.feature.settings.about.AppVersionProvider
import com.countflow.feature.settings.notification.AndroidNotificationStatusProvider
import com.countflow.feature.settings.notification.NotificationStatusProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface SettingsModule {

    @Binds
    fun bindNotificationStatusProvider(impl: AndroidNotificationStatusProvider): NotificationStatusProvider

    @Binds
    fun bindAppVersionProvider(impl: AndroidAppVersionProvider): AppVersionProvider
}
