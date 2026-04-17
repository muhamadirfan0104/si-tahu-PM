package muhamad.irfan.si_tahu.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import muhamad.irfan.si_tahu.util.Formatter
import java.text.SimpleDateFormat
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
        val tanggalIso: String
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

    private fun newId(prefix: String): String {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        return "${prefix}_${suffix}"
    }

    private fun statusProduk(produk: Produk): String = when {
        produk.stock <= 0 -> "Habis"
        produk.stock <= produk.minStock -> "Menipis"
        else -> "Aman"
    }

    private suspend fun cariPengguna(authUid: String): UserRingkas? {
        if (authUid.isBlank()) return null

        val query = firestore.collection("pengguna")
            .whereEqualTo("authUid", authUid)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()

        if (query != null) return query.toUserRingkas(authUid)

        val byId = runCatching {
            firestore.collection("pengguna").document(authUid).get().await()
        }.getOrNull()

        if (byId != null && byId.exists()) return byId.toUserRingkas(authUid)
        return null
    }

    private fun DocumentSnapshot.toUserRingkas(authUid: String): UserRingkas {
        return UserRingkas(
            idDokumen = id,
            authUid = authUid,
            nama = getString("namaPengguna").orEmpty().ifBlank { getString("email").orEmpty().ifBlank { "Pengguna" } },
            email = getString("email").orEmpty()
        )
    }

    private suspend fun muatChannelsProduk(productRefPath: String): MutableList<HargaKanal> {
        val snapshot = firestore.document(productRefPath).collection("hargaJual").get().await()
        return snapshot.documents.map {
            HargaKanal(
                id = it.id,
                label = it.getString("kanalHarga").orEmpty(),
                price = it.getLong("hargaSatuan") ?: 0L,
                active = it.getBoolean("aktif") ?: true,
                defaultCashier = it.getBoolean("hargaUtama") ?: false,
                deleted = false
            )
        }.sortedWith(compareByDescending<HargaKanal> { it.defaultCashier }.thenBy { it.label }).toMutableList()
    }

    suspend fun muatSemuaProduk(): List<Produk> = coroutineScope {
        val snapshot = firestore.collection("produk")
            .orderBy("namaProduk")
            .get()
            .await()

        snapshot.documents.map { doc ->
            async {
                Produk(
                    id = doc.id,
                    code = doc.getString("kodeProduk").orEmpty().ifBlank { doc.id },
                    name = doc.getString("namaProduk").orEmpty(),
                    category = doc.getString("jenisProduk").orEmpty().ifBlank { "DASAR" },
                    unit = doc.getString("satuan").orEmpty().ifBlank { "pcs" },
                    stock = (doc.getLong("stokSaatIni") ?: 0L).toInt(),
                    minStock = (doc.getLong("stokMinimum") ?: 0L).toInt(),
                    active = doc.getBoolean("aktifDijual") ?: true,
                    showInCashier = doc.getBoolean("tampilDiKasir") ?: true,
                    photoTone = "",
                    channels = muatChannelsProduk(doc.reference.path),
                    deleted = false
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
        val snapshot = firestore.collection("parameterProduksi")
            .whereEqualTo("idProduk", productId)
            .whereEqualTo("aktif", true)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull() ?: return null

        return ParameterProduksi(
            id = snapshot.id,
            productId = snapshot.getString("idProduk").orEmpty(),
            resultPerBatch = (snapshot.getLong("hasilPerProduksi") ?: 0L).toInt(),
            note = snapshot.getString("catatan").orEmpty(),
            active = snapshot.getBoolean("aktif") ?: true
        )
    }

    suspend fun muatSemuaParameter(): List<ParameterProduksi> {
        val snapshot = firestore.collection("parameterProduksi").get().await()
        return snapshot.documents.map {
            ParameterProduksi(
                id = it.id,
                productId = it.getString("idProduk").orEmpty(),
                resultPerBatch = (it.getLong("hasilPerProduksi") ?: 0L).toInt(),
                note = it.getString("catatan").orEmpty(),
                active = it.getBoolean("aktif") ?: true
            )
        }
    }

    suspend fun simpanProduksiDasar(
        dateTime: String,
        productId: String,
        batches: Int,
        note: String,
        userAuthId: String
    ): String {
        require(batches > 0) { "Jumlah batch harus lebih dari 0" }

        val productRef = firestore.collection("produk").document(productId)
        val parameter = muatParameterAktif(productId)
        require(parameter != null && parameter.resultPerBatch > 0) { "Produk dasar harus punya parameter aktif sebelum produksi" }
        val hasilPerProduksi = parameter.resultPerBatch
        val totalHasil = batches * hasilPerProduksi
        val user = cariPengguna(userAuthId)
        val catatanRef = firestore.collection("catatanProduksi").document(newId("prod"))
        val tanggalProduksi = parseTimestamp(dateTime)
        val kunciTanggal = dayKeyFromString(dateTime)
        val dibuatPada = nowTimestamp()

        firestore.runTransaction { trx ->
            val produkSnap = trx.get(productRef)
            check(produkSnap.exists()) { "Produk tidak ditemukan" }
            val jenisProduk = produkSnap.getString("jenisProduk").orEmpty()
            check(jenisProduk.equals("DASAR", ignoreCase = true)) { "Produksi dasar hanya untuk produk kategori DASAR" }
            val stokSaatIni = produkSnap.getLong("stokSaatIni") ?: 0L
            trx.update(
                productRef,
                mapOf(
                    "stokSaatIni" to stokSaatIni + totalHasil,
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
                    "satuanBahan" to "batch",
                    "idProdukHasil" to productId,
                    "namaProdukHasil" to (produkSnap.getString("namaProduk") ?: "Produk"),
                    "jumlahHasil" to totalHasil,
                    "satuanHasil" to (produkSnap.getString("satuan") ?: "pcs"),
                    "catatan" to note,
                    "dibuatOlehId" to (user?.idDokumen ?: userAuthId),
                    "dibuatOlehNama" to (user?.nama ?: "Pengguna"),
                    "dibuatPada" to dibuatPada
                )
            )
        }.await()

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

        val fromRef = firestore.collection("produk").document(fromProductId)
        val toRef = firestore.collection("produk").document(toProductId)
        val user = cariPengguna(userAuthId)
        val catatanRef = firestore.collection("catatanProduksi").document(newId("prod"))
        val tanggalProduksi = parseTimestamp(dateTime)
        val kunciTanggal = dayKeyFromString(dateTime)
        val dibuatPada = nowTimestamp()

        firestore.runTransaction { trx ->
            val fromSnap = trx.get(fromRef)
            val toSnap = trx.get(toRef)
            check(fromSnap.exists()) { "Produk asal tidak ditemukan" }
            check(toSnap.exists()) { "Produk hasil tidak ditemukan" }
            check((fromSnap.getString("jenisProduk") ?: "").equals("DASAR", ignoreCase = true)) {
                "Produk asal konversi harus kategori DASAR"
            }
            check((toSnap.getString("jenisProduk") ?: "").equals("OLAHAN", ignoreCase = true)) {
                "Produk hasil konversi harus kategori OLAHAN"
            }

            val stokAsal = fromSnap.getLong("stokSaatIni") ?: 0L
            check(stokAsal >= inputQty) { "Stok bahan tidak mencukupi" }
            val stokHasil = toSnap.getLong("stokSaatIni") ?: 0L

            trx.update(fromRef, mapOf("stokSaatIni" to stokAsal - inputQty, "diperbaruiPada" to dibuatPada))
            trx.update(toRef, mapOf("stokSaatIni" to stokHasil + outputQty, "diperbaruiPada" to dibuatPada))
            trx.set(
                catatanRef,
                mapOf(
                    "jenisProduksi" to "OLAHAN",
                    "tanggalProduksi" to tanggalProduksi,
                    "kunciTanggal" to kunciTanggal,
                    "idParameterProduksi" to "",
                    "idProdukAsal" to fromProductId,
                    "namaProdukAsal" to (fromSnap.getString("namaProduk") ?: "Bahan"),
                    "jumlahBahan" to inputQty,
                    "satuanBahan" to (fromSnap.getString("satuan") ?: "pcs"),
                    "idProdukHasil" to toProductId,
                    "namaProdukHasil" to (toSnap.getString("namaProduk") ?: "Hasil"),
                    "jumlahHasil" to outputQty,
                    "satuanHasil" to (toSnap.getString("satuan") ?: "pcs"),
                    "catatan" to note,
                    "dibuatOlehId" to (user?.idDokumen ?: userAuthId),
                    "dibuatOlehNama" to (user?.nama ?: "Pengguna"),
                    "dibuatPada" to dibuatPada
                )
            )
        }.await()

        perbaruiRingkasanHarian(kunciTanggal)
        return catatanRef.id
    }

    suspend fun muatRingkasanProduksi(): RingkasanProduksi {
        val today = Formatter.currentDateOnly()
        val parameters = muatSemuaParameter()
        val riwayat = muatRiwayatProduksi()
        val dasarHariIni = riwayat.filter { it.badge == "Produksi Dasar" && dayKeyFromString(it.tanggalIso) == today }
        val konversiHariIni = riwayat.filter { it.badge == "Konversi" && dayKeyFromString(it.tanggalIso) == today }

        return RingkasanProduksi(
            totalProduksiDasarHariIni = dasarHariIni.sumOf { it.amount.substringAfter('+').substringBefore(' ').trim().toIntOrNull() ?: 0 },
            totalBatchHariIni = dasarHariIni.sumOf { it.subtitle.substringAfterLast('•').substringBefore("batch").trim().toIntOrNull() ?: 0 },
            totalKonversiHariIni = konversiHariIni.size,
            totalParameterAktif = parameters.count { it.active },
            totalRiwayat = riwayat.size,
            recentRows = riwayat.take(8)
        )
    }

    suspend fun muatRiwayatProduksi(): List<BarisRiwayatProduksi> {
        val snapshot = firestore.collection("catatanProduksi")
            .orderBy("tanggalProduksi", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.map { doc ->
            val jenis = doc.getString("jenisProduksi").orEmpty()
            val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalProduksi") ?: doc.getTimestamp("dibuatPada"))
            if (jenis == "OLAHAN") {
                val namaAsal = doc.getString("namaProdukAsal").orEmpty().ifBlank { "Bahan" }
                val namaHasil = doc.getString("namaProdukHasil").orEmpty().ifBlank { "Hasil" }
                val bahan = doc.getLong("jumlahBahan")?.toInt() ?: 0
                val hasil = doc.getLong("jumlahHasil")?.toInt() ?: 0
                BarisRiwayatProduksi(
                    id = doc.id,
                    badge = "Konversi",
                    title = "$namaAsal → $namaHasil",
                    subtitle = Formatter.readableDateTime(tanggalIso),
                    amount = "$bahan → $hasil",
                    tanggalIso = tanggalIso
                )
            } else {
                val namaHasil = doc.getString("namaProdukHasil").orEmpty().ifBlank { "Produk Dasar" }
                val jumlahBahan = doc.getLong("jumlahBahan")?.toInt() ?: 0
                val jumlahHasil = doc.getLong("jumlahHasil")?.toInt() ?: 0
                val satuan = doc.getString("satuanHasil").orEmpty().ifBlank { "pcs" }
                BarisRiwayatProduksi(
                    id = doc.id,
                    badge = "Produksi Dasar",
                    title = namaHasil,
                    subtitle = "${Formatter.readableDateTime(tanggalIso)} • $jumlahBahan batch",
                    amount = "+$jumlahHasil $satuan",
                    tanggalIso = tanggalIso
                )
            }
        }
    }

    suspend fun buildProductionDetailText(id: String): String {
        val doc = firestore.collection("catatanProduksi").document(id).get().await()
        if (!doc.exists()) return "Data produksi tidak ditemukan"

        val jenis = doc.getString("jenisProduksi").orEmpty()
        val tanggal = isoFromTimestamp(doc.getTimestamp("tanggalProduksi") ?: doc.getTimestamp("dibuatPada"))
        return if (jenis == "OLAHAN") {
            """
            ID: ${doc.id}
            Jenis: Produksi Olahan / Konversi
            Tanggal: ${Formatter.readableDateTime(tanggal)}
            Produk Asal: ${doc.getString("namaProdukAsal").orEmpty()}
            Jumlah Bahan: ${doc.getLong("jumlahBahan") ?: 0} ${doc.getString("satuanBahan").orEmpty()}
            Produk Hasil: ${doc.getString("namaProdukHasil").orEmpty()}
            Jumlah Hasil: ${doc.getLong("jumlahHasil") ?: 0} ${doc.getString("satuanHasil").orEmpty()}
            Catatan: ${doc.getString("catatan").orEmpty().ifBlank { "-" }}
            Dibuat Oleh: ${doc.getString("dibuatOlehNama").orEmpty().ifBlank { "-" }}
            """.trimIndent()
        } else {
            """
            ID: ${doc.id}
            Jenis: Produksi Tahu Dasar
            Tanggal: ${Formatter.readableDateTime(tanggal)}
            Produk Hasil: ${doc.getString("namaProdukHasil").orEmpty()}
            Batch: ${doc.getLong("jumlahBahan") ?: 0}
            Hasil Produksi: ${doc.getLong("jumlahHasil") ?: 0} ${doc.getString("satuanHasil").orEmpty()}
            Parameter: ${doc.getString("idParameterProduksi").orEmpty().ifBlank { "Default" }}
            Catatan: ${doc.getString("catatan").orEmpty().ifBlank { "-" }}
            Dibuat Oleh: ${doc.getString("dibuatOlehNama").orEmpty().ifBlank { "-" }}
            """.trimIndent()
        }
    }

    suspend fun hapusCatatanProduksi(id: String) {
        val ref = firestore.collection("catatanProduksi").document(id)
        val doc = ref.get().await()
        if (!doc.exists()) throw IllegalStateException("Data produksi tidak ditemukan")
        val jenis = doc.getString("jenisProduksi").orEmpty()
        val tanggalKey = doc.getString("kunciTanggal").orEmpty().ifBlank { dayKeyFromTimestamp(doc.getTimestamp("tanggalProduksi")) }
        val hasilQty = doc.getLong("jumlahHasil") ?: 0L
        val bahanQty = doc.getLong("jumlahBahan") ?: 0L
        val hasilRef = firestore.collection("produk").document(doc.getString("idProdukHasil").orEmpty())

        firestore.runTransaction { trx ->
            if (jenis == "OLAHAN") {
                val asalRef = firestore.collection("produk").document(doc.getString("idProdukAsal").orEmpty())
                val asalSnap = trx.get(asalRef)
                val hasilSnap = trx.get(hasilRef)
                val stokAsal = asalSnap.getLong("stokSaatIni") ?: 0L
                val stokHasil = hasilSnap.getLong("stokSaatIni") ?: 0L
                check(stokHasil >= hasilQty) { "Stok hasil tidak cukup untuk menghapus catatan ini" }
                trx.update(asalRef, mapOf("stokSaatIni" to stokAsal + bahanQty, "diperbaruiPada" to nowTimestamp()))
                trx.update(hasilRef, mapOf("stokSaatIni" to stokHasil - hasilQty, "diperbaruiPada" to nowTimestamp()))
            } else {
                val hasilSnap = trx.get(hasilRef)
                val stokHasil = hasilSnap.getLong("stokSaatIni") ?: 0L
                check(stokHasil >= hasilQty) { "Stok produk tidak cukup untuk menghapus catatan ini" }
                trx.update(hasilRef, mapOf("stokSaatIni" to stokHasil - hasilQty, "diperbaruiPada" to nowTimestamp()))
            }
            trx.delete(ref)
        }.await()

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

        val ref = firestore.collection("penyesuaianStok").document(newId("adj"))
        val produkRef = firestore.collection("produk").document(productId)
        val user = cariPengguna(userAuthId)
        val dibuatPada = nowTimestamp()
        val kunciTanggal = dateOnly.ifBlank { Formatter.currentDateOnly() }

        firestore.runTransaction { trx ->
            val produkSnap = trx.get(produkRef)
            check(produkSnap.exists()) { "Produk tidak ditemukan" }
            val stok = produkSnap.getLong("stokSaatIni") ?: 0L
            val nextStok = if (type == "add") stok + qty else stok - qty
            check(nextStok >= 0) { "Stok tidak boleh minus" }

            trx.update(produkRef, mapOf("stokSaatIni" to nextStok, "diperbaruiPada" to dibuatPada))
            trx.set(
                ref,
                mapOf(
                    "tanggalPenyesuaian" to Timestamp(Formatter.parseDate("${kunciTanggal}T00:00:00")),
                    "kunciTanggal" to kunciTanggal,
                    "idProduk" to productId,
                    "kodeProduk" to (produkSnap.getString("kodeProduk") ?: productId),
                    "namaProduk" to (produkSnap.getString("namaProduk") ?: "Produk"),
                    "jenisPenyesuaian" to if (type == "add") "TAMBAH" else "KURANG",
                    "jumlah" to qty,
                    "alasanPenyesuaian" to if (type == "add") "Koreksi stok" else "Pengurangan stok",
                    "catatan" to note,
                    "idPembuat" to (user?.idDokumen ?: userAuthId),
                    "namaPembuat" to (user?.nama ?: "Pengguna"),
                    "dibuatPada" to dibuatPada
                )
            )
        }.await()

        return ref.id
    }

    suspend fun buildAdjustmentDetailText(id: String): String {
        val doc = firestore.collection("penyesuaianStok").document(id).get().await()
        if (!doc.exists()) return "Data adjustment tidak ditemukan"
        val tanggal = isoFromTimestamp(doc.getTimestamp("tanggalPenyesuaian") ?: doc.getTimestamp("dibuatPada"))
        return """
            ID: ${doc.id}
            Tanggal: ${Formatter.readableDateTime(tanggal)}
            Produk: ${doc.getString("namaProduk").orEmpty()}
            Jenis: ${doc.getString("jenisPenyesuaian").orEmpty()}
            Jumlah: ${doc.getLong("jumlah") ?: 0}
            Alasan: ${doc.getString("alasanPenyesuaian").orEmpty().ifBlank { "-" }}
            Catatan: ${doc.getString("catatan").orEmpty().ifBlank { "-" }}
            Dibuat Oleh: ${doc.getString("namaPembuat").orEmpty().ifBlank { "-" }}
        """.trimIndent()
    }

    suspend fun muatPergerakanStok(productId: String): List<RiwayatStokItem> = coroutineScope {
        val dasarMasuk = async {
            firestore.collection("catatanProduksi")
                .whereEqualTo("idProdukHasil", productId)
                .get().await().documents
        }
        val olahanAsal = async {
            firestore.collection("catatanProduksi")
                .whereEqualTo("idProdukAsal", productId)
                .get().await().documents
        }
        val adjustments = async {
            firestore.collection("penyesuaianStok")
                .whereEqualTo("idProduk", productId)
                .get().await().documents
        }
        val saleDetails = async {
            firestore.collectionGroup("rincian")
                .whereEqualTo("idProduk", productId)
                .get().await().documents
        }

        val rows = mutableListOf<RiwayatStokItem>()

        dasarMasuk.await().forEach { doc ->
            val jenis = doc.getString("jenisProduksi").orEmpty()
            val tanggal = isoFromTimestamp(doc.getTimestamp("tanggalProduksi") ?: doc.getTimestamp("dibuatPada"))
            if (jenis == "OLAHAN") {
                rows += RiwayatStokItem(
                    id = doc.id + "-in",
                    title = "Konversi masuk",
                    subtitle = "Hasil dari ${doc.getString("namaProdukAsal").orEmpty().ifBlank { "produk" }}",
                    qtyText = "+${doc.getLong("jumlahHasil") ?: 0} ${doc.getString("satuanHasil").orEmpty().ifBlank { "pcs" }}",
                    tone = "green",
                    tanggalIso = tanggal
                )
            } else {
                rows += RiwayatStokItem(
                    id = doc.id,
                    title = "Produksi masuk",
                    subtitle = doc.getString("catatan").orEmpty().ifBlank { "Produksi dasar" },
                    qtyText = "+${doc.getLong("jumlahHasil") ?: 0} ${doc.getString("satuanHasil").orEmpty().ifBlank { "pcs" }}",
                    tone = "green",
                    tanggalIso = tanggal
                )
            }
        }

        olahanAsal.await().forEach { doc ->
            val tanggal = isoFromTimestamp(doc.getTimestamp("tanggalProduksi") ?: doc.getTimestamp("dibuatPada"))
            rows += RiwayatStokItem(
                id = doc.id + "-out",
                title = "Konversi keluar",
                subtitle = "Dipakai ke ${doc.getString("namaProdukHasil").orEmpty().ifBlank { "produk" }}",
                qtyText = "-${doc.getLong("jumlahBahan") ?: 0} ${doc.getString("satuanBahan").orEmpty().ifBlank { "pcs" }}",
                tone = "orange",
                tanggalIso = tanggal
            )
        }

        adjustments.await().forEach { doc ->
            val tanggal = isoFromTimestamp(doc.getTimestamp("tanggalPenyesuaian") ?: doc.getTimestamp("dibuatPada"))
            val tambah = doc.getString("jenisPenyesuaian") == "TAMBAH"
            rows += RiwayatStokItem(
                id = doc.id,
                title = "Adjustment stok",
                subtitle = doc.getString("catatan").orEmpty().ifBlank { doc.getString("alasanPenyesuaian").orEmpty().ifBlank { "Penyesuaian stok" } },
                qtyText = if (tambah) "+${doc.getLong("jumlah") ?: 0}" else "-${doc.getLong("jumlah") ?: 0}",
                tone = if (tambah) "blue" else "orange",
                tanggalIso = tanggal
            )
        }

        val saleDocs = mutableListOf<Pair<DocumentSnapshot, DocumentSnapshot>>()
        saleDetails.await().forEach { detail ->
            val parentSale = detail.reference.parent.parent ?: return@forEach
            val saleDoc = parentSale.get().await()
            if (saleDoc.exists()) {
                saleDocs += saleDoc to detail
            }
        }

        saleDocs.forEach { (saleDoc, detail) ->
            if ((saleDoc.getString("statusPenjualan") ?: "SELESAI") == "DIBATALKAN") return@forEach
            val tanggal = isoFromTimestamp(saleDoc.getTimestamp("tanggalPenjualan") ?: saleDoc.getTimestamp("dibuatPada"))
            rows += RiwayatStokItem(
                id = saleDoc.id + productId,
                title = "Penjualan keluar",
                subtitle = "${saleDoc.getString("sumberTransaksi").orEmpty()} • ${saleDoc.getString("nomorPenjualan").orEmpty().ifBlank { saleDoc.id }}",
                qtyText = "-${detail.getLong("jumlah") ?: 0} ${detail.getString("satuan").orEmpty().ifBlank { "pcs" }}",
                tone = if (saleDoc.getString("sumberTransaksi") == "KASIR") "gold" else "blue",
                tanggalIso = tanggal
            )
        }

        rows.sortedByDescending { Formatter.parseDate(it.tanggalIso) }
    }

    suspend fun simpanPenjualanRumahan(
        userAuthId: String,
        metodePembayaranUi: String,
        uangDiterima: Long,
        cartItems: List<ItemKeranjang>,
        products: List<Produk>
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

        val saleRef = firestore.collection("penjualan").document(newId("pjl"))
        val user = cariPengguna(userAuthId)
        val dibuatPada = nowTimestamp()
        val kunciTanggal = Formatter.currentDateOnly()
        val tanggalPenjualan = dibuatPada
        val nomorPenjualan = "PJL-${kunciTanggal.replace("-", "")}-${saleRef.id.takeLast(4).uppercase()}"

        firestore.runTransaction { trx ->
            cartItems.forEach { item ->
                val produk = products.firstOrNull { it.id == item.productId }
                    ?: throw IllegalStateException("Produk keranjang tidak ditemukan")
                val produkRef = firestore.collection("produk").document(item.productId)
                val snap = trx.get(produkRef)
                val stok = snap.getLong("stokSaatIni") ?: 0L
                check(stok >= item.qty) { "Stok ${produk.name} tidak mencukupi" }
                trx.update(produkRef, mapOf("stokSaatIni" to stok - item.qty, "diperbaruiPada" to dibuatPada))
            }

            trx.set(
                saleRef,
                mapOf(
                    "nomorPenjualan" to nomorPenjualan,
                    "tanggalPenjualan" to tanggalPenjualan,
                    "kunciTanggal" to kunciTanggal,
                    "sumberTransaksi" to "KASIR",
                    "metodePembayaran" to metode,
                    "totalItem" to cartItems.size,
                    "totalBelanja" to total,
                    "uangDiterima" to if (metode == "TUNAI") uangDiterima else total,
                    "uangKembalian" to if (metode == "TUNAI") (uangDiterima - total).coerceAtLeast(0L) else 0L,
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
                        "subtotal" to item.qty.toLong() * item.price
                    )
                )
            }
        }.await()

        perbaruiRingkasanHarian(kunciTanggal)
        return saleRef.id
    }

    suspend fun simpanRekapPasar(
        dateOnly: String,
        sumberTransaksi: String,
        draftItems: List<ItemDraftRekap>,
        userAuthId: String,
        products: List<Produk>
    ): String {
        require(draftItems.isNotEmpty()) { "Item rekap masih kosong" }
        val sumber = when (sumberTransaksi.uppercase()) {
            "RESELLER" -> "RESELLER"
            else -> "PASAR"
        }
        val saleRef = firestore.collection("penjualan").document(newId("pjl"))
        val user = cariPengguna(userAuthId)
        val dibuatPada = nowTimestamp()
        val kunciTanggal = dateOnly.ifBlank { Formatter.currentDateOnly() }
        val tanggalPenjualan = Timestamp(Formatter.parseDate("${kunciTanggal}T00:00:00"))
        val total = draftItems.sumOf { it.qty.toLong() * it.price }
        val nomorPenjualan = "PJL-${kunciTanggal.replace("-", "")}-${saleRef.id.takeLast(4).uppercase()}"

        firestore.runTransaction { trx ->
            draftItems.forEach { item ->
                val produk = products.firstOrNull { it.id == item.productId }
                    ?: throw IllegalStateException("Produk rekap tidak ditemukan")
                val produkRef = firestore.collection("produk").document(item.productId)
                val snap = trx.get(produkRef)
                val stok = snap.getLong("stokSaatIni") ?: 0L
                check(stok >= item.qty) { "Stok ${produk.name} tidak mencukupi" }
                trx.update(produkRef, mapOf("stokSaatIni" to stok - item.qty, "diperbaruiPada" to dibuatPada))
            }

            trx.set(
                saleRef,
                mapOf(
                    "nomorPenjualan" to nomorPenjualan,
                    "tanggalPenjualan" to tanggalPenjualan,
                    "kunciTanggal" to kunciTanggal,
                    "sumberTransaksi" to sumber,
                    "metodePembayaran" to "REKAP",
                    "totalItem" to draftItems.size,
                    "totalBelanja" to total,
                    "uangDiterima" to 0,
                    "uangKembalian" to 0,
                    "statusPenjualan" to "SELESAI",
                    "alasanPembatalan" to "",
                    "dibatalkanOlehId" to "",
                    "dibatalkanOlehNama" to "",
                    "dibatalkanPada" to null,
                    "catatanPenjualan" to if (sumber == "RESELLER") "Rekap reseller" else "Titip pasar",
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
                        "subtotal" to item.qty.toLong() * item.price
                    )
                )
            }
        }.await()

        perbaruiRingkasanHarian(kunciTanggal)
        return saleRef.id
    }

    suspend fun muatRingkasanPenjualan(): RingkasanPenjualan = coroutineScope {
        val today = Formatter.currentDateOnly()
        val snapshot = firestore.collection("penjualan")
            .whereEqualTo("kunciTanggal", today)
            .whereEqualTo("statusPenjualan", "SELESAI")
            .get().await()

        val rows = muatRiwayatPenjualan()
        val detailQty = snapshot.documents.map { sale ->
            async {
                sale.reference.collection("rincian").get().await().documents.sumOf { (it.getLong("jumlah") ?: 0L).toInt() }
            }
        }.awaitAll()

        RingkasanPenjualan(
            totalHariIni = snapshot.documents.sumOf { it.getLong("totalBelanja") ?: 0L },
            totalKasirHariIni = snapshot.documents.filter { it.getString("sumberTransaksi") == "KASIR" }.sumOf { it.getLong("totalBelanja") ?: 0L },
            totalRekapHariIni = snapshot.documents.filter { it.getString("sumberTransaksi") != "KASIR" }.sumOf { it.getLong("totalBelanja") ?: 0L },
            jumlahTransaksiHariIni = snapshot.size(),
            totalItemHariIni = detailQty.sum(),
            recentRows = rows.take(8)
        )
    }

    suspend fun muatRiwayatPenjualan(): List<ItemBarisPenjualan> = coroutineScope {
        val snapshot = firestore.collection("penjualan")
            .orderBy("tanggalPenjualan", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get().await()

        snapshot.documents.map { doc ->
            async {
                val detailSnapshot = doc.reference.collection("rincian").get().await()
                val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalPenjualan") ?: doc.getTimestamp("dibuatPada"))
                ItemBarisPenjualan(
                    id = doc.id,
                    title = doc.getString("nomorPenjualan").orEmpty().ifBlank { doc.id },
                    subtitle = "${Formatter.readableDateTime(tanggalIso)} • ${detailSnapshot.documents.sumOf { (it.getLong("jumlah") ?: 0L).toInt() }} pcs",
                    badge = when (doc.getString("sumberTransaksi")) {
                        "KASIR" -> "Rumahan"
                        else -> doc.getString("sumberTransaksi").orEmpty().ifBlank { "PASAR" }
                    },
                    amount = Formatter.currency(doc.getLong("totalBelanja") ?: 0L),
                    tanggalIso = tanggalIso
                )
            }
        }.awaitAll()
    }

    suspend fun buildReceiptText(id: String): String {
        val saleDoc = firestore.collection("penjualan").document(id).get().await()
        if (!saleDoc.exists()) return "Data penjualan tidak ditemukan"
        val detailSnapshot = saleDoc.reference.collection("rincian").orderBy(FieldPath.documentId()).get().await()
        val tanggal = isoFromTimestamp(saleDoc.getTimestamp("tanggalPenjualan") ?: saleDoc.getTimestamp("dibuatPada"))
        val detailText = if (detailSnapshot.isEmpty) {
            "-"
        } else {
            detailSnapshot.documents.joinToString("\n") { item ->
                val qty = item.getLong("jumlah") ?: 0L
                val harga = item.getLong("hargaSatuan") ?: 0L
                val subtotal = item.getLong("subtotal") ?: 0L
                "- ${item.getString("namaProduk").orEmpty()} • ${qty} x ${Formatter.currency(harga)} = ${Formatter.currency(subtotal)}"
            }
        }

        return """
            Nomor: ${saleDoc.getString("nomorPenjualan").orEmpty().ifBlank { saleDoc.id }}
            Tanggal: ${Formatter.readableDateTime(tanggal)}
            Sumber: ${saleDoc.getString("sumberTransaksi").orEmpty()}
            Metode: ${saleDoc.getString("metodePembayaran").orEmpty()}
            Status: ${saleDoc.getString("statusPenjualan").orEmpty()}

            Item:
            $detailText

            Total: ${Formatter.currency(saleDoc.getLong("totalBelanja") ?: 0L)}
            Uang diterima: ${Formatter.currency(saleDoc.getLong("uangDiterima") ?: 0L)}
            Kembalian: ${Formatter.currency(saleDoc.getLong("uangKembalian") ?: 0L)}
            Kasir: ${saleDoc.getString("namaKasir").orEmpty().ifBlank { "-" }}
            Catatan: ${saleDoc.getString("catatanPenjualan").orEmpty().ifBlank { "-" }}
        """.trimIndent()
    }

    suspend fun hapusPenjualan(id: String) {
        val saleRef = firestore.collection("penjualan").document(id)
        val saleDoc = saleRef.get().await()
        if (!saleDoc.exists()) throw IllegalStateException("Data penjualan tidak ditemukan")
        val detailSnapshot = saleRef.collection("rincian").get().await()
        val kunciTanggal = saleDoc.getString("kunciTanggal").orEmpty().ifBlank { dayKeyFromTimestamp(saleDoc.getTimestamp("tanggalPenjualan")) }

        firestore.runTransaction { trx ->
            val selesai = (saleDoc.getString("statusPenjualan") ?: "SELESAI") == "SELESAI"
            if (selesai) {
                detailSnapshot.documents.forEach { detail ->
                    val produkRef = firestore.collection("produk").document(detail.getString("idProduk").orEmpty())
                    val produkSnap = trx.get(produkRef)
                    val stok = produkSnap.getLong("stokSaatIni") ?: 0L
                    val qty = detail.getLong("jumlah") ?: 0L
                    trx.update(produkRef, mapOf("stokSaatIni" to stok + qty, "diperbaruiPada" to nowTimestamp()))
                }
            }
            detailSnapshot.documents.forEach { trx.delete(it.reference) }
            trx.delete(saleRef)
        }.await()

        perbaruiRingkasanHarian(kunciTanggal)
    }

    suspend fun muatRingkasanDashboard(): RingkasanDashboard {
        val todayKey = Formatter.currentDateOnly()
        val settingDoc = runCatching { firestore.collection("pengaturan").document("umum").get().await() }.getOrNull()
        val ringkasanDoc = runCatching { firestore.collection("ringkasanHarian").document(todayKey).get().await() }.getOrNull()
        val produk = muatSemuaProduk()
        val lowStock = produk.filter { statusProduk(it) != "Aman" }.sortedBy { it.stock }.take(6)

        val recentSales = muatRiwayatPenjualan().take(3).map {
            ItemDashboard(
                id = it.id,
                title = "Penjualan",
                subtitle = it.subtitle,
                amount = it.amount,
                badge = it.badge,
                tanggalIso = it.tanggalIso
            )
        }
        val recentProduction = muatRiwayatProduksi().take(3).map {
            ItemDashboard(
                id = it.id,
                title = if (it.badge == "Konversi") "Konversi" else "Produksi",
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
        val totalProduksi = ((ringkasanDoc?.getLong("totalProduksiDasar") ?: 0L) + (ringkasanDoc?.getLong("totalProduksiOlahan") ?: 0L)).toInt()

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
        return when {
            type.contains("Penjualan", true) -> buildReceiptText(id)
            type.contains("Konversi", true) || type.contains("Produksi", true) -> buildProductionDetailText(id)
            else -> "Detail aktivitas belum tersedia"
        }
    }

    suspend fun perbaruiRingkasanHarian(kunciTanggal: String) {
        if (kunciTanggal.isBlank()) return

        val sales = firestore.collection("penjualan")
            .whereEqualTo("kunciTanggal", kunciTanggal)
            .whereEqualTo("statusPenjualan", "SELESAI")
            .get().await().documents

        val expenses = firestore.collection("pengeluaran")
            .whereEqualTo("kunciTanggal", kunciTanggal)
            .get().await().documents

        val produksi = firestore.collection("catatanProduksi")
            .whereEqualTo("kunciTanggal", kunciTanggal)
            .get().await().documents

        val totalPenjualan = sales.sumOf { it.getLong("totalBelanja") ?: 0L }
        val totalTransaksi = sales.size
        val totalItemTerjual = coroutineScope {
            sales.map { sale ->
                async {
                    sale.reference.collection("rincian").get().await().documents.sumOf { (it.getLong("jumlah") ?: 0L).toInt() }
                }
            }.awaitAll().sum()
        }
        val totalPengeluaran = expenses.sumOf { it.getLong("nominal") ?: 0L }
        val totalProduksiDasar = produksi.filter { it.getString("jenisProduksi") == "DASAR" }.sumOf { (it.getLong("jumlahHasil") ?: 0L).toInt() }
        val totalProduksiOlahan = produksi.filter { it.getString("jenisProduksi") == "OLAHAN" }.sumOf { (it.getLong("jumlahHasil") ?: 0L).toInt() }

        firestore.collection("ringkasanHarian")
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
