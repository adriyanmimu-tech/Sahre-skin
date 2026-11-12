package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class RemoteSession(
    @DocumentId val id: String = "",
    val sessionCode: String = "",
    val accessPin: String = "",
    val hostUserId: String = "",
    val hostDeviceName: String = "",
    val controllerUserId: String = "",
    val controllerDeviceName: String = "",
    val status: String = "WAITING",
    val latestFrameBase64: String = "",
    val frameWidth: Long = 720L,
    val frameHeight: Long = 1600L,
    val frameSeq: Long = 0L,
    val batteryLevel: Long = 100L,
    val isCharging: Boolean = false,
    val networkType: String = "Wi-Fi",
    val ramUsageMb: Long = 2048L,
    val ramTotalMb: Long = 8192L,
    val storageFreeGb: Long = 64L,
    val activeAppName: String = "QuickLink Remote",
    val torchEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val screenCaptureEnabled: Boolean = false,
    val clipboardText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RemoteCommand(
    @DocumentId val id: String = "",
    val sessionId: String = "",
    val hostUserId: String = "",
    val senderUserId: String = "",
    val type: String = "TAP",
    val xPermille: Long = 500L,
    val yPermille: Long = 500L,
    val endXPermille: Long = 500L,
    val endYPermille: Long = 500L,
    val durationMs: Long = 80L,
    val payload: String = "",
    val executed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedDevice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionCode: String = "",
    val deviceName: String = "",
    val accessPin: String = "",
    val lastRole: String = "CONTROLLER",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DeviceTelemetrySnapshot(
    val deviceName: String = "Android Device",
    val batteryLevel: Long = 85L,
    val isCharging: Boolean = false,
    val networkType: String = "Wi-Fi • Connected",
    val ramUsageMb: Long = 3200L,
    val ramTotalMb: Long = 8192L,
    val storageFreeGb: Long = 64L,
    val activeAppName: String = "QuickLink Remote",
    val torchEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val screenCaptureEnabled: Boolean = false
)

object CommandType {
    const val TAP = "TAP"
    const val SWIPE = "SWIPE"
    const val LONG_PRESS = "LONG_PRESS"
    const val GLOBAL_BACK = "GLOBAL_BACK"
    const val GLOBAL_HOME = "GLOBAL_HOME"
    const val GLOBAL_RECENTS = "GLOBAL_RECENTS"
    const val GLOBAL_NOTIFICATIONS = "GLOBAL_NOTIFICATIONS"
    const val GLOBAL_LOCK = "GLOBAL_LOCK"
    const val TOGGLE_TORCH = "TOGGLE_TORCH"
    const val RING_DEVICE = "RING_DEVICE"
    const val SET_CLIPBOARD = "SET_CLIPBOARD"
    const val POINTER_MARK = "POINTER_MARK"
    const val CHAT_MESSAGE = "CHAT_MESSAGE"
    const val LAUNCH_APP = "LAUNCH_APP"
}
