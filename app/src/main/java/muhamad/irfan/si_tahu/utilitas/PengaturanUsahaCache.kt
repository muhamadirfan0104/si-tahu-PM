package muhamad.irfan.si_tahu.utilitas

import android.content.Context

object PengaturanUsahaCache {
    private const val PREF_NAME = "pengaturan_usaha_cache"
    private const val KEY_NAMA_USAHA = "nama_usaha"
    private const val KEY_TEKS_LOGO = "teks_logo"
    private const val KEY_ALAMAT = "alamat"
    private const val KEY_TELEPON = "nomor_telepon"
    private const val KEY_FOOTER_STRUK = "footer_struk"
    private const val KEY_NAMA_PEMILIK = "nama_pemilik"

    data class IdentitasUsaha(
        val namaUsaha: String,
        val teksLogo: String,
        val alamat: String,
        val nomorTelepon: String,
        val footerStruk: String,
        val namaPemilik: String
    )

    fun baca(context: Context): IdentitasUsaha {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val namaUsaha = prefs.getString(KEY_NAMA_USAHA, "").orEmpty().trim()
        val teksLogo = prefs.getString(KEY_TEKS_LOGO, "").orEmpty().trim()
        return IdentitasUsaha(
            namaUsaha = namaUsaha,
            teksLogo = teksLogo.ifBlank { buatTeksLogoDariNama(namaUsaha) },
            alamat = prefs.getString(KEY_ALAMAT, "").orEmpty().trim(),
            nomorTelepon = prefs.getString(KEY_TELEPON, "").orEmpty().trim(),
            footerStruk = prefs.getString(KEY_FOOTER_STRUK, "").orEmpty().trim(),
            namaPemilik = prefs.getString(KEY_NAMA_PEMILIK, "").orEmpty().trim()
        )
    }

    fun simpan(
        context: Context,
        namaUsaha: String,
        teksLogo: String,
        alamat: String = "",
        nomorTelepon: String = "",
        footerStruk: String = "",
        namaPemilik: String = ""
    ) {
        val namaBersih = namaUsaha.trim()
        val logoBersih = teksLogo.trim().ifBlank { buatTeksLogoDariNama(namaBersih) }
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAMA_USAHA, namaBersih)
            .putString(KEY_TEKS_LOGO, logoBersih)
            .putString(KEY_ALAMAT, alamat.trim())
            .putString(KEY_TELEPON, nomorTelepon.trim())
            .putString(KEY_FOOTER_STRUK, footerStruk.trim())
            .putString(KEY_NAMA_PEMILIK, namaPemilik.trim())
            .apply()
    }

    fun buatTeksLogoDariNama(namaUsaha: String): String {
        val kata = namaUsaha.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            kata.isEmpty() -> "TAHU"
            kata.size == 1 -> kata.first().take(4).uppercase()
            else -> kata.take(4).joinToString("") { it.take(1) }.uppercase()
        }
    }
}
