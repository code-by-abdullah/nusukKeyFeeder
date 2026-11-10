package com.keyfeeder

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    // ---- palette ----
    private val BG = Color.parseColor("#F5F3EC")
    private val CARD = Color.WHITE
    private val LINE = Color.parseColor("#E3DFD3")
    private val INPUT_BG = Color.parseColor("#FAF9F5")
    private val INK = Color.parseColor("#1C1B1F")
    private val MUTED = Color.parseColor("#6B6860")
    private val PRIMARY = Color.parseColor("#1B5E4B")
    private val GOOD = Color.parseColor("#2E9E6B")
    private val BAD = Color.parseColor("#B3261E")
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var keysBox: EditText
    private lateinit var delayBox: EditText
    private lateinit var keyCount: TextView
    private lateinit var serviceDot: View
    private lateinit var serviceText: TextView
    private lateinit var enableBtn: Button
    private lateinit var countText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var stateText: TextView
    private lateinit var statusText: TextView
    private lateinit var failedText: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var diagCard: LinearLayout
    private lateinit var diagText: TextView

    private val h = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            h.postDelayed(this, 1000)
        }
    }

    // ---- helpers ----
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun label(s: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(CARD, 18, LINE)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

    private fun button(text: String, filled: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            stateListAnimator = null
            setTextColor(if (filled) Color.WHITE else PRIMARY)
            val base = if (filled) rounded(PRIMARY, 14) else rounded(Color.TRANSPARENT, 14, PRIMARY)
            background = RippleDrawable(ColorStateList.valueOf(0x22000000), base, null)
            minHeight = dp(52)
            minimumHeight = dp(52)
            setOnClickListener { onClick() }
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ---- UI ----
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = BG
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        // header
        content.addView(label("Key Feeder", 28f, INK, true))
        content.addView(label("Enters Nusuk key codes for you, one at a time.", 14f, MUTED).apply {
            setPadding(0, dp(4), 0, dp(4))
        })

        // service status card
        val serviceCard = card()
        val serviceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        serviceDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(10) }
        }
        serviceText = label("", 15f, INK, true)
        serviceRow.addView(serviceDot)
        serviceRow.addView(serviceText)
        serviceCard.addView(serviceRow)
        enableBtn = button("Turn on accessibility service", false) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
        }
        serviceCard.addView(enableBtn)
        content.addView(serviceCard)

        // input card
        val inputCard = card()
        val headRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headRow.addView(label("Key codes", 16f, INK, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        keyCount = label("0 codes", 13f, MUTED)
        headRow.addView(keyCount)
        inputCard.addView(headRow)
        inputCard.addView(label("Paste one code per line.", 13f, MUTED).apply {
            setPadding(0, dp(2), 0, dp(10))
        })

        keysBox = EditText(this).apply {
            hint = "NG-xxxxxxxx"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 6
            maxLines = 12
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            textSize = 14f
            setTextColor(INK)
            setHintTextColor(Color.parseColor("#A8A59B"))
            background = rounded(INPUT_BG, 12, LINE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setText(Runner.draft)
        }
        keysBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { updateCount() }
        })
        inputCard.addView(keysBox)

        val delayRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
        }
        delayRow.addView(label("Delay between codes (seconds)", 14f, INK).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        delayBox = EditText(this).apply {
            setText("3")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(INK)
            background = rounded(INPUT_BG, 10, LINE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(72), WRAP)
        }
        delayRow.addView(delayBox)
        inputCard.addView(delayRow)
        inputCard.addView(label("Keep this at 3 seconds or more.", 12f, MUTED).apply {
            setPadding(0, dp(6), 0, 0)
        })
        content.addView(inputCard)

        // progress card
        val progressCard = card()
        val progRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        progRow.addView(label("Progress", 16f, INK, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        countText = label("0 / 0", 15f, INK, true)
        progRow.addView(countText)
        progressCard.addView(progRow)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, dp(8)).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
            progressTintList = ColorStateList.valueOf(PRIMARY)
            progressBackgroundTintList = ColorStateList.valueOf(LINE)
            max = 1
        }
        progressCard.addView(progressBar)
        stateText = label("Idle", 14f, MUTED, true)
        statusText = label("", 13f, MUTED).apply { setPadding(0, dp(2), 0, 0) }
        failedText = label("", 13f, BAD).apply { setPadding(0, dp(6), 0, 0) }
        progressCard.addView(stateText)
        progressCard.addView(statusText)
        progressCard.addView(failedText)
        content.addView(progressCard)

        // action buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) }
        }
        startBtn = button("Start", true) { onStart() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) }
        }
        stopBtn = button("Stop", false) {
            Runner.running = false
            refresh()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(6) }
        }
        btnRow.addView(startBtn)
        btnRow.addView(stopBtn)
        content.addView(btnRow)

        content.addView(button("Diagnose screen", false) { onDiagnose() }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
        })
        content.addView(label(
            "If nothing happens, tap Diagnose, open the Nusuk Keys screen within 5 seconds, then return here to see what the app can read.",
            12f, MUTED
        ).apply { setPadding(0, dp(8), 0, 0) })

        // diagnosis card
        diagCard = card()
        diagCard.addView(label("Screen diagnosis", 15f, INK, true))
        diagText = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(INK)
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, 0)
        }
        diagCard.addView(diagText)
        diagCard.visibility = View.GONE
        content.addView(diagCard)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            addView(content)
        })

        updateCount()
    }

    private fun updateCount() {
        val n = keysBox.text.toString().lines().count { it.trim().isNotEmpty() }
        keyCount.text = if (n == 1) "1 code" else "$n codes"
    }

    private fun onStart() {
        if (!Runner.serviceConnected) {
            toast("Turn on the accessibility service first.")
            return
        }
        val list = keysBox.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (list.isEmpty()) {
            toast("Add at least one key code.")
            return
        }
        Runner.keys = list
        Runner.index = 0
        Runner.failed = mutableListOf()
        Runner.lastMsg = "Started. Waiting for the Nusuk Keys screen."
        Runner.delayMs = ((delayBox.text.toString().toDoubleOrNull() ?: 3.0) * 1000).toLong().coerceAtLeast(1000L)
        Runner.running = true
        toast("Now open Nusuk and go to the Keys screen.")
        moveTaskToBack(true)
    }

    private fun onDiagnose() {
        if (!Runner.serviceConnected) {
            toast("Turn on the accessibility service first.")
            return
        }
        Runner.debugText = ""
        Runner.debugRequested = true
        toast("Open the Nusuk Keys screen within 5 seconds, then come back here.")
        moveTaskToBack(true)
    }

    override fun onResume() {
        super.onResume()
        h.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        Runner.draft = keysBox.text.toString()
        h.removeCallbacks(ticker)
    }

    private fun refresh() {
        // service status
        val connected = Runner.serviceConnected
        serviceDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (connected) GOOD else BAD)
        }
        serviceText.text = if (connected) "Accessibility service connected" else "Accessibility service is off"
        enableBtn.visibility = if (connected) View.GONE else View.VISIBLE

        // progress
        val total = Runner.keys.size
        val done = Runner.index.coerceAtMost(total)
        progressBar.max = maxOf(total, 1)
        progressBar.progress = done
        countText.text = "$done / $total"

        val finished = !Runner.running && total > 0 && Runner.index >= total
        stateText.text = when {
            Runner.running -> "Running"
            finished -> "Finished"
            else -> "Idle"
        }
        stateText.setTextColor(
            when {
                Runner.running -> PRIMARY
                finished -> GOOD
                else -> MUTED
            }
        )
        statusText.text = Runner.lastMsg

        val failed = Runner.failed
        if (failed.isEmpty()) {
            failedText.visibility = View.GONE
        } else {
            failedText.visibility = View.VISIBLE
            failedText.text = "Failed: " + failed.joinToString(", ")
        }

        // buttons
        startBtn.isEnabled = !Runner.running
        startBtn.alpha = if (Runner.running) 0.45f else 1f
        stopBtn.isEnabled = Runner.running
        stopBtn.alpha = if (Runner.running) 1f else 0.45f

        // diagnosis
        if (Runner.debugText.isEmpty()) {
            diagCard.visibility = View.GONE
        } else {
            diagCard.visibility = View.VISIBLE
            diagText.text = Runner.debugText
        }
    }
}
