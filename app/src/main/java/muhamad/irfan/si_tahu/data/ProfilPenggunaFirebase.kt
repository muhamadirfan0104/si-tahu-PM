package muhamad.irfan.si_tahu.data

data class ProfilPenggunaFirebase(
    val namaPengguna: String = "",
    val email: String = "",
    val nomorTelepon: String = "",
    val peranAsli: String = "",
    val modeAplikasi: String = "",
    val aktif: Boolean = true,
    val bolehMasuk: Boolean = true
)