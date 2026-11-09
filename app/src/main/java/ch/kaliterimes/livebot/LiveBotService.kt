package ch.kaliterimes.livebot

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.random.Random

/**
 * Le cœur du bot. Tourne en service d'accessibilité : il voit l'écran TikTok
 * et peut appuyer sur les boutons à ta place, uniquement quand tu as appuyé sur ▶.
 */
class LiveBotService : AccessibilityService() {

    companion object {
        @Volatile var instance: LiveBotService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val uiLock = Mutex()          // une seule manipulation d'écran à la fois
    private lateinit var ui: UiDriver
    private var overlay: Overlay? = null

    private var cfg = Config()
    var running = false
        private set
    var spent = 0
        private set
    var chests = 0
        private set
    var bags = 0
        private set
    var status = "Prêt"
        private set
    private var chestJob: Job? = null
    private var bagJob: Job? = null
    private var failStreak = 0

    // Détection des arrivées de spectateurs (pour le mode « réactif »)
    private val seenJoins = LinkedHashSet<String>()
    private val joinTimes = ArrayDeque<Long>()
    private var lastJoinScan = 0L
    private val joinRegex = Regex("""(a rejoint|joined|est arrivé|vient d'arriver)""", RegexOption.IGNORE_CASE)

    override fun onServiceConnected() {
        instance = this
        ui = UiDriver(this)
        overlay = Overlay(this).also { it.show() }
        BotLog.add("Service activé. Ouvre ton live puis appuie sur ▶ dans la bulle.")
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!running || !cfg.reactive) return
        val now = System.currentTimeMillis()
        if (now - lastJoinScan < 2000) return
        lastJoinScan = now
        for (t in ui.visibleTexts()) {
            if (t.length < 80 && joinRegex.containsMatchIn(t) && seenJoins.add(t)) {
                joinTimes.addLast(now)
            }
        }
        while (seenJoins.size > 300) seenJoins.remove(seenJoins.first())
        while (joinTimes.isNotEmpty() && now - joinTimes.first() > 120_000) joinTimes.removeFirst()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        stopBot("Service désactivé")
        overlay?.hide()
        scope.cancel()
        instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Démarrer / arrêter

    fun startBot() {
        if (running) return
        cfg = Config.load(this)
        if (!ui.isTikTokVisible()) {
            setStatus("Ouvre d'abord ton live TikTok")
            return
        }
        if (!cfg.chestEnabled && !cfg.bagEnabled) {
            setStatus("Coffres et sacs désactivés dans les réglages")
            return
        }
        running = true
        spent = 0; chests = 0; bags = 0; failStreak = 0
        seenJoins.clear(); joinTimes.clear()
        // Les messages déjà à l'écran ne comptent pas comme nouvelles arrivées
        ui.visibleTexts().filter { joinRegex.containsMatchIn(it) }.forEach { seenJoins.add(it) }
        BotLog.add("▶ Démarré – budget ${cfg.budget} pièces, coffre de ${cfg.chestAmount}")
        setStatus("En marche")
        if (cfg.bagEnabled) bagJob = scope.launch { bagLoop() }
        if (cfg.chestEnabled) chestJob = scope.launch { chestLoop() }
    }

    fun stopBot(reason: String = "Arrêté") {
        if (!running && chestJob == null && bagJob == null) return
        running = false
        chestJob?.cancel(); bagJob?.cancel()
        chestJob = null; bagJob = null
        BotLog.add("■ $reason – dépensé $spent pièces ($chests coffres, $bags sacs)")
        setStatus(reason)
    }

    fun toggle() { if (running) stopBot("Mis en pause") else startBot() }

    private fun setStatus(s: String) { status = s; refresh() }

    private fun refresh() {
        overlay?.update(running, "$spent/${cfg.budget} 🪙  ·  📦$chests  🎒$bags\n$status")
    }

    private fun remaining() = cfg.budget - spent

    private fun rnd(minS: Int, maxS: Int): Long =
        Random.nextLong(minS * 1000L, maxS * 1000L + 1)

    // ------------------------------------------------------------------ Boucle des coffres

    private suspend fun chestLoop() {
        delay(rnd(5, 15))
        while (scope.isActive && running) {
            val n = Random.nextInt(cfg.burstMin, cfg.burstMax + 1)
            BotLog.add("Rafale de $n coffre(s)")
            for (i in 0 until n) {
                if (!running) return
                if (remaining() < cfg.chestAmount) {
                    BotLog.add("Plus assez de budget pour un coffre de ${cfg.chestAmount}")
                    chestJob = null
                    checkBudgetEnd()
                    return
                }
                setStatus("Pose un coffre…")
                val ok = uiLock.withLock { sendChest() }
                if (!running) return
                if (ok) {
                    spent += cfg.chestAmount; chests++; failStreak = 0
                    BotLog.add("📦 Coffre de ${cfg.chestAmount} posé (total $spent)")
                } else if (++failStreak >= 4) {
                    stopBot("Arrêt : 4 échecs d'affilée (regarde le journal)")
                    return
                }
                setStatus("En marche")
                if (i < n - 1) delay(rnd(cfg.gapMinS, cfg.gapMaxS))
            }
            var pause = rnd(cfg.pauseMinS, cfg.pauseMaxS)
            if (cfg.reactive) {
                val joins = joinTimes.size // arrivées sur les 2 dernières minutes
                pause = when {
                    joins >= 6 -> (pause * 0.6).toLong()
                    joins >= 3 -> (pause * 0.8).toLong()
                    joins == 0 -> (pause * 1.25).toLong()
                    else -> pause
                }
                BotLog.add("$joins arrivée(s) récentes → pause ${pause / 1000} s")
            } else {
                BotLog.add("Pause ${pause / 1000} s")
            }
            setStatus("Prochain coffre dans ~${pause / 60000} min")
            delay(pause)
            setStatus("En marche")
        }
    }

    // ------------------------------------------------------------------ Boucle du sac-cadeau

    private suspend fun bagLoop() {
        delay(rnd(2, 6))
        while (scope.isActive && running) {
            setStatus("Lance le sac-cadeau…")
            val cost = uiLock.withLock { sendBag() }
            if (!running) return
            setStatus("En marche")
            when {
                cost > 0 -> {
                    spent += cost; bags++; failStreak = 0
                    BotLog.add("🎒 Sac-cadeau de $cost lancé (total $spent)")
                    // On attend la fin du sac, puis on réessaie toutes les 10 s jusqu'à ce que ça passe
                    delay(cfg.bagDurationS * 1000L + Random.nextLong(1000, 4000))
                }
                cost == -2 -> {
                    BotLog.add("Plus assez de budget pour un sac-cadeau")
                    bagJob = null
                    checkBudgetEnd()
                    return
                }
                else -> delay(10_000) // sac encore actif ou panneau introuvable : on réessaie
            }
        }
    }

    private fun checkBudgetEnd() {
        if (chestJob == null && bagJob == null) stopBot("Budget atteint ✔")
    }

    // ------------------------------------------------------------------ Actions à l'écran

    /** Ouvre le panneau « Interagir ». */
    private suspend fun openInteract(): Boolean {
        if (!ui.isTikTokVisible()) { BotLog.add("TikTok n'est pas à l'écran"); return false }
        if (ui.findExact("Sac-cadeau") != null || ui.findExact("Coffre au trésor") != null) return true

        // 1) Par son nom d'accessibilité
        val btn = ui.allNodes().firstOrNull {
            val d = (it.contentDescription ?: it.text ?: "").toString()
            it.isVisibleToUser && (d.equals("Interagir", true) || d.equals("Interact", true))
        }
        if (btn != null) ui.click(btn) else ui.tapPercent(cfg.interactX, cfg.interactY)

        val opened = ui.waitFor(3000) {
            ui.findExact("Sac-cadeau") ?: ui.findExact("Coffre au trésor")
        } != null
        if (!opened) BotLog.add("Panneau Interagir introuvable (fais un diagnostic 🔍)")
        return opened
    }

    /** Vérifie qu'aucun texte « argent réel » n'est affiché ; sinon referme. */
    private suspend fun moneyGuard(): Boolean {
        val m = ui.moneyTextFound() ?: return true
        BotLog.add("⛔ Sécurité : « $m » visible → je n'appuie sur rien")
        ui.closePanelSafely()
        return false
    }

    private suspend fun sendChest(): Boolean {
        if (!openInteract()) return false
        val item = ui.findExact("Coffre au trésor") ?: return abort("Bouton Coffre au trésor introuvable")
        ui.click(item)

        // Attendre le panneau du coffre (liste « Articles » + bouton Envoyer)
        ui.waitFor(4000) { ui.findExact("Envoyer") } ?: return abort("Panneau du coffre pas chargé")
        delay(500)
        if (!moneyGuard()) return false

        // Choisir le montant (20, 100, ...)
        val amount = ui.findAllExact(cfg.chestAmount.toString())
        if (amount.isNotEmpty()) {
            ui.click(amount.first()); delay(500)
        } else {
            BotLog.add("Montant ${cfg.chestAmount} pas trouvé, j'utilise celui déjà choisi")
        }
        if (!moneyGuard()) return false

        val send = ui.findExact("Envoyer") ?: return abort("Bouton Envoyer disparu")
        if (!send.isEnabled) return abort("Bouton Envoyer grisé")
        ui.click(send)

        // Succès = le panneau se ferme
        val closed = ui.waitFor(6000) { if (ui.findExact("Envoyer") == null) true else null } == true
        if (!closed) {
            if (!moneyGuard()) { stopBot("Arrêt sécurité : écran de paiement"); return false }
            return abort("Le coffre ne semble pas parti")
        }
        return true
    }

    /** Renvoie le coût du sac lancé, -1 si impossible pour l'instant, -2 si budget insuffisant. */
    private suspend fun sendBag(): Int {
        if (!openInteract()) return -1
        val item = ui.findExact("Sac-cadeau") ?: run { abort("Bouton Sac-cadeau introuvable"); return -1 }
        ui.click(item)

        val send = ui.waitFor(4000) { ui.findExact("Envoyer") }
        if (send == null) {
            // Pas de bouton Envoyer : en général le sac précédent tourne encore
            ui.closePanelSafely()
            return -1
        }
        delay(500)
        if (!moneyGuard()) return -1
        if (!send.isEnabled) { ui.closePanelSafely(); return -1 }

        val cost = readBagCost() ?: cfg.bagCostFallback
        if (cost > remaining()) { ui.closePanelSafely(); return -2 }

        ui.click(ui.findExact("Envoyer") ?: send)
        val closed = ui.waitFor(6000) { if (ui.findExact("Envoyer") == null) true else null } == true
        if (!closed) {
            if (!moneyGuard()) { stopBot("Arrêt sécurité : écran de paiement"); return -1 }
            abort("Le sac ne semble pas parti")
            return -1
        }
        return cost
    }

    /** Lit le nombre à côté de « Total des Pièces » dans le panneau du sac. */
    private fun readBagCost(): Int? {
        val nodes = ui.allNodes().filter { it.isVisibleToUser }
        val idx = nodes.indexOfFirst {
            (it.text ?: it.contentDescription ?: "").toString().startsWith("Total des Pièces", true)
        }
        if (idx < 0) return null
        // Le texte peut contenir le nombre (« Total des Pièces 20 ») ou être dans un élément voisin
        Regex("""\d+""").find((nodes[idx].text ?: "").toString())?.let { return it.value.toInt() }
        for (j in idx + 1 until minOf(idx + 6, nodes.size)) {
            val t = (nodes[j].text ?: "").toString().trim()
            if (t.matches(Regex("""\d{1,6}"""))) return t.toInt()
        }
        return null
    }

    private suspend fun abort(msg: String): Boolean {
        BotLog.add("⚠ $msg")
        ui.closePanelSafely()
        return false
    }

    // ------------------------------------------------------------------ Diagnostic

    /** Enregistre la liste des éléments visibles à l'écran, pour m'aider à corriger si ça bloque. */
    fun diagnostic(): File {
        val f = File(filesDir, "diagnostic.txt")
        val dm = resources.displayMetrics
        f.writeText(
            "Écran ${dm.widthPixels}x${dm.heightPixels}\nStatut: $status\n\n" +
                ui.dump() + "\n\n--- Journal ---\n" + BotLog.text()
        )
        BotLog.add("🔍 Diagnostic enregistré (Partager depuis l'appli)")
        return f
    }
}
