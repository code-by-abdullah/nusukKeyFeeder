package com.keyfeeder

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class KeyService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var alive = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        alive = true
        Runner.serviceConnected = true
        Runner.lastMsg = "Service connected."
        Thread { loop() }.also { it.isDaemon = true; it.start() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        alive = false
        Runner.serviceConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        alive = false
        Runner.serviceConnected = false
        super.onDestroy()
    }

    private fun toast(msg: String) = main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }
    private fun sleep(ms: Long) = try { Thread.sleep(ms) } catch (_: InterruptedException) {}

    // ---------- helpers ----------
    private fun roots(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { list.add(it) }
        try {
            for (w in windows) w.root?.let { list.add(it) }
        } catch (_: Exception) {}
        return list
    }

    private fun label(n: AccessibilityNodeInfo): String =
        listOfNotNull(n.text, n.contentDescription, n.hintText).joinToString(" | ")

    private fun find(n: AccessibilityNodeInfo?, depth: Int = 0, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null || depth > 40) return null
        if (pred(n)) return n
        for (i in 0 until n.childCount) {
            val r = find(n.getChild(i), depth + 1, pred)
            if (r != null) return r
        }
        return null
    }

    private fun findField(rs: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        for (r in rs) {
            find(r) { it.isEditable || it.className?.toString()?.contains("EditText") == true }?.let { return it }
        }
        for (r in rs) {
            find(r) { label(it).contains("Add code to get new key", true) }?.let { return it }
        }
        return null
    }

    private fun findByLabel(rs: List<AccessibilityNodeInfo>, text: String): AccessibilityNodeInfo? {
        for (r in rs) {
            find(r) { label(it).contains(text, true) }?.let { return it }
        }
        return null
    }

    private fun tap(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 60))
            .build()
        return dispatchGesture(g, null, null)
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        return tap(r.centerX().toFloat(), r.centerY().toFloat())
    }

    private fun typeInto(field: AccessibilityNodeInfo, key: String): String {
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, key)
        }
        if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return "set text"

        // fallback: tap the field, then paste from the clipboard
        val r = Rect()
        field.getBoundsInScreen(r)
        tap(r.centerX().toFloat(), r.centerY().toFloat())
        sleep(600)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("key", key))
        return if (field.performAction(AccessibilityNodeInfo.ACTION_PASTE)) "paste" else "failed"
    }

    private fun dump(): String {
        val sb = StringBuilder()
        val rs = roots()
        sb.append("Windows found: ${rs.size}\n")
        var lines = 0
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || lines > 70 || d > 30) return
            val cls = n.className?.toString()?.substringAfterLast('.') ?: "?"
            val flags = (if (n.isClickable) "C" else "") + (if (n.isEnabled) "E" else "e") + (if (n.isEditable) "T" else "")
            val l = label(n)
            if (l.isNotEmpty() || n.isClickable || n.isEditable) {
                sb.append("  ".repeat(minOf(d, 6))).append(cls).append(" [").append(flags).append("] ").append(l.take(60)).append('\n')
                lines++
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        rs.firstOrNull()?.let { walk(it, 0) }
        return sb.toString().take(3500)
    }

    // ---------- main loop ----------
    private fun loop() {
        var noFieldCount = 0
        while (alive) {
            if (Runner.debugRequested) {
                sleep(5000)
                Runner.debugText = dump()
                Runner.debugRequested = false
                toast("Diagnosis ready. Open Key Feeder.")
                continue
            }
            if (!Runner.running) { sleep(500); continue }

            if (Runner.index >= Runner.keys.size) {
                Runner.running = false
                Runner.lastMsg = "Finished. ${Runner.keys.size} codes submitted."
                toast("Done: ${Runner.keys.size} codes submitted")
                continue
            }

            val rs = roots()
            if (rs.isEmpty()) {
                Runner.lastMsg = "Cannot read the screen. Open the Nusuk Keys screen."
                sleep(1000); continue
            }

            val field = findField(rs)
            if (field == null) {
                noFieldCount++
                val another = findByLabel(rs, "Add another code")
                if (another != null) {
                    click(another)
                    Runner.lastMsg = "Result screen: tapped \"Add another code\"."
                    noFieldCount = 0
                } else if (noFieldCount >= 3) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Runner.lastMsg = "Input field not found. Pressed Back; open the Nusuk Keys screen."
                    noFieldCount = 0
                } else {
                    Runner.lastMsg = "Input field not found. Open the Nusuk Keys screen."
                }
                sleep(1000); continue
            }
            noFieldCount = 0

            val key = Runner.keys[Runner.index]
            val how = typeInto(field, key)
            sleep(900)

            var clicked = false
            var btnState = "not found"
            for (i in 0 until 5) {
                val b = findByLabel(roots(), "Check the code")
                if (b != null) {
                    btnState = if (b.isEnabled) "enabled" else "disabled"
                    if (b.isEnabled) { clicked = click(b); if (clicked) break }
                }
                sleep(600)
            }

            Runner.lastMsg = "Code ${Runner.index + 1}: typed via $how, button $btnState, " +
                if (clicked) "tapped." else "tap failed."
            if (!clicked) Runner.failed.add(key)
            Runner.index++
            sleep(Runner.delayMs)
        }
    }
}
