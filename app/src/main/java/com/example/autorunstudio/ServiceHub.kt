package com.example.autorunstudio

import android.os.Handler
import android.os.Looper
import android.util.Log

object ServiceHub {
    private var accessibility: AutomationAccessibilityService? = null
    private var screenCapture: ScreenCaptureService? = null
    val mainHandler = Handler(Looper.getMainLooper())

    fun attach(service: AutomationAccessibilityService) { accessibility = service }
    fun detach(service: AutomationAccessibilityService) { if (accessibility === service) accessibility = null }
    fun attach(service: ScreenCaptureService) { screenCapture = service }
    fun detach(service: ScreenCaptureService) { if (screenCapture === service) screenCapture = null }

    fun accessibility(): AutomationAccessibilityService? = accessibility
    fun startRecording() = accessibility?.startRecording()
    fun pauseRecording() = accessibility?.pauseRecording()
    fun resumeRecording() = accessibility?.resumeRecording()
    fun stopRecording() = accessibility?.stopRecording()
    fun startCase(case: AutomationCase) = accessibility?.startCase(case)
    fun pauseCase() = accessibility?.pauseCase()
    fun resumeCase() = accessibility?.resumeCase()
    fun stopCase() = accessibility?.stopCase()

    fun log(message: String) = Log.d("AutoRunStudio", message)
}
