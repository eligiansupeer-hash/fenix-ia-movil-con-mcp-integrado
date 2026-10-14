package com.fenix.mobilemcp.data.model

import com.fenix.mobilemcp.services.notifications.NotificationData

data class NotificationChangeEvent(
    val eventType: NotificationChangeType,
    val notification: NotificationData,
)

enum class NotificationChangeType {
    POSTED,
    REMOVED,
}
