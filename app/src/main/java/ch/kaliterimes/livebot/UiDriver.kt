package ch.kaliterimes.livebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Petite boîte à outils pour « lire » l'écran de TikTok et appuyer sur les boutons,
 * via le service d'accessibilité.
 */
class UiDriver(private val service: AccessibilityService) {

    companion object {
        val TIKTOK_PACKAGES = setOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go",
        )

        /**
         * Tout ce qui ressemble à un paiement en vrai argent (Coffre de Super Fan, achat de pièces...).
         * Si un de ces textes est visible, le bot n'appuie sur RIEN et referme.
         */
        private val MONEY_REGEX = Regex(
            """(\d+[.,]\d{2}\s?(CHF|€|EUR|USD|\$|Fr\.?))|((CHF|€|\$)\s?\d)|Statut de Super Fan|Super Fan x|Recharger|Recharge|Acheter des pièces|Obtenir des pièces""",
            RegexOption.IGNORE_CASE
        )
    }

    private var lastBackAt = 0L

    /** Racine de la fenêtre TikTok (null si TikTok n'est pas au premier plan). */
    fun tiktokRoot(): AccessibilityNodeInfo? {
        try {
            for (w in service.windows) {
                val r = w.root ?: continue
                if ((r.packageName?.toString() ?: "") in TIKTOK_PACKAGES) return r
            }
        } catch (_: Exception) { }
        val r = service.rootInActiveWindow ?: return null
        return if ((r.packageName?.toString() ?: "") in TIKTOK_PACKAGES) r else null
    }

    fun isTikTokVisible(): Boolean = tiktokRoot() != null

    /** Parcourt tous les éléments de l'écran TikTok. */
    fun allNodes(): List<AccessibilityNodeInfo> {
        val root = tiktokRoot() ?: return emptyList()
        val out = ArrayList<AccessibilityNodeInfo>(256)
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty() && out.size < 3000) {
            val n = stack.removeLast()
            out.add(n)
            for (i in n.childCount - 1 downTo 0) {
                n.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return out
    }

    private fun label(n: AccessibilityNodeInfo): String =
        (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim()

    private fun isOnScreen(n: AccessibilityNodeInfo): Boolean {
        if (!n.isVisibleToUser) return false
        val r = Rect(); n.getBoundsInScreen(r)
        return r.width() > 0 && r.height() > 0
    }

    /** Élément visible dont le texte est exactement [text] (sans tenir compte des majuscules). */
    fun findExact(text: String): AccessibilityNodeInfo? =
        allNodes().firstOrNull { isOnScreen(it) && label(it).equals(text, ignoreCase = true) }

    fun findAllExact(text: String): List<AccessibilityNodeInfo> =
        allNodes().filter { isOnScreen(it) && label(it).equals(text, ignoreCase = true) }

    /** Élément visible dont le texte commence par [prefix]. */
    fun findStartsWith(prefix: String): AccessibilityNodeInfo? =
        allNodes().firstOrNull { isOnScreen(it) && label(it).startsWith(prefix, ignoreCase = true) }

    fun visibleTexts(): List<String> =
        allNodes().filter { isOnScreen(it) }.map { label(it) }.filter { it.isNotEmpty() }

    /** true si l'écran montre quelque chose qui coûte de l'argent réel. */
    /**
     * Texte « argent réel » visible, ou null. On ne regarde que les textes courts (boutons,
     * étiquettes) : les longs messages du chat (« ...pour recharger et envoyer des Cadeaux ») sont ignorés.
     */
    fun moneyTextFound(): String? =
        visibleTexts().firstOrNull { it.length <= 40 && MONEY_REGEX.containsMatchIn(it) }

    /** Attend jusqu'à [timeoutMs] que [probe] renvoie autre chose que null. */
    suspend fun <T> waitFor(timeoutMs: Long = 4000, stepMs: Long = 250, probe: () -> T?): T? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            probe()?.let { return it }
            delay(stepMs)
        }
        return probe()
    }

    /** Appuie sur l'élément (ou sur son parent cliquable), sinon tape au centre. */
    suspend fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 6) {
            if (n.isClickable && n.isEnabled) {
                if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                break
            }
            n = n.parent; depth++
        }
        val r = Rect(); node.getBoundsInScreen(r)
        if (r.isEmpty) return false
        return tap(r.exactCenterX(), r.exactCenterY())
    }

    /** Tape à une position précise de l'écran (en pixels). */
    suspend fun tap(x: Float, y: Float): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        val ok = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!ok && cont.isActive) cont.resume(false)
    }

    /** Tape à une position en % de l'écran. */
    suspend fun tapPercent(px: Float, py: Float): Boolean {
        val dm = service.resources.displayMetrics
        return tap(px * dm.widthPixels, py * dm.heightPixels)
    }

    /**
     * Ferme un panneau ouvert avec « Retour ».
     * ATTENTION : sur l'écran du live, deux « Retour » rapprochés = quitter le live !
     * Donc on n'appuie que si un panneau est vraiment ouvert, et jamais deux fois en moins de 4 s.
     */
    suspend fun closePanelSafely(): Boolean {
        if (!panelOpen()) return false
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 4000) delay(4000 - (now - lastBackAt))
        if (!panelOpen()) return false
        lastBackAt = System.currentTimeMillis()
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(700)
        return true
    }

    /** Un des panneaux (Interagir / Coffre / Sac-cadeau / Super Fan) est-il ouvert ? */
    fun panelOpen(): Boolean {
        val t = visibleTexts()
        return t.any {
            it.equals("Envoyer", true) || it.equals("Sac-cadeau", true) ||
                it.equals("Coffre au trésor", true) || it.equals("Articles", true) ||
                it.startsWith("Total des Pièces", true) || it.contains("Super Fan", true)
        }
    }

    /** Liste lisible de l'écran, pour le diagnostic. */
    fun dump(): String {
        val sb = StringBuilder()
        val r = Rect()
        for (n in allNodes()) {
            n.getBoundsInScreen(r)
            val t = n.text?.toString()?.replace('\n', ' ') ?: ""
            val d = n.contentDescription?.toString()?.replace('\n', ' ') ?: ""
            if (t.isEmpty() && d.isEmpty() && !n.isClickable) continue
            sb.append("${n.className?.toString()?.substringAfterLast('.')} ")
            sb.append("id=${n.viewIdResourceName?.substringAfterLast('/') ?: "-"} ")
            if (t.isNotEmpty()) sb.append("text=\"$t\" ")
            if (d.isNotEmpty()) sb.append("desc=\"$d\" ")
            if (n.isClickable) sb.append("[clic] ")
            if (!n.isVisibleToUser) sb.append("[caché] ")
            sb.append(r.toShortString()).append('\n')
        }
        return sb.toString()
    }
}
