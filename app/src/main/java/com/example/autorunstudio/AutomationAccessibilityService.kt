package com.example.autorunstudio

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot

class AutomationAccessibilityService : AccessibilityService() {
    private var ball: View? = null
    private var ballMenu: LinearLayout? = null
    private var recordOverlay: RecorderOverlayView? = null
    private var runningCase = false
    private var casePaused = false
    private var automationThread: Thread? = null
    private var currentCase: AutomationCase? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceHub.attach(this)
        showFloatingBall()
    }

    override fun onDestroy() {
        automationThread?.interrupt()
        hideFloatingBall()
        hideRecordOverlay()
        ServiceHub.detach(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun showFloatingBall() {
        if (ball != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            text = "●"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xAA222222.toInt())
            setPadding(12, 6, 12, 6)
            setOnClickListener { toggleMenu() }
            setOnTouchListener(DragTouchListener())
        }
        val lp = WindowManager.LayoutParams(
            56, 56,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 180 }
        wm.addView(view, lp)
        ball = view
    }

    fun hideFloatingBall() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        ball?.let { runCatching { wm.removeView(it) } }
        ball = null
        ballMenu?.let { runCatching { wm.removeView(it) } }
        ballMenu = null
    }

    private fun toggleMenu() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (ballMenu != null) {
            ballMenu?.let { runCatching { wm.removeView(it) } }
            ballMenu = null
            return
        }
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xF0202024.toInt())
        }
        fun add(label: String, action: () -> Unit) {
            menu.addView(Button(this).apply { text = label; setOnClickListener { action(); toggleMenu() } })
        }
        add(getString(R.string.menu_start_recording)) { startRecording() }
        add(getString(R.string.menu_pause_recording)) { pauseRecording() }
        add(getString(R.string.menu_resume_recording)) { resumeRecording() }
        add(getString(R.string.menu_end_recording)) { stopRecording() }
        add(getString(R.string.menu_run_case)) { CaseStore.load(this).firstOrNull()?.let { startCase(it) } }
        add(getString(R.string.menu_pause_case)) { pauseCase() }
        add(getString(R.string.menu_resume_case)) { resumeCase() }
        add(getString(R.string.menu_end_case)) { stopCase() }
        val lp = WindowManager.LayoutParams(
            340, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 88; y = 160 }
        wm.addView(menu, lp)
        ballMenu = menu
    }

    fun startRecording() {
        if (recordOverlay == null) {
            recordOverlay = RecorderOverlayView(this).also { addOverlay(it) }
        }
        recordOverlay?.start()
    }

    fun pauseRecording() = recordOverlay?.pause()
    fun resumeRecording() = recordOverlay?.resume()

    fun stopRecording() {
        recordOverlay?.stop()
        val events = recordOverlay?.actions?.toMutableList() ?: mutableListOf()
        hideRecordOverlay()
        if (events.isNotEmpty()) {
            val cases = CaseStore.load(this)
            val base = AutomationCase(getString(R.string.recorded_case_name, System.currentTimeMillis()), events)
            cases += base
            CaseStore.save(this, cases)
        }
    }

    private fun addOverlay(view: View) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(view, lp)
    }

    private fun hideRecordOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        recordOverlay?.let { runCatching { wm.removeView(it) } }
        recordOverlay = null
    }

    fun tapNormalized(x: Float, y: Float) {
        dispatchSingleTap((x.coerceIn(0f, 1f) * screenWidth()).toFloat(), (y.coerceIn(0f, 1f) * screenHeight()).toFloat())
    }

    fun tripleTapNormalized(x: Float, y: Float, gapMs: Long = 70L) {
        Thread {
            repeat(3) { idx ->
                dispatchSingleTap((x * screenWidth()).toFloat(), (y * screenHeight()).toFloat())
                if (idx < 2) SystemClock.sleep(gapMs)
            }
        }.start()
    }

    private fun dispatchSingleTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels
    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    fun startCase(case: AutomationCase) {
        if (runningCase) return
        currentCase = case
        runningCase = true
        casePaused = false
        automationThread = Thread {
            for (action in case.actions) {
                if (!runningCase) break
                while (casePaused && runningCase) SystemClock.sleep(60)
                if (!runningCase) break
                SystemClock.sleep(action.delayMs)
                execute(action)
            }
            runningCase = false
        }.also { it.start() }
    }

    private fun execute(action: GestureAction) {
        val w = screenWidth(); val h = screenHeight()
        val x = action.x.coerceIn(0f, 1f) * w
        val y = action.y.coerceIn(0f, 1f) * h
        val x2 = action.x2.coerceIn(0f, 1f) * w
        val y2 = action.y2.coerceIn(0f, 1f) * h
        val path = Path()
        when (action.type) {
            "swipe" -> { path.moveTo(x, y); path.lineTo(x2, y2) }
            else -> path.moveTo(x, y)
        }
        val duration = action.durationMs.coerceIn(40L, 8000L)
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build(), null, null)
    }

    fun pauseCase() { if (runningCase) casePaused = true }
    fun resumeCase() { if (runningCase) casePaused = false }
    fun stopCase() { runningCase = false; casePaused = false; automationThread?.interrupt() }

    private inner class DragTouchListener : View.OnTouchListener {
        var downX = 0; var downY = 0; var startX = 0; var startY = 0
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = ball?.layoutParams as? WindowManager.LayoutParams ?: return false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX.toInt(); downY = e.rawY.toInt(); startX = lp.x; startY = lp.y; return true }
                MotionEvent.ACTION_MOVE -> { lp.x = startX + (e.rawX - downX).toInt(); lp.y = startY + (e.rawY - downY).toInt(); (getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(v, lp); return true }
            }
            return false
        }
    }

    class RecorderOverlayView(private val service: AutomationAccessibilityService) : View(service) {
        val actions = mutableListOf<GestureAction>()
        private var active = false
        private var paused = false
        private var downX = 0f; private var downY = 0f; private var downT = 0L

        fun start() { active = true; paused = false; invalidate() }
        fun pause() { paused = true; invalidate() }
        fun resume() { paused = false; invalidate() }
        fun stop() { active = false; invalidate() }

        override fun onDraw(canvas: android.graphics.Canvas) {
            if (!active) return
            canvas.drawColor(0x01000000)
            val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.FILL; alpha = 220 }
            canvas.drawCircle(downX, downY, 12f, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!active || paused) return true
            val now = SystemClock.elapsedRealtime()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; downT = now; return true }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX; val dy = event.y - downY
                    val dist = hypot(dx.toDouble(), dy.toDouble())
                    val duration = (now - downT).coerceAtLeast(1L)
                    val w = width.coerceAtLeast(1).toFloat(); val h = height.coerceAtLeast(1).toFloat()
                    val type = when { dist >= 50.0 -> "swipe"; duration >= 550 -> "long_press"; else -> "tap" }
                    val a = if (type == "swipe") GestureAction(type, downX / w, downY / h, event.x / w, event.y / h, duration) else GestureAction(type, downX / w, downY / h, durationMs = duration)
                    a.delayMs = if (actions.isEmpty()) 0L else 80L
                    actions += a
                    service.dispatchRecorded(a)
                    invalidate()
                    return true
                }
            }
            invalidate(); return true
        }
    }

    private fun dispatchRecorded(action: GestureAction) {
        val w = screenWidth(); val h = screenHeight()
        val path = Path().apply {
            moveTo(action.x * w, action.y * h)
            if (action.type == "swipe") lineTo(action.x2 * w, action.y2 * h)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, action.durationMs.coerceIn(40L, 8000L))
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
