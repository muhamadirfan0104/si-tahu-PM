package muhamad.irfan.si_tahu.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import muhamad.irfan.si_tahu.util.Formatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object RepositoriFirebaseUtama {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val formatIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val formatTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    data class BarisRiwayatProduksi(
        val id: String,
        val badge: String,
        val title: String,
        val subtitle: String,
        val amount: String,
        val tanggalIso: String
    )

    data class RingkasanProduksi(
        val totalProduksiDasarHariIni: Int,
        val totalBatchHariIni: Int,
        val totalKonversiHariIni: Int,
        val totalParameterAktif: Int,
        val totalRiwayat: Int,
        val recentRows: List<BarisRiwayatProduksi>
    )

    data class RiwayatStokItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val qtyText: String,
        val tone: String,
        val tanggalIso: String
    )

    data class RingkasanPenjualan(
        val totalHariIni: Long,
        val totalKasirHariIni: Long,
        val totalRekapHariIni: Long,
        val jumlahTransaksiHariIni: Int,
        val totalItemHariIni: Int,
        val recentRows: List<ItemBarisPenjualan>
    )

    data class ItemBarisPenjualan(
        val id: String,
        val title: String,
        val subtitle: String,
        val badge: String,
        val amount: String,
        val tanggalIso: String,
        val statusPenjualan: String
    )

    data class QrisPendingInfo(
        val saleId: String,
        val nomorPenjualan: String,
        val paymentOrderId: String,
        val paymentQrId: String,
        val paymentQrString: String,
        val paymentQrCreatedAtMillis: Long,
        val paymentQrExpiresAtMillis: Long,
        val totalBelanja: Long,
        val statusPembayaran: String,
        val statusPenjualan: String
    )

    data class RingkasanDashboard(
        val namaUsaha: String,
        val tanggalRingkasan: String,
        val totalPenjualan: Long,
        val totalProduksi: Int,
        val totalPengeluaran: Long,
        val totalTransaksi: Int,
        val totalLaba: Long,
        val lowStock: List<Produk>,
        val recentItems: List<ItemDashboard>
    )

    data class ItemDashboard(
        val id: String,
        val title: String,
        val subtitle: String,
        val amount: String,
        val badge: String,
        val tanggalIso: String
    )

    private data class UserRingkas(
        val idDokumen: String,
        val authUid: String,
        val nama: String,
        val email: String
    )

    data class RingkasanKasir(
        val totalHariIni: Long,
        val jumlahTransaksiHariIni: Int,
        val topProducts: List<ItemDashboard>,
        val recentRows: List<ItemBarisPenjualan>
    )

    private data class DraftRiwayatStok(
        val tanggalMutasi: Timestamp,
        val kunciTanggal: String,
        val idProduk: String,
        val kodeProduk: String,
        val namaProduk: String,
        val jenisMutasi: String,
        val sumberMutasi: String,
        val referensiId: String,
        val qtyMasuk: Long,
        val qtyKeluar: Long,
        val stokSebelum: Long,
        val stokSesudah: Long,
        val catatan: String,
        val idPembuat: String,
        val namaPembuat: String
    )


    private data class RingkasanBatchStok(
        val safeStock: Long,
        val nearExpiredStock: Long,
        val expiredStock: Long,
        val producedToday: Boolean,
        val nearestExpiryDate: String,
        val statusLabel: String
    )

    private data class AlokasiBatch(
        val ref: DocumentReference,
        val idBatch: String,
        val qtyDiambil: Long,
        val qtySebelum: Long,
        val qtySesudah: Long,
        val tanggalProduksi: String,
        val tanggalKadaluarsa: String
    )

    private fun nowTimestamp(): Timestamp = Timestamp.now()

    private fun parseTimestamp(dateTime: String): Timestamp {
        return Timestamp(Formatter.parseDate(dateTime))
    }

    private fun isoFromTimestamp(timestamp: Timestamp?): String {
        return formatIso.format(timestamp?.toDate() ?: Date())
    }

    private fun dayKeyFromTimestamp(timestamp: Timestamp?): String {
        return formatTanggal.format(timestamp?.toDate() ?: Date())
    }

    private fun dayKeyFromString(dateTime: String): String = Formatter.toDateOnly(dateTime)


    private fun tanggalKeySaatIni(): String = Formatter.currentDateOnly()

    private fun tambahHariKey(dateKey: String, days: Int): String {
        val cal = Calendar.getInstance().apply {
            time = Formatter.parseDate("${dateKey}T00:00:00")
            add(Calendar.DAY_OF_MONTH, days.coerceAtLeast(1))
        }
        return formatTanggal.format(cal.time)
    }

    private fun batasHampirKadaluarsaKey(hariHampirKadaluarsa: Int): String {
        val cal = Calendar.getInstance().apply {
            time = Formatter.parseDate("${tanggalKeySaatIni()}T00:00:00")
            add(Calendar.DAY_OF_MONTH, hariHampirKadaluarsa.coerceAtLeast(0))
        }
        return formatTanggal.format(cal.time)
    }

    private fun statusBatchKadaluarsa(
        expiryKey: String,
        hariHampirKadaluarsa: Int = 1
    ): String {
        val today = tanggalKeySaatIni()
        val warningLimit = batasHampirKadaluarsaKey(hariHampirKadaluarsa)
        return when {
            expiryKey.isBlank() -> "AMAN"
            expiryKey < today -> "KADALUARSA"
            expiryKey <= warningLimit -> "HAMPIR_KADALUARSA"
            else -> "AMAN"
        }
    }

    private fun tanggalKadaluarsaTimestamp(expiryKey: String): Timestamp {
        return Timestamp(Formatter.parseDate("${expiryKey}T23:59:59"))
    }

    private suspend fun ringkasanBatchStok(
        productId: String,
        stokProduk: Long,
        hariHampirKadaluarsaProduk: Int = 1
    ): RingkasanBatchStok {
        val snapshot = firestore.collection("BatchStok")
            .whereEqualTo("idProduk", productId)
            .get()
            .await()

        val docs = snapshot.documents.filter { (it.getLong("qtySisa") ?: 0L) > 0L }
        if (docs.isEmpty()) {
            return if (stokProduk > 0L) {
                RingkasanBatchStok(
                    safeStock = stokProduk,
                    nearExpiredStock = 0L,
                    expiredStock = 0L,
                    producedToday = false,
                    nearestExpiryDate = "",
                    statusLabel = "Stok Lama"
                )
            } else {
                RingkasanBatchStok(0L, 0L, 0L, false, "", "Habis")
            }
        }

        val today = tanggalKeySaatIni()
        var safe = 0L
        var near = 0L
        var expired = 0L
        var producedToday = false
        var nearest = ""

        val totalBatchQty = docs.sumOf { it.getLong("qtySisa") ?: 0L }
        val legacyQty = (stokProduk - totalBatchQty).coerceAtLeast(0L)
        if (legacyQty > 0L) safe += legacyQty

        docs.forEach { doc ->
            val qty = doc.getLong("qtySisa") ?: 0L
            val prodKey = doc.getString("kunciTanggalProduksi").orEmpty()
                .ifBlank { dayKeyFromTimestamp(doc.getTimestamp("tanggalProduksi")) }
            val expiryKey = doc.getString("kunciTanggalKadaluarsa").orEmpty()
                .ifBlank { dayKeyFromTimestamp(doc.getTimestamp("tanggalKadaluarsa")) }
            val hariHampirKadaluarsa = hariHampirKadaluarsaProduk.coerceAtLeast(0)

            if (prodKey == today) producedToday = true

            when (statusBatchKadaluarsa(expiryKey, hariHampirKadaluarsa)) {
                "KADALUARSA" -> expired += qty
                "HAMPIR_KADALUARSA" -> {
                    near += qty
                    if (nearest.isBlank() || expiryKey < nearest) nearest = expiryKey
                }
                else -> {
                    safe += qty
                    if (nearest.isBlank() || expiryKey < nearest) nearest = expiryKey
                }
            }
        }

        val status = when {
            safe + near <= 0L && expired > 0L -> "Kadaluarsa"
            near > 0L -> "Hampir Kadaluarsa"
            producedToday -> "Produksi Hari Ini"
            safe > 0L -> "Stok Sisa"
            else -> "Habis"
        }

        return RingkasanBatchStok(safe, near, expired, producedToday, nearest, status)
    }

    private suspend fun muatBatchStokFefoRefs(productIds: Collection<String>): Map<String, List<DocumentReference>> {
        return productIds.distinct().associateWith { productId ->
            firestore.collection("BatchStok")
                .whereEqualTo("idProduk", productId)
                .get()
                .await()
                .documents
                .filter { doc ->
                    val qtySisa = doc.getLong("qtySisa") ?: 0L
                    val expiryKey = doc.getString("kunciTanggalKadaluarsa").orEmpty()
                        .ifBlank { dayKeyFromTimestamp(doc.getTimestamp("tanggalKadaluarsa")) }
                    qtySisa > 0L && statusBatchKadaluarsa(expiryKey) != "KADALUARSA"
                }
                .sortedWith(
                    compareBy<DocumentSnapshot> {
                        it.getString("kunciTanggalKadaluarsa").orEmpty()
                            .ifBlank { dayKeyFromTimestamp(it.getTimestamp("tanggalKadaluarsa")) }
                    }.thenBy {
                        it.getString("kunciTanggalProduksi").orEmpty()
                            .ifBlank { dayKeyFromTimestamp(it.getTimestamp("tanggalProduksi")) }
                    }
                )
                .map { it.reference }
        }
    }

    private fun siapkanAlokasiBatchFefo(
        trx: Transaction,
        productId: String,
        productName: String,
        qtyDiminta: Long,
        batchRefsByProduct: Map<String, List<DocumentReference>>
    ): List<AlokasiBatch> {
        val refs = batchRefsByProduct[productId].orEmpty()
        if (refs.isEmpty()) return emptyList()

        var sisaDiminta = qtyDiminta
        val alokasi = mutableListOf<AlokasiBatch>()

        refs.forEach { ref ->
            if (sisaDiminta <= 0L) return@forEach
            val snap = trx.get(ref)
            val qtySisa = snap.getLong("qtySisa") ?: 0L
            if (qtySisa <= 0L) return@forEach

            val ambil = minOf(qtySisa, sisaDiminta)
            val prodKey = snap.getString("kunciTanggalProduksi").orEmpty()
                .ifBlank { dayKeyFromTimestamp(snap.getTimestamp("tanggalProduksi")) }
            val expiryKey = snap.getString("kunciTanggalKadaluarsa").orEmpty()
                .ifBlank { dayKeyFromTimestamp(snap.getTimestamp("tanggalKadaluarsa")) }

            alokasi += AlokasiBatch(
                ref = ref,
                idBatch = snap.id,
                qtyDiambil = ambil,
                qtySebelum = qtySisa,
                qtySesudah = qtySisa - ambil,
                tanggalProduksi = prodKey,
                tanggalKadaluarsa = expiryKey
            )
            sisaDiminta -= ambil
        }

        return alokasi
    }

    private fun terapkanAlokasiBatchFefo(
        trx: Transaction,
        allocations: Collection<AlokasiBatch>,
        updatedAt: Timestamp
    ) {
        allocations.forEach { alokasi ->
            trx.update(
                alokasi.ref,
                mapOf(
                    "qtySisa" to alokasi.qtySesudah,
                    "statusBatch" to if (alokasi.qtySesudah <= 0L) "HABIS" else "AKTIF",
                    "diperbaruiPada" to updatedAt
                )
            )
        }
    }

    private fun newId(prefix: String): String {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        return "${prefix}_${suffix}"
    }

    private fun statusProduk(produk: Produk): String {
        val stokLayak = produk.safeStock + produk.nearExpiredStock
        return when {
            stokLayak <= 0 -> "Habis"
            stokLayak <= produk.minStock -> "Menipis"
            else -> "Aman"
        }
    }

    private fun labelSumberPenjualan(raw: String?): String {
        return when (raw.orEmpty().uppercase()) {
            "KASIR" -> "Rumahan"
            "PASAR" -> "Pasar"
            else -> raw.orEmpty().ifBlank { "Rumahan" }
        }
    }

    private fun labelMetodePembayaran(raw: String?): String {
        return when (raw.orEmpty().uppercase()) {
            "TUNAI" -> "Tunai"
            "QRIS" -> "QRIS"
            "TRANSFER" -> "Transfer"
            "REKAP" -> "Rekap"
            else -> raw.orEmpty().ifBlank { "-" }
        }
    }

    private fun DocumentSnapshot.numberAsDouble(field: String): Double {
        val raw = get(field)
        return when (raw) {
            is Number -> raw.toDouble()
            else -> 0.0
        }
    }

    private fun formatBatch(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString().replace(".", ",")
        }
    }

    private suspend fun cariPengguna(authUid: String): UserRingkas? {
        if (authUid.isBlank()) return null

        val doc = PenggunaFirestoreCompat.findByAuthUidSuspend(firestore, authUid)
        if (doc != null && doc.exists()) return doc.toUserRingkas(authUid)
        return null
    }

    private fun DocumentSnapshot.toUserRingkas(authUid: String): UserRingkas {
        return UserRingkas(
            idDokumen = id,
            authUid = authUid,
            nama = getString("namaPengguna").orEmpty()
                .ifBlank { getString("email").orEmpty().ifBlank { "Pengguna" } },
            email = getString("email").orEmpty()
        )
    }

    private suspend fun muatChannelsProduk(productRefPath: String): MutableList<HargaKanal> {
        val snapshot = firestore.document(productRefPath).collection("hargaJual").get().await()
        return snapshot.documents.map {
            HargaKanal(
                id = it.id,
                label = it.getString("kanalHarga").orEmpty().ifBlank { it.getString("namaHarga").orEmpty() },
                price = it.getLong("hargaSatuan") ?: 0L,
                active = it.getBoolean("aktif") ?: true,
                defaultCashier = it.getBoolean("hargaUtama") ?: false,
                deleted = false
            )
        }.sortedWith(compareByDescending<HargaKanal> { it.defaultCashier }.thenBy { it.label }).toMutableList()
    }


    private fun produkRef(productId: String) = firestore.collection("Produk").document(productId)

    private fun parameterProduksiRef(productId: String) =
        produkRef(productId).collection("parameterProduksi")

    private suspend fun catatRiwayatStok(drafts: List<DraftRiwayatStok>) {
        if (drafts.isEmpty()) return

        val batch = firestore.batch()
        drafts.forEach { draft ->
            batch.set(
                firestore.collection("RiwayatStok").document(newId("rst")),
                mapOf(
                    "tanggalMutasi" to draft.tanggalMutasi,
                    "kunciTanggal" to draft.kunciTanggal,
                    "idProduk" to draft.idProduk,
                    "kodeProduk" to draft.kodeProduk,
                    "namaProduk" to draft.namaProduk,
                    "jenisMutasi" to draft.jenisMutasi,
                    "sumberMutasi" to draft.sumberMutasi,
                    "referensiId" to draft.referensiId,
                    "qtyMasuk" to draft.qtyMasuk,
                    "qtyKeluar" to draft.qtyKeluar,
                    "stokSebelum" to draft.stokSebelum,
                    "stokSesudah" to draft.stokSesudah,
                    "catatan" to draft.catatan,
                    "idPembuat" to draft.idPembuat,
                    "namaPembuat" to draft.namaPembuat,
                    "dibuatPada" to nowTimestamp()
                )
            )
        }
        batch.commit().await()
    }

    private suspend fun hapusRiwayatStokByReferensi(referensiId: String, sumberMutasi: String) {
        if (referensiId.isBlank()) return

        val snapshot = firestore.collection("RiwayatStok")
            .whereEqualTo("referensiId", referensiId)
            .whereEqualTo("sumberMutasi", sumberMutasi)
            .get()
            .await()

        if (snapshot.isEmpty) return

        val batch = firestore.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    suspend fun muatSemuaProduk(): List<Produk> = coroutineScope {
        val snapshot = firestore.collection("Produk")
            .orderBy("namaProduk")
            .get()
            .await()

        snapshot.documents.map { doc ->
            async {
                val stokSaatIni = doc.getLong("stokSaatIni") ?: 0L
                val masaSimpanHari = (doc.getLong("masaSimpanHari") ?: 2L).toInt().coerceAtLeast(1)
                val hariHampirKadaluarsa = (doc.getLong("hariHampirKadaluarsa") ?: 1L)
                    .toInt()
                    .coerceAtLeast(0)
                    .coerceAtMost(masaSimpanHari)
                val ringkasanBatch = ringkasanBatchStok(doc.id, stokSaatIni, hariHampirKadaluarsa)
                Produk(
                    id = doc.id,
                    code = doc.getString("kodeProduk").orEmpty().ifBlank { doc.id },
                    name = doc.getString("namaProduk").orEmpty(),
                    category = doc.getString("jenisProduk").orEmpty().ifBlank { "DASAR" },
                    unit = doc.getString("satuan").orEmpty().ifBlank { "pcs" },
                    stock = stokSaatIni.toInt(),
                    minStock = (doc.getLong("stokMinimum") ?: 0L).toInt(),
                    active = doc.getBoolean("aktifDijual") ?: true,
                    showInCashier = doc.getBoolean("tampilDiKasir") ?: true,
                    photoTone = "",
                    channels = muatChannelsProduk(doc.reference.path),
                    deleted = false,
                    shelfLifeDays = masaSimpanHari,
                    nearExpiryWarningDays = hariHampirKadaluarsa,
                    producedToday = ringkasanBatch.producedToday,
                    safeStock = ringkasanBatch.safeStock.toInt(),
                    nearExpiredStock = ringkasanBatch.nearExpiredStock.toInt(),
                    expiredStock = ringkasanBatch.expiredStock.toInt(),
                    nearestExpiryDate = ringkasanBatch.nearestExpiryDate,
                    stockBatchStatus = ringkasanBatch.statusLabel
                )
            }
        }.awaitAll()
    }

    suspend fun muatProdukAktif(): List<Produk> = muatSemuaProduk().filter { it.active }

    suspend fun muatProdukKasir(): List<Produk> = muatSemuaProduk().filter { it.active && it.showInCashier }

    suspend fun muatProdukById(productId: String): Produk? {
        return muatSemuaProduk().firstOrNull { it.id == productId }
    }

    suspend fun muatParameterAktif(productId: String?): ParameterProduksi? {
        if (productId.isNullOrBlank()) return null

        val snapshot = parameterProduksiRef(productId)
            .get()
            .await()
            .documents
            .filter { it.getBoolean("dihapus") != true }
            .firstOrNull { it.getBoolean("aktif") != false } ?: return null

        return ParameterProduksi(
            id = snapshot.id,
            productId = snapshot.getString("idProduk").orEmpty().ifBlank { productId },
            resultPerBatch = (snapshot.getLong("hasilPerProduksi") ?: 0L).toInt(),
            note = snapshot.getString("catatan").orEmpty(),
            active = snapshot.getBoolean("aktif") != false
        )
    }

    suspend fun muatSemuaParameter(): List<ParameterProduksi> = coroutineScope {
        val produkSnapshot = firestore.collection("Produk").get().await()
        produkSnapshot.documents.map { produkDoc ->
            async {
                parameterProduksiRef(produkDoc.id)
                    .get()
                    .await()
                    .documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map {
                        ParameterProduksi(
                            id = it.id,
                            productId = it.getString("idProduk").orEmpty().ifBlank {
                                it.reference.parent.parent?.id.orEmpty()
                            },
                            resultPerBatch = (it.getLong("hasilPerProduksi") ?: 0L).toInt(),
                            note = it.getString("catatan").orEmpty(),
                            active = it.getBoolean("aktif") != false
                        )
                    }
            }
        }.awaitAll().flatten().sortedBy { it.productId }
    }

    suspend fun simpanProduksiDasar(
        dateTime: String,
        productId: String,
        batches: Double,
        note: String,
        userAuthId: String
    ): String {
        require(batches > 0.0) { "Jumlah masak harus lebih dari 0" }

        val productRef = produkRef(productId)
        val parameter = muatParameterAktif(productId)
        require(parameter != null && parameter.resultPerBatch > 0) {
            "Produk dasar harus punya parameter aktif sebelum produksi"
        }

        val hasilPerProduksi = parameter.resultPerBatch
        val totalHasil = kotlin.math.round(hasilPerProduksi * batches).toInt()
        val user = cariPengguna(userAuthId)
        val catatanRef = firestore.collection("CatatanProduksi").document(newId("prod"))
        val batchStokRef = firestore.collection("BatchStok").document(newId("bst"))
        val tanggalProduksi = parseTimestamp(dateTime)
        val kunciTanggal = dayKeyFromString(dateTime)
        val dibuatPada = nowTimestamp()
        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()

        firestore.runTransaction { trx ->
            val produkSnap = trx.get(productRef)
            check(produkSnap.exists()) { "Produk tidak ditemukan" }

            val jenisProduk = produkSnap.getString("jenisProduk").orEmpty()
            check(jenisProduk.equals("DASAR", ignoreCase = true)) {
                "Produksi dasar hanya untuk produk kategori DASAR"
            }

            val namaProduk = produkSnap.getString("namaProduk") ?: "Produk"
            val kodeProduk = produkSnap.getString("kodeProduk") ?: productId
            val satuanHasil = produkSnap.getString("satuan") ?: "pcs"
            val masaSimpanHari = ((produkSnap.getLong("masaSimpanHari") ?: 2L).toInt()).coerceAtLeast(1)
            val hariHampirKadaluarsa = ((produkSnap.getLong("hariHampirKadaluarsa") ?: 1L).toInt())
                .coerceAtLeast(0)
                .coerceAtMost(masaSimpanHari)
            val kunciKadaluarsa = tambahHariKey(kunciTanggal, masaSimpanHari)
            val stokSaatIni = produkSnap.getLong("stokSaatIni") ?: 0L
            val stokSesudah = stokSaatIni + totalHasil.toLong()

            trx.update(
                productRef,
                mapOf(
                    "stokSaatIni" to stokSesudah,
                    "diperbaruiPada" to dibuatPada
                )
            )

            trx.set(
                catatanRef,
                mapOf(
                    "jenisProduksi" to "DASAR",
                    "tanggalProduksi" to tanggalProduksi,
                    "kunciTanggal" to kunciTanggal,
                    "idParameterProduksi" to parameter.id,
                    "idProdukAsal" to "",
                    "namaProdukAsal" to "",
                    "jumlahBahan" to batches,
                    "satuanBahan" to "Masak",
                    "idProdukHasil" to productId,
                    "namaProdukHasil" to namaProduk,
                    "jumlahHasil" to totalHasil,
                    "satuanHasil" to satuanHasil,
                    "catatan" to note,
                    "dibuatOlehId" to (user?.idDokumen ?: userAuthId),
                    "dibuatOlehNama" to (user?.nama ?: "Pengguna"),
                    "dibuatPada" to dibuatPada
                )
            )

            trx.set(
                batchStokRef,
                mapOf(
                    "idProduk" to productId,
                    "kodeProduk" to kodeProduk,
                    "namaProduk" to namaProduk,
                    "jenisProduk" to jenisProduk,
                    "satuan" to satuanHasil,
                    "tanggalProduksi" to tanggalProduksi,
                    "kunciTanggalProduksi" to kunciTanggal,
                    "tanggalKadaluarsa" to tanggalKadaluarsaTimestamp(kunciKadaluarsa),
                    "kunciTanggalKadaluarsa" to kunciKadaluarsa,
                    "masaSimpanHari" to masaSimpanHari,
                    "hariHampirKadaluarsa" to hariHampirKadaluarsa,
                    "qtyAwal" to totalHasil.toLong(),
                    "qtySisa" to totalHasil.toLong(),
                    "sumberProduksiId" to catatanRef.id,
                    "statusBatch" to "AKTIF",
                    "dibuatPada" to dibuatPada,
                    "diperbaruiPada" to dibuatPada
                )
            )

            riwayatDrafts = listOf(
                DraftRiwayatStok(
                    tanggalMutasi = tanggalProduksi,
                    kunciTanggal = kunciTanggal,
                    idProduk = productId,
                    kodeProduk = kodeProduk,
                    namaProduk = namaProduk,
                    jenisMutasi = "PRODUKSI_DASAR_MASUK",
                    sumberMutasi = "CatatanProduksi",
                    referensiId = catatanRef.id,
                    qtyMasuk = totalHasil.toLong(),
                    qtyKeluar = 0L,
                    stokSebelum = stokSaatIni,
                    stokSesudah = stokSesudah,
                    catatan = note.ifBlank { "Produksi dasar" },
                    idPembuat = user?.idDokumen ?: userAuthId,
                    namaPembuat = user?.nama ?: "Pengguna"
                )
            )
        }.await()

        catatRiwayatStok(riwayatDrafts)
        perbaruiRingkasanHarian(kunciTanggal)
        return catatanRef.id
    }

    suspend fun simpanKonversi(
        dateTime: String,
        fromProductId: String,
        toProductId: String,
        inputQty: Int,
        outputQty: Int,
        note: String,
        userAuthId: String
    ): String {
        require(fromProductId != toProductId) { "Produk asal dan hasil tidak boleh sama" }
        require(inputQty > 0) { "Jumlah bahan harus lebih dari 0" }
        require(outputQty > 0) { "Jumlah hasil harus lebih dari 0" }

        val fromRef = produkRef(fromProductId)
        val toRef = produkRef(toProductId)
        val user = cariPengguna(userAuthId)
        val catatanRef = firestore.collection("CatatanProduksi").document(newId("prod"))
        val batchStokRef = firestore.collection("BatchStok").document(newId("bst"))
        val tanggalProduksi = parseTimestamp(dateTime)
        val kunciTanggal = dayKeyFromString(dateTime)
        val dibuatPada = nowTimestamp()
        val batchRefsByProduct = muatBatchStokFefoRefs(listOf(fromProductId))
        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()

        firestore.runTransaction { trx ->
            val fromSnap = trx.get(fromRef)
            val toSnap = trx.get(toRef)
            check(fromSnap.exists()) { "Produk asal tidak ditemukan" }
            check(toSnap.exists()) { "Produk hasil tidak ditemukan" }
            check((fromSnap.getString("jenisProduk") ?: "").equals("DASAR", ignoreCase = true)) {
                "Produk asal produk olahan harus kategori DASAR"
            }
            check((toSnap.getString("jenisProduk") ?: "").equals("OLAHAN", ignoreCase = true)) {
                "Produk hasil produk olahan harus kategori OLAHAN"
            }

            val stokAsal = fromSnap.getLong("stokSaatIni") ?: 0L
            check(stokAsal >= inputQty) { "Stok bahan tidak mencukupi" }
            val alokasiBahan = siapkanAlokasiBatchFefo(trx, fromProductId, fromSnap.getString("namaProduk") ?: "Bahan", inputQty.toLong(), batchRefsByProduct)
            val stokAsalSesudah = stokAsal - inputQty.toLong()
            val stokHasil = toSnap.getLong("stokSaatIni") ?: 0L
            val stokHasilSesudah = stokHasil + outputQty.toLong()

            val fromNama = fromSnap.getString("namaProduk") ?: "Bahan"
            val fromKode = fromSnap.getString("kodeProduk") ?: fromProductId
            val fromSatuan = fromSnap.getString("satuan") ?: "pcs"
            val toNama = toSnap.getString("namaProduk") ?: "Hasil"
            val toKode = toSnap.getString("kodeProduk") ?: toProductId
            val toSatuan = toSnap.getString("satuan") ?: "pcs"
            val masaSimpanHari = ((toSnap.getLong("masaSimpanHari") ?: 2L).toInt()).coerceAtLeast(1)
            val hariHampirKadaluarsa = ((toSnap.getLong("hariHampirKadaluarsa") ?: 1L).toInt())
                .coerceAtLeast(0)
                .coerceAtMost(masaSimpanHari)
            val kunciKadaluarsa = tambahHariKey(kunciTanggal, masaSimpanHari)

            trx.update(fromRef, mapOf("stokSaatIni" to stokAsalSesudah, "diperbaruiPada" to dibuatPada))
            trx.update(toRef, mapOf("stokSaatIni" to stokHasilSesudah, "diperbaruiPada" to dibuatPada))
            terapkanAlokasiBatchFefo(trx, alokasiBahan, dibuatPada)
            trx.set(
                catatanRef,
                mapOf(
                    "jenisProduksi" to "OLAHAN",
                    "tanggalProduksi" to tanggalProduksi,
                    "kunciTanggal" to kunciTanggal,
                    "idParameterProduksi" to "",
                    "idProdukAsal" to fromProductId,
                    "namaProdukAsal" to fromNama,
                    "jumlahBahan" to inputQty,
                    "satuanBahan" to fromSatuan,
                    "idProdukHasil" to toProductId,
                    "namaProdukHasil" to toNama,
                    "jumlahHasil" to outputQty,
                    "satuanHasil" to toSatuan,
                    "catatan" to note,
                    "dibuatOlehId" to (user?.idDokumen ?: userAuthId),
                    "dibuatOlehNama" to (user?.nama ?: "Pengguna"),
                    "dibuatPada" to dibuatPada
                )
            )

            trx.set(
                batchStokRef,
                mapOf(
                    "idProduk" to toProductId,
                    "kodeProduk" to toKode,
                    "namaProduk" to toNama,
                    "jenisProduk" to "OLAHAN",
                    "satuan" to toSatuan,
                    "tanggalProduksi" to tanggalProduksi,
                    "kunciTanggalProduksi" to kunciTanggal,
                    "tanggalKadaluarsa" to tanggalKadaluarsaTimestamp(kunciKadaluarsa),
                    "kunciTanggalKadaluarsa" to kunciKadaluarsa,
                    "masaSimpanHari" to masaSimpanHari,
                    "hariHampirKadaluarsa" to hariHampirKadaluarsa,
                    "qtyAwal" to outputQty.toLong(),
                    "qtySisa" to outputQty.toLong(),
                    "sumberProduksiId" to catatanRef.id,
                    "statusBatch" to "AKTIF",
                    "dibuatPada" to dibuatPada,
                    "diperbaruiPada" to dibuatPada
                )
            )

            riwayatDrafts = listOf(
                DraftRiwayatStok(
                    tanggalMutasi = tanggalProduksi,
                    kunciTanggal = kunciTanggal,
                    idProduk = fromProductId,
                    kodeProduk = fromKode,
                    namaProduk = fromNama,
                    jenisMutasi = "KONVERSI_KELUAR",
                    sumberMutasi = "CatatanProduksi",
                    referensiId = catatanRef.id,
                    qtyMasuk = 0L,
                    qtyKeluar = inputQty.toLong(),
                    stokSebelum = stokAsal,
                    stokSesudah = stokAsalSesudah,
                    catatan = note.ifBlank { "Produk olahan ke $toNama" },
                    idPembuat = user?.idDokumen ?: userAuthId,
                    namaPembuat = user?.nama ?: "Pengguna"
                ),
                DraftRiwayatStok(
                    tanggalMutasi = tanggalProduksi,
                    kunciTanggal = kunciTanggal,
                    idProduk = toProductId,
                    kodeProduk = toKode,
                    namaProduk = toNama,
                    jenisMutasi = "KONVERSI_MASUK",
                    sumberMutasi = "CatatanProduksi",
                    referensiId = catatanRef.id,
                    qtyMasuk = outputQty.toLong(),
                    qtyKeluar = 0L,
                    stokSebelum = stokHasil,
                    stokSesudah = stokHasilSesudah,
                    catatan = note.ifBlank { "Hasil produk olahan dari $fromNama" },
                    idPembuat = user?.idDokumen ?: userAuthId,
                    namaPembuat = user?.nama ?: "Pengguna"
                )
            )
        }.await()

        catatRiwayatStok(riwayatDrafts)
        perbaruiRingkasanHarian(kunciTanggal)
        return catatanRef.id
    }

    suspend fun muatRingkasanProduksi(): RingkasanProduksi {
        val today = Formatter.currentDateOnly()
        val parameters = muatSemuaParameter()

        val snapshot = firestore.collection("CatatanProduksi")
            .whereEqualTo("kunciTanggal", today)
            .get()
            .await()

        var totalProduksiDasarHariIni = 0
        var totalBatchHariIni = 0.0
        var totalKonversiHariIni = 0

        snapshot.documents.forEach { doc ->
            val jenis = doc.getString("jenisProduksi").orEmpty()
            if (jenis == "OLAHAN") {
                totalKonversiHariIni += 1
            } else {
                totalProduksiDasarHariIni += (doc.getLong("jumlahHasil") ?: 0L).toInt()
                totalBatchHariIni += doc.numberAsDouble("jumlahBahan")
            }
        }

        val riwayat = muatRiwayatProduksi()

        return RingkasanProduksi(
            totalProduksiDasarHariIni = totalProduksiDasarHariIni,
            totalBatchHariIni = totalBatchHariIni.toInt(),
            totalKonversiHariIni = totalKonversiHariIni,
            totalParameterAktif = parameters.count { it.active },
            totalRiwayat = riwayat.size,
            recentRows = riwayat.take(5)
        )
    }

    suspend fun muatRiwayatProduksi(): List<BarisRiwayatProduksi> {
        val snapshot = firestore.collection("CatatanProduksi")
            .orderBy("dibuatPada", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.map { doc ->
            val jenis = doc.getString("jenisProduksi").orEmpty()
            val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalProduksi") ?: doc.getTimestamp("dibuatPada"))

            if (jenis == "OLAHAN") {
                val namaAsal = doc.getString("namaProdukAsal").orEmpty().ifBlank { "Bahan" }
                val namaHasil = doc.getString("namaProdukHasil").orEmpty().ifBlank { "Hasil" }
                val bahan = (doc.getLong("jumlahBahan") ?: 0L).toInt()
                val hasil = (doc.getLong("jumlahHasil") ?: 0L).toInt()

                BarisRiwayatProduksi(
                    id = doc.id,
                    badge = "Produk Olahan",
                    title = "$namaAsal → $namaHasil",
                    subtitle = Formatter.readableDateTime(tanggalIso),
                    amount = "$bahan → $hasil",
                    tanggalIso = tanggalIso
                )
            } else {
                val namaHasil = doc.getString("namaProdukHasil").orEmpty().ifBlank { "Produk Dasar" }
                val jumlahBatch = doc.numberAsDouble("jumlahBahan")
                val jumlahHasil = (doc.getLong("jumlahHasil") ?: 0L).toInt()
                val satuan = doc.getString("satuanHasil").orEmpty().ifBlank { "pcs" }

                BarisRiwayatProduksi(
                    id = doc.id,
                    badge = "Produksi Dasar",
                    title = namaHasil,
                    subtitle = "${Formatter.readableDateTime(tanggalIso)} • ${formatBatch(jumlahBatch)} Masak",
                    amount = "+${Formatter.ribuan(jumlahHasil.toLong())} $satuan",
                    tanggalIso = tanggalIso
                )
            }
        }
    }

    suspend fun buildProductionDetailText(id: String): String {
        val doc = firestore.collection("CatatanProduksi").document(id).get().await()
        if (!doc.exists()) return "Data produksi tidak ditemukan"

        val jenis = doc.getString("jenisProduksi").orEmpty()
        val tanggalProduksi = isoFromTimestamp(doc.getTimestamp("tanggalProduksi") ?: doc.getTimestamp("dibuatPada"))
        val tanggalInput = isoFromTimestamp(doc.getTimestamp("dibuatPada"))
        val namaInput = doc.getString("dibuatOlehNama").orEmpty().ifBlank { "-" }
        val idInput = doc.getString("dibuatOlehId").orEmpty().ifBlank { "-" }
        val catatan = doc.getString("catatan").orEmpty().ifBlank { "-" }

        return if (jenis == "OLAHAN") {
            """
        ID: ${doc.id}
        Jenis: Produk Olahan
        Tanggal Produksi: ${Formatter.readableDateTime(tanggalProduksi)}
        Waktu Input: ${Formatter.readableDateTime(tanggalInput)}
        Produk Asal: ${doc.getString("namaProdukAsal").orEmpty().ifBlank { "-" }}
        Jumlah Bahan: ${Formatter.ribuan(doc.getLong("jumlahBahan") ?: 0L)} ${doc.getString("satuanBahan").orEmpty().ifBlank { "pcs" }}
        Produk Hasil: ${doc.getString("namaProdukHasil").orEmpty().ifBlank { "-" }}
        Jumlah Hasil: ${Formatter.ribuan(doc.getLong("jumlahHasil") ?: 0L)} ${doc.getString("satuanHasil").orEmpty().ifBlank { "pcs" }}
        Catatan: $catatan
        Diinput Oleh: $namaInput
        ID Input: $idInput
        """.trimIndent()
        } else {
            """
        ID: ${doc.id}
        Jenis: Produksi Dasar
        Tanggal Produksi: ${Formatter.readableDateTime(tanggalProduksi)}
        Waktu Input: ${Formatter.readableDateTime(tanggalInput)}
        Produk Hasil: ${doc.getString("namaProdukHasil").orEmpty().ifBlank { "-" }}
        Masak: ${formatBatch(doc.numberAsDouble("jumlahBahan"))}
        Hasil Produksi: ${Formatter.ribuan(doc.getLong("jumlahHasil") ?: 0L)} ${doc.getString("satuanHasil").orEmpty().ifBlank { "pcs" }}
        Parameter: ${doc.getString("idParameterProduksi").orEmpty().ifBlank { "Default" }}
        Catatan: $catatan
        Diinput Oleh: $namaInput
        ID Input: $idInput
        """.trimIndent()
        }
    }

    suspend fun hapusCatatanProduksi(id: String) {
        val ref = firestore.collection("CatatanProduksi").document(id)
        val doc = ref.get().await()
        if (!doc.exists()) throw IllegalStateException("Data produksi tidak ditemukan")

        val jenis = doc.getString("jenisProduksi").orEmpty()
        val tanggalKey = doc.getString("kunciTanggal").orEmpty()
            .ifBlank { dayKeyFromTimestamp(doc.getTimestamp("tanggalProduksi")) }
        val hasilQty = doc.getLong("jumlahHasil") ?: 0L
        val bahanQtyLong = kotlin.math.round(doc.numberAsDouble("jumlahBahan")).toLong()
        val hasilRef = produkRef(doc.getString("idProdukHasil").orEmpty())

        firestore.runTransaction { trx ->
            if (jenis == "OLAHAN") {
                val asalRef = produkRef(doc.getString("idProdukAsal").orEmpty())
                val asalSnap = trx.get(asalRef)
                val hasilSnap = trx.get(hasilRef)
                val stokAsal = asalSnap.getLong("stokSaatIni") ?: 0L
                val stokHasil = hasilSnap.getLong("stokSaatIni") ?: 0L
                check(stokHasil >= hasilQty) { "Stok hasil tidak cukup untuk menghapus catatan ini" }
                trx.update(asalRef, mapOf("stokSaatIni" to stokAsal + bahanQtyLong, "diperbaruiPada" to nowTimestamp()))
                trx.update(hasilRef, mapOf("stokSaatIni" to stokHasil - hasilQty, "diperbaruiPada" to nowTimestamp()))
            } else {
                val hasilSnap = trx.get(hasilRef)
                val stokHasil = hasilSnap.getLong("stokSaatIni") ?: 0L
                check(stokHasil >= hasilQty) { "Stok produk tidak cukup untuk menghapus catatan ini" }
                trx.update(hasilRef, mapOf("stokSaatIni" to stokHasil - hasilQty, "diperbaruiPada" to nowTimestamp()))
            }
            trx.delete(ref)
        }.await()

        hapusRiwayatStokByReferensi(id, "CatatanProduksi")
        perbaruiRingkasanHarian(tanggalKey)
    }

    suspend fun muatMonitoringStok(): List<Produk> = muatSemuaProduk()

    suspend fun simpanAdjustment(
        dateOnly: String,
        productId: String,
        type: String,
        qty: Int,
        note: String,
        userAuthId: String
    ): String {
        require(qty > 0) { "Jumlah adjustment harus lebih dari 0" }

        val ref = firestore.collection("PenyesuaianStok").document(newId("adj"))
        val produkRef = produkRef(productId)
        val user = cariPengguna(userAuthId)
        val dibuatPada = nowTimestamp()
        val kunciTanggal = dateOnly.ifBlank { Formatter.currentDateOnly() }
        val tanggalPenyesuaian = Timestamp(Formatter.parseDate("${kunciTanggal}T00:00:00"))
        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()

        firestore.runTransaction { trx ->
            val produkSnap = trx.get(produkRef)
            check(produkSnap.exists()) { "Produk tidak ditemukan" }
            val stok = produkSnap.getLong("stokSaatIni") ?: 0L
            val nextStok = if (type == "add") stok + qty else stok - qty
            check(nextStok >= 0) { "Stok tidak boleh minus" }

            val kodeProduk = produkSnap.getString("kodeProduk") ?: productId
            val namaProduk = produkSnap.getString("namaProduk") ?: "Produk"

            trx.update(produkRef, mapOf("stokSaatIni" to nextStok, "diperbaruiPada" to dibuatPada))
            trx.set(
                ref,
                mapOf(
                    "tanggalPenyesuaian" to tanggalPenyesuaian,
                    "kunciTanggal" to kunciTanggal,
                    "idProduk" to productId,
                    "kodeProduk" to kodeProduk,
                    "namaProduk" to namaProduk,
                    "jenisPenyesuaian" to if (type == "add") "TAMBAH" else "KURANG",
                    "jumlah" to qty,
                    "alasanPenyesuaian" to note,
                    "catatan" to note,
                    "idPembuat" to (user?.idDokumen ?: userAuthId),
                    "namaPembuat" to (user?.nama ?: "Pengguna"),
                    "dibuatPada" to dibuatPada
                )
            )

            riwayatDrafts = listOf(
                DraftRiwayatStok(
                    tanggalMutasi = tanggalPenyesuaian,
                    kunciTanggal = kunciTanggal,
                    idProduk = productId,
                    kodeProduk = kodeProduk,
                    namaProduk = namaProduk,
                    jenisMutasi = if (type == "add") "ADJUSTMENT_TAMBAH" else "ADJUSTMENT_KURANG",
                    sumberMutasi = "PenyesuaianStok",
                    referensiId = ref.id,
                    qtyMasuk = if (type == "add") qty.toLong() else 0L,
                    qtyKeluar = if (type == "add") 0L else qty.toLong(),
                    stokSebelum = stok,
                    stokSesudah = nextStok,
                    catatan = note.ifBlank { "Penyesuaian stok" },
                    idPembuat = user?.idDokumen ?: userAuthId,
                    namaPembuat = user?.nama ?: "Pengguna"
                )
            )
        }.await()

        catatRiwayatStok(riwayatDrafts)
        return ref.id
    }

    suspend fun buildAdjustmentDetailText(id: String): String {
        val doc = firestore.collection("PenyesuaianStok").document(id).get().await()
        if (!doc.exists()) return "Data adjustment tidak ditemukan"
        val tanggal = isoFromTimestamp(doc.getTimestamp("tanggalPenyesuaian") ?: doc.getTimestamp("dibuatPada"))
        return """
            ID: ${doc.id}
            Tanggal: ${Formatter.readableDateTime(tanggal)}
            Produk: ${doc.getString("namaProduk").orEmpty()}
            Jenis: ${doc.getString("jenisPenyesuaian").orEmpty()}
            Jumlah: ${Formatter.ribuan(doc.getLong("jumlah") ?: 0L)}
            Alasan: ${doc.getString("alasanPenyesuaian").orEmpty().ifBlank { "-" }}
            Catatan: ${doc.getString("catatan").orEmpty().ifBlank { "-" }}
            Dibuat Oleh: ${doc.getString("namaPembuat").orEmpty().ifBlank { "-" }}
        """.trimIndent()
    }


    suspend fun muatTotalStokKadaluarsa(productId: String): Long {
        val today = tanggalKeySaatIni()
        return firestore.collection("BatchStok")
            .whereEqualTo("idProduk", productId)
            .get()
            .await()
            .documents
            .filter { doc ->
                val qty = doc.getLong("qtySisa") ?: 0L
                val expiryKey = doc.getString("kunciTanggalKadaluarsa").orEmpty()
                    .ifBlank { dayKeyFromTimestamp(doc.getTimestamp("tanggalKadaluarsa")) }
                qty > 0L && expiryKey.isNotBlank() && expiryKey < today
            }
            .sumOf { it.getLong("qtySisa") ?: 0L }
    }

    suspend fun simpanAdjustmentKadaluarsa(
        dateOnly: String,
        productId: String,
        qty: Int,
        note: String,
        userAuthId: String
    ): String {
        require(qty > 0) { "Jumlah stok kadaluarsa harus lebih dari 0" }

        val today = tanggalKeySaatIni()
        val ref = firestore.collection("PenyesuaianStok").document(newId("adj"))
        val produkRef = produkRef(productId)
        val user = cariPengguna(userAuthId)
        val dibuatPada = nowTimestamp()
        val kunciTanggal = dateOnly.ifBlank { Formatter.currentDateOnly() }
        val tanggalPenyesuaian = Timestamp(Formatter.parseDate("${kunciTanggal}T00:00:00"))

        val expiredBatchRefs = firestore.collection("BatchStok")
            .whereEqualTo("idProduk", productId)
            .get()
            .await()
            .documents
            .filter { doc ->
                val qtySisa = doc.getLong("qtySisa") ?: 0L
                val expiryKey = doc.getString("kunciTanggalKadaluarsa").orEmpty()
                    .ifBlank { dayKeyFromTimestamp(doc.getTimestamp("tanggalKadaluarsa")) }
                qtySisa > 0L && expiryKey.isNotBlank() && expiryKey < today
            }
            .sortedWith(
                compareBy<DocumentSnapshot> {
                    it.getString("kunciTanggalKadaluarsa").orEmpty()
                        .ifBlank { dayKeyFromTimestamp(it.getTimestamp("tanggalKadaluarsa")) }
                }.thenBy {
                    it.getString("kunciTanggalProduksi").orEmpty()
                        .ifBlank { dayKeyFromTimestamp(it.getTimestamp("tanggalProduksi")) }
                }
            )
            .map { it.reference }

        check(expiredBatchRefs.isNotEmpty()) { "Tidak ada stok kadaluarsa untuk produk ini" }

        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()
        var detailBatchTerbuang: List<Map<String, Any>> = emptyList()

        firestore.runTransaction { trx ->
            val produkSnap = trx.get(produkRef)
            check(produkSnap.exists()) { "Produk tidak ditemukan" }

            val batchSnaps = expiredBatchRefs.map { trx.get(it) }
            var sisaBuang = qty.toLong()
            val alokasi = mutableListOf<AlokasiBatch>()

            batchSnaps.forEach { snap ->
                if (sisaBuang <= 0L) return@forEach
                val qtySisa = snap.getLong("qtySisa") ?: 0L
                if (qtySisa <= 0L) return@forEach
                val expiryKey = snap.getString("kunciTanggalKadaluarsa").orEmpty()
                    .ifBlank { dayKeyFromTimestamp(snap.getTimestamp("tanggalKadaluarsa")) }
                if (expiryKey >= today) return@forEach
                val ambil = minOf(qtySisa, sisaBuang)
                val prodKey = snap.getString("kunciTanggalProduksi").orEmpty()
                    .ifBlank { dayKeyFromTimestamp(snap.getTimestamp("tanggalProduksi")) }
                alokasi += AlokasiBatch(
                    ref = snap.reference,
                    idBatch = snap.id,
                    qtyDiambil = ambil,
                    qtySebelum = qtySisa,
                    qtySesudah = qtySisa - ambil,
                    tanggalProduksi = prodKey,
                    tanggalKadaluarsa = expiryKey
                )
                sisaBuang -= ambil
            }

            check(sisaBuang <= 0L) { "Jumlah melebihi stok kadaluarsa yang tersedia" }

            val stok = produkSnap.getLong("stokSaatIni") ?: 0L
            val nextStok = stok - qty.toLong()
            check(nextStok >= 0L) { "Stok produk tidak cukup" }

            val kodeProduk = produkSnap.getString("kodeProduk") ?: productId
            val namaProduk = produkSnap.getString("namaProduk") ?: "Produk"

            alokasi.forEach { a ->
                trx.update(
                    a.ref,
                    mapOf(
                        "qtySisa" to a.qtySesudah,
                        "statusBatch" to if (a.qtySesudah <= 0L) "DIBUANG" else "KADALUARSA",
                        "ditindakPada" to dibuatPada,
                        "diperbaruiPada" to dibuatPada
                    )
                )
            }

            detailBatchTerbuang = alokasi.map {
                mapOf(
                    "idBatch" to it.idBatch,
                    "qtyDibuang" to it.qtyDiambil,
                    "tanggalProduksi" to it.tanggalProduksi,
                    "tanggalKadaluarsa" to it.tanggalKadaluarsa
                )
            }

            trx.update(produkRef, mapOf("stokSaatIni" to nextStok, "diperbaruiPada" to dibuatPada))
            trx.set(
                ref,
                mapOf(
                    "tanggalPenyesuaian" to tanggalPenyesuaian,
                    "kunciTanggal" to kunciTanggal,
                    "idProduk" to productId,
                    "kodeProduk" to kodeProduk,
                    "namaProduk" to namaProduk,
                    "jenisPenyesuaian" to "KADALUARSA",
                    "jumlah" to qty,
                    "alasanPenyesuaian" to note.ifBlank { "Buang stok kadaluarsa" },
                    "catatan" to note.ifBlank { "Buang stok kadaluarsa" },
                    "batchKadaluarsaDetail" to detailBatchTerbuang,
                    "idPembuat" to (user?.idDokumen ?: userAuthId),
                    "namaPembuat" to (user?.nama ?: "Pengguna"),
                    "dibuatPada" to dibuatPada
                )
            )

            riwayatDrafts = listOf(
                DraftRiwayatStok(
                    tanggalMutasi = tanggalPenyesuaian,
                    kunciTanggal = kunciTanggal,
                    idProduk = productId,
                    kodeProduk = kodeProduk,
                    namaProduk = namaProduk,
                    jenisMutasi = "ADJUSTMENT_KADALUARSA",
                    sumberMutasi = "PenyesuaianStok",
                    referensiId = ref.id,
                    qtyMasuk = 0L,
                    qtyKeluar = qty.toLong(),
                    stokSebelum = stok,
                    stokSesudah = nextStok,
                    catatan = note.ifBlank { "Buang stok kadaluarsa" },
                    idPembuat = user?.idDokumen ?: userAuthId,
                    namaPembuat = user?.nama ?: "Pengguna"
                )
            )
        }.await()

        catatRiwayatStok(riwayatDrafts)
        return ref.id
    }


    suspend fun buildStockMutationDetailText(id: String): String {
        val doc = firestore.collection("RiwayatStok").document(id).get().await()
        if (!doc.exists()) return "Detail mutasi stok tidak ditemukan"

        val referensiId = doc.getString("referensiId").orEmpty()
        val sumberMutasi = doc.getString("sumberMutasi").orEmpty()
        val jenisMutasi = doc.getString("jenisMutasi").orEmpty()

        if (referensiId.isNotBlank()) {
            return when {
                sumberMutasi.equals("CatatanProduksi", ignoreCase = true) ||
                        jenisMutasi.contains("PRODUKSI", ignoreCase = true) ||
                        jenisMutasi.contains("KONVERSI", ignoreCase = true) -> {
                    buildProductionDetailText(referensiId)
                }

                sumberMutasi.equals("Penjualan", ignoreCase = true) ||
                        jenisMutasi.contains("PENJUALAN", ignoreCase = true) -> {
                    buildReceiptText(referensiId)
                }

                sumberMutasi.equals("PenyesuaianStok", ignoreCase = true) ||
                        jenisMutasi.contains("ADJUSTMENT", ignoreCase = true) -> {
                    buildAdjustmentDetailText(referensiId)
                }

                else -> {
                    // fallback ke detail mutasi generik
                    buildStockMutationFallbackDetail(doc)
                }
            }
        }

        return buildStockMutationFallbackDetail(doc)
    }

    private fun buildStockMutationFallbackDetail(doc: DocumentSnapshot): String {
        val tanggal = isoFromTimestamp(doc.getTimestamp("tanggalMutasi") ?: doc.getTimestamp("dibuatPada"))
        val namaProduk = doc.getString("namaProduk").orEmpty().ifBlank { "-" }
        val kodeProduk = doc.getString("kodeProduk").orEmpty().ifBlank { "-" }
        val jenisMutasi = doc.getString("jenisMutasi").orEmpty().ifBlank { "-" }
        val sumberMutasi = doc.getString("sumberMutasi").orEmpty().ifBlank { "-" }
        val referensiId = doc.getString("referensiId").orEmpty().ifBlank { "-" }
        val qtyMasuk = doc.getLong("qtyMasuk") ?: 0L
        val qtyKeluar = doc.getLong("qtyKeluar") ?: 0L
        val stokSebelum = doc.getLong("stokSebelum") ?: 0L
        val stokSesudah = doc.getLong("stokSesudah") ?: 0L
        val catatan = doc.getString("catatan").orEmpty().ifBlank { "-" }
        val pembuat = doc.getString("namaPembuat").orEmpty()
            .ifBlank { doc.getString("dibuatOlehNama").orEmpty().ifBlank { "-" } }

        val jenisLabel = when {
            jenisMutasi.contains("PRODUKSI_DASAR", ignoreCase = true) -> "Produksi Dasar"
            jenisMutasi.contains("KONVERSI_MASUK", ignoreCase = true) -> "Produk Olahan Masuk"
            jenisMutasi.contains("KONVERSI_KELUAR", ignoreCase = true) -> "Produk Olahan Keluar"
            jenisMutasi.contains("ADJUSTMENT_TAMBAH", ignoreCase = true) -> "Adjustment Tambah"
            jenisMutasi.contains("ADJUSTMENT_KURANG", ignoreCase = true) -> "Adjustment Kurang"
            jenisMutasi.contains("PENJUALAN", ignoreCase = true) -> "Penjualan"
            else -> jenisMutasi
        }

        return """
            ID Mutasi: ${doc.id}
            Tanggal: ${Formatter.readableDateTime(tanggal)}
            Produk: $namaProduk
            Kode Produk: $kodeProduk
            Jenis Mutasi: $jenisLabel
            Sumber: $sumberMutasi
            Referensi ID: $referensiId
            Qty Masuk: ${Formatter.ribuan(qtyMasuk)}
            Qty Keluar: ${Formatter.ribuan(qtyKeluar)}
            Stok Sebelum: ${Formatter.ribuan(stokSebelum)}
            Stok Sesudah: ${Formatter.ribuan(stokSesudah)}
            Catatan: $catatan
            Dibuat Oleh: $pembuat
        """.trimIndent()
    }

    suspend fun muatPergerakanStok(productId: String): List<RiwayatStokItem> {
        val snapshot = firestore.collection("RiwayatStok")
            .whereEqualTo("idProduk", productId)
            .get()
            .await()

        return snapshot.documents.map { doc ->
            val jenisMutasi = doc.getString("jenisMutasi").orEmpty()
            val tanggal = isoFromTimestamp(doc.getTimestamp("tanggalMutasi") ?: doc.getTimestamp("dibuatPada"))
            val qtyMasuk = doc.getLong("qtyMasuk") ?: 0L
            val qtyKeluar = doc.getLong("qtyKeluar") ?: 0L
            val sumberMutasi = doc.getString("sumberMutasi").orEmpty()
            val stokSebelum = doc.getLong("stokSebelum") ?: 0L
            val stokSesudah = doc.getLong("stokSesudah") ?: 0L
            val title = when {
                jenisMutasi.contains("PRODUKSI_DASAR") -> "Produksi masuk"
                jenisMutasi.contains("KONVERSI_MASUK") -> "Produk Olahan masuk"
                jenisMutasi.contains("KONVERSI_KELUAR") -> "Produk Olahan keluar"
                jenisMutasi.contains("ADJUSTMENT_TAMBAH") -> "Adjustment tambah"
                jenisMutasi.contains("ADJUSTMENT_KURANG") -> "Adjustment kurang"
                jenisMutasi.contains("PENJUALAN") -> "Penjualan keluar"
                else -> jenisMutasi.ifBlank { "Mutasi stok" }
            }
            val tone = when {
                qtyMasuk > 0L && jenisMutasi.contains("ADJUSTMENT") -> "blue"
                qtyMasuk > 0L -> "green"
                jenisMutasi.contains("PENJUALAN") -> "gold"
                else -> "orange"
            }

            RiwayatStokItem(
                id = doc.id,
                title = title,
                subtitle = doc.getString("catatan").orEmpty().ifBlank {
                    "$sumberMutasi • stok ${Formatter.ribuan(stokSebelum)} → ${Formatter.ribuan(stokSesudah)}"
                },
                qtyText = if (qtyMasuk > 0L) "+${Formatter.ribuan(qtyMasuk)}" else "-${Formatter.ribuan(qtyKeluar)}",
                tone = tone,
                tanggalIso = tanggal
            )
        }.sortedByDescending { Formatter.parseDate(it.tanggalIso) }
    }

    suspend fun simpanPenjualanRumahan(
        userAuthId: String,
        metodePembayaranUi: String,
        uangDiterima: Long,
        cartItems: List<ItemKeranjang>,
        products: List<Produk>,
        paymentGateway: String = "",
        paymentOrderId: String = "",
        paymentQrId: String = "",
        paymentQrString: String = "",
        paymentQrCreatedAtMillis: Long = 0L,
        paymentQrExpiresAtMillis: Long = 0L,
        paymentStatus: String = "",
        paymentSource: String = "",
        paymentReferenceId: String = "",
        paymentPaidAt: String = "",
        paymentAmount: Long = 0L
    ): String {
        require(cartItems.isNotEmpty()) { "Keranjang masih kosong" }

        val metode = when (metodePembayaranUi.uppercase()) {
            "TUNAI" -> "TUNAI"
            "QRIS" -> "QRIS"
            "TRANSFER" -> "TRANSFER"
            else -> "TUNAI"
        }

        val total = cartItems.sumOf { it.qty.toLong() * it.price }
        if (metode == "TUNAI" && uangDiterima < total) {
            throw IllegalArgumentException("Uang diterima kurang dari total belanja")
        }
        if (metode == "QRIS") {
            require(paymentGateway.isNotBlank()) { "Gateway QRIS belum tercatat" }
            require(paymentOrderId.isNotBlank()) { "Order ID QRIS belum tercatat" }
            require(paymentStatus.equals("COMPLETED", ignoreCase = true)) {
                "Pembayaran QRIS belum selesai"
            }
        }
        val statusPembayaranFinal = when {
            paymentStatus.equals("COMPLETED", ignoreCase = true) -> "PAID"
            paymentStatus.isNotBlank() -> paymentStatus.uppercase()
            else -> "PAID"
        }
        val amountTerbayar = if (paymentAmount > 0L) paymentAmount else if (metode == "TUNAI") uangDiterima else total

        val saleRef = firestore.collection("Penjualan").document(newId("pjl"))
        val user = cariPengguna(userAuthId)
        val dibuatPada = nowTimestamp()
        val kunciTanggal = Formatter.currentDateOnly()
        val tanggalPenjualan = dibuatPada
        val nomorPenjualan = "PJL-${kunciTanggal.replace("-", "")}-${saleRef.id.takeLast(4).uppercase()}"
        val batchRefsByProduct = muatBatchStokFefoRefs(cartItems.map { it.productId })
        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()

        firestore.runTransaction { trx ->
            val localRiwayat = mutableListOf<DraftRiwayatStok>()
            val localAlokasiBatch = mutableMapOf<String, List<AlokasiBatch>>()

            // 1. Semua READ dulu
            val produkSnapshots = cartItems.associate { item ->
                val ref = produkRef(item.productId)
                item.productId to trx.get(ref)
            }

            // 2. Validasi + siapkan draft
            cartItems.forEach { item ->
                val produk = products.firstOrNull { it.id == item.productId }
                    ?: throw IllegalStateException("Produk keranjang tidak ditemukan")

                val snap = produkSnapshots[item.productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")

                val stok = snap.getLong("stokSaatIni") ?: 0L
                check(stok >= item.qty) { "Stok ${produk.name} tidak mencukupi" }
                val stokLayakJual = produk.safeStock + produk.nearExpiredStock
                check(stokLayakJual >= item.qty) {
                    "Stok layak jual ${produk.name} tidak mencukupi. Stok kadaluarsa tidak bisa dijual."
                }
                val alokasiBatch = siapkanAlokasiBatchFefo(
                    trx = trx,
                    productId = produk.id,
                    productName = produk.name,
                    qtyDiminta = item.qty.toLong(),
                    batchRefsByProduct = batchRefsByProduct
                )
                localAlokasiBatch[produk.id] = alokasiBatch

                val stokSesudah = stok - item.qty.toLong()

                localRiwayat += DraftRiwayatStok(
                    tanggalMutasi = tanggalPenjualan,
                    kunciTanggal = kunciTanggal,
                    idProduk = produk.id,
                    kodeProduk = produk.code,
                    namaProduk = produk.name,
                    jenisMutasi = "PENJUALAN_RUMAHAN",
                    sumberMutasi = "Penjualan",
                    referensiId = saleRef.id,
                    qtyMasuk = 0L,
                    qtyKeluar = item.qty.toLong(),
                    stokSebelum = stok,
                    stokSesudah = stokSesudah,
                    catatan = "$nomorPenjualan • ${labelMetodePembayaran(metode)}",
                    idPembuat = user?.idDokumen ?: userAuthId,
                    namaPembuat = user?.nama ?: "Kasir"
                )
            }

            // 3. Semua WRITE setelah semua read selesai
            terapkanAlokasiBatchFefo(trx, localAlokasiBatch.values.flatten(), dibuatPada)

            cartItems.forEach { item ->
                val snap = produkSnapshots[item.productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")
                val stok = snap.getLong("stokSaatIni") ?: 0L
                val stokSesudah = stok - item.qty.toLong()

                trx.update(
                    produkRef(item.productId),
                    mapOf(
                        "stokSaatIni" to stokSesudah,
                        "diperbaruiPada" to dibuatPada
                    )
                )
            }

            trx.set(
                saleRef,
                mapOf(
                    "nomorPenjualan" to nomorPenjualan,
                    "tanggalPenjualan" to tanggalPenjualan,
                    "kunciTanggal" to kunciTanggal,
                    "sumberTransaksi" to "KASIR",
                    "metodePembayaran" to metode,
                    "totalItem" to cartItems.sumOf { it.qty },
                    "totalBelanja" to total,
                    "uangDiterima" to if (metode == "TUNAI") uangDiterima else total,
                    "uangKembalian" to if (metode == "TUNAI") (uangDiterima - total).coerceAtLeast(0L) else 0L,
                    "paymentGateway" to paymentGateway,
                    "paymentOrderId" to paymentOrderId,
                    "paymentQrId" to paymentQrId,
                    "paymentQrString" to paymentQrString,
                    "paymentQrCreatedAtMillis" to paymentQrCreatedAtMillis,
                    "paymentQrExpiresAtMillis" to paymentQrExpiresAtMillis,
                    "paymentStatus" to paymentStatus.uppercase(),
                    "paymentSource" to paymentSource,
                    "paymentReferenceId" to paymentReferenceId,
                    "paymentPaidAt" to paymentPaidAt,
                    "paymentAmount" to amountTerbayar,
                    "statusPembayaran" to statusPembayaranFinal,
                    "statusTransaksiKasir" to "CLOSED",
                    "statusPenjualan" to "SELESAI",
                    "alasanPembatalan" to "",
                    "dibatalkanOlehId" to "",
                    "dibatalkanOlehNama" to "",
                    "dibatalkanPada" to null,
                    "catatanPenjualan" to "",
                    "idKasir" to (user?.idDokumen ?: userAuthId),
                    "namaKasir" to (user?.nama ?: "Kasir"),
                    "dibuatPada" to dibuatPada,
                    "diperbaruiPada" to dibuatPada
                )
            )

            cartItems.forEachIndexed { index, item ->
                val produk = products.first { it.id == item.productId }
                val rincianRef = saleRef.collection("rincian").document("rnc_${index + 1}")
                trx.set(
                    rincianRef,
                    mapOf(
                        "idProduk" to produk.id,
                        "kodeProduk" to produk.code,
                        "namaProduk" to produk.name,
                        "jumlah" to item.qty,
                        "satuan" to produk.unit,
                        "hargaSatuan" to item.price,
                        "kanalHarga" to "Rumahan",
                        "subtotal" to item.qty.toLong() * item.price
                    )
                )

                localAlokasiBatch[produk.id].orEmpty().forEachIndexed { batchIndex, alokasi ->
                    trx.set(
                        rincianRef.collection("batch").document("btch_${batchIndex + 1}"),
                        mapOf(
                            "idBatch" to alokasi.idBatch,
                            "qtyDiambil" to alokasi.qtyDiambil,
                            "tanggalProduksi" to alokasi.tanggalProduksi,
                            "tanggalKadaluarsa" to alokasi.tanggalKadaluarsa
                        )
                    )
                }
            }

            riwayatDrafts = localRiwayat
        }.await()

        catatRiwayatStok(riwayatDrafts)
        perbaruiRingkasanHarian(kunciTanggal)
        return saleRef.id
    }


    suspend fun buatPenjualanQrisPending(
        userAuthId: String,
        cartItems: List<ItemKeranjang>,
        products: List<Produk>,
        paymentGateway: String,
        paymentOrderId: String,
        paymentQrId: String,
        paymentQrString: String,
        paymentQrCreatedAtMillis: Long,
        paymentQrExpiresAtMillis: Long,
        paymentStatus: String,
        paymentAmount: Long
    ): String {
        require(cartItems.isNotEmpty()) { "Keranjang masih kosong" }
        require(paymentGateway.isNotBlank()) { "Gateway QRIS belum tercatat" }
        require(paymentOrderId.isNotBlank()) { "Order ID QRIS belum tercatat" }

        val total = cartItems.sumOf { it.qty.toLong() * it.price }
        val saleRef = firestore.collection("Penjualan").document(newId("pjl"))
        val user = cariPengguna(userAuthId)
        val dibuatPada = nowTimestamp()
        val kunciTanggal = Formatter.currentDateOnly()
        val tanggalPenjualan = dibuatPada
        val nomorPenjualan = "PJL-${kunciTanggal.replace("-", "")}-${saleRef.id.takeLast(4).uppercase()}"

        firestore.runTransaction { trx ->
            val produkSnapshots = cartItems.associate { item ->
                val ref = produkRef(item.productId)
                item.productId to trx.get(ref)
            }

            cartItems.forEach { item ->
                val produk = products.firstOrNull { it.id == item.productId }
                    ?: throw IllegalStateException("Produk keranjang tidak ditemukan")
                val snap = produkSnapshots[item.productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")
                val stok = snap.getLong("stokSaatIni") ?: 0L
                check(stok >= item.qty) { "Stok ${produk.name} tidak mencukupi" }
                val stokLayakJual = produk.safeStock + produk.nearExpiredStock
                check(stokLayakJual >= item.qty) {
                    "Stok layak jual ${produk.name} tidak mencukupi. Stok kadaluarsa tidak bisa dijual."
                }
            }

            trx.set(
                saleRef,
                mapOf(
                    "nomorPenjualan" to nomorPenjualan,
                    "tanggalPenjualan" to tanggalPenjualan,
                    "kunciTanggal" to kunciTanggal,
                    "sumberTransaksi" to "KASIR",
                    "metodePembayaran" to "QRIS",
                    "totalItem" to cartItems.sumOf { it.qty },
                    "totalBelanja" to total,
                    "uangDiterima" to 0L,
                    "uangKembalian" to 0L,
                    "paymentGateway" to paymentGateway,
                    "paymentOrderId" to paymentOrderId,
                    "paymentQrId" to paymentQrId,
                    "paymentQrString" to paymentQrString,
                    "paymentQrCreatedAtMillis" to paymentQrCreatedAtMillis,
                    "paymentQrExpiresAtMillis" to paymentQrExpiresAtMillis,
                    "paymentStatus" to paymentStatus.uppercase(),
                    "paymentSource" to "",
                    "paymentReferenceId" to "",
                    "paymentPaidAt" to "",
                    "paymentAmount" to paymentAmount,
                    "statusPembayaran" to "PENDING_PAYMENT",
                    "statusTransaksiKasir" to "PENDING_PAYMENT",
                    "statusPenjualan" to "PENDING",
                    "alasanPembatalan" to "",
                    "dibatalkanOlehId" to "",
                    "dibatalkanOlehNama" to "",
                    "dibatalkanPada" to null,
                    "catatanPenjualan" to "QRIS dibuat dan menunggu pembayaran",
                    "idKasir" to (user?.idDokumen ?: userAuthId),
                    "namaKasir" to (user?.nama ?: "Kasir"),
                    "dibuatPada" to dibuatPada,
                    "diperbaruiPada" to dibuatPada
                )
            )

            cartItems.forEachIndexed { index, item ->
                val produk = products.first { it.id == item.productId }
                trx.set(
                    saleRef.collection("rincian").document("rnc_${index + 1}"),
                    mapOf(
                        "idProduk" to produk.id,
                        "kodeProduk" to produk.code,
                        "namaProduk" to produk.name,
                        "jumlah" to item.qty,
                        "satuan" to produk.unit,
                        "hargaSatuan" to item.price,
                        "kanalHarga" to "Rumahan",
                        "subtotal" to item.qty.toLong() * item.price
                    )
                )
            }
        }.await()

        return saleRef.id
    }

    suspend fun muatInfoQrisPending(id: String): QrisPendingInfo {
        val doc = firestore.collection("Penjualan").document(id).get().await()
        if (!doc.exists()) throw IllegalStateException("Data penjualan tidak ditemukan")
        return QrisPendingInfo(
            saleId = doc.id,
            nomorPenjualan = doc.getString("nomorPenjualan").orEmpty().ifBlank { doc.id },
            paymentOrderId = doc.getString("paymentOrderId").orEmpty(),
            paymentQrId = doc.getString("paymentQrId").orEmpty(),
            paymentQrString = doc.getString("paymentQrString").orEmpty(),
            paymentQrCreatedAtMillis = doc.getLong("paymentQrCreatedAtMillis") ?: 0L,
            paymentQrExpiresAtMillis = doc.getLong("paymentQrExpiresAtMillis") ?: 0L,
            totalBelanja = doc.getLong("totalBelanja") ?: 0L,
            statusPembayaran = doc.getString("statusPembayaran").orEmpty(),
            statusPenjualan = doc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }
        )
    }

    suspend fun selesaikanPenjualanQrisPending(
        id: String,
        userAuthId: String,
        products: List<Produk>,
        paymentStatus: String,
        paymentSource: String,
        paymentReferenceId: String,
        paymentPaidAt: String,
        paymentAmount: Long
    ): String {
        val saleRef = firestore.collection("Penjualan").document(id)
        val saleDoc = saleRef.get().await()
        if (!saleDoc.exists()) throw IllegalStateException("Data penjualan tidak ditemukan")

        val statusPenjualan = saleDoc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }
        val statusPembayaran = saleDoc.getString("statusPembayaran").orEmpty()
        if (!statusPenjualan.equals("PENDING", true) && !statusPembayaran.equals("PENDING_PAYMENT", true)) {
            if (statusPenjualan.equals("SELESAI", true)) return saleRef.id
            throw IllegalStateException("Transaksi QRIS ini tidak dalam status pending")
        }
        if (!paymentStatus.equals("COMPLETED", ignoreCase = true)) {
            throw IllegalStateException("Pembayaran QRIS belum selesai")
        }

        val detailSnapshot = saleRef.collection("rincian").get().await()
        if (detailSnapshot.isEmpty) throw IllegalStateException("Rincian penjualan tidak ditemukan")

        val user = cariPengguna(userAuthId)
        val dibuatPada = nowTimestamp()
        val tanggalPenjualan = saleDoc.getTimestamp("tanggalPenjualan") ?: dibuatPada
        val kunciTanggal = saleDoc.getString("kunciTanggal").orEmpty()
            .ifBlank { dayKeyFromTimestamp(tanggalPenjualan) }
        val nomorPenjualan = saleDoc.getString("nomorPenjualan").orEmpty().ifBlank { saleRef.id }
        val metode = "QRIS"
        val batchRefsByProduct = muatBatchStokFefoRefs(detailSnapshot.documents.map { it.getString("idProduk").orEmpty() })
        val total = saleDoc.getLong("totalBelanja") ?: 0L
        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()

        firestore.runTransaction { trx ->
            val produkSnapshots = detailSnapshot.documents.associate { detail ->
                val productId = detail.getString("idProduk").orEmpty()
                if (productId.isBlank()) throw IllegalStateException("Produk pada rincian penjualan tidak valid")
                productId to trx.get(produkRef(productId))
            }

            val localRiwayat = mutableListOf<DraftRiwayatStok>()
            val localAlokasiBatch = mutableMapOf<String, List<AlokasiBatch>>()

            detailSnapshot.documents.forEach { detail ->
                val productId = detail.getString("idProduk").orEmpty()
                val qty = detail.getLong("jumlah") ?: 0L
                val namaProdukDetail = detail.getString("namaProduk").orEmpty().ifBlank { productId }
                val produk = products.firstOrNull { it.id == productId }
                    ?: throw IllegalStateException("Produk $namaProdukDetail tidak ditemukan")
                val snap = produkSnapshots[productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")
                val stok = snap.getLong("stokSaatIni") ?: 0L
                check(stok >= qty) { "Stok ${produk.name} tidak mencukupi" }
                val stokLayakJual = produk.safeStock + produk.nearExpiredStock
                check(stokLayakJual >= qty) {
                    "Stok layak jual ${produk.name} tidak mencukupi. Stok kadaluarsa tidak bisa dijual."
                }

                val alokasiBatch = siapkanAlokasiBatchFefo(
                    trx = trx,
                    productId = produk.id,
                    productName = produk.name,
                    qtyDiminta = qty,
                    batchRefsByProduct = batchRefsByProduct
                )
                localAlokasiBatch[produk.id] = alokasiBatch

                localRiwayat += DraftRiwayatStok(
                    tanggalMutasi = tanggalPenjualan,
                    kunciTanggal = kunciTanggal,
                    idProduk = produk.id,
                    kodeProduk = produk.code,
                    namaProduk = produk.name,
                    jenisMutasi = "PENJUALAN_RUMAHAN",
                    sumberMutasi = "Penjualan",
                    referensiId = saleRef.id,
                    qtyMasuk = 0L,
                    qtyKeluar = qty,
                    stokSebelum = stok,
                    stokSesudah = stok - qty,
                    catatan = "$nomorPenjualan • ${labelMetodePembayaran(metode)}",
                    idPembuat = user?.idDokumen ?: userAuthId,
                    namaPembuat = user?.nama ?: "Kasir"
                )
            }

            terapkanAlokasiBatchFefo(trx, localAlokasiBatch.values.flatten(), dibuatPada)

            detailSnapshot.documents.forEach { detail ->
                val productId = detail.getString("idProduk").orEmpty()
                val qty = detail.getLong("jumlah") ?: 0L
                val snap = produkSnapshots[productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")
                val stok = snap.getLong("stokSaatIni") ?: 0L
                trx.update(
                    produkRef(productId),
                    mapOf(
                        "stokSaatIni" to (stok - qty),
                        "diperbaruiPada" to dibuatPada
                    )
                )
            }

            trx.update(
                saleRef,
                mapOf(
                    "uangDiterima" to total,
                    "uangKembalian" to 0L,
                    "paymentStatus" to paymentStatus.uppercase(),
                    "paymentSource" to paymentSource,
                    "paymentReferenceId" to paymentReferenceId,
                    "paymentPaidAt" to paymentPaidAt,
                    "paymentAmount" to if (paymentAmount > 0L) paymentAmount else total,
                    "statusPembayaran" to "PAID",
                    "statusTransaksiKasir" to "CLOSED",
                    "statusPenjualan" to "SELESAI",
                    "catatanPenjualan" to "QRIS sudah dibayar",
                    "diperbaruiPada" to dibuatPada
                )
            )

            riwayatDrafts = localRiwayat
        }.await()

        catatRiwayatStok(riwayatDrafts)
        perbaruiRingkasanHarian(kunciTanggal)
        return saleRef.id
    }
    suspend fun simpanRekapPasar(
        dateOnly: String,
        timeOnly: String,
        sumberTransaksi: String,
        draftItems: List<ItemDraftRekap>,
        userAuthId: String,
        products: List<Produk>
    ): String {
        require(draftItems.isNotEmpty()) { "Item rekap masih kosong" }

        val sumber = if (sumberTransaksi.isBlank()) "PASAR" else sumberTransaksi.trim().uppercase()
        val saleRef = firestore.collection("Penjualan").document(newId("pjl"))
        val user = cariPengguna(userAuthId)
        val dibuatPada = nowTimestamp()
        val kunciTanggal = dateOnly.ifBlank { Formatter.currentDateOnly() }
        val jamPenjualan = timeOnly.ifBlank { Formatter.currentTimeOnly() }
        val tanggalPenjualan = Timestamp(Formatter.parseDate("${kunciTanggal}T${jamPenjualan}:00"))
        val total = draftItems.sumOf { it.qty.toLong() * it.price }
        val nomorPenjualan = "PJL-${kunciTanggal.replace("-", "")}-${saleRef.id.takeLast(4).uppercase()}"
        val batchRefsByProduct = muatBatchStokFefoRefs(draftItems.map { it.productId })
        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()

        firestore.runTransaction { trx ->
            val localRiwayat = mutableListOf<DraftRiwayatStok>()
            val localAlokasiBatch = mutableMapOf<String, List<AlokasiBatch>>()

            // 1. Semua READ dulu
            val produkSnapshots = draftItems.associate { item ->
                val ref = produkRef(item.productId)
                item.productId to trx.get(ref)
            }

            // 2. Validasi + draft
            draftItems.forEach { item ->
                val produk = products.firstOrNull { it.id == item.productId }
                    ?: throw IllegalStateException("Produk rekap tidak ditemukan")

                val snap = produkSnapshots[item.productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")

                val stok = snap.getLong("stokSaatIni") ?: 0L
                check(stok >= item.qty) { "Stok ${produk.name} tidak mencukupi" }
                val stokLayakJual = produk.safeStock + produk.nearExpiredStock
                check(stokLayakJual >= item.qty) {
                    "Stok layak jual ${produk.name} tidak mencukupi. Stok kadaluarsa tidak bisa dijual."
                }
                val alokasiBatch = siapkanAlokasiBatchFefo(
                    trx = trx,
                    productId = produk.id,
                    productName = produk.name,
                    qtyDiminta = item.qty.toLong(),
                    batchRefsByProduct = batchRefsByProduct
                )
                localAlokasiBatch[produk.id] = alokasiBatch

                val stokSesudah = stok - item.qty.toLong()

                localRiwayat += DraftRiwayatStok(
                    tanggalMutasi = tanggalPenjualan,
                    kunciTanggal = kunciTanggal,
                    idProduk = produk.id,
                    kodeProduk = produk.code,
                    namaProduk = produk.name,
                    jenisMutasi = "PENJUALAN_${sumber}",
                    sumberMutasi = "Penjualan",
                    referensiId = saleRef.id,
                    qtyMasuk = 0L,
                    qtyKeluar = item.qty.toLong(),
                    stokSebelum = stok,
                    stokSesudah = stokSesudah,
                    catatan = "$nomorPenjualan • Rekap ${item.channelLabel}",
                    idPembuat = user?.idDokumen ?: userAuthId,
                    namaPembuat = user?.nama ?: "Admin"
                )
            }

            // 3. Semua WRITE
            terapkanAlokasiBatchFefo(trx, localAlokasiBatch.values.flatten(), dibuatPada)

            draftItems.forEach { item ->
                val snap = produkSnapshots[item.productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")
                val stok = snap.getLong("stokSaatIni") ?: 0L
                val stokSesudah = stok - item.qty.toLong()

                trx.update(
                    produkRef(item.productId),
                    mapOf(
                        "stokSaatIni" to stokSesudah,
                        "diperbaruiPada" to dibuatPada
                    )
                )
            }

            trx.set(
                saleRef,
                mapOf(
                    "nomorPenjualan" to nomorPenjualan,
                    "tanggalPenjualan" to tanggalPenjualan,
                    "kunciTanggal" to kunciTanggal,
                    "sumberTransaksi" to sumber,
                    "metodePembayaran" to "REKAP",
                    "totalItem" to draftItems.sumOf { it.qty },
                    "totalBelanja" to total,
                    "uangDiterima" to 0,
                    "uangKembalian" to 0,
                    "statusPenjualan" to "SELESAI",
                    "alasanPembatalan" to "",
                    "dibatalkanOlehId" to "",
                    "dibatalkanOlehNama" to "",
                    "dibatalkanPada" to null,
                    "catatanPenjualan" to "Rekap ${sumber.lowercase().replaceFirstChar { it.uppercase() }}",
                    "idKasir" to (user?.idDokumen ?: userAuthId),
                    "namaKasir" to (user?.nama ?: "Admin"),
                    "dibuatPada" to dibuatPada,
                    "diperbaruiPada" to dibuatPada
                )
            )

            draftItems.forEachIndexed { index, item ->
                val produk = products.first { it.id == item.productId }
                val rincianRef = saleRef.collection("rincian").document("rnc_${index + 1}")
                trx.set(
                    rincianRef,
                    mapOf(
                        "idProduk" to produk.id,
                        "kodeProduk" to produk.code,
                        "namaProduk" to produk.name,
                        "jumlah" to item.qty,
                        "satuan" to produk.unit,
                        "hargaSatuan" to item.price,
                        "kanalHarga" to item.channelLabel,
                        "subtotal" to item.qty.toLong() * item.price
                    )
                )

                localAlokasiBatch[produk.id].orEmpty().forEachIndexed { batchIndex, alokasi ->
                    trx.set(
                        rincianRef.collection("batch").document("btch_${batchIndex + 1}"),
                        mapOf(
                            "idBatch" to alokasi.idBatch,
                            "qtyDiambil" to alokasi.qtyDiambil,
                            "tanggalProduksi" to alokasi.tanggalProduksi,
                            "tanggalKadaluarsa" to alokasi.tanggalKadaluarsa
                        )
                    )
                }
            }

            riwayatDrafts = localRiwayat
        }.await()

        catatRiwayatStok(riwayatDrafts)
        perbaruiRingkasanHarian(kunciTanggal)
        return saleRef.id
    }

    suspend fun muatRingkasanPenjualan(): RingkasanPenjualan = coroutineScope {
        val today = Formatter.currentDateOnly()
        val snapshot = firestore.collection("Penjualan")
            .whereEqualTo("kunciTanggal", today)
            .whereEqualTo("statusPenjualan", "SELESAI")
            .get()
            .await()

        val rows = muatRiwayatPenjualan()

        val detailQty = snapshot.documents.map { sale ->
            async {
                sale.reference.collection("rincian").get().await().documents
                    .sumOf { (it.getLong("jumlah") ?: 0L).toInt() }
            }
        }.awaitAll()

        RingkasanPenjualan(
            totalHariIni = snapshot.documents.sumOf { it.getLong("totalBelanja") ?: 0L },
            totalKasirHariIni = snapshot.documents
                .filter { (it.getString("sumberTransaksi") ?: "").uppercase() == "KASIR" }
                .sumOf { it.getLong("totalBelanja") ?: 0L },
            totalRekapHariIni = snapshot.documents
                .filter { (it.getString("sumberTransaksi") ?: "").uppercase() != "KASIR" }
                .sumOf { it.getLong("totalBelanja") ?: 0L },
            jumlahTransaksiHariIni = snapshot.size(),
            totalItemHariIni = detailQty.sum(),
            recentRows = rows.take(5)
        )
    }

    suspend fun muatRingkasanKasir(): RingkasanKasir = coroutineScope {
        val todayKey = Formatter.currentDateOnly()

        val salesSnapshot = firestore.collection("Penjualan")
            .whereEqualTo("kunciTanggal", todayKey)
            .whereEqualTo("statusPenjualan", "SELESAI")
            .whereEqualTo("sumberTransaksi", "KASIR")
            .get()
            .await()

        data class AgregatProduk(
            val id: String,
            val nama: String,
            var qty: Int,
            var nominal: Long
        )

        val dokumenTerurut = salesSnapshot.documents.sortedByDescending {
            (it.getTimestamp("tanggalPenjualan") ?: it.getTimestamp("dibuatPada"))?.toDate()
        }

        val hasilDetail = dokumenTerurut.map { saleDoc ->
            async {
                val detailDocs = saleDoc.reference.collection("rincian").get().await().documents
                saleDoc to detailDocs
            }
        }.awaitAll()

        val agregat = linkedMapOf<String, AgregatProduk>()

        val recentRows = hasilDetail.map { (saleDoc, detailDocs) ->
            detailDocs.forEach { item ->
                val productId = item.getString("idProduk").orEmpty().ifBlank { item.id }
                val namaProduk = item.getString("namaProduk").orEmpty().ifBlank { "Produk" }
                val qty = (item.getLong("jumlah") ?: 0L).toInt()
                val subtotal = item.getLong("subtotal") ?: 0L

                val current = agregat[productId]
                if (current == null) {
                    agregat[productId] = AgregatProduk(
                        id = productId,
                        nama = namaProduk,
                        qty = qty,
                        nominal = subtotal
                    )
                } else {
                    current.qty += qty
                    current.nominal += subtotal
                }
            }

            val qtyTotal = detailDocs.sumOf { (it.getLong("jumlah") ?: 0L).toInt() }
            val tanggalIso = isoFromTimestamp(
                saleDoc.getTimestamp("tanggalPenjualan") ?: saleDoc.getTimestamp("dibuatPada")
            )

            ItemBarisPenjualan(
                id = saleDoc.id,
                title = saleDoc.getString("nomorPenjualan").orEmpty().ifBlank { saleDoc.id },
                subtitle = "${Formatter.readableDateTime(tanggalIso)} • ${Formatter.ribuan(qtyTotal.toLong())} pcs • Rumahan",
                badge = "Rumahan",
                amount = Formatter.currency(saleDoc.getLong("totalBelanja") ?: 0L),
                tanggalIso = tanggalIso,
                statusPenjualan = saleDoc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }
            )
        }

        val topProducts = agregat.values
            .sortedWith(
                compareByDescending<AgregatProduk> { it.qty }
                    .thenByDescending { it.nominal }
            )
            .take(5)
            .map {
                ItemDashboard(
                    id = it.id,
                    title = it.nama,
                    subtitle = "${Formatter.ribuan(it.qty.toLong())} pcs terjual hari ini",
                    amount = Formatter.currency(it.nominal),
                    badge = "Top",
                    tanggalIso = todayKey
                )
            }

        RingkasanKasir(
            totalHariIni = dokumenTerurut.sumOf { it.getLong("totalBelanja") ?: 0L },
            jumlahTransaksiHariIni = dokumenTerurut.size,
            topProducts = topProducts,
            recentRows = recentRows.take(5)
        )
    }

    suspend fun muatRiwayatPenjualan(): List<ItemBarisPenjualan> = coroutineScope {
        val snapshot = firestore.collection("Penjualan")
            .orderBy("dibuatPada", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()

        snapshot.documents.map { doc ->
            async {
                val detailSnapshot = doc.reference.collection("rincian").get().await()
                val tanggalIso = isoFromTimestamp(
                    doc.getTimestamp("dibuatPada") ?: doc.getTimestamp("tanggalPenjualan")
                )
                val status = doc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }

                ItemBarisPenjualan(
                    id = doc.id,
                    title = doc.getString("nomorPenjualan").orEmpty().ifBlank { doc.id },
                    subtitle = "${Formatter.readableDateTime(tanggalIso)} • ${Formatter.ribuan(detailSnapshot.documents.sumOf { (it.getLong("jumlah") ?: 0L).toInt() }.toLong())} pcs",
                    badge = labelSumberPenjualan(doc.getString("sumberTransaksi")),
                    amount = Formatter.currency(doc.getLong("totalBelanja") ?: 0L),
                    tanggalIso = tanggalIso,
                    statusPenjualan = status
                )
            }
        }.awaitAll()
    }

    suspend fun buildReceiptText(id: String): String {
        val saleDoc = firestore.collection("Penjualan").document(id).get().await()
        if (!saleDoc.exists()) return "Data penjualan tidak ditemukan"

        val settingDoc = runCatching {
            firestore.collection("Pengaturan").document("umum").get().await()
        }.getOrNull()

        val detailSnapshot = saleDoc.reference.collection("rincian")
            .orderBy(FieldPath.documentId())
            .get()
            .await()

        val tanggal = isoFromTimestamp(
            saleDoc.getTimestamp("tanggalPenjualan") ?: saleDoc.getTimestamp("dibuatPada")
        )
        val statusRaw = saleDoc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }
        val statusLabel = when (statusRaw.uppercase()) {
            "SELESAI" -> "Selesai"
            "PENDING" -> "Pending QRIS"
            "BATAL" -> "Dibatalkan"
            else -> statusRaw
        }
        val metodeLabel = labelMetodePembayaran(saleDoc.getString("metodePembayaran"))
        val paymentGateway = saleDoc.getString("paymentGateway").orEmpty()
        val paymentOrderId = saleDoc.getString("paymentOrderId").orEmpty()
        val paymentStatus = saleDoc.getString("statusPembayaran").orEmpty()
            .ifBlank { saleDoc.getString("paymentStatus").orEmpty() }
        val gatewayStatus = saleDoc.getString("paymentStatus").orEmpty()
        val paymentSource = saleDoc.getString("paymentSource").orEmpty()
        val paymentReferenceId = saleDoc.getString("paymentReferenceId").orEmpty()
        val paymentPaidAt = saleDoc.getString("paymentPaidAt").orEmpty()
        val paymentAmount = saleDoc.getLong("paymentAmount") ?: 0L

        val detailText = if (detailSnapshot.isEmpty) {
            "Tidak ada rincian item"
        } else {
            detailSnapshot.documents.mapIndexed { index, item ->
                val qty = item.getLong("jumlah") ?: 0L
                val harga = item.getLong("hargaSatuan") ?: 0L
                val subtotal = item.getLong("subtotal") ?: 0L
                val satuan = item.getString("satuan").orEmpty().ifBlank { "pcs" }
                String.format(
                    Locale.US,
                    "%02d. %s\n    %d %s x %s = %s",
                    index + 1,
                    item.getString("namaProduk").orEmpty(),
                    qty,
                    satuan,
                    Formatter.currency(harga),
                    Formatter.currency(subtotal)
                )
            }.joinToString("\n")
        }

        val total = saleDoc.getLong("totalBelanja") ?: 0L
        val uangDiterima = saleDoc.getLong("uangDiterima") ?: 0L
        val uangKembalian = saleDoc.getLong("uangKembalian") ?: 0L
        val namaUsaha = settingDoc?.getString("namaTampilanToko").orEmpty().ifBlank { "SI Tahu" }
        val alamat = settingDoc?.getString("alamatToko").orEmpty().ifBlank { "-" }
        val telepon = settingDoc?.getString("nomorTelepon").orEmpty().ifBlank { "-" }
        val footer = settingDoc?.getString("footerStruk").orEmpty()
        val alasanBatal = saleDoc.getString("alasanPembatalan").orEmpty()
        val pembatal = saleDoc.getString("dibatalkanOlehNama").orEmpty()
        val timestampBatal = saleDoc.getTimestamp("dibatalkanPada")
        val tanggalBatal = timestampBatal?.let { Formatter.readableDateTime(isoFromTimestamp(it)) }.orEmpty()

        val gatewayBlock = if (paymentGateway.isNotBlank() || paymentOrderId.isNotBlank()) {
            """
Gateway       : ${paymentGateway.ifBlank { "-" }}
Order Gateway : ${paymentOrderId.ifBlank { "-" }}
Status Bayar  : ${paymentStatus.ifBlank { "-" }}
Status Gateway: ${gatewayStatus.ifBlank { "-" }}
Nominal Bayar : ${if (paymentAmount > 0L) Formatter.currency(paymentAmount) else "-"}
Sumber Bayar  : ${paymentSource.ifBlank { "-" }}
Ref Bayar     : ${paymentReferenceId.ifBlank { "-" }}
Waktu Gateway : ${paymentPaidAt.ifBlank { "-" }}
            """.trimIndent()
        } else {
            ""
        }

        val pembayaranText = if (metodeLabel.equals("Rekap", true)) {
            """
RINGKASAN PEMBAYARAN
Total          : ${Formatter.currency(total)}
Nilai Rekap    : ${Formatter.currency(total)}
            """.trimIndent()
        } else {
            """
RINGKASAN PEMBAYARAN
Total          : ${Formatter.currency(total)}
Dibayar        : ${Formatter.currency(uangDiterima)}
Kembalian      : ${Formatter.currency(uangKembalian)}
            """.trimIndent()
        }

        val pembatalanBlock = if (statusRaw.uppercase() == "BATAL") {
            """

PEMBATALAN
Alasan         : ${alasanBatal.ifBlank { "-" }}
Dibatalkan Oleh: ${pembatal.ifBlank { "-" }}
Waktu Batal    : ${tanggalBatal.ifBlank { "-" }}
            """.trimIndent()
        } else {
            ""
        }

        return """
$namaUsaha
$alamat
Telp: $telepon
────────────────────────
NOTA PENJUALAN
Nomor          : ${saleDoc.getString("nomorPenjualan").orEmpty().ifBlank { saleDoc.id }}
Tanggal        : ${Formatter.readableDateTime(tanggal)}
Metode         : $metodeLabel
Status         : $statusLabel
Kasir/Admin    : ${saleDoc.getString("namaKasir").orEmpty().ifBlank { "-" }}
────────────────────────
DETAIL ITEM
$detailText
────────────────────────
$pembayaranText
$gatewayBlock$pembatalanBlock
Catatan        : ${saleDoc.getString("catatanPenjualan").orEmpty().ifBlank { "-" }}
${footer.ifBlank { "Terima kasih sudah bertransaksi." }}
        """.trimIndent()
    }

    suspend fun batalkanPenjualan(id: String, alasan: String, userAuthId: String) {
        val saleRef = firestore.collection("Penjualan").document(id)
        val saleDoc = saleRef.get().await()

        if (!saleDoc.exists()) {
            throw IllegalStateException("Data penjualan tidak ditemukan")
        }
        if (alasan.isBlank()) {
            throw IllegalArgumentException("Alasan pembatalan wajib diisi")
        }

        val status = saleDoc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }
        val dibuatPada = nowTimestamp()
        val user = cariPengguna(userAuthId)

        if (status.equals("PENDING", ignoreCase = true)) {
            saleRef.update(
                mapOf(
                    "statusPenjualan" to "BATAL",
                    "statusPembayaran" to "CANCELLED",
                    "statusTransaksiKasir" to "CANCELLED",
                    "paymentStatus" to "CANCELLED",
                    "alasanPembatalan" to alasan.trim(),
                    "dibatalkanOlehId" to (user?.idDokumen ?: userAuthId),
                    "dibatalkanOlehNama" to (user?.nama ?: "Pengguna"),
                    "dibatalkanPada" to dibuatPada,
                    "diperbaruiPada" to dibuatPada
                )
            ).await()
            return
        }

        if (status != "SELESAI") {
            throw IllegalStateException("Hanya transaksi selesai atau pending QRIS yang bisa dibatalkan")
        }

        val detailSnapshot = saleRef.collection("rincian").get().await()
        if (detailSnapshot.isEmpty) {
            throw IllegalStateException("Rincian penjualan tidak ditemukan")
        }

        val kunciTanggal = saleDoc.getString("kunciTanggal").orEmpty()
            .ifBlank { dayKeyFromTimestamp(saleDoc.getTimestamp("tanggalPenjualan")) }

        firestore.runTransaction { trx ->
            val produkSnapshots = detailSnapshot.documents.associate { detail ->
                val productId = detail.getString("idProduk").orEmpty()
                if (productId.isBlank()) {
                    throw IllegalStateException("Produk pada rincian penjualan tidak valid")
                }
                productId to trx.get(produkRef(productId))
            }

            detailSnapshot.documents.forEach { detail ->
                val productId = detail.getString("idProduk").orEmpty()
                val produkSnap = produkSnapshots[productId]
                    ?: throw IllegalStateException("Produk $productId tidak ditemukan saat rollback stok")

                if (!produkSnap.exists()) {
                    throw IllegalStateException("Produk $productId tidak ditemukan saat rollback stok")
                }

                val stok = produkSnap.getLong("stokSaatIni") ?: 0L
                val qty = detail.getLong("jumlah") ?: 0L

                trx.update(
                    produkRef(productId),
                    mapOf(
                        "stokSaatIni" to (stok + qty),
                        "diperbaruiPada" to dibuatPada
                    )
                )
            }

            trx.update(
                saleRef,
                mapOf(
                    "statusPenjualan" to "BATAL",
                    "alasanPembatalan" to alasan.trim(),
                    "dibatalkanOlehId" to (user?.idDokumen ?: userAuthId),
                    "dibatalkanOlehNama" to (user?.nama ?: "Pengguna"),
                    "dibatalkanPada" to dibuatPada,
                    "diperbaruiPada" to dibuatPada
                )
            )
        }.await()

        hapusRiwayatStokByReferensi(id, "Penjualan")
        perbaruiRingkasanHarian(kunciTanggal)
    }

    suspend fun hapusPenjualan(id: String) {
        val saleRef = firestore.collection("Penjualan").document(id)
        val saleDoc = saleRef.get().await()

        if (!saleDoc.exists()) {
            throw IllegalStateException("Data penjualan tidak ditemukan")
        }

        val status = saleDoc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }
        if (status != "SELESAI") {
            throw IllegalStateException("Hanya transaksi selesai yang bisa dihapus")
        }

        val detailSnapshot = saleRef.collection("rincian").get().await()
        if (detailSnapshot.isEmpty) {
            throw IllegalStateException("Rincian penjualan tidak ditemukan")
        }

        val kunciTanggal = saleDoc.getString("kunciTanggal").orEmpty()
            .ifBlank { dayKeyFromTimestamp(saleDoc.getTimestamp("tanggalPenjualan")) }

        val dibuatPada = nowTimestamp()

        firestore.runTransaction { trx ->
            // 1. Semua READ dulu
            val produkSnapshots = detailSnapshot.documents.associate { detail ->
                val productId = detail.getString("idProduk").orEmpty()
                if (productId.isBlank()) {
                    throw IllegalStateException("Produk pada rincian penjualan tidak valid")
                }
                productId to trx.get(produkRef(productId))
            }

            // 2. Semua WRITE
            detailSnapshot.documents.forEach { detail ->
                val productId = detail.getString("idProduk").orEmpty()
                val produkSnap = produkSnapshots[productId]
                    ?: throw IllegalStateException("Produk $productId tidak ditemukan saat rollback stok")

                if (!produkSnap.exists()) {
                    throw IllegalStateException("Produk $productId tidak ditemukan saat rollback stok")
                }

                val stok = produkSnap.getLong("stokSaatIni") ?: 0L
                val qty = detail.getLong("jumlah") ?: 0L

                trx.update(
                    produkRef(productId),
                    mapOf(
                        "stokSaatIni" to (stok + qty),
                        "diperbaruiPada" to dibuatPada
                    )
                )
            }

            detailSnapshot.documents.forEach { detail ->
                trx.delete(detail.reference)
            }

            trx.delete(saleRef)
        }.await()

        hapusRiwayatStokByReferensi(id, "Penjualan")
        perbaruiRingkasanHarian(kunciTanggal)
    }

    suspend fun muatRingkasanDashboard(): RingkasanDashboard {
        val todayKey = Formatter.currentDateOnly()

        val settingDoc = runCatching {
            firestore.collection("Pengaturan").document("umum").get().await()
        }.getOrNull()

        val ringkasanDoc = runCatching {
            firestore.collection("RingkasanHarian").document(todayKey).get().await()
        }.getOrNull()

        val produk = muatSemuaProduk()
        val lowStock = produk
            .filter { statusProduk(it) != "Aman" }
            .sortedBy { it.stock }
            .take(6)

        val recentSales = muatRiwayatPenjualan().take(4).map {
            ItemDashboard(
                id = it.id,
                title = it.title,
                subtitle = it.subtitle,
                amount = it.amount,
                badge = it.badge,
                tanggalIso = it.tanggalIso
            )
        }

        val recentProduction = muatRiwayatProduksi().take(4).map {
            ItemDashboard(
                id = it.id,
                title = if (it.badge == "Produk Olahan") "Produk Olahan" else "Produksi",
                subtitle = it.subtitle,
                amount = it.amount,
                badge = it.badge,
                tanggalIso = it.tanggalIso
            )
        }

        val recentItems = (recentSales + recentProduction)
            .sortedByDescending { Formatter.parseDate(it.tanggalIso) }
            .take(6)

        val totalPenjualan = ringkasanDoc?.getLong("totalPenjualan") ?: 0L
        val totalPengeluaran = ringkasanDoc?.getLong("totalPengeluaran") ?: 0L
        val totalTransaksi = (ringkasanDoc?.getLong("totalTransaksi") ?: 0L).toInt()
        val totalProduksi = (
                (ringkasanDoc?.getLong("totalProduksiDasar") ?: 0L) +
                        (ringkasanDoc?.getLong("totalProduksiOlahan") ?: 0L)
                ).toInt()

        return RingkasanDashboard(
            namaUsaha = settingDoc?.getString("namaTampilanToko").orEmpty().ifBlank { "SI Tahu" },
            tanggalRingkasan = todayKey,
            totalPenjualan = totalPenjualan,
            totalProduksi = totalProduksi,
            totalPengeluaran = totalPengeluaran,
            totalTransaksi = totalTransaksi,
            totalLaba = totalPenjualan - totalPengeluaran,
            lowStock = lowStock,
            recentItems = recentItems
        )
    }

    suspend fun buildTransactionDetailText(id: String, type: String): String {
        val normalized = type.uppercase()

        return when {
            normalized.contains("RUMAHAN") ||
                    normalized.contains("PASAR") ||
                    normalized.contains("PENJUALAN") -> {
                buildReceiptText(id)
            }

            normalized.contains("KONVERSI") ||
                    normalized.contains("PRODUKSI") -> {
                buildProductionDetailText(id)
            }
            else -> "Detail aktivitas belum tersedia"
        }
    }

    suspend fun perbaruiRingkasanHarian(kunciTanggal: String) {
        if (kunciTanggal.isBlank()) return

        val sales = firestore.collection("Penjualan")
            .whereEqualTo("kunciTanggal", kunciTanggal)
            .whereEqualTo("statusPenjualan", "SELESAI")
            .get().await().documents

        val expenses = firestore.collection("Pengeluaran")
            .whereEqualTo("kunciTanggal", kunciTanggal)
            .get().await().documents

        val produksi = firestore.collection("CatatanProduksi")
            .whereEqualTo("kunciTanggal", kunciTanggal)
            .get().await().documents

        val totalPenjualan = sales.sumOf { it.getLong("totalBelanja") ?: 0L }
        val totalTransaksi = sales.size
        val totalItemTerjual = coroutineScope {
            sales.map { sale ->
                async {
                    sale.reference.collection("rincian").get().await().documents
                        .sumOf { (it.getLong("jumlah") ?: 0L).toInt() }
                }
            }.awaitAll().sum()
        }
        val totalPengeluaran = expenses.sumOf { it.getLong("nominal") ?: 0L }
        val totalProduksiDasar = produksi
            .filter { it.getString("jenisProduksi") == "DASAR" }
            .sumOf { (it.getLong("jumlahHasil") ?: 0L).toInt() }
        val totalProduksiOlahan = produksi
            .filter { it.getString("jenisProduksi") == "OLAHAN" }
            .sumOf { (it.getLong("jumlahHasil") ?: 0L).toInt() }

        firestore.collection("RingkasanHarian")
            .document(kunciTanggal)
            .set(
                mapOf(
                    "kunciTanggal" to kunciTanggal,
                    "totalPenjualan" to totalPenjualan,
                    "totalPengeluaran" to totalPengeluaran,
                    "totalTransaksi" to totalTransaksi,
                    "totalItemTerjual" to totalItemTerjual,
                    "totalProduksiDasar" to totalProduksiDasar,
                    "totalProduksiOlahan" to totalProduksiOlahan,
                    "diperbaruiPada" to nowTimestamp()
                   )
            ).await()
    }
}
