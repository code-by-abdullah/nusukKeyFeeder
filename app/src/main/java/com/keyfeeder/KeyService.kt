package com.keyfeeder

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class KeyService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var alive = false
    private var worker: Thread? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        alive = true
        worker = Thread { loop() }.also { it.isDaemon = true; it.start() }
    }

    override fun onDestroy() {
        alive = false
        super.onDestroy()
    }

    private fun toast(msg: String) = main.post { Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show() }

    private fun loop() {
        var attempts = 0
        while (alive) {
            if (!Runner.running) { sleep(500); continue }

            if (Runner.index >= Runner.keys.size) {
                Runner.running = false
                toast("Done: ${Runner.keys.size} keys")
                continue
            }

            val root = rootInActiveWindow
            val field = root?.let { findEdit(it) }
            val button = root?.let { findClickableByLabel(it, "Check the code") }

            if (field == null || button == null) {
                // Shayad result popup khula hai; ek baar Back
                if (root != null && findByLabel(root, "Your keys") == null && attempts++ > 3) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    attempts = 0
                }
                sleep(700); continue
            }
            attempts = 0

            val key = Runner.keys[Runner.index]
            field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, key)
            }
            field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            sleep(700)

            var clicked = false
            for (i in 0 until 4) {
                val r = rootInActiveWindow ?: break
                val b = findClickableByLabel(r, "Check the code")
                if (b != null && b.isEnabled) {
                    clicked = b.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) break
                }
                sleep(500)
            }

            if (!clicked) Runner.failed.add(key)
            Runner.index++
            sleep(Runner.delayMs)

            // Result dialog aaya ho to band karo
            val after = rootInActiveWindow
            if (after != null && findEdit(after) == null) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                sleep(800)
            }
        }
    }

    private fun sleep(ms: Long) = try { Thread.sleep(ms) } catch (_: InterruptedException) {}

    private fun findEdit(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.className?.toString()?.contains("EditText") == true && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findEdit(c)?.let { return it }
        }
        return null
    }

    private fun matches(n: AccessibilityNodeInfo, label: String) =
        n.text?.toString()?.trim().equals(label, true) ||
        n.contentDescription?.toString()?.trim().equals(label, true)

    private fun findByLabel(n: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (matches(n, label)) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findByLabel(c, label)?.let { return it }
        }
        return null
    }

    private fun findClickableByLabel(n: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        var node = findByLabel(n, label) ?: return null
        while (!node.isClickable) node = node.parent ?: return null
        return node
    }
}
