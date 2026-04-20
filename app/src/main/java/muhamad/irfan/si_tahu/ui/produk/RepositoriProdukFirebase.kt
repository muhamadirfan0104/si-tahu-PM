package muhamad.irfan.si_tahu.data.product

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RepositoriProdukFirebase(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : RepositoriProduk {

    override suspend fun getAllProducts(): List<ItemProduk> {
        val snapshot = firestore.collection("Produk").get().await()

        return snapshot.documents.mapNotNull { doc ->
            val dto = doc.toObject(DataProduk::class.java) ?: return@mapNotNull null
            ItemProduk(
                id = doc.id,
                kodeProduk = dto.kodeProduk,
                namaProduk = dto.namaProduk,
                jenisProduk = dto.jenisProduk,
                satuan = dto.satuan,
                stokSaatIni = dto.stokSaatIni,
                stokMinimum = dto.stokMinimum,
                tampilDiKasir = dto.tampilDiKasir,
                aktifDijual = dto.aktifDijual,
                urlFoto = dto.urlFoto
            )
        }
    }

    override suspend fun getCashierProducts(): List<ItemProduk> {
        return getAllProducts().filter { it.aktifDijual && it.tampilDiKasir }
    }
}