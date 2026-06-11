package muhamad.irfan.si_tahu.utilitas

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Menyimpan dan menerapkan pilihan tema aplikasi.
 * Mode yang tersedia: terang, gelap, dan mengikuti sistem perangkat.
 */
object TemaAplikasi {
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    private const val PREF_NAME = "sitahu_tema_aplikasi"
    private const val KEY_MODE = "mode_tema"

    fun bacaMode(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_SYSTEM)
            .orEmpty()
            .ifBlank { MODE_SYSTEM }
            .let { normalisasiMode(it) }
    }

    fun simpanDanTerapkan(context: Context, mode: String) {
        val modeNormal = normalisasiMode(mode)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, modeNormal)
            .apply()
        terapkanMode(modeNormal)
    }

    fun terapkanModeTersimpan(context: Context) {
        terapkanMode(bacaMode(context))
    }

    fun label(mode: String): String = when (normalisasiMode(mode)) {
        MODE_LIGHT -> "Terang"
        MODE_DARK -> "Gelap"
        else -> "Ikuti Sistem"
    }

    fun deskripsi(mode: String): String = when (normalisasiMode(mode)) {
        MODE_LIGHT -> "Selalu memakai tampilan terang."
        MODE_DARK -> "Selalu memakai tampilan gelap."
        else -> "Otomatis mengikuti pengaturan tema HP."
    }

    private fun terapkanMode(mode: String) {
        val nightMode = when (normalisasiMode(mode)) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private fun normalisasiMode(mode: String): String = when (mode.lowercase()) {
        MODE_LIGHT -> MODE_LIGHT
        MODE_DARK -> MODE_DARK
        else -> MODE_SYSTEM
    }
}
