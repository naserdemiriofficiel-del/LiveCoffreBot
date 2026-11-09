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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
        y = (service.resources.displayMetrics.heightPixels * 0.18).toInt()
    }

    /** Bouton rond, simple, bien visible. */
    private fun button(label: String, color: Int, onClick: () -> Unit) = TextView(service).apply {
        text = label
        textSize = 17f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        val size = (40 * dp).toInt()
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginStart = (3 * dp).toInt(); marginEnd = (3 * dp).toInt()
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        setOnClickListener { onClick() }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (shown) return
        // Une seule ligne horizontale :  [▶] [🎯] [✕]  infos  [✥]
        root = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 28 * dp
                setColor(Color.argb(210, 20, 20, 28))
            }
            setPadding((5 * dp).toInt(), (5 * dp).toInt(), (5 * dp).toInt(), (5 * dp).toInt())
        }
        play = button("▶", Color.rgb(0, 170, 90)) { service.toggle() }
        root.addView(play)
        root.addView(button("🎯", Color.rgb(60, 60, 75)) { service.startCalibration() })
        root.addView(button("✕", Color.rgb(220, 40, 60)) { service.quit() })

        info = TextView(service).apply {
            textSize = 11f
            setTextColor(Color.rgb(230, 230, 235))
            setPadding((8 * dp).toInt(), 0, (6 * dp).toInt(), 0)
            maxLines = 2
            // Appui long sur les infos = relevé de diagnostic
            setOnLongClickListener {
                service.diagnostic()
                Toast.makeText(service, "Relevé enregistré 🔍", Toast.LENGTH_SHORT).show()
                true
            }
        }
        root.addView(info)

        val grip = button("✥", Color.argb(0, 0, 0, 0)) {}
        root.addView(grip)

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
        (play.background as? GradientDrawable)?.setColor(
            if (running) Color.rgb(240, 150, 0) else Color.rgb(0, 170, 90)
        )
        info.text = text
        info.visibility = View.VISIBLE
    }

    /**
     * Apprentissage : affiche un voile transparent sur tout l'écran avec une consigne,
     * attend que tu touches l'écran, et renvoie la position touchée (en fraction de l'écran).
     * Toucher la consigne elle-même = annuler (renvoie null).
     */
    @SuppressLint("ClickableViewAccessibility")
    suspend fun captureTouch(instruction: String): Pos? = suspendCancellableCoroutine { cont ->
        val size = UiDriver(service).screenSize()
        val catcher = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(40, 255, 0, 80))
        }
        val banner = TextView(service).apply {
            text = "$instruction\n\n(touche ce cadre pour annuler)"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor(Color.argb(230, 230, 30, 80))
            }
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (size.y * 0.12).toInt()
            leftMargin = (20 * dp).toInt(); rightMargin = (20 * dp).toInt()
        }
        catcher.addView(banner, lp)

        val full = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        fun finish(result: Pos?) {
            try { wm.removeView(catcher) } catch (_: Exception) { }
            if (cont.isActive) cont.resume(result)
        }
        banner.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) finish(null); true
        }
        catcher.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                finish(Pos(e.rawX / size.x, e.rawY / size.y))
            }
            true
        }
        cont.invokeOnCancellation { try { wm.removeView(catcher) } catch (_: Exception) { } }
        wm.addView(catcher, full)
    }

    fun hide() {
        if (!shown) return
        try { wm.removeView(root) } catch (_: Exception) { }
        shown = false
    }
}
