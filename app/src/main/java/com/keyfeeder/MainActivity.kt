package com.keyfeeder

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    // ---------- theme ----------
    private val night get() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val cBg get() = if (night) 0xFF0F1115.toInt() else 0xFFF3F5F7.toInt()
    private val cCard get() = if (night) 0xFF1A1D24.toInt() else 0xFFFFFFFF.toInt()
    private val cText get() = if (night) 0xFFE8EAED.toInt() else 0xFF13181F.toInt()
    private val cMuted get() = if (night) 0xFF9AA3AE.toInt() else 0xFF5E6B7A.toInt()
    private val cBorder get() = if (night) 0xFF2C323C.toInt() else 0xFFDDE3E9.toInt()
    private val cAccent get() = if (night) 0xFF2DD4BF.toInt() else 0xFF0B7F70.toInt()
    private val cOnAccent get() = if (night) 0xFF04201C.toInt() else 0xFFFFFFFF.toInt()
    private val cDanger get() = if (night) 0xFFF28B82.toInt() else 0xFFC5221F.toInt()
    private val cSuccess get() = if (night) 0xFF81C995.toInt() else 0xFF188038.toInt()

    // ---------- views ----------
    private lateinit var accChip: TextView
    private lateinit var runChip: TextView
    private lateinit var keyCount: TextView
    private lateinit var progressView: ProgressBarView
    private lateinit var progressText: TextView
    private lateinit var messageText: TextView
    private lateinit var failedText: TextView
    private lateinit var debugText: TextView

    private val h = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Key Feeder"
        styleSystemBars()

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(
                pad,
                insets.systemWindowInsetTop + pad,
                pad,
                insets.systemWindowInsetBottom + pad
            )
            insets
        }

        // ----- header -----
        root.addView(TextView(this).apply {
            text = "Key Feeder"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(cText)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        root.addView(TextView(this).apply {
            text = "Enter your Nusuk keys automatically, one after another."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(cMuted)
            setPadding(0, dp(4), 0, dp(20))
        })

        // ----- card 1: accessibility -----
        accChip = chip()
        val accBtn = button("Open accessibility settings", Kind.TONAL).apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        root.addView(card().apply {
            addView(row(sectionLabel("Step 1 · Permission"), accChip))
            addView(title("Turn on accessibility"))
            addView(body("Key Feeder needs its accessibility service to type into the Nusuk app. Find \"Key Feeder\" in the list and switch it on."))
            addView(accBtn, buttonParams(top = 14))
        })

        // ----- card 2: keys -----
        val keysBox = EditText(this).apply {
            hint = "Paste your keys here, one per line"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.MONOSPACE
            fieldStyle(this)
        }
        keyCount = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(cMuted)
            text = "0 keys"
        }
        keysBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val n = parseKeys(s?.toString().orEmpty()).size
                keyCount.text = if (n == 1) "1 key" else "$n keys"
            }
        })

        val delayBox = EditText(this).apply {
            setText("3")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            fieldStyle(this)
        }

        root.addView(card().apply {
            addView(row(sectionLabel("Step 2 · Keys"), keyCount))
            addView(title("Add your keys"))
            addView(keysBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = "Delay between keys"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        setTextColor(cText)
                    })
                    addView(TextView(context).apply {
                        text = "In seconds (minimum 1)"
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTextColor(cMuted)
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(delayBox, LinearLayout.LayoutParams(dp(84), -2))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        })

        // ----- action buttons -----
        val startBtn = button("Start", Kind.PRIMARY).apply {
            setOnClickListener {
                val list = parseKeys(keysBox.text.toString())
                if (list.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Please enter at least one key", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Runner.keys = list
                Runner.index = 0
                Runner.failed = mutableListOf()
                Runner.lastMsg = "Started. Waiting for the Nusuk Keys screen…"
                Runner.delayMs = ((delayBox.text.toString().toDoubleOrNull() ?: 3.0) * 1000)
                    .toLong().coerceAtLeast(1000L)
                Runner.running = true
                Toast.makeText(this@MainActivity, "Now open the Keys screen in Nusuk", Toast.LENGTH_LONG).show()
                moveTaskToBack(true)
            }
        }
        val stopBtn = button("Stop", Kind.DANGER).apply {
            setOnClickListener {
                Runner.running = false
                Runner.lastMsg = "Stopped by you"
                refresh()
            }
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(startBtn, LinearLayout.LayoutParams(0, dp(52), 1.4f))
            addView(stopBtn, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(10) })
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        // ----- card 3: status -----
        runChip = chip()
        progressView = ProgressBarView(this, cBorder, cAccent)
        progressText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(cText)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        messageText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(cMuted)
            setTextIsSelectable(true)
        }
        failedText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(cDanger)
            setTextIsSelectable(true)
            visibility = View.GONE
        }
        val debugBtn = button("Debug: show what's on screen", Kind.TEXT).apply {
            setOnClickListener {
                Runner.debugText = ""
                Runner.debugRequested = true
                Toast.makeText(
                    this@MainActivity,
                    "Open the Keys screen in Nusuk within 5 seconds, then come back here",
                    Toast.LENGTH_LONG
                ).show()
                moveTaskToBack(true)
            }
        }
        debugText = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.MONOSPACE
            setTextColor(cText)
            setTextIsSelectable(true)
            background = rounded(if (night) 0xFF12151A.toInt() else 0xFFF3F5F7.toInt(), 10, cBorder)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            visibility = View.GONE
        }

        root.addView(card().apply {
            addView(row(sectionLabel("Status"), runChip))
            addView(progressText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(progressView, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8) })
            addView(messageText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(failedText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            addView(debugText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(debugBtn, buttonParams(top = 12))
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(cBg)
            isFillViewport = true
            clipToPadding = false
            addView(root)
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        h.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacks(ticker)
    }

    // ---------- state ----------
    private fun parseKeys(raw: String) = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

    private fun accessibilityEnabled(): Boolean {
        val enabled = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (_: Exception) { null }
        return enabled?.contains(packageName, ignoreCase = true) == true
    }

    private fun refresh() {
        val total = Runner.keys.size
        val done = Runner.index.coerceAtMost(total)
        val running = Runner.running

        if (accessibilityEnabled()) setChip(accChip, "Enabled", cSuccess)
        else setChip(accChip, "Not enabled", cDanger)

        if (running) setChip(runChip, "Running", cSuccess) else setChip(runChip, "Stopped", cMuted)

        progressView.fraction = if (total == 0) 0f else done.toFloat() / total
        setIfChanged(progressText, "$done of $total ${if (total == 1) "key" else "keys"} processed")
        setIfChanged(messageText, Runner.lastMsg)

        if (Runner.failed.isEmpty()) {
            failedText.visibility = View.GONE
        } else {
            failedText.visibility = View.VISIBLE
            setIfChanged(failedText, "Failed: " + Runner.failed.joinToString(", "))
        }

        if (Runner.debugText.isEmpty()) {
            debugText.visibility = View.GONE
        } else {
            debugText.visibility = View.VISIBLE
            setIfChanged(debugText, Runner.debugText)
        }
    }

    // Avoids resetting text selection every second when nothing changed.
    private fun setIfChanged(tv: TextView, value: String) {
        if (tv.text.toString() != value) tv.text = value
    }

    // ---------- UI helpers ----------
    private enum class Kind { PRIMARY, TONAL, DANGER, TEXT }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(cCard, 18, cBorder)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t.uppercase()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        letterSpacing = 0.08f
        setTextColor(cMuted)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(cText)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(8), 0, 0)
    }

    private fun body(t: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(cMuted)
        setLineSpacing(0f, 1.15f)
        setPadding(0, dp(4), 0, 0)
    }

    /** Label on the left, trailing view (chip / counter) on the right. */
    private fun row(left: View, right: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        addView(right, LinearLayout.LayoutParams(-2, -2))
    }

    private fun chip() = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun setChip(tv: TextView, text: String, color: Int) {
        tv.text = text
        tv.setTextColor(color)
        tv.background = rounded(withAlpha(color, 0x26), 50)
    }

    private fun fieldStyle(et: EditText) {
        val idle = rounded(if (night) 0xFF12151A.toInt() else 0xFFF8F9FA.toInt(), 12, cBorder)
        val focused = GradientDrawable().apply {
            setColor(if (night) 0xFF12151A.toInt() else 0xFFFFFFFF.toInt())
            cornerRadius = dp(12).toFloat()
            setStroke(dp(2), cAccent)
        }
        et.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), idle)
        }
        et.setTextColor(cText)
        et.setHintTextColor(cMuted)
        et.setPadding(dp(14), dp(12), dp(14), dp(12))
        et.highlightColor = withAlpha(cAccent, 0x55)
        if (Build.VERSION.SDK_INT >= 29) {
            et.textCursorDrawable = GradientDrawable().apply {
                setColor(cAccent); setSize(dp(2), dp(20))
            }
        }
    }

    private fun button(label: String, kind: Kind) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        stateListAnimator = null
        elevation = 0f
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(16), 0, dp(16), 0)

        val fill: Int
        val fg: Int
        val stroke: Int?
        when (kind) {
            Kind.PRIMARY -> { fill = cAccent; fg = cOnAccent; stroke = null }
            Kind.TONAL -> { fill = withAlpha(cAccent, 0x1F); fg = cAccent; stroke = null }
            Kind.DANGER -> { fill = Color.TRANSPARENT; fg = cDanger; stroke = withAlpha(cDanger, 0x99) }
            Kind.TEXT -> { fill = Color.TRANSPARENT; fg = cMuted; stroke = null }
        }
        setTextColor(fg)
        background = RippleDrawable(
            ColorStateList.valueOf(withAlpha(fg, 0x33)),
            rounded(fill, 14, stroke),
            null
        )
    }

    private fun buttonParams(top: Int = 0) =
        LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(top) }

    @Suppress("DEPRECATION")
    private fun styleSystemBars() {
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        if (Build.VERSION.SDK_INT >= 23 && !night) {
            var flags = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = flags
        }
    }

    /** Slim rounded progress bar. */
    private class ProgressBarView(
        ctx: android.content.Context,
        private val track: Int,
        private val fill: Int
    ) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        var fraction = 0f
            set(v) {
                val c = v.coerceIn(0f, 1f)
                if (c != field) { field = c; invalidate() }
            }

        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            paint.color = track
            canvas.drawRoundRect(rect, r, r, paint)
            if (fraction > 0f) {
                rect.set(0f, 0f, maxOf(width * fraction, height.toFloat()), height.toFloat())
                paint.color = fill
                canvas.drawRoundRect(rect, r, r, paint)
            }
        }
    }
}
