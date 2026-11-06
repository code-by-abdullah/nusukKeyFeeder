package com.keyfeeder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private val h = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        val keysBox = EditText(this).apply {
            hint = "Keys paste karo (ek line me ek key)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            gravity = Gravity.TOP
        }

        val delayBox = EditText(this).apply {
            hint = "Har key ke baad wait (seconds)"
            setText("3")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val accBtn = Button(this).apply {
            text = "1) Accessibility ON karo"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }

        val startBtn = Button(this).apply {
            text = "2) Start"
            setOnClickListener {
                val list = keysBox.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (list.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Keys daalo", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Runner.keys = list
                Runner.index = 0
                Runner.failed = mutableListOf()
                Runner.lastMsg = "start hua, Nusuk Keys screen ka wait"
                Runner.delayMs = ((delayBox.text.toString().toDoubleOrNull() ?: 3.0) * 1000).toLong().coerceAtLeast(1000L)
                Runner.running = true
                Toast.makeText(this@MainActivity, "Ab Nusuk > Keys screen kholo", Toast.LENGTH_LONG).show()
                moveTaskToBack(true)
            }
        }

        val stopBtn = Button(this).apply {
            text = "Stop"
            setOnClickListener { Runner.running = false }
        }

        val debugBtn = Button(this).apply {
            text = "Debug (screen kya dikh rahi hai)"
            setOnClickListener {
                Runner.debugText = ""
                Runner.debugRequested = true
                Toast.makeText(this@MainActivity, "5 sec me Nusuk > Keys screen kholo, phir wapas yahan aao", Toast.LENGTH_LONG).show()
                moveTaskToBack(true)
            }
        }

        status = TextView(this).apply {
            setPadding(0, pad, 0, 0)
            textSize = 12f
            setTextIsSelectable(true)
        }

        listOf(keysBox, delayBox, accBtn, startBtn, stopBtn, debugBtn, status).forEach { root.addView(it) }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        h.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(ticker)
    }

    private fun refresh() {
        val failed = if (Runner.failed.isEmpty()) "" else "\nFail: " + Runner.failed.joinToString(", ")
        val dbg = if (Runner.debugText.isEmpty()) "" else "\n\nDEBUG:\n" + Runner.debugText
        status.text = "Progress: ${Runner.index} / ${Runner.keys.size}  (${if (Runner.running) "chal raha hai" else "ruka hua"})\n" +
            "Status: ${Runner.lastMsg}$failed$dbg"
    }
}
