package muhamad.irfan.si_tahu.data.product

data class DataHarga(
    val kanalHarga: String = "",
    val namaHarga: String = "",
    val hargaSatuan: Long = 0L,
    val hargaUtama: Boolean = false,
    val aktif: Boolean = true
)