package com.example.autorunstudio

import org.json.JSONArray
import org.json.JSONObject

/** A normalized action that can be recorded, edited, saved, and replayed. */
data class GestureAction(
    var type: String,
    var x: Float,
    var y: Float,
    var x2: Float = x,
    var y2: Float = y,
    var durationMs: Long = 80L,
    var delayMs: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        put("x", x)
        put("y", y)
        put("x2", x2)
        put("y2", y2)
        put("durationMs", durationMs)
        put("delayMs", delayMs)
    }

    companion object {
        fun fromJson(o: JSONObject) = GestureAction(
            type = o.optString("type", "tap"),
            x = o.optDouble("x", 0.5).toFloat(),
            y = o.optDouble("y", 0.5).toFloat(),
            x2 = o.optDouble("x2", o.optDouble("x", 0.5)).toFloat(),
            y2 = o.optDouble("y2", o.optDouble("y", 0.5)).toFloat(),
            durationMs = o.optLong("durationMs", 80L),
            delayMs = o.optLong("delayMs", 0L)
        )
    }
}

data class AutomationCase(
    var name: String,
    var actions: MutableList<GestureAction>,
    var visionEnabled: Boolean = true,
    var purpleMinPixels: Int = 50,
    var visionIntervalMs: Long = 250L,
    var tripleTapGapMs: Long = 70L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("visionEnabled", visionEnabled)
        put("purpleMinPixels", purpleMinPixels)
        put("visionIntervalMs", visionIntervalMs)
        put("tripleTapGapMs", tripleTapGapMs)
        put("actions", JSONArray().also { arr -> actions.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): AutomationCase {
            val list = mutableListOf<GestureAction>()
            val a = o.optJSONArray("actions") ?: JSONArray()
            for (i in 0 until a.length()) list += GestureAction.fromJson(a.getJSONObject(i))
            return AutomationCase(
                name = o.optString("name", "Unnamed Case"),
                actions = list,
                visionEnabled = o.optBoolean("visionEnabled", true),
                purpleMinPixels = o.optInt("purpleMinPixels", 50),
                visionIntervalMs = o.optLong("visionIntervalMs", 250L),
                tripleTapGapMs = o.optLong("tripleTapGapMs", 70L)
            )
        }
    }
}

object CaseStore {
    private const val PREFS = "automation_cases"
    private const val KEY = "cases"

    fun load(context: android.content.Context): MutableList<AutomationCase> {
        val raw = context.getSharedPreferences(PREFS, 0).getString(KEY, null) ?: return mutableListOf(defaultCase(context))
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { AutomationCase.fromJson(arr.getJSONObject(it)) }
        }.getOrElse { mutableListOf(defaultCase(context)) }
    }

    fun save(context: android.content.Context, cases: List<AutomationCase>) {
        val arr = JSONArray()
        cases.forEach { arr.put(it.toJson()) }
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY, arr.toString()).apply()
    }

    fun defaultCase(context: android.content.Context) = AutomationCase(
        name = context.getString(R.string.default_case_name),
        actions = mutableListOf(
            GestureAction("tap", 0.50f, 0.82f),
            GestureAction("swipe", 0.50f, 0.68f, 0.50f, 0.32f, 550L, 250L),
            GestureAction("long_press", 0.50f, 0.50f, durationMs = 700L, delayMs = 350L)
        )
    )
}
