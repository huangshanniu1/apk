package com.example.autorunstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.util.concurrent.atomic.AtomicBoolean

interface VisionDetector {
    data class Detection(val kind: String, val centerX: Float, val centerY: Float, val score: Float)
    fun detect(frame: Bitmap): List<Detection>
}

/** Local HSV detector for purple-or-better item highlights. */
class PurpleRegionDetector(private val minPixels: Int = 50) : VisionDetector {
    override fun detect(frame: Bitmap): List<VisionDetector.Detection> {
        val sampleStep = 4
        var count = 0
        var sx = 0L; var sy = 0L
        val hsv = FloatArray(3)
        for (y in 0 until frame.height step sampleStep) {
            for (x in 0 until frame.width step sampleStep) {
                Color.colorToHSV(frame.getPixel(x, y), hsv)
                val hue = hsv[0]; val sat = hsv[1]; val value = hsv[2]
                if (hue in 260f..315f && sat >= 0.35f && value >= 0.25f) {
                    count++; sx += x.toLong(); sy += y.toLong()
                }
            }
        }
        if (count < minPixels) return emptyList()
        val cx = (sx.toFloat() / count / frame.width).coerceIn(0f, 1f)
        val cy = (sy.toFloat() / count / frame.height).coerceIn(0f, 1f)
        return listOf(VisionDetector.Detection("purple_item", cx, cy, (count / 1000f).coerceAtMost(1f)))
    }
}

/** Template detector hook. Replace assets/search_template.png with a screenshot crop from your own game. */
class SearchTemplateDetector(private val template: Bitmap?, private val threshold: Float = 0.90f) : VisionDetector {
    override fun detect(frame: Bitmap): List<VisionDetector.Detection> {
        val t = template ?: return emptyList()
        if (t.width > frame.width || t.height > frame.height) return emptyList()
        val scaleX = frame.width.toFloat() / 1080f
        val scaleY = frame.height.toFloat() / 1920f
        val tw = (t.width * scaleX).toInt().coerceAtLeast(8)
        val th = (t.height * scaleY).toInt().coerceAtLeast(8)
        val step = 16
        val top = (frame.height * 0.35f).toInt().coerceAtMost(frame.height - th)
        var best = -1f; var bestX = 0; var bestY = top
        for (y in top..(frame.height - th) step step) {
            for (x in 0..(frame.width - tw) step step) {
                var score = 0f; var n = 0
                for (ty in 0 until th step 8) for (tx in 0 until tw step 8) {
                    val a = Color.luminance(frame.getPixel(x + tx, y + ty))
                    val b = Color.luminance(t.getPixel((tx * t.width / tw).coerceIn(0, t.width - 1), (ty * t.height / th).coerceIn(0, t.height - 1)))
                    score += 1f - kotlin.math.abs(a - b)
                    n++
                }
                val s = score / n.coerceAtLeast(1)
                if (s > best) { best = s; bestX = x; bestY = y }
            }
        }
        if (best < threshold) return emptyList()
        return listOf(VisionDetector.Detection("search_button", (bestX + tw / 2f) / frame.width, (bestY + th / 2f) / frame.height, best))
    }
}

class VisionEngine(private val context: Context, private val settings: AutomationCase) {
    private val active = AtomicBoolean(false)
    private var thread: Thread? = null
    private var latestFrame: Bitmap? = null
    private val detectors: List<VisionDetector>

    init {
        val template = runCatching {
            context.assets.open("search_template.png").use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
        detectors = listOf(SearchTemplateDetector(template), PurpleRegionDetector(settings.purpleMinPixels))
    }

    fun submit(frame: Bitmap) { latestFrame?.recycle(); latestFrame = frame.copy(Bitmap.Config.ARGB_8888, false) }

    fun start(onDetection: (VisionDetector.Detection) -> Unit) {
        if (!active.compareAndSet(false, true)) return
        thread = Thread {
            while (active.get()) {
                val frame = latestFrame
                if (frame != null && settings.visionEnabled) detectors.flatMap { it.detect(frame) }.forEach(onDetection)
                Thread.sleep(settings.visionIntervalMs.coerceAtLeast(120L))
            }
        }.also { it.start() }
    }

    fun stop() { active.set(false); thread?.interrupt(); thread = null; latestFrame?.recycle(); latestFrame = null }
}
