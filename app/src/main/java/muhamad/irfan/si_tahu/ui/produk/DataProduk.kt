package muhamad.irfan.si_tahu.data.product

data class DataProduk(
    val kodeProduk: String = "",
    val namaProduk: String = "",
    val jenisProduk: String = "",
    val satuan: String = "",
    val stokSaatIni: Long = 0L,
    val stokMinimum: Long = 0L,
    val tampilDiKasir: Boolean = true,
    val aktifDijual: Boolean = true,
    val urlFoto: String = ""
)