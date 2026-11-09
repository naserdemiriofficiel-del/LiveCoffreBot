package ch.kaliterimes.livebot

import android.content.Context

/** Une position à l'écran, en fraction de la taille réelle de l'écran (0..1). */
data class Pos(val x: Float, val y: Float)

/**
 * Positions apprises avec le mode 🎯 (apprentissage).
 * Tant qu'une position n'est pas apprise, elle vaut null et le bot cherche le bouton par son texte.
 */
data class Positions(
    val interact: Pos? = null,
    val chest: Pos? = null,
    val amount: Pos? = null,
    val chestSend: Pos? = null,
    val bag: Pos? = null,
    val bagSend: Pos? = null,
    /** true si, pendant l'apprentissage, les textes des panneaux TikTok étaient lisibles. */
    val textsOk: Boolean = true,
) {
    val calibrated: Boolean
        get() = interact != null && chest != null && chestSend != null && bag != null && bagSend != null

    companion object {
        private const val NAME = "livebot_pos"
        private val KEYS = listOf("interact", "chest", "amount", "chestSend", "bag", "bagSend")

        fun load(ctx: Context): Positions {
            val p = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            fun get(k: String): Pos? {
                val x = p.getFloat("${k}_x", -1f); val y = p.getFloat("${k}_y", -1f)
                return if (x < 0 || y < 0) null else Pos(x, y)
            }
            return Positions(
                interact = get("interact"), chest = get("chest"), amount = get("amount"),
                chestSend = get("chestSend"), bag = get("bag"), bagSend = get("bagSend"),
                textsOk = p.getBoolean("textsOk", true),
            )
        }

        fun clear(ctx: Context) {
            ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    fun save(ctx: Context) {
        val e = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
        val values = listOf(interact, chest, amount, chestSend, bag, bagSend)
        for ((k, v) in KEYS.zip(values)) {
            e.putFloat("${k}_x", v?.x ?: -1f).putFloat("${k}_y", v?.y ?: -1f)
        }
        e.putBoolean("textsOk", textsOk).apply()
    }
}
