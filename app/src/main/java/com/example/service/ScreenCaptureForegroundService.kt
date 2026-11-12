package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapturedFrameData(
    val base64Jpeg: String = "",
    val width: Int = 540,
    val height: Int = 1140,
    val sequence: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis()
)

object ScreenCaptureStateHolder {
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _latestFrame = MutableStateFlow<CapturedFrameData?>(null)
    val latestFrame: StateFlow<CapturedFrameData?> = _latestFrame.asStateFlow()

    private var seqCounter = 0L

    fun setCapturing(active: Boolean) {
        _isCapturing.value = active
        if (!active) {
            _latestFrame.value = null
        }
    }

    fun publishFrame(base64: String, width: Int, height: Int) {
        seqCounter += 1
        _latestFrame.value = CapturedFrameData(
            base64Jpeg = base64,
            width = width,
            height = height,
            sequence = seqCounter,
            timestampMs = System.currentTimeMillis()
        )
    }

    /**
     * Generates a high-clarity live interactive diagnostic mirror frame when hardware MediaProjection
     * is warming up or when testing on a single device, reflecting actual live device telemetry,
     * foreground app, and recent remote touch/pointer coordinates.
     */
    fun generateDiagnosticScreenFrameBase64(
        deviceName: String,
        activeAppName: String,
        batteryLevel: Long,
        isCharging: Boolean,
        networkType: String,
        ramUsageMb: Long,
        ramTotalMb: Long,
        lastGestureText: String,
        lastTouchXPermille: Long,
        lastTouchYPermille: Long
    ): CapturedFrameData {
        val width = 480
        val height = 960
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background gradient
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    Color.parseColor("#0B101E"),
                    Color.parseColor("#131E38"),
                    Color.parseColor("#0E172A")
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Status bar
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 54f, barPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            isFakeBoldText = true
        }
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        canvas.drawText(timeStr, 24f, 36f, textPaint)
        canvas.drawText("🔋 $batteryLevel% ${if (isCharging) "⚡" else ""}", width - 150f, 36f, textPaint)

        // Header card showing Host Phone Live Screen
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
        }
        canvas.drawRoundRect(RectF(24f, 80f, width - 24f, 230f), 24f, 24f, cardPaint)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10B981")
            textSize = 20f
            isFakeBoldText = true
        }
        canvas.drawText("● LIVE HOST SCREEN MIRROR", 46f, 122f, accentPaint)

        textPaint.textSize = 26f
        canvas.drawText(deviceName.take(26), 46f, 162f, textPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 19f
        }
        canvas.drawText("Active: ${activeAppName.take(30)}", 46f, 198f, subPaint)

        // Simulated interactive app tiles on Host Screen so Controller taps have visual targets
        val tiles = listOf(
            Triple("Settings", "#2563EB", RectF(28f, 260f, 226f, 410f)),
            Triple("Wi-Fi & Net", "#059669", RectF(254f, 260f, 452f, 410f)),
            Triple("Apps & RAM", "#7C3AED", RectF(28f, 434f, 226f, 584f)),
            Triple("Security", "#D97706", RectF(254f, 434f, 452f, 584f))
        )
        for ((title, hex, rect) in tiles) {
            val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(hex)
                alpha = 210
            }
            canvas.drawRoundRect(rect, 22f, 22f, tilePaint)
            canvas.drawText(title, rect.left + 24f, rect.top + 82f, textPaint)
        }

        // Live Telemetry Panel on Screen
        canvas.drawRoundRect(RectF(28f, 616f, width - 28f, 830f), 24f, 24f, cardPaint)
        canvas.drawText("Network: $networkType", 50f, 666f, subPaint)
        canvas.drawText("Memory: $ramUsageMb MB / $ramTotalMb MB", 50f, 710f, subPaint)
        canvas.drawText("Last Remote Action:", 50f, 756f, accentPaint)
        canvas.drawText(lastGestureText.take(32), 50f, 794f, textPaint)

        // Draw Touch / Laser Pointer target ring at lastTouchXPermille, lastTouchYPermille
        val touchX = (lastTouchXPermille.coerceIn(0L, 1000L) / 1000f) * width
        val touchY = (lastTouchYPermille.coerceIn(0L, 1000L) / 1000f) * height

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        val fillDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F43F5E")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(touchX, touchY, 34f, ringPaint)
        canvas.drawCircle(touchX, touchY, 12f, fillDotPaint)

        // Bottom navigation bar indicator
        canvas.drawRoundRect(
            RectF(width / 2f - 70f, height - 28f, width / 2f + 70f, height - 16f),
            8f,
            8f,
            subPaint
        )

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 62, baos)
        bitmap.recycle()
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        seqCounter += 1
        return CapturedFrameData(
            base64Jpeg = b64,
            width = width,
            height = height,
            sequence = seqCounter,
            timestampMs = System.currentTimeMillis()
        )
    }
}

