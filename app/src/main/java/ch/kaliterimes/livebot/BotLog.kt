package ch.kaliterimes.livebot

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Journal partagé entre le service et l'écran principal. */
object BotLog {
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
    var listener: (() -> Unit)? = null

    @Synchronized
    fun add(msg: String) {
        Log.i("LiveBot", msg)
        lines.addLast("${fmt.format(Date())}  $msg")
        while (lines.size > 200) lines.removeFirst()
        listener?.invoke()
    }

    @Synchronized
    fun text(): String = lines.reversed().joinToString("\n")
}
