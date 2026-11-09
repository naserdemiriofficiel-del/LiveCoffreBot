package ch.kaliterimes.livebot

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Petite bulle flottante par-dessus TikTok : ▶/⏸, ■ stop, 🔍 diagnostic, compteur. */
class Overlay(private val service: LiveBotService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val dp = service.resources.displayMetrics.density
    private lateinit var root: LinearLayout
    private lateinit var info: TextView
    private lateinit var play: TextView
    private var shown = false

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (8 * service.resources.displayMetrics.density).toInt()
        y = (service.resources.displayMetrics.heightPixels * 0.30).toInt()
    }

    private fun button(label: String, onClick: () -> Unit) = TextView(service).apply {
        text = label
        textSize = 18f
        setTextColor(Color.WHITE)
        setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
        setOnClickListener { onClick() }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (shown) return
        root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor(Color.argb(200, 20, 20, 28))
            }
            setPadding((6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
        }
        val row = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        play = button("▶") { service.toggle() }
        row.addView(play)
        row.addView(button("■") { service.stopBot("Arrêté") })
        row.addView(button("🔍") {
            service.diagnostic()
            Toast.makeText(service, "Diagnostic enregistré", Toast.LENGTH_SHORT).show()
        })
        val grip = button("✥") {}
        row.addView(grip)
        root.addView(row)

        info = TextView(service).apply {
            textSize = 11f
            setTextColor(Color.rgb(230, 230, 235))
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        }
        root.addView(info)

        // Déplacer la bulle en glissant le ✥
        var sx = 0f; var sy = 0f; var px = 0; var py = 0
        grip.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; px = params.x; py = params.y; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = px + (e.rawX - sx).toInt()
                    params.y = py + (e.rawY - sy).toInt()
                    wm.updateViewLayout(root, params); true
                }
                else -> true
            }
        }

        wm.addView(root, params)
        shown = true
    }

    fun update(running: Boolean, text: String) {
        if (!shown) return
        play.text = if (running) "⏸" else "▶"
        info.text = text
        info.visibility = View.VISIBLE
    }

    fun hide() {
        if (!shown) return
        try { wm.removeView(root) } catch (_: Exception) { }
        shown = false
    }
}