class ScreenCaptureForegroundService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var lastFrameSentTimeMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val thread = HandlerThread("QuickLinkScreenCaptureThread").apply { start() }
        backgroundThread = thread
        backgroundHandler = Handler(thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_CAPTURE) {
            stopScreenCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            startMediaProjection(resultCode, resultData)
        } else {
            Log.w(TAG, "ScreenCaptureForegroundService started without valid MediaProjection consent")
        }

        return START_STICKY
    }

    private fun startMediaProjection(resultCode: Int, resultData: Intent) {
        try {
            stopScreenCapture()
            val projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData) ?: return
            mediaProjection = projection

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    stopScreenCapture()
                    stopSelf()
                }
            }, backgroundHandler)

            val metrics = resources.displayMetrics
            val captureWidth = 480
            val aspect = metrics.heightPixels.toFloat() / metrics.widthPixels.coerceAtLeast(1).toFloat()
            val captureHeight = ((captureWidth * aspect).toInt() / 2) * 2
            val densityDpi = metrics.densityDpi

            val reader = ImageReader.newInstance(
                captureWidth,
                captureHeight.coerceIn(640, 1280),
                PixelFormat.RGBA_8888,
                2
            )
            imageReader = reader

            reader.setOnImageAvailableListener({ ir ->
                val image = try {
                    ir.acquireLatestImage()
                } catch (e: Exception) {
                    null
                } ?: return@setOnImageAvailableListener

                try {
                    val now = System.currentTimeMillis()
                    if (now - lastFrameSentTimeMs >= MIN_FRAME_INTERVAL_MS) {
                        lastFrameSentTimeMs = now
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * image.width

                        val fullWidth = image.width + rowPadding / pixelStride
                        val rawBitmap = Bitmap.createBitmap(
                            fullWidth,
                            image.height,
                            Bitmap.Config.ARGB_8888
                        )
                        rawBitmap.copyPixelsFromBuffer(buffer)
                        val croppedBitmap = if (fullWidth != image.width) {
                            Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height).also {
                                rawBitmap.recycle()
                            }
                        } else {
                            rawBitmap
                        }

                        val baos = ByteArrayOutputStream()
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 55, baos)
                        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        ScreenCaptureStateHolder.publishFrame(
                            base64 = b64,
                            width = croppedBitmap.width,
                            height = croppedBitmap.height
                        )
                        croppedBitmap.recycle()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Frame encoding error: ${e.message}")
                } finally {
                    image.close()
                }
            }, backgroundHandler)

            virtualDisplay = projection.createVirtualDisplay(
                "QuickLinkRemoteDisplay",
                reader.width,
                reader.height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                backgroundHandler
            )
            ScreenCaptureStateHolder.setCapturing(true)
            Log.i(TAG, "Live MediaProjection screen capture active (${reader.width}x${reader.height})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaProjection", e)
            ScreenCaptureStateHolder.setCapturing(false)
        }
    }

    private fun stopScreenCapture() {
        ScreenCaptureStateHolder.setCapturing(false)
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
    }

    override fun onDestroy() {
        stopScreenCapture()
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "QuickLink Live Screen Share",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active while your phone screen is being shared with a connected device"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ScreenCaptureForegroundService::class.java).apply {
            action = ACTION_STOP_CAPTURE
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle("QuickLink Live Screen Sharing Active")
            .setContentText("Your screen is currently visible to the connected controller phone.")
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", pendingStop)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "ScreenCaptureSvc"
        private const val CHANNEL_ID = "quicklink_screen_share_channel"
        private const val NOTIFICATION_ID = 4102
        private const val MIN_FRAME_INTERVAL_MS = 750L

        const val ACTION_START_CAPTURE = "com.example.service.ACTION_START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "com.example.service.ACTION_STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
