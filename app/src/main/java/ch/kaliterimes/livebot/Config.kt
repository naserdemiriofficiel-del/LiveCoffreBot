package ch.kaliterimes.livebot

import android.content.Context

/** Tous les réglages modifiables depuis l'écran principal. */
data class Config(
    /** Budget maximum en pièces pour UN live (coffres + sacs). */
    val budget: Int = 300,

    // --- Coffres au trésor ---
    val chestEnabled: Boolean = true,
    /** Montant du coffre à choisir dans la liste « Articles » (20, 100, ...). */
    val chestAmount: Int = 20,
    /** Nombre de coffres par rafale : tiré au hasard entre min et max. */
    val burstMin: Int = 2,
    val burstMax: Int = 3,
    /** Écart entre deux coffres d'une même rafale (secondes). */
    val gapMinS: Int = 10,
    val gapMaxS: Int = 20,
    /** Pause entre deux rafales (secondes). */
    val pauseMinS: Int = 120,
    val pauseMaxS: Int = 180,
    /** Adapter la pause à l'activité du live (arrivées de spectateurs). */
    val reactive: Boolean = true,

    // --- Sac-cadeau ---
    val bagEnabled: Boolean = true,
    /** Coût d'un sac si l'appli n'arrive pas à le lire dans le panneau. */
    val bagCostFallback: Int = 20,
    /** Durée d'un sac-cadeau (secondes) avant de pouvoir en relancer un. */
    val bagDurationS: Int = 180,

    // --- Position de secours du bouton « Interagir » (en % de l'écran) ---
    val interactX: Float = 0.577f,
    val interactY: Float = 0.905f,
) {
    companion object {
        private const val NAME = "livebot"

        fun load(ctx: Context): Config {
            val p = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            val d = Config()
            return Config(
                budget = p.getInt("budget", d.budget),
                chestEnabled = p.getBoolean("chestEnabled", d.chestEnabled),
                chestAmount = p.getInt("chestAmount", d.chestAmount),
                burstMin = p.getInt("burstMin", d.burstMin),
                burstMax = p.getInt("burstMax", d.burstMax),
                gapMinS = p.getInt("gapMinS", d.gapMinS),
                gapMaxS = p.getInt("gapMaxS", d.gapMaxS),
                pauseMinS = p.getInt("pauseMinS", d.pauseMinS),
                pauseMaxS = p.getInt("pauseMaxS", d.pauseMaxS),
                reactive = p.getBoolean("reactive", d.reactive),
                bagEnabled = p.getBoolean("bagEnabled", d.bagEnabled),
                bagCostFallback = p.getInt("bagCostFallback", d.bagCostFallback),
                bagDurationS = p.getInt("bagDurationS", d.bagDurationS),
                interactX = p.getFloat("interactX", d.interactX),
                interactY = p.getFloat("interactY", d.interactY),
            ).sanitized()
        }
    }

    fun save(ctx: Context) {
        val c = sanitized()
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("budget", c.budget)
            .putBoolean("chestEnabled", c.chestEnabled)
            .putInt("chestAmount", c.chestAmount)
            .putInt("burstMin", c.burstMin)
            .putInt("burstMax", c.burstMax)
            .putInt("gapMinS", c.gapMinS)
            .putInt("gapMaxS", c.gapMaxS)
            .putInt("pauseMinS", c.pauseMinS)
            .putInt("pauseMaxS", c.pauseMaxS)
            .putBoolean("reactive", c.reactive)
            .putBoolean("bagEnabled", c.bagEnabled)
            .putInt("bagCostFallback", c.bagCostFallback)
            .putInt("bagDurationS", c.bagDurationS)
            .putFloat("interactX", c.interactX)
            .putFloat("interactY", c.interactY)
            .apply()
    }

    /** Remet les valeurs dans des bornes raisonnables (min <= max, pas de négatif...). */
    fun sanitized(): Config {
        val bMin = burstMin.coerceIn(1, 10)
        val gMin = gapMinS.coerceIn(3, 600)
        val pMin = pauseMinS.coerceIn(10, 3600)
        return copy(
            budget = budget.coerceIn(0, 1_000_000),
            chestAmount = chestAmount.coerceIn(1, 100_000),
            burstMin = bMin,
            burstMax = burstMax.coerceIn(bMin, 10),
            gapMinS = gMin,
            gapMaxS = gapMaxS.coerceIn(gMin, 600),
            pauseMinS = pMin,
            pauseMaxS = pauseMaxS.coerceIn(pMin, 3600),
            bagCostFallback = bagCostFallback.coerceIn(1, 100_000),
            bagDurationS = bagDurationS.coerceIn(30, 3600),
            interactX = interactX.coerceIn(0f, 1f),
            interactY = interactY.coerceIn(0f, 1f),
        )
    }
}
