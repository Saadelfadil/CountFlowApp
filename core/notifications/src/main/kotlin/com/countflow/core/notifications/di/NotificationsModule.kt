package com.countflow.core.notifications.di

import com.countflow.core.notifications.AndroidNotificationAlarmScheduler
import com.countflow.core.notifications.AndroidNotificationReminderScheduler
import com.countflow.core.notifications.AndroidNotificationSender
import com.countflow.core.notifications.NotificationAlarmScheduler
import com.countflow.core.notifications.NotificationReminderScheduler
import com.countflow.core.notifications.NotificationSender
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the reminder-scheduling seams to their real Android implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsModule {

    @Binds
    @Singleton
    abstract fun bindsNotificationReminderScheduler(
        impl: AndroidNotificationReminderScheduler,
    ): NotificationReminderScheduler

    @Binds
    @Singleton
    abstract fun bindsNotificationAlarmScheduler(impl: AndroidNotificationAlarmScheduler): NotificationAlarmScheduler

    @Binds
    @Singleton
    abstract fun bindsNotificationSender(impl: AndroidNotificationSender): NotificationSender
}
