package muhamad.irfan.si_tahu.data.product

data class ItemProduk(
    val id: String,
    val kodeProduk: String,
    val namaProduk: String,
    val jenisProduk: String,
    val satuan: String,
    val stokSaatIni: Long,
    val stokMinimum: Long,
    val tampilDiKasir: Boolean,
    val aktifDijual: Boolean,
    val urlFoto: String
)