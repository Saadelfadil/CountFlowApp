package com.countflow.feature.settings.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [NotificationStatusProvider] backed by the real platform [NotificationManagerCompat]. */
@Singleton
internal class AndroidNotificationStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationStatusProvider {

    override fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
