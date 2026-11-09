package ch.kaliterimes.livebot

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Écran des réglages : budget, coffres, sac-cadeau, activation, journal. */
class MainActivity : Activity() {

    private lateinit var form: LinearLayout
    private lateinit var serviceState: TextView
    private lateinit var logView: TextView
    private val fields = HashMap<String, EditText>()
    private val switches = HashMap<String, Switch>()
    private val dp by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * dp).toInt(), (24 * dp).toInt(), (18 * dp).toInt(), (24 * dp).toInt())
        }
        scroll.addView(form)
        setContentView(scroll)

        heading("Live Coffre Bot", 24f)
        note("Pose tes Coffres au trésor au hasard et relance le Sac-cadeau à ta place pendant tes lives.")

        serviceState = note("")
        btn("1. Activer le service d'accessibilité") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val c = Config.load(this)

        section("💰 Budget")
        num("budget", "Budget max par live (pièces)", c.budget)

        section("📦 Coffres au trésor")
        sw("chestEnabled", "Poser des coffres", c.chestEnabled)
        num("chestAmount", "Montant d'un coffre (20, 100…)", c.chestAmount)
        num("burstMin", "Coffres par rafale – minimum", c.burstMin)
        num("burstMax", "Coffres par rafale – maximum", c.burstMax)
        num("gapMinS", "Écart entre coffres – min (s)", c.gapMinS)
        num("gapMaxS", "Écart entre coffres – max (s)", c.gapMaxS)
        num("pauseMinS", "Pause entre rafales – min (s)", c.pauseMinS)
        num("pauseMaxS", "Pause entre rafales – max (s)", c.pauseMaxS)
        sw("reactive", "S'adapter aux arrivées de spectateurs", c.reactive)

        section("🎒 Sac-cadeau")
        sw("bagEnabled", "Relancer le sac-cadeau automatiquement", c.bagEnabled)
        num("bagDurationS", "Durée d'un sac (s)", c.bagDurationS)
        num("bagCostFallback", "Coût d'un sac si illisible (pièces)", c.bagCostFallback)

        section("🎯 Position de secours du bouton Interagir")
        note("À ne toucher que si le bot n'arrive pas à ouvrir « Interagir ». En % de l'écran (0 à 100).")
        num("interactX", "Horizontal (%)", Math.round(c.interactX * 100))
        num("interactY", "Vertical (%)", Math.round(c.interactY * 100))

        btn("💾 Enregistrer les réglages") { save() }
        btn("📤 Partager le diagnostic") { shareDiagnostic() }

        section("📋 Journal")
        logView = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.DKGRAY)
        }
        form.addView(logView)
    }

    override fun onResume() {
        super.onResume()
        val on = LiveBotService.instance != null
        serviceState.text = if (on) "✅ Service actif – la bulle apparaît sur TikTok."
        else "❌ Service inactif – active « Live Coffre Bot » dans Accessibilité."
        serviceState.setTextColor(if (on) Color.rgb(0, 130, 60) else Color.rgb(200, 30, 50))
        BotLog.listener = { runOnUiThread { logView.text = BotLog.text() } }
        logView.text = BotLog.text()
    }

    override fun onPause() {
        BotLog.listener = null
        super.onPause()
    }

    private fun save() {
        fun i(k: String) = fields[k]?.text?.toString()?.trim()?.toIntOrNull() ?: 0
        val c = Config(
            budget = i("budget"),
            chestEnabled = switches["chestEnabled"]!!.isChecked,
            chestAmount = i("chestAmount"),
            burstMin = i("burstMin"), burstMax = i("burstMax"),
            gapMinS = i("gapMinS"), gapMaxS = i("gapMaxS"),
            pauseMinS = i("pauseMinS"), pauseMaxS = i("pauseMaxS"),
            reactive = switches["reactive"]!!.isChecked,
            bagEnabled = switches["bagEnabled"]!!.isChecked,
            bagDurationS = i("bagDurationS"),
            bagCostFallback = i("bagCostFallback"),
            interactX = i("interactX") / 100f,
            interactY = i("interactY") / 100f,
        ).sanitized()
        c.save(this)
        // Réafficher les valeurs corrigées
        fields["budget"]?.setText(c.budget.toString())
        fields["burstMax"]?.setText(c.burstMax.toString())
        fields["gapMaxS"]?.setText(c.gapMaxS.toString())
        fields["pauseMaxS"]?.setText(c.pauseMaxS.toString())
        Toast.makeText(this, "Réglages enregistrés (pris en compte au prochain ▶)", Toast.LENGTH_SHORT).show()
    }

    private fun shareDiagnostic() {
        val f = File(filesDir, "diagnostic.txt")
        if (!f.exists()) {
            Toast.makeText(this, "Pendant le live, appuie d'abord sur 🔍 dans la bulle", Toast.LENGTH_LONG).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "ch.kaliterimes.livebot.files", f)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Envoyer le diagnostic"))
    }

    // ------------------------------------------------------------------ petits constructeurs d'UI

    private fun heading(t: String, size: Float) = TextView(this).apply {
        text = t; textSize = size; setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.rgb(20, 20, 30))
        form.addView(this)
    }

    private fun section(t: String) = TextView(this).apply {
        text = t; textSize = 17f; setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.rgb(230, 30, 80))
        setPadding(0, (22 * dp).toInt(), 0, (6 * dp).toInt())
        form.addView(this)
    }

    private fun note(t: String) = TextView(this).apply {
        text = t; textSize = 14f; setTextColor(Color.GRAY)
        setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
        form.addView(this)
    }

    private fun num(key: String, label: String, value: Int) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (2 * dp).toInt(), 0, (2 * dp).toInt())
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 15f; setTextColor(Color.rgb(40, 40, 50))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val e = EditText(this).apply {
            setText(value.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            minWidth = (90 * dp).toInt()
            textSize = 15f
        }
        row.addView(e)
        fields[key] = e
        form.addView(row)
    }

    private fun sw(key: String, label: String, value: Boolean) {
        val s = Switch(this).apply { text = label; isChecked = value; textSize = 15f }
        switches[key] = s
        form.addView(s)
    }

    private fun btn(label: String, onClick: () -> Unit) {
        form.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        })
    }
}
