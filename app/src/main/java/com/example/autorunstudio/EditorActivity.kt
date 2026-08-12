package com.example.autorunstudio

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.*

class EditorActivity : Activity() {
    private val cases by lazy { CaseStore.load(this) }
    private var current = 0
    private lateinit var timeline: TimelineView
    private lateinit var typeSpinner: Spinner
    private lateinit var xField: EditText
    private lateinit var yField: EditText
    private lateinit var durationField: EditText
    private lateinit var delayField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 24, 18, 18); setBackgroundColor(0xFF101114.toInt()) }
        val title = TextView(this).apply { text = getString(R.string.editor_title); textSize = 24f; setTextColor(Color.WHITE) }
        root.addView(title)
        val casePicker = Spinner(this).apply { adapter = ArrayAdapter(this@EditorActivity, android.R.layout.simple_spinner_dropdown_item, cases.map { it.name }) }
        casePicker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { current = position; timeline.updateActions(cases[current].actions) }
        }
        root.addView(casePicker)
        timeline = TimelineView(this)
        root.addView(timeline, LinearLayout.LayoutParams(-1, 240))

        typeSpinner = Spinner(this).apply { adapter = ArrayAdapter(this@EditorActivity, android.R.layout.simple_spinner_dropdown_item, listOf(getString(R.string.type_tap), getString(R.string.type_long_press), getString(R.string.type_swipe))) }
        root.addView(typeSpinner)
        xField = field(getString(R.string.x_normalized))
        yField = field(getString(R.string.y_normalized))
        durationField = field(getString(R.string.duration_ms))
        delayField = field(getString(R.string.delay_ms))
        root.addView(xField); root.addView(yField); root.addView(durationField); root.addView(delayField)

        val save = Button(this).apply { text = getString(R.string.save_changes); setOnClickListener { saveSelected() } }
        root.addView(save)
        val delete = Button(this).apply { text = getString(R.string.delete_selected); setOnClickListener { if (timeline.selected >= 0 && cases[current].actions.isNotEmpty()) { cases[current].actions.removeAt(timeline.selected.coerceAtMost(cases[current].actions.lastIndex)); timeline.updateActions(cases[current].actions); CaseStore.save(this@EditorActivity, cases) } } }
        root.addView(delete)
        setContentView(root)
    }

    private fun field(hint: String) = EditText(this).apply { this.hint = hint; setTextColor(Color.WHITE); setHintTextColor(0xFF858893.toInt()); setPadding(12, 6, 12, 6) }

    private fun saveSelected() {
        val i = timeline.selected
        if (i < 0 || i >= cases[current].actions.size) return
        val a = cases[current].actions[i]
        a.type = when (typeSpinner.selectedItem?.toString()) { getString(R.string.type_swipe) -> "swipe"; getString(R.string.type_long_press) -> "long_press"; else -> "tap" }
        a.x = xField.text.toString().toFloatOrNull() ?: a.x
        a.y = yField.text.toString().toFloatOrNull() ?: a.y
        a.durationMs = durationField.text.toString().toLongOrNull() ?: a.durationMs
        a.delayMs = delayField.text.toString().toLongOrNull() ?: a.delayMs
        CaseStore.save(this, cases)
        timeline.invalidate()
    }

    inner class TimelineView(context: android.content.Context) : View(context) {
        var actions: MutableList<GestureAction> = mutableListOf()
        var selected = -1
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun updateActions(list: MutableList<GestureAction>) { actions = list; selected = if (list.isNotEmpty()) 0 else -1; invalidate() }
        override fun onDraw(c: Canvas) {
            c.drawColor(0xFF191B20.toInt())
            paint.color = 0xFF555963.toInt(); paint.strokeWidth = 4f
            c.drawLine(20f, height / 2f, width - 20f, height / 2f, paint)
            paint.color = Color.WHITE
            for ((i, a) in actions.withIndex()) {
                val x = 20f + (width - 40f) * a.x.coerceIn(0f, 1f)
                val y = height / 2f
                c.drawCircle(x, y, if (i == selected) 16f else 11f, paint)
            }
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    selected = nearest(e.x)
                    invalidate();
                    return true
                }
                MotionEvent.ACTION_MOVE -> if (selected >= 0) {
                    val nx = ((e.x - 20f) / (width - 40f)).coerceIn(0f, 1f)
                    actions[selected].x = nx
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> return true
            }
            return true
        }
        private fun nearest(px: Float): Int {
            if (actions.isEmpty()) return -1
            var best = 0; var bestD = Float.MAX_VALUE
            for (i in actions.indices) {
                val x = 20f + (width - 40f) * actions[i].x.coerceIn(0f, 1f)
                val d = kotlin.math.abs(px - x)
                if (d < bestD) { bestD = d; best = i }
            }
            return best
        }
    }
}
