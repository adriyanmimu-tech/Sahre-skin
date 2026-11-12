package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemoteControlAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        Log.i(TAG, "RemoteControlAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString().orEmpty()
            val cls = event.className?.toString().orEmpty()
            if (pkg.isNotBlank() && !pkg.contains("systemui")) {
                val friendly = formatFriendlyAppName(pkg, cls)
                _activeForegroundApp.value = friendly
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "RemoteControlAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        _isServiceConnected.value = false
    }

    fun performRemoteTap(xPermille: Long, yPermille: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val (width, height) = getScreenDimensions()
        val x = (xPermille.coerceIn(0L, 1000L) / 1000f) * width
        val y = (yPermille.coerceIn(0L, 1000L) / 1000f) * height

        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        _lastExecutedGestureSummary.value = "TAP (${x.toInt()}, ${y.toInt()})"
        return dispatchGesture(gesture, null, null)
    }

    fun performRemoteLongPress(xPermille: Long, yPermille: Long, durationMs: Long = 650L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val (width, height) = getScreenDimensions()
        val x = (xPermille.coerceIn(0L, 1000L) / 1000f) * width
        val y = (yPermille.coerceIn(0L, 1000L) / 1000f) * height

        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMs.coerceIn(300L, 3000L)
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        _lastExecutedGestureSummary.value = "LONG PRESS (${x.toInt()}, ${y.toInt()})"
        return dispatchGesture(gesture, null, null)
    }

    fun performRemoteSwipe(
        startXPermille: Long,
        startYPermille: Long,
        endXPermille: Long,
        endYPermille: Long,
        durationMs: Long = 280L
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val (width, height) = getScreenDimensions()
        val startX = (startXPermille.coerceIn(0L, 1000L) / 1000f) * width
        val startY = (startYPermille.coerceIn(0L, 1000L) / 1000f) * height
        val endX = (endXPermille.coerceIn(0L, 1000L) / 1000f) * width
        val endY = (endYPermille.coerceIn(0L, 1000L) / 1000f) * height

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMs.coerceIn(80L, 2500L)
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        _lastExecutedGestureSummary.value =
            "SWIPE (${startX.toInt()},${startY.toInt()} → ${endX.toInt()},${endY.toInt()})"
        return dispatchGesture(gesture, null, null)
    }

    fun performSystemNavigation(commandType: String): Boolean {
        val action = when (commandType) {
            "GLOBAL_BACK" -> GLOBAL_ACTION_BACK
            "GLOBAL_HOME" -> GLOBAL_ACTION_HOME
            "GLOBAL_RECENTS" -> GLOBAL_ACTION_RECENTS
            "GLOBAL_NOTIFICATIONS" -> GLOBAL_ACTION_NOTIFICATIONS
            "GLOBAL_LOCK" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                GLOBAL_ACTION_LOCK_SCREEN
            } else {
                GLOBAL_ACTION_HOME
            }
            else -> return false
        }
        _lastExecutedGestureSummary.value = commandType
        return performGlobalAction(action)
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            Pair(bounds.width().coerceAtLeast(720), bounds.height().coerceAtLeast(1280))
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels.coerceAtLeast(720), dm.heightPixels.coerceAtLeast(1280))
        }
    }

    private fun formatFriendlyAppName(packageName: String, className: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(appInfo).toString()
            val shortScreen = className.substringAfterLast('.')
            if (shortScreen.isNotBlank() && !shortScreen.equals(label, ignoreCase = true)) {
                "$label • $shortScreen"
            } else {
                label
            }
        } catch (e: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    companion object {
        private const val TAG = "RemoteControlA11y"

        @Volatile
        var instance: RemoteControlAccessibilityService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        private val _activeForegroundApp = MutableStateFlow("QuickLink Remote • Dashboard")
        val activeForegroundApp: StateFlow<String> = _activeForegroundApp.asStateFlow()

        private val _lastExecutedGestureSummary = MutableStateFlow("Ready for remote gestures")
        val lastExecutedGestureSummary: StateFlow<String> = _lastExecutedGestureSummary.asStateFlow()

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            if (instance != null) return true
            val expectedComponentName =
                "${context.packageName}/${RemoteControlAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabledServices.contains(expectedComponentName, ignoreCase = true)
        }
    }
}
