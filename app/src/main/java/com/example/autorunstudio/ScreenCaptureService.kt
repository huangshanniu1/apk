package com.example.autorunstudio

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var vision: VisionEngine? = null
    private var lastSearch = 0L
    private var lastPurple = 0L

    override fun onCreate() { super.onCreate(); ServiceHub.attach(this); createNotificationChannel(); startForeground(9, notification()) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA) ?: return START_NOT_STICKY
        startCapture(resultCode, data)
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection?.stop()
        projection = mgr.getMediaProjection(resultCode, data)
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        val density = resources.displayMetrics.densityDpi
        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bitmap = imageToBitmap(image) ?: return@setOnImageAvailableListener
                if (vision == null) {
                    val selected = CaseStore.load(this).firstOrNull() ?: CaseStore.defaultCase(this)
                    vision = VisionEngine(this, selected).also { engine ->
                        engine.start { detection ->
                            val now = System.currentTimeMillis()
                            when (detection.kind) {
                                "search_button" -> if (now - lastSearch > 1000) {
                                    lastSearch = now
                                    ServiceHub.accessibility()?.tapNormalized(detection.centerX, detection.centerY)
                                }
                                "purple_item" -> if (now - lastPurple > 1300) {
                                    lastPurple = now
                                    val c = CaseStore.load(this).firstOrNull() ?: CaseStore.defaultCase(this)
                                    ServiceHub.accessibility()?.tripleTapNormalized(detection.centerX, detection.centerY, c.tripleTapGapMs)
                                }
                            }
                        }
                    }
                }
                vision?.submit(bitmap)
                bitmap.recycle()
            } finally { image.close() }
        }, ServiceHub.mainHandler)
        display = projection!!.createVirtualDisplay("AutoRunVision", width, height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader!!.surface, null, null)
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also { bitmap.recycle() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("vision", getString(R.string.vision_channel_name), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification() = Notification.Builder(this, "vision")
        .setContentTitle(getString(R.string.vision_notification_title))
        .setContentText(getString(R.string.vision_notification_text))
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .build()

    override fun onDestroy() {
        vision?.stop(); vision = null
        display?.release(); display = null
        imageReader?.close(); imageReader = null
        projection?.stop(); projection = null
        ServiceHub.detach(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "result_data"

        fun start(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stop(context: Context) = context.startService(Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP))
        fun startWithPermission(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply { putExtra(EXTRA_RESULT_CODE, resultCode); putExtra(EXTRA_DATA, data) }
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
