package com.keyfeeder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

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
            minLines = 8
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
                Runner.delayMs = ((delayBox.text.toString().toDoubleOrNull() ?: 3.0) * 1000).toLong().coerceAtLeast(1000L)
                Runner.running = true
                Toast.makeText(this@MainActivity, "Ab Nusuk > Keys screen kholo", Toast.LENGTH_LONG).show()
                moveTaskToBack(true)
            }
        }

        val stopBtn = Button(this).apply {
            text = "Stop"
            setOnClickListener {
                Runner.running = false
                refresh()
            }
        }

        status = TextView(this).apply { setPadding(0, pad, 0, 0) }

        listOf(keysBox, delayBox, accBtn, startBtn, stopBtn, status).forEach { root.addView(it) }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val done = Runner.index
        val total = Runner.keys.size
        val failed = if (Runner.failed.isEmpty()) "" else "\nFail: " + Runner.failed.joinToString(", ")
        status.text = "Progress: $done / $total  (${if (Runner.running) "chal raha hai" else "ruka hua"})$failed"
    }
}
