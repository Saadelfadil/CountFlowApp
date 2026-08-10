package com.countflow.feature.settings.notification

/**
 * Whether CountFlow's notifications will actually reach the user right now.
 *
 * Deliberately not the raw `POST_NOTIFICATIONS` runtime permission — that only exists from
 * API 33, and checking it directly on older versions is exactly the "misleading messaging" this
 * screen must avoid, since a pre-33 user can still disable notifications for the app through the
 * classic per-app notification toggle without ever seeing a runtime prompt. [areNotificationsEnabled]
 * is the one check that reflects the real outcome — "will a notification show" — uniformly across
 * every supported Android version.
 */
fun interface NotificationStatusProvider {
    fun areNotificationsEnabled(): Boolean
}
