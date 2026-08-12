package com.example.autorunstudio

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var caseSpinner: Spinner
    private lateinit var status: TextView
    private val cases by lazy { CaseStore.load(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(0xFF101114.toInt())
        }
        val title = TextView(this).apply { text = getString(R.string.main_title); textSize = 28f; setTextColor(0xFFFFFFFF.toInt()) }
        root.addView(title)
        status = TextView(this).apply { text = getString(R.string.main_subtitle); textSize = 14f; setTextColor(0xFFB9BCC5.toInt()); setPadding(0, 8, 0, 24) }
        root.addView(status)

        fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }
        root.addView(button(getString(R.string.enable_accessibility)) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        root.addView(button(getString(R.string.start_floating_ball)) { ServiceHub.accessibility()?.showFloatingBall() ?: update(getString(R.string.accessibility_required)) })
        root.addView(button(getString(R.string.start_recording)) { ServiceHub.startRecording() ?: update(getString(R.string.accessibility_required)) })
        root.addView(button(getString(R.string.edit_cases)) { startActivity(Intent(this, EditorActivity::class.java)) })

        caseSpinner = Spinner(this)
        caseSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cases.map { it.name })
        root.addView(caseSpinner)
        root.addView(button(getString(R.string.start_selected_case)) { ServiceHub.startCase(cases.getOrNull(caseSpinner.selectedItemPosition) ?: CaseStore.defaultCase(this)) ?: update(getString(R.string.accessibility_required)) })
        root.addView(button(getString(R.string.pause_case)) { ServiceHub.pauseCase() })
        root.addView(button(getString(R.string.resume_case)) { ServiceHub.resumeCase() })
        root.addView(button(getString(R.string.stop_case)) { ServiceHub.stopCase() })
        root.addView(button(getString(R.string.screen_capture_setup)) { requestScreenCapture() })
        root.addView(button(getString(R.string.start_vision)) { ScreenCaptureService.start(this) })
        root.addView(button(getString(R.string.stop_vision)) { ScreenCaptureService.stop(this) })
        root.addView(statusBlock())
        setContentView(root)
    }

    private fun statusBlock(): TextView = TextView(this).apply {
        text = "\n" + getString(R.string.vision_status)
        setTextColor(0xFF8E919C.toInt())
        textSize = 13f
    }

    private fun update(message: String) { status.text = message }

    private fun requestScreenCapture() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), 4001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4001 && resultCode == RESULT_OK && data != null) {
            ScreenCaptureService.startWithPermission(this, resultCode, data)
            update(getString(R.string.screen_capture_granted))
        }
    }
}
