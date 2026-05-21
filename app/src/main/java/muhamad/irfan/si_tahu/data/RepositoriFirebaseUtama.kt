package muhamad.irfan.si_tahu.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object RepositoriFirebaseUtama {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val formatIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val formatTanggal = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private const val MAX_FIRESTORE_CONCURRENCY = 8
    private const val RECENT_FETCH_MULTIPLIER = 4

    private suspend fun <T, R> Iterable<T>.mapAsyncLimited(
        parallelism: Int = MAX_FIRESTORE_CONCURRENCY,
        block: suspend (T) -> R
    ): List<R> = coroutineScope {
        val safeParallelism = parallelism.coerceAtLeast(1)
        chunked(safeParallelism).flatMap { chunk ->
            chunk.map { item -> async { block(item) } }.awaitAll()
        }
    }

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
        val totalProduksiOlahanHariIni: Int,
        val totalBatchHariIni: Int,
        val totalKonversiHariIni: Int,
        val totalParameterAktif: Int,
        val totalRiwayat: Int,
        val recentRows: List<BarisRiwayatProduksi>
    )

    data class TitikGrafikProduksi(
        val kunciTanggal: String,
        val labelTanggal: String,
        val totalDasar: Int,
        val totalOlahan: Int,
        val total: Int
    )

    data class RiwayatStokItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val qtyText: String,
        val tone: String,
        val tanggalIso: String
    )

    data class BarisStokDashboard(
        val id: String,
        val namaProduk: String,
        val kodeProduk: String,
        val kategori: String,
        val satuan: String,
        val stokFisik: Int,
        val stokLayakJual: Int,
        val stokAman: Int,
        val stokHampirKadaluarsa: Int,
        val stokEdHariIni: Int,
        val stokKadaluarsa: Int,
        val stokMinimum: Int,
        val status: String,
        val edTerdekat: String
    )

    data class RingkasanStokDashboard(
        val totalProdukAktif: Int,
        val totalStokFisik: Long,
        val totalStokLayakJual: Long,
        val totalStokAman: Long,
        val totalHampirKadaluarsa: Long,
        val totalKadaluarsa: Long,
        val totalMenipis: Int,
        val totalHabis: Int,
        val totalPerluTindakan: Int,
        val produkKritis: List<BarisStokDashboard>,
        val produkTerbanyak: List<BarisStokDashboard>
    )

    data class RingkasanPenjualan(
        val totalHariIni: Long,
        val totalKasirHariIni: Long,
        val totalRekapHariIni: Long,
        val jumlahTransaksiHariIni: Int,
        val totalItemHariIni: Int,
        val recentRows: List<ItemBarisPenjualan>,
        val topProducts: List<ItemDashboard> = emptyList()
    )

    data class TitikGrafikPenjualan(
        val kunciTanggal: String,
        val labelTanggal: String,
        val totalNominal: Long,
        val totalTransaksi: Int,
        val totalItem: Int
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
        val totalItemTerjual: Int,
        val totalLaba: Long,
        val totalProdukAktif: Int,
        val lowStock: List<Produk>,
        val stokMenipis: List<ItemDashboard> = emptyList(),
        val hampirEd: List<ItemDashboard> = emptyList(),
        val recentItems: List<ItemDashboard> = emptyList(),
        val topProducts: List<ItemDashboard> = emptyList(),
        val expenseCategories: List<ItemDashboard> = emptyList()
    )

    data class ItemDashboard(
        val id: String,
        val title: String,
        val subtitle: String,
        val amount: String,
        val badge: String,
        val tanggalIso: String
    )

    data class NotifikasiAdmin(
        val id: String,
        val jenis: String,
        val judul: String,
        val isi: String,
        val jumlah: Int,
        val warna: String,
        val tujuan: String
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
        val totalItemHariIni: Int,
        val topProducts: List<ItemDashboard>,
        val recentRows: List<ItemBarisPenjualan>
    )
    data class ItemBarisPengeluaran(
        val id: String,
        val title: String,
        val subtitle: String,
        val badge: String,
        val amount: String,
        val tanggalIso: String,
        val kategori: String,
        val nominal: Long,
        val catatan: String
    )

    data class ItemAnalitikLaporan(
        val id: String,
        val title: String,
        val subtitle: String,
        val amount: String,
        val badge: String,
        val qty: Int = 0,
        val nominal: Long = 0L
    )

    data class BarisRiwayatTransaksi(
        val id: String,
        val jenis: String,
        val title: String,
        val subtitle: String,
        val amount: String,
        val badge: String,
        val tanggalIso: String,
        val status: String = "",
        val userId: String = "",
        val userName: String = "Pengguna"
    )

    data class RingkasanLaporanFirebase(
        val rangeKey: String,
        val rangeLabel: String,
        val totalPenjualan: Long,
        val totalPengeluaran: Long,
        val totalProduksi: Int,
        val totalTransaksi: Int,
        val totalItemTerjual: Int,
        val labaRugi: Long,
        val produkTerlaris: List<ItemAnalitikLaporan>,
        val kategoriPengeluaran: List<ItemAnalitikLaporan>,
        val transaksiTerbaru: List<BarisRiwayatTransaksi>
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
        val edTodayStock: Long,
        val expiredStock: Long,
        val producedToday: Boolean,
        val nearestExpiryDate: String,
        val statusLabel: String,
        val lastProductionDate: String
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
            expiryKey == today -> "ED_HARI_INI"
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

        return ringkasanBatchStokDariDokumen(
            stokProduk = stokProduk,
            hariHampirKadaluarsaProduk = hariHampirKadaluarsaProduk,
            dokumenBatch = snapshot.documents
        )
    }

    private fun ringkasanBatchStokDariDokumen(
        stokProduk: Long,
        hariHampirKadaluarsaProduk: Int = 1,
        dokumenBatch: List<DocumentSnapshot>
    ): RingkasanBatchStok {
        val docs = dokumenBatch.filter { (it.getLong("qtySisa") ?: 0L) > 0L }
        if (docs.isEmpty()) {
            return if (stokProduk > 0L) {
                RingkasanBatchStok(
                    safeStock = stokProduk,
                    nearExpiredStock = 0L,
                    edTodayStock = 0L,
                    expiredStock = 0L,
                    producedToday = false,
                    nearestExpiryDate = "",
                    statusLabel = "Stok Lama",
                    lastProductionDate = ""
                )
            } else {
                RingkasanBatchStok(0L, 0L, 0L, 0L, false, "", "Habis", "")
            }
        }

        val today = tanggalKeySaatIni()
        var safe = 0L
        var near = 0L
        var edToday = 0L
        var expired = 0L
        var producedToday = false
        var nearest = ""
        var lastProduction = ""

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
            if (prodKey.isNotBlank() && (lastProduction.isBlank() || prodKey > lastProduction)) lastProduction = prodKey
            if (expiryKey.isNotBlank() && (nearest.isBlank() || expiryKey < nearest)) nearest = expiryKey

            when (statusBatchKadaluarsa(expiryKey, hariHampirKadaluarsa)) {
                "KADALUARSA" -> expired += qty
                "ED_HARI_INI" -> edToday += qty
                "HAMPIR_KADALUARSA" -> near += qty
                else -> safe += qty
            }
        }

        val stokLayak = safe + near + edToday
        val status = when {
            stokLayak <= 0L && expired > 0L -> "Kedaluwarsa"
            edToday > 0L -> "ED Hari Ini"
            near > 0L -> "Hampir Kedaluwarsa"
            producedToday -> "Produksi Hari Ini"
            safe > 0L -> "Stok Sisa"
            else -> "Habis"
        }

        return RingkasanBatchStok(safe, near, edToday, expired, producedToday, nearest, status, lastProduction)
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
        val stokLayak = produk.safeStock + produk.nearExpiredStock + produk.edTodayStock
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

    private fun cocokSumberPenjualan(raw: String?, sourceFilter: String?): Boolean {
        val filter = sourceFilter.orEmpty().trim().uppercase(Locale.US)
        if (filter.isBlank() || filter == "SEMUA") return true
        val sumber = raw.orEmpty().trim().uppercase(Locale.US).ifBlank { "KASIR" }
        return when (filter) {
            "PASAR", "REKAP" -> sumber == "PASAR"
            "KASIR", "RUMAHAN" -> sumber == "KASIR"
            else -> sumber == filter
        }
    }

    private suspend fun kandidatIdKasir(authUid: String?): Set<String> {
        val auth = authUid.orEmpty().trim()
        if (auth.isBlank()) return emptySet()
        val user = cariPengguna(auth)
        return listOf(auth, user?.idDokumen, user?.authUid)
            .filterNotNull()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun cocokKasirPenjualan(doc: DocumentSnapshot, kandidatId: Set<String>): Boolean {
        if (kandidatId.isEmpty()) return true
        val idKasir = doc.getString("idKasir").orEmpty().trim()
        val idPembuat = doc.getString("dibuatOlehId").orEmpty().trim()
        return idKasir in kandidatId || idPembuat in kandidatId
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

    private fun rangeKeyNormal(rangeKey: String): String {
        val raw = rangeKey.trim().lowercase(Locale.US)
        return when {
            raw.isBlank() -> "7"
            raw.startsWith("custom:") -> raw
            raw.startsWith("bulan:") -> raw
            raw.contains("hari ini") || raw == "hari_ini" || raw == "today" -> "hari_ini"
            raw.contains("minggu") || raw == "7_hari" -> "7"
            raw.contains("bulan") || raw == "30_hari" || raw == "30" || raw == "bulan_ini" -> "30"
            raw.contains("semua") || raw == "all" -> "semua"
            raw.toIntOrNull() != null -> raw
            else -> "7"
        }
    }

    private fun validKunciTanggal(value: String): Boolean {
        return Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)
    }

    private fun labelTanggalLaporan(key: String): String {
        return runCatching {
            SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(formatTanggal.parse(key) ?: Date())
        }.getOrElse { key }
    }

    private fun labelBulanLaporan(yyyyMm: String): String {
        return runCatching {
            SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(SimpleDateFormat("yyyy-MM", Locale.US).parse(yyyyMm) ?: Date())
        }.getOrElse { yyyyMm }
    }

    private fun batasRentangKunciTanggal(rangeKey: String): Pair<String?, String?> {
        val normalized = rangeKeyNormal(rangeKey)
        val today = Formatter.currentDateOnly()
        return when {
            normalized == "semua" -> null to null
            normalized == "hari_ini" -> today to today
            normalized.startsWith("custom:") -> {
                val parts = normalized.split(":")
                val startRaw = parts.getOrNull(1).orEmpty()
                val endRaw = parts.getOrNull(2).orEmpty()
                val start = if (validKunciTanggal(startRaw)) startRaw else today
                val end = if (validKunciTanggal(endRaw)) endRaw else start
                if (start <= end) start to end else end to start
            }
            normalized.startsWith("bulan:") -> {
                val ym = normalized.removePrefix("bulan:").take(7)
                val start = if (Regex("\\d{4}-\\d{2}").matches(ym)) "$ym-01" else today.take(7) + "-01"
                val cal = Calendar.getInstance().apply {
                    time = Formatter.parseDate("${start}T00:00:00")
                    add(Calendar.MONTH, 1)
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                start to formatTanggal.format(cal.time)
            }
            else -> {
                val days = (normalized.toIntOrNull() ?: 7).coerceAtLeast(1)
                val calendar = Calendar.getInstance().apply {
                    time = Formatter.parseDate("${today}T00:00:00")
                    add(Calendar.DAY_OF_MONTH, -(days - 1))
                }
                formatTanggal.format(calendar.time) to today
            }
        }
    }

    private fun labelRentang(rangeKey: String): String {
        val normalized = rangeKeyNormal(rangeKey)
        return when {
            normalized == "hari_ini" -> "Hari ini"
            normalized == "7" -> "7 hari terakhir"
            normalized == "30" -> "30 hari terakhir"
            normalized == "semua" -> "Semua data"
            normalized.startsWith("custom:") -> {
                val (start, end) = batasRentangKunciTanggal(normalized)
                if (start == null || end == null) "Rentang custom" else "${labelTanggalLaporan(start)} - ${labelTanggalLaporan(end)}"
            }
            normalized.startsWith("bulan:") -> "Bulan ${labelBulanLaporan(normalized.removePrefix("bulan:").take(7))}"
            else -> "${normalized} hari terakhir"
        }
    }

    private fun labelPeriodeTanggal(rangeKey: String): String {
        val normalized = rangeKeyNormal(rangeKey)
        if (normalized == "semua") return "Semua data"
        val (start, end) = batasRentangKunciTanggal(normalized)
        if (start.isNullOrBlank() || end.isNullOrBlank()) return labelRentang(normalized)
        return if (start == end) labelTanggalLaporan(start) else "${labelTanggalLaporan(start)} - ${labelTanggalLaporan(end)}"
    }

    private fun normalisasiKunciTanggal(value: String): String {
        val clean = value.trim()
        return Regex("^\\d{4}-\\d{2}-\\d{2}").find(clean)?.value.orEmpty()
    }

    private fun dalamRentangKunciTanggal(kunciTanggal: String, rangeKey: String): Boolean {
        val (start, end) = batasRentangKunciTanggal(rangeKey)
        if (start == null || end == null) return true
        val key = normalisasiKunciTanggal(kunciTanggal).ifBlank { Formatter.currentDateOnly() }
        return key >= start && key <= end
    }

    private fun awalRentangKunciTanggal(rangeKey: String): String? {
        return batasRentangKunciTanggal(rangeKey).first
    }

    private fun akhirRentangKunciTanggal(rangeKey: String): String? {
        return batasRentangKunciTanggal(rangeKey).second
    }

    private suspend fun ambilDokumenKunciTanggal(
        namaKoleksi: String,
        keyField: String,
        timestampField: String,
        rangeKey: String,
        limit: Int? = null
    ): List<DocumentSnapshot> {
        val normalized = rangeKeyNormal(rangeKey)
        val ref = firestore.collection(namaKoleksi)
        val today = Formatter.currentDateOnly()

        val query = when (normalized) {
            "semua" -> {
                val fetchLimit = limit?.let { (it * RECENT_FETCH_MULTIPLIER).coerceAtLeast(it).toLong() }
                if (fetchLimit != null) ref.orderBy("dibuatPada", Query.Direction.DESCENDING).limit(fetchLimit) else ref
            }
            else -> {
                val start = awalRentangKunciTanggal(normalized) ?: today
                val end = akhirRentangKunciTanggal(normalized) ?: today
                val endExclusive = Calendar.getInstance().apply {
                    time = Formatter.parseDate("${end}T00:00:00")
                    add(Calendar.DAY_OF_MONTH, 1)
                }.time
                ref.whereGreaterThanOrEqualTo(timestampField, Timestamp(Formatter.parseDate("${start}T00:00:00")))
                    .whereLessThan(timestampField, Timestamp(endExclusive))
            }
        }

        val docs = runCatching { query.get().await().documents }
            .getOrElse { ref.get().await().documents }

        return docs.filter { doc ->
            dalamRentangKunciTanggal(kunciTanggalDoc(doc, keyField, timestampField), normalized)
        }
    }

    private suspend fun totalItemPenjualan(doc: DocumentSnapshot): Int {
        doc.getLong("totalItem")?.let { return it.toInt() }
        return doc.reference.collection("rincian").get().await().documents
            .sumOf { (it.getLong("jumlah") ?: 0L).toInt() }
    }

    private fun kunciTanggalDoc(
        doc: DocumentSnapshot,
        keyField: String,
        timestampField: String
    ): String {
        return normalisasiKunciTanggal(doc.getString(keyField).orEmpty())
            .ifBlank { dayKeyFromTimestamp(doc.getTimestamp(timestampField)) }
            .ifBlank { dayKeyFromTimestamp(doc.getTimestamp("dibuatPada")) }
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('\n') || escaped.contains('\r') || escaped.contains('\"')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun xmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
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


    private fun Long.toIntAman(): Int = this.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun produkTransaksiAman(
        products: List<Produk>,
        productId: String,
        snap: DocumentSnapshot
    ): Produk {
        products.firstOrNull { it.id == productId }?.let { return it }
        val stokFisik = (snap.getLong("stokSaatIni") ?: 0L).coerceAtLeast(0L)
        return Produk(
            id = productId,
            code = snap.getString("kodeProduk").orEmpty().ifBlank { productId },
            name = snap.getString("namaProduk").orEmpty().ifBlank { "Produk" },
            category = snap.getString("kategoriProduk").orEmpty(),
            unit = snap.getString("satuanProduk").orEmpty().ifBlank { "pcs" },
            stock = stokFisik.toIntAman(),
            minStock = (snap.getLong("stokMinimum") ?: 0L).toIntAman(),
            active = snap.getBoolean("aktif") ?: true,
            showInCashier = snap.getBoolean("tampilDiKasir") ?: true,
            photoTone = snap.getString("warnaProduk").orEmpty(),
            channels = mutableListOf(),
            safeStock = stokFisik.toIntAman(),
            nearExpiredStock = 0,
            edTodayStock = 0,
            expiredStock = 0,
            stockBatchStatus = "Stok Sisa"
        )
    }

    private fun stokLayakTransaksi(produk: Produk, stokFisik: Long): Long {
        val stokDariBatch = (produk.safeStock + produk.nearExpiredStock + produk.edTodayStock).toLong().coerceAtLeast(0L)
        val punyaInfoBatchEd = produk.expiredStock > 0 || produk.nearExpiredStock > 0 || produk.edTodayStock > 0 || produk.nearestExpiryDate.isNotBlank()
        return when {
            stokDariBatch > 0L -> stokDariBatch
            punyaInfoBatchEd -> 0L
            else -> stokFisik.coerceAtLeast(0L)
        }
    }

    private fun validasiAlokasiBatchAtauStokLegacy(
        produk: Produk,
        stokFisik: Long,
        qtyDiminta: Long,
        alokasiBatch: List<AlokasiBatch>,
        konteks: String
    ) {
        val stokLayak = stokLayakTransaksi(produk, stokFisik)
        check(stokLayak >= qtyDiminta) {
            "Stok layak $konteks ${produk.name} tidak mencukupi. Stok kedaluwarsa tidak bisa dipakai."
        }
        val qtyBatchLayak = alokasiBatch.sumOf { it.qtyDiambil }
        val stokLegacyLayak = (stokLayak - qtyBatchLayak).coerceAtLeast(0L)
        check(qtyBatchLayak + stokLegacyLayak >= qtyDiminta) {
            "Stok batch layak $konteks ${produk.name} tidak mencukupi."
        }
    }

    private fun DocumentSnapshot.produksiDibatalkan(): Boolean {
        val status = getString("statusProduksi").orEmpty().uppercase(Locale.US)
        return getBoolean("dibatalkan") == true || status == "BATAL" || status == "DIBATALKAN"
    }

    private fun DocumentSnapshot.penyesuaianDibatalkan(): Boolean {
        val status = getString("statusPenyesuaian").orEmpty().uppercase(Locale.US)
        return getBoolean("dibatalkan") == true || status == "BATAL" || status == "DIBATALKAN"
    }

    private fun DocumentSnapshot.dataDihapus(): Boolean {
        return getBoolean("dihapus") == true
    }

    private fun statusBatchDariTanggal(expiryKey: String, qtySisa: Long): String {
        if (qtySisa <= 0L) return "HABIS"
        return when (statusBatchKadaluarsa(expiryKey)) {
            "KADALUARSA" -> "KADALUARSA"
            else -> "AKTIF"
        }
    }

    private suspend fun cariPengguna(authUid: String): UserRingkas? {
        if (authUid.isBlank()) return null

        val doc = PenggunaFirestoreCompat.findByAuthUidSuspend(firestore, authUid)
        if (doc != null && doc.exists() && !doc.dataDihapus()) return doc.toUserRingkas(authUid)
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

    private suspend fun tandaiRiwayatStokDibatalkan(
        referensiId: String,
        sumberMutasi: String,
        alasan: String,
        userId: String,
        userName: String,
        waktu: Timestamp
    ) {
        if (referensiId.isBlank()) return
        val snapshot = firestore.collection("RiwayatStok")
            .whereEqualTo("referensiId", referensiId)
            .whereEqualTo("sumberMutasi", sumberMutasi)
            .get()
            .await()
        if (snapshot.isEmpty) return
        val batch = firestore.batch()
        snapshot.documents
            .filter { !it.getString("jenisMutasi").orEmpty().contains("PEMBATALAN", ignoreCase = true) }
            .forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "dibatalkan" to true,
                        "alasanPembatalan" to alasan,
                        "dibatalkanOlehId" to userId,
                        "dibatalkanOlehNama" to userName,
                        "dibatalkanPada" to waktu,
                        "diperbaruiPada" to waktu
                    )
                )
            }
        batch.commit().await()
    }

    suspend fun muatSemuaProduk(): List<Produk> = coroutineScope {
        val snapshot = firestore.collection("Produk")
            .orderBy("namaProduk")
            .get()
            .await()

        val productIds = snapshot.documents.map { it.id }.toSet()
        val batchDocsByProduct: Map<String, List<DocumentSnapshot>>? = if (productIds.isEmpty()) {
            emptyMap<String, List<DocumentSnapshot>>()
        } else {
            runCatching {
                firestore.collection("BatchStok")
                    .get()
                    .await()
                    .documents
                    .filter { doc ->
                        val productId = doc.getString("idProduk").orEmpty()
                        productId in productIds && (doc.getLong("qtySisa") ?: 0L) > 0L
                    }
                    .groupBy { it.getString("idProduk").orEmpty() }
            }.getOrNull()
        }

        snapshot.documents.mapAsyncLimited { doc ->
            val stokSaatIni = doc.getLong("stokSaatIni") ?: 0L
            val masaSimpanHari = (doc.getLong("masaSimpanHari") ?: 2L).toInt().coerceAtLeast(1)
            val hariHampirKadaluarsa = (doc.getLong("hariHampirKadaluarsa") ?: 1L)
                .toInt()
                .coerceAtLeast(0)
                .coerceAtMost(masaSimpanHari)
            val ringkasanBatch = batchDocsByProduct?.let { grouped ->
                ringkasanBatchStokDariDokumen(
                    stokProduk = stokSaatIni,
                    hariHampirKadaluarsaProduk = hariHampirKadaluarsa,
                    dokumenBatch = grouped[doc.id].orEmpty()
                )
            } ?: ringkasanBatchStok(doc.id, stokSaatIni, hariHampirKadaluarsa)
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
                edTodayStock = ringkasanBatch.edTodayStock.toInt(),
                expiredStock = ringkasanBatch.expiredStock.toInt(),
                nearestExpiryDate = ringkasanBatch.nearestExpiryDate,
                stockBatchStatus = ringkasanBatch.statusLabel,
                lastProductionDate = ringkasanBatch.lastProductionDate
            )
        }
    }

    suspend fun muatProdukAktif(): List<Produk> = muatSemuaProduk().filter { it.active }

    suspend fun muatNotifikasiAdmin(): List<NotifikasiAdmin> {
        val produkAktif = muatProdukAktif().filter { !it.deleted }
        val notifikasi = mutableListOf<NotifikasiAdmin>()

        val produkEdHariIni = produkAktif.filter { it.edTodayStock > 0 }
        val totalEdHariIni = produkEdHariIni.sumOf { it.edTodayStock }
        if (totalEdHariIni > 0) {
            val contoh = produkEdHariIni.take(2).joinToString(", ") { it.name }
            notifikasi += NotifikasiAdmin(
                id = "ed_hari_ini",
                jenis = "ED_HARI_INI",
                judul = "Produk ED Hari Ini",
                isi = "${produkEdHariIni.size} produk perlu diprioritaskan keluar${if (contoh.isNotBlank()) ": $contoh" else ""}.",
                jumlah = totalEdHariIni,
                warna = "warning",
                tujuan = "stok"
            )
        }

        val produkHampirEd = produkAktif.filter { it.nearExpiredStock > 0 }
        val totalHampirEd = produkHampirEd.sumOf { it.nearExpiredStock }
        if (totalHampirEd > 0) {
            val contoh = produkHampirEd.take(2).joinToString(", ") { it.name }
            notifikasi += NotifikasiAdmin(
                id = "hampir_ed",
                jenis = "HAMPIR_ED",
                judul = "Stok Hampir Kedaluwarsa",
                isi = "${produkHampirEd.size} produk mendekati ED${if (contoh.isNotBlank()) ": $contoh" else ""}.",
                jumlah = totalHampirEd,
                warna = "orange",
                tujuan = "stok"
            )
        }

        val produkKadaluarsa = produkAktif.filter { it.expiredStock > 0 }
        val totalKadaluarsa = produkKadaluarsa.sumOf { it.expiredStock }
        if (totalKadaluarsa > 0) {
            val contoh = produkKadaluarsa.take(2).joinToString(", ") { it.name }
            notifikasi += NotifikasiAdmin(
                id = "kedaluwarsa",
                jenis = "KADALUARSA",
                judul = "Stok Kedaluwarsa",
                isi = "${produkKadaluarsa.size} produk memiliki stok kedaluwarsa${if (contoh.isNotBlank()) ": $contoh" else ""}. Segera cek dan buang produk kedaluwarsa.",
                jumlah = totalKadaluarsa,
                warna = "danger",
                tujuan = "stok"
            )
        }

        val produkMenipis = produkAktif.filter { produk ->
            val stokLayak = produk.safeStock + produk.nearExpiredStock + produk.edTodayStock
            produk.minStock > 0 && stokLayak in 1..produk.minStock
        }
        if (produkMenipis.isNotEmpty()) {
            val contoh = produkMenipis.take(2).joinToString(", ") { it.name }
            notifikasi += NotifikasiAdmin(
                id = "stok_menipis",
                jenis = "STOK_MENIPIS",
                judul = "Stok Menipis",
                isi = "${produkMenipis.size} produk berada di bawah batas minimum${if (contoh.isNotBlank()) ": $contoh" else ""}.",
                jumlah = produkMenipis.size,
                warna = "orange",
                tujuan = "stok"
            )
        }

        val produkHabis = produkAktif.filter { produk ->
            val stokLayak = produk.safeStock + produk.nearExpiredStock + produk.edTodayStock
            stokLayak <= 0 && produk.stock > 0
        }
        if (produkHabis.isNotEmpty()) {
            val contoh = produkHabis.take(2).joinToString(", ") { it.name }
            notifikasi += NotifikasiAdmin(
                id = "stok_tidak_layak",
                jenis = "STOK_TIDAK_LAYAK",
                judul = "Stok Tidak Layak Jual",
                isi = "${produkHabis.size} produk punya stok fisik, tapi tidak ada stok layak jual${if (contoh.isNotBlank()) ": $contoh" else ""}.",
                jumlah = produkHabis.size,
                warna = "danger",
                tujuan = "stok"
            )
        }

        val hargaPasarBelumLengkap = produkAktif.filter { produk ->
            produk.showInCashier && produk.channels.none { channel ->
                channel.active && !channel.deleted && channel.price > 0L && channel.label.contains("pasar", ignoreCase = true)
            }
        }
        if (hargaPasarBelumLengkap.isNotEmpty()) {
            val contoh = hargaPasarBelumLengkap.take(2).joinToString(", ") { it.name }
            notifikasi += NotifikasiAdmin(
                id = "harga_pasar",
                jenis = "HARGA_PASAR",
                judul = "Harga Pasar Belum Lengkap",
                isi = "${hargaPasarBelumLengkap.size} produk belum punya harga kanal Pasar${if (contoh.isNotBlank()) ": $contoh" else ""}.",
                jumlah = hargaPasarBelumLengkap.size,
                warna = "warning",
                tujuan = "harga"
            )
        }

        return notifikasi
    }

    suspend fun muatProdukProduksiDasar(): List<Produk> = muatProdukProduksiByJenis("DASAR")

    suspend fun muatProdukProduksiOlahan(): List<Produk> = muatProdukProduksiByJenis("OLAHAN")

    private suspend fun muatProdukProduksiByJenis(jenisProduk: String): List<Produk> {
        val dokumen = firestore.collection("Produk")
            .get()
            .await()
            .documents
            .filter { it.getBoolean("dihapus") != true }
            .filter { it.getString("jenisProduk").orEmpty().equals(jenisProduk, ignoreCase = true) }
            .filter { it.getString("namaProduk").orEmpty().isNotBlank() }

        val productIds = dokumen.map { it.id }.toSet()
        val batchDocsByProduct = if (productIds.isEmpty()) {
            emptyMap<String, List<DocumentSnapshot>>()
        } else {
            runCatching {
                firestore.collection("BatchStok")
                    .get()
                    .await()
                    .documents
                    .filter { doc ->
                        val productId = doc.getString("idProduk").orEmpty()
                        productId in productIds && (doc.getLong("qtySisa") ?: 0L) > 0L
                    }
                    .groupBy { it.getString("idProduk").orEmpty() }
            }.getOrDefault(emptyMap<String, List<DocumentSnapshot>>())
        }

        return dokumen
            .map { doc ->
                val stokSaatIni = doc.getLong("stokSaatIni") ?: 0L
                val masaSimpanHari = (doc.getLong("masaSimpanHari") ?: 2L).toInt().coerceAtLeast(1)
                val hariHampirKadaluarsa = (doc.getLong("hariHampirKadaluarsa") ?: 1L)
                    .toInt()
                    .coerceAtLeast(0)
                    .coerceAtMost(masaSimpanHari)
                val ringkasanBatch = ringkasanBatchStokDariDokumen(
                    stokProduk = stokSaatIni,
                    hariHampirKadaluarsaProduk = hariHampirKadaluarsa,
                    dokumenBatch = batchDocsByProduct[doc.id].orEmpty()
                )
                Produk(
                    id = doc.id,
                    code = doc.getString("kodeProduk").orEmpty().ifBlank { doc.id },
                    name = doc.getString("namaProduk").orEmpty(),
                    category = doc.getString("jenisProduk").orEmpty().ifBlank { jenisProduk },
                    unit = doc.getString("satuan").orEmpty().ifBlank { "pcs" },
                    stock = stokSaatIni.toInt(),
                    minStock = (doc.getLong("stokMinimum") ?: 0L).toInt(),
                    active = doc.getBoolean("aktifDijual") ?: true,
                    showInCashier = doc.getBoolean("tampilDiKasir") ?: true,
                    photoTone = "",
                    channels = mutableListOf(),
                    deleted = false,
                    shelfLifeDays = masaSimpanHari,
                    nearExpiryWarningDays = hariHampirKadaluarsa,
                    producedToday = ringkasanBatch.producedToday,
                    safeStock = ringkasanBatch.safeStock.toInt(),
                    nearExpiredStock = ringkasanBatch.nearExpiredStock.toInt(),
                    edTodayStock = ringkasanBatch.edTodayStock.toInt(),
                    expiredStock = ringkasanBatch.expiredStock.toInt(),
                    nearestExpiryDate = ringkasanBatch.nearestExpiryDate,
                    stockBatchStatus = ringkasanBatch.statusLabel,
                    lastProductionDate = ringkasanBatch.lastProductionDate
                )
            }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

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
        produkSnapshot.documents.mapAsyncLimited { produkDoc ->
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
        }.flatten().sortedBy { it.productId }
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
                    "statusProduksi" to "AKTIF",
                    "dibatalkan" to false,
                    "alasanPembatalan" to "",
                    "dibatalkanOlehId" to "",
                    "dibatalkanOlehNama" to "",
                    "dibatalkanPada" to null,
                    "dihapus" to false,
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
            val totalAlokasiLayak = alokasiBahan.sumOf { it.qtyDiambil }
            check(totalAlokasiLayak >= inputQty.toLong()) {
                "Stok layak pakai bahan tidak mencukupi. Stok kedaluwarsa tidak bisa dipakai."
            }
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
                    "batchBahanDetail" to alokasiBahan.map {
                        mapOf(
                            "idBatch" to it.idBatch,
                            "qtyKeluar" to it.qtyDiambil,
                            "tanggalProduksi" to it.tanggalProduksi,
                            "tanggalKadaluarsa" to it.tanggalKadaluarsa
                        )
                    },
                    "statusProduksi" to "AKTIF",
                    "dibatalkan" to false,
                    "alasanPembatalan" to "",
                    "dibatalkanOlehId" to "",
                    "dibatalkanOlehNama" to "",
                    "dibatalkanPada" to null,
                    "dihapus" to false,
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
        var totalProduksiOlahanHariIni = 0
        var totalBatchHariIni = 0.0
        var totalKonversiHariIni = 0

        snapshot.documents.filter { !it.produksiDibatalkan() && !it.dataDihapus() }.forEach { doc ->
            val jenis = doc.getString("jenisProduksi").orEmpty()
            if (jenis == "OLAHAN") {
                totalKonversiHariIni += 1
                totalProduksiOlahanHariIni += (doc.getLong("jumlahHasil") ?: 0L).toInt()
            } else {
                totalProduksiDasarHariIni += (doc.getLong("jumlahHasil") ?: 0L).toInt()
                totalBatchHariIni += doc.numberAsDouble("jumlahBahan")
            }
        }

        val riwayat = muatRiwayatProduksi()

        return RingkasanProduksi(
            totalProduksiDasarHariIni = totalProduksiDasarHariIni,
            totalProduksiOlahanHariIni = totalProduksiOlahanHariIni,
            totalBatchHariIni = totalBatchHariIni.toInt(),
            totalKonversiHariIni = totalKonversiHariIni,
            totalParameterAktif = parameters.count { it.active },
            totalRiwayat = riwayat.size,
            recentRows = riwayat.take(5)
        )
    }

    suspend fun muatGrafikProduksi7Hari(): List<TitikGrafikProduksi> {
        val hariIni = Calendar.getInstance()
        val kunciTanggal = (6 downTo 0).map { mundur ->
            (hariIni.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -mundur) }
        }.map { calendar -> formatTanggal.format(calendar.time) }

        val dokumen = firestore.collection("CatatanProduksi")
            .whereGreaterThanOrEqualTo("kunciTanggal", kunciTanggal.first())
            .whereLessThanOrEqualTo("kunciTanggal", kunciTanggal.last())
            .get()
            .await()
            .documents
            .filter { !it.produksiDibatalkan() && !it.dataDihapus() }

        val agregat = dokumen.groupBy { doc ->
            doc.getString("kunciTanggal").orEmpty().ifBlank {
                dayKeyFromTimestamp(doc.getTimestamp("tanggalProduksi") ?: doc.getTimestamp("dibuatPada"))
            }
        }

        val labelFormat = SimpleDateFormat("dd/MM", Locale("id", "ID"))
        return kunciTanggal.map { key ->
            val docs = agregat[key].orEmpty()
            val totalDasar = docs
                .filter { it.getString("jenisProduksi").orEmpty().equals("DASAR", ignoreCase = true) }
                .sumOf { (it.getLong("jumlahHasil") ?: 0L).toInt() }
            val totalOlahan = docs
                .filter { it.getString("jenisProduksi").orEmpty().equals("OLAHAN", ignoreCase = true) }
                .sumOf { (it.getLong("jumlahHasil") ?: 0L).toInt() }
            val labelTanggal = runCatching { labelFormat.format(formatTanggal.parse(key) ?: Date()) }
                .getOrDefault(key.takeLast(5))
            TitikGrafikProduksi(
                kunciTanggal = key,
                labelTanggal = labelTanggal,
                totalDasar = totalDasar,
                totalOlahan = totalOlahan,
                total = totalDasar + totalOlahan
            )
        }
    }

    suspend fun muatRiwayatProduksi(): List<BarisRiwayatProduksi> {
        val snapshot = firestore.collection("CatatanProduksi")
            .orderBy("dibuatPada", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents
            .filter { !it.dataDihapus() }
            .map { doc ->
                val jenis = doc.getString("jenisProduksi").orEmpty()
                val batal = doc.produksiDibatalkan()
                val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalProduksi") ?: doc.getTimestamp("dibuatPada"))

                if (jenis == "OLAHAN") {
                    val namaAsal = doc.getString("namaProdukAsal").orEmpty().ifBlank { "Bahan" }
                    val namaHasil = doc.getString("namaProdukHasil").orEmpty().ifBlank { "Hasil" }
                    val bahan = (doc.getLong("jumlahBahan") ?: 0L).toInt()
                    val hasil = (doc.getLong("jumlahHasil") ?: 0L).toInt()

                    BarisRiwayatProduksi(
                        id = doc.id,
                        badge = if (batal) "Produk Olahan • Batal" else "Produk Olahan",
                        title = "$namaAsal → $namaHasil",
                        subtitle = Formatter.readableDateTime(tanggalIso) + if (batal) " • DIBATALKAN" else "",
                        amount = if (batal) "BATAL" else "$bahan → $hasil",
                        tanggalIso = tanggalIso
                    )
                } else {
                    val namaHasil = doc.getString("namaProdukHasil").orEmpty().ifBlank { "Produk Dasar" }
                    val jumlahBatch = doc.numberAsDouble("jumlahBahan")
                    val jumlahHasil = (doc.getLong("jumlahHasil") ?: 0L).toInt()
                    val satuan = doc.getString("satuanHasil").orEmpty().ifBlank { "pcs" }

                    BarisRiwayatProduksi(
                        id = doc.id,
                        badge = if (batal) "Produksi Dasar • Batal" else "Produksi Dasar",
                        title = namaHasil,
                        subtitle = "${Formatter.readableDateTime(tanggalIso)} • ${formatBatch(jumlahBatch)} Masak" + if (batal) " • DIBATALKAN" else "",
                        amount = if (batal) "BATAL" else "+${Formatter.ribuan(jumlahHasil.toLong())} $satuan",
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
        val catatan = doc.getString("catatan").orEmpty().ifBlank { "-" }
        val statusProduksi = if (doc.produksiDibatalkan()) "DIBATALKAN" else "AKTIF"

        val pembatalanBlock = if (doc.produksiDibatalkan()) {
            val tanggalBatal = isoFromTimestamp(doc.getTimestamp("dibatalkanPada"))
            """

PEMBATALAN
Status           : DIBATALKAN
Alasan           : ${doc.getString("alasanPembatalan").orEmpty().ifBlank { "-" }}
Dibatalkan Oleh  : ${doc.getString("dibatalkanOlehNama").orEmpty().ifBlank { "-" }}
Waktu Batal      : ${Formatter.readableDateTime(tanggalBatal)}
            """.trimIndent()
        } else ""

        return if (jenis == "OLAHAN") {
            val namaAsal = doc.getString("namaProdukAsal").orEmpty().ifBlank { "-" }
            val namaHasil = doc.getString("namaProdukHasil").orEmpty().ifBlank { "-" }
            val jumlahBahan = doc.getLong("jumlahBahan") ?: 0L
            val jumlahHasil = doc.getLong("jumlahHasil") ?: 0L
            val satuanBahan = doc.getString("satuanBahan").orEmpty().ifBlank { "pcs" }
            val satuanHasil = doc.getString("satuanHasil").orEmpty().ifBlank { "pcs" }
            """
Detail Produksi Olahan
──────────────────────
ID Catatan      : ${doc.id}
Tanggal Produksi: ${Formatter.readableDateTime(tanggalProduksi)}
Status          : $statusProduksi

Bahan Baku
- Produk        : $namaAsal
- Jumlah Keluar : ${Formatter.ribuan(jumlahBahan)} $satuanBahan

Hasil Olahan
- Produk        : $namaHasil
- Jumlah Masuk  : ${Formatter.ribuan(jumlahHasil)} $satuanHasil

Catatan         : $catatan
Diinput Oleh    : $namaInput
Waktu Input     : ${Formatter.readableDateTime(tanggalInput)}
$pembatalanBlock
            """.trimIndent()
        } else {
            val namaHasil = doc.getString("namaProdukHasil").orEmpty().ifBlank { "-" }
            val jumlahHasil = doc.getLong("jumlahHasil") ?: 0L
            val satuanHasil = doc.getString("satuanHasil").orEmpty().ifBlank { "pcs" }

            // Mengambil nilai hasilPerProduksi (Hasil Per Masak) asli dari ParameterProduksi
            var hasilPerMasakLabel = "Default"
            val idParam = doc.getString("idParameterProduksi").orEmpty()
            if (idParam.isNotBlank()) {
                runCatching {
                    val paramDoc = firestore.collection("Produk")
                        .document(doc.getString("idProdukHasil").orEmpty())
                        .collection("parameterProduksi")
                        .document(idParam)
                        .get()
                        .await()
                    if (paramDoc.exists()) {
                        val qty = paramDoc.getLong("hasilPerProduksi") ?: 0L
                        hasilPerMasakLabel = "${Formatter.ribuan(qty)} $satuanHasil"
                    }
                }
            }

            """
Detail Produksi Dasar
─────────────────────
ID Catatan      : ${doc.id}
Tanggal Produksi: ${Formatter.readableDateTime(tanggalProduksi)}
Status          : $statusProduksi

Hasil Produksi
- Produk        : $namaHasil
- Hasil / Masak : $hasilPerMasakLabel
- Jumlah Masak  : ${formatBatch(doc.numberAsDouble("jumlahBahan"))}
- Total Masuk   : ${Formatter.ribuan(jumlahHasil)} $satuanHasil

Catatan         : $catatan
Diinput Oleh    : $namaInput
Waktu Input     : ${Formatter.readableDateTime(tanggalInput)}
$pembatalanBlock
            """.trimIndent()
        }
    }

    suspend fun batalkanCatatanProduksi(id: String, alasan: String, userAuthId: String) {
        val ref = firestore.collection("CatatanProduksi").document(id)
        val doc = ref.get().await()
        if (!doc.exists()) throw IllegalStateException("Data produksi tidak ditemukan")
        require(alasan.isNotBlank()) { "Alasan pembatalan wajib diisi" }
        if (doc.produksiDibatalkan()) throw IllegalStateException("Data produksi sudah dibatalkan")

        val jenis = doc.getString("jenisProduksi").orEmpty()
        val tanggalKey = doc.getString("kunciTanggal").orEmpty()
            .ifBlank { dayKeyFromTimestamp(doc.getTimestamp("tanggalProduksi")) }
        val hasilQty = doc.getLong("jumlahHasil") ?: 0L
        val bahanQtyLong = kotlin.math.round(doc.numberAsDouble("jumlahBahan")).toLong()
        val hasilProductId = doc.getString("idProdukHasil").orEmpty()
        val asalProductId = doc.getString("idProdukAsal").orEmpty()
        val hasilRef = produkRef(hasilProductId)
        val asalRef = if (jenis == "OLAHAN" && asalProductId.isNotBlank()) produkRef(asalProductId) else null
        val batchHasilRefs = firestore.collection("BatchStok")
            .whereEqualTo("sumberProduksiId", id)
            .get()
            .await()
            .documents
            .map { it.reference }
        @Suppress("UNCHECKED_CAST")
        val batchBahanDetail = doc.get("batchBahanDetail") as? List<Map<String, Any>> ?: emptyList()
        val batchBahanRefs = batchBahanDetail.mapNotNull { detail ->
            detail["idBatch"]?.toString()?.takeIf { it.isNotBlank() }?.let { firestore.collection("BatchStok").document(it) }
        }
        val dibuatPada = nowTimestamp()
        val user = cariPengguna(userAuthId)
        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()

        firestore.runTransaction { trx ->
            val produksiSnap = trx.get(ref)
            check(produksiSnap.exists()) { "Data produksi tidak ditemukan" }
            check(!produksiSnap.produksiDibatalkan()) { "Data produksi sudah dibatalkan" }

            val hasilSnap = trx.get(hasilRef)
            check(hasilSnap.exists()) { "Produk hasil tidak ditemukan" }
            val stokHasil = hasilSnap.getLong("stokSaatIni") ?: 0L
            check(stokHasil >= hasilQty) { "Stok hasil tidak cukup untuk membatalkan produksi" }

            val hasilBatchSnaps = batchHasilRefs.map { it to trx.get(it) }
            if (hasilBatchSnaps.isNotEmpty()) {
                val qtySisaBatchHasil = hasilBatchSnaps.sumOf { (_, snap) -> snap.getLong("qtySisa") ?: 0L }
                check(qtySisaBatchHasil >= hasilQty) {
                    "Stok hasil produksi sudah terpakai, produksi tidak bisa dibatalkan. Buat penyesuaian stok jika diperlukan."
                }
            }

            val kodeHasil = hasilSnap.getString("kodeProduk") ?: hasilProductId
            val namaHasil = hasilSnap.getString("namaProduk") ?: produksiSnap.getString("namaProdukHasil").orEmpty().ifBlank { "Produk" }
            val namaPembatal = user?.nama ?: "Pengguna"
            val idPembatal = user?.idDokumen ?: userAuthId

            if (jenis == "OLAHAN") {
                val asalRefNonNull = asalRef ?: throw IllegalStateException("Produk asal tidak valid")
                val asalSnap = trx.get(asalRefNonNull)
                check(asalSnap.exists()) { "Produk asal tidak ditemukan" }
                val batchBahanSnaps = batchBahanRefs.map { it to trx.get(it) }
                val stokAsal = asalSnap.getLong("stokSaatIni") ?: 0L
                val stokAsalSesudah = stokAsal + bahanQtyLong
                val stokHasilSesudah = stokHasil - hasilQty
                trx.update(asalRefNonNull, mapOf("stokSaatIni" to stokAsalSesudah, "diperbaruiPada" to dibuatPada))
                trx.update(hasilRef, mapOf("stokSaatIni" to stokHasilSesudah, "diperbaruiPada" to dibuatPada))

                batchBahanDetail.forEach { detail ->
                    val idBatch = detail["idBatch"]?.toString().orEmpty()
                    val qtyKembali = (detail["qtyKeluar"] as? Number)?.toLong() ?: detail["qtyKeluar"]?.toString()?.toLongOrNull() ?: 0L
                    val pair = batchBahanSnaps.firstOrNull { it.first.id == idBatch }
                    if (pair != null && qtyKembali > 0L) {
                        val snap = pair.second
                        val qtySisa = snap.getLong("qtySisa") ?: 0L
                        val expiryKey = snap.getString("kunciTanggalKadaluarsa").orEmpty()
                            .ifBlank { dayKeyFromTimestamp(snap.getTimestamp("tanggalKadaluarsa")) }
                        val qtyBaru = qtySisa + qtyKembali
                        trx.update(pair.first, mapOf("qtySisa" to qtyBaru, "statusBatch" to statusBatchDariTanggal(expiryKey, qtyBaru), "diperbaruiPada" to dibuatPada))
                    }
                }

                riwayatDrafts = listOf(
                    DraftRiwayatStok(
                        tanggalMutasi = dibuatPada,
                        kunciTanggal = dayKeyFromTimestamp(dibuatPada),
                        idProduk = asalProductId,
                        kodeProduk = asalSnap.getString("kodeProduk") ?: asalProductId,
                        namaProduk = asalSnap.getString("namaProduk") ?: produksiSnap.getString("namaProdukAsal").orEmpty().ifBlank { "Bahan" },
                        jenisMutasi = "PEMBATALAN_KONVERSI_MASUK",
                        sumberMutasi = "CatatanProduksi",
                        referensiId = id,
                        qtyMasuk = bahanQtyLong,
                        qtyKeluar = 0L,
                        stokSebelum = stokAsal,
                        stokSesudah = stokAsalSesudah,
                        catatan = "Pembatalan produksi olahan: ${alasan.trim()}",
                        idPembuat = idPembatal,
                        namaPembuat = namaPembatal
                    ),
                    DraftRiwayatStok(
                        tanggalMutasi = dibuatPada,
                        kunciTanggal = dayKeyFromTimestamp(dibuatPada),
                        idProduk = hasilProductId,
                        kodeProduk = kodeHasil,
                        namaProduk = namaHasil,
                        jenisMutasi = "PEMBATALAN_KONVERSI_KELUAR",
                        sumberMutasi = "CatatanProduksi",
                        referensiId = id,
                        qtyMasuk = 0L,
                        qtyKeluar = hasilQty,
                        stokSebelum = stokHasil,
                        stokSesudah = stokHasilSesudah,
                        catatan = "Pembatalan produksi olahan: ${alasan.trim()}",
                        idPembuat = idPembatal,
                        namaPembuat = namaPembatal
                    )
                )
            } else {
                val stokHasilSesudah = stokHasil - hasilQty
                trx.update(hasilRef, mapOf("stokSaatIni" to stokHasilSesudah, "diperbaruiPada" to dibuatPada))
                riwayatDrafts = listOf(
                    DraftRiwayatStok(
                        tanggalMutasi = dibuatPada,
                        kunciTanggal = dayKeyFromTimestamp(dibuatPada),
                        idProduk = hasilProductId,
                        kodeProduk = kodeHasil,
                        namaProduk = namaHasil,
                        jenisMutasi = "PEMBATALAN_PRODUKSI_DASAR_KELUAR",
                        sumberMutasi = "CatatanProduksi",
                        referensiId = id,
                        qtyMasuk = 0L,
                        qtyKeluar = hasilQty,
                        stokSebelum = stokHasil,
                        stokSesudah = stokHasilSesudah,
                        catatan = "Pembatalan produksi dasar: ${alasan.trim()}",
                        idPembuat = idPembatal,
                        namaPembuat = namaPembatal
                    )
                )
            }

            hasilBatchSnaps.forEach { (batchRef, snap) ->
                val qtySisa = snap.getLong("qtySisa") ?: 0L
                if (qtySisa > 0L) {
                    trx.update(batchRef, mapOf("qtySisa" to 0L, "statusBatch" to "DIBATALKAN", "dibatalkanPada" to dibuatPada, "diperbaruiPada" to dibuatPada))
                }
            }

            trx.update(
                ref,
                mapOf(
                    "statusProduksi" to "BATAL",
                    "dibatalkan" to true,
                    "alasanPembatalan" to alasan.trim(),
                    "dibatalkanOlehId" to idPembatal,
                    "dibatalkanOlehNama" to namaPembatal,
                    "dibatalkanPada" to dibuatPada,
                    "diperbaruiPada" to dibuatPada
                )
            )
        }.await()

        catatRiwayatStok(riwayatDrafts)
        tandaiRiwayatStokDibatalkan(id, "CatatanProduksi", alasan.trim(), user?.idDokumen ?: userAuthId, user?.nama ?: "Pengguna", dibuatPada)
        perbaruiRingkasanHarian(tanggalKey)
    }

    suspend fun hapusCatatanProduksi(id: String) {
        batalkanCatatanProduksi(id, "Dibatalkan dari riwayat produksi", "")
    }

    suspend fun muatMonitoringStok(): List<Produk> = muatSemuaProduk()


    suspend fun muatRingkasanStokDashboard(): RingkasanStokDashboard {
        val produkAktif = muatSemuaProduk()
            .filter { !it.deleted && it.active }
            .sortedBy { it.name.lowercase(Locale.US) }

        val baris = produkAktif.map { produk ->
            val stokLayak = produk.safeStock + produk.nearExpiredStock + produk.edTodayStock
            val status = statusProduk(produk)
            BarisStokDashboard(
                id = produk.id,
                namaProduk = produk.name,
                kodeProduk = produk.code,
                kategori = produk.category,
                satuan = produk.unit,
                stokFisik = produk.stock,
                stokLayakJual = stokLayak,
                stokAman = produk.safeStock,
                stokHampirKadaluarsa = produk.nearExpiredStock,
                stokEdHariIni = produk.edTodayStock,
                stokKadaluarsa = produk.expiredStock,
                stokMinimum = produk.minStock,
                status = when {
                    produk.expiredStock > 0 -> "Perlu Tindakan"
                    status == "Habis" -> "Habis"
                    status == "Menipis" -> "Menipis"
                    produk.edTodayStock > 0 -> "ED Hari Ini"
                    produk.nearExpiredStock > 0 -> "Hampir Kedaluwarsa"
                    else -> "Aman"
                },
                edTerdekat = produk.nearestExpiryDate
            )
        }

        val produkKritis = baris
            .filter { it.stokKadaluarsa > 0 || it.status == "Habis" || it.status == "Menipis" || it.stokEdHariIni > 0 || it.stokHampirKadaluarsa > 0 }
            .sortedWith(
                compareByDescending<BarisStokDashboard> { it.stokKadaluarsa > 0 }
                    .thenByDescending { it.status == "Habis" }
                    .thenByDescending { it.status == "Menipis" }
                    .thenByDescending { it.stokEdHariIni > 0 }
                    .thenByDescending { it.stokHampirKadaluarsa > 0 }
                    .thenBy { it.namaProduk.lowercase(Locale.US) }
            )
            .take(6)

        val produkTerbanyak = baris
            .sortedWith(compareByDescending<BarisStokDashboard> { it.stokLayakJual }.thenBy { it.namaProduk.lowercase(Locale.US) })
            .take(5)

        return RingkasanStokDashboard(
            totalProdukAktif = produkAktif.size,
            totalStokFisik = produkAktif.sumOf { it.stock.toLong() },
            totalStokLayakJual = produkAktif.sumOf { (it.safeStock + it.nearExpiredStock + it.edTodayStock).toLong() },
            totalStokAman = produkAktif.sumOf { it.safeStock.toLong() },
            totalHampirKadaluarsa = produkAktif.sumOf { (it.nearExpiredStock + it.edTodayStock).toLong() },
            totalKadaluarsa = produkAktif.sumOf { it.expiredStock.toLong() },
            totalMenipis = baris.count { it.status == "Menipis" },
            totalHabis = baris.count { it.status == "Habis" },
            totalPerluTindakan = baris.count { it.stokKadaluarsa > 0 || it.stokEdHariIni > 0 || it.stokHampirKadaluarsa > 0 || it.status == "Habis" || it.status == "Menipis" },
            produkKritis = produkKritis,
            produkTerbanyak = produkTerbanyak
        )
    }

    suspend fun simpanAdjustment(
        dateOnly: String,
        timeOnly: String = "",
        productId: String,
        type: String,
        qty: Int,
        note: String,
        userAuthId: String
    ): String {
        require(qty > 0) { "Jumlah penyesuaian harus lebih dari 0" }
        require(!type.equals("add", ignoreCase = true) && !type.equals("tambah", ignoreCase = true)) {
            "Penyesuaian stok hanya untuk mengurangi stok. Penambahan stok dilakukan lewat menu Produksi."
        }

        val qtyLong = qty.toLong()
        val ref = firestore.collection("PenyesuaianStok").document(newId("adj"))
        val produkRef = produkRef(productId)
        val user = cariPengguna(userAuthId)
        val dibuatPada = nowTimestamp()
        val kunciTanggal = dateOnly.ifBlank { Formatter.currentDateOnly() }
        val jamPenyesuaian = timeOnly.ifBlank { Formatter.currentTimeOnly() }
        val tanggalPenyesuaian = Timestamp(Formatter.parseDate("${kunciTanggal}T${jamPenyesuaian}:00"))
        val batchRefsByProduct = muatBatchStokFefoRefs(listOf(productId))
        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()
        var detailBatchAdjustment: List<Map<String, Any>> = emptyList()

        firestore.runTransaction { trx ->
            val produkSnap = trx.get(produkRef)
            check(produkSnap.exists()) { "Produk tidak ditemukan" }

            val stok = produkSnap.getLong("stokSaatIni") ?: 0L
            val nextStok = stok - qtyLong
            check(nextStok >= 0L) { "Stok tidak boleh minus" }

            val kodeProduk = produkSnap.getString("kodeProduk") ?: productId
            val namaProduk = produkSnap.getString("namaProduk") ?: "Produk"

            val allocations = siapkanAlokasiBatchFefo(
                trx = trx,
                productId = productId,
                productName = namaProduk,
                qtyDiminta = qtyLong,
                batchRefsByProduct = batchRefsByProduct
            )
            val qtyDariBatch = allocations.sumOf { it.qtyDiambil }
            val legacyStockTanpaBatch = (stok - qtyDariBatch).coerceAtLeast(0L)
            check(qtyDariBatch + legacyStockTanpaBatch >= qtyLong) { "Stok batch $namaProduk tidak mencukupi" }
            terapkanAlokasiBatchFefo(trx, allocations, dibuatPada)
            detailBatchAdjustment = allocations.map {
                mapOf(
                    "idBatch" to it.idBatch,
                    "qtyKeluar" to it.qtyDiambil,
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
                    "jenisPenyesuaian" to "KURANG",
                    "jumlah" to qtyLong,
                    "alasanPenyesuaian" to note.ifBlank { "Penyesuaian stok kurang" },
                    "catatan" to note.ifBlank { "Penyesuaian stok kurang" },
                    "batchAdjustmentDetail" to detailBatchAdjustment,
                    "statusPenyesuaian" to "AKTIF",
                    "dibatalkan" to false,
                    "alasanPembatalan" to "",
                    "dibatalkanOlehId" to "",
                    "dibatalkanOlehNama" to "",
                    "dibatalkanPada" to null,
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
                    jenisMutasi = "ADJUSTMENT_KURANG",
                    sumberMutasi = "PenyesuaianStok",
                    referensiId = ref.id,
                    qtyMasuk = 0L,
                    qtyKeluar = qtyLong,
                    stokSebelum = stok,
                    stokSesudah = nextStok,
                    catatan = note.ifBlank { "Penyesuaian stok kurang" },
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
        if (!doc.exists()) return "Data penyesuaian tidak ditemukan"
        val tanggal = isoFromTimestamp(doc.getTimestamp("tanggalPenyesuaian") ?: doc.getTimestamp("dibuatPada"))
        val status = if (doc.penyesuaianDibatalkan()) "DIBATALKAN" else "AKTIF"
        val pembatalanBlock = if (doc.penyesuaianDibatalkan()) {
            val tanggalBatal = isoFromTimestamp(doc.getTimestamp("dibatalkanPada"))
            """
            Alasan Batal: ${doc.getString("alasanPembatalan").orEmpty().ifBlank { "-" }}
            Dibatalkan Oleh: ${doc.getString("dibatalkanOlehNama").orEmpty().ifBlank { "-" }}
            Waktu Batal: ${Formatter.readableDateTime(tanggalBatal)}
            """.trimIndent()
        } else ""
        return """
            ID: ${doc.id}
            Tanggal: ${Formatter.readableDateTime(tanggal)}
            Produk: ${doc.getString("namaProduk").orEmpty()}
            Jenis: ${doc.getString("jenisPenyesuaian").orEmpty()}
            Status: $status
            Jumlah: ${Formatter.ribuan(doc.getLong("jumlah") ?: 0L)}
            Alasan: ${doc.getString("alasanPenyesuaian").orEmpty().ifBlank { "-" }}
            Catatan: ${doc.getString("catatan").orEmpty().ifBlank { "-" }}
            Dibuat Oleh: ${doc.getString("namaPembuat").orEmpty().ifBlank { "-" }}
            $pembatalanBlock
        """.trimIndent()
    }


    suspend fun batalkanPenyesuaianStok(id: String, alasan: String, userAuthId: String) {
        val ref = firestore.collection("PenyesuaianStok").document(id)
        val doc = ref.get().await()
        if (!doc.exists()) throw IllegalStateException("Data penyesuaian tidak ditemukan")
        require(alasan.isNotBlank()) { "Alasan pembatalan wajib diisi" }
        if (doc.penyesuaianDibatalkan()) throw IllegalStateException("Penyesuaian sudah dibatalkan")

        val productId = doc.getString("idProduk").orEmpty()
        val produkRef = produkRef(productId)
        val qty = doc.getLong("jumlah") ?: 0L
        val jenis = doc.getString("jenisPenyesuaian").orEmpty().ifBlank { "KURANG" }
        @Suppress("UNCHECKED_CAST")
        val detailBatch = when {
            jenis.equals("KADALUARSA", true) -> doc.get("batchKadaluarsaDetail") as? List<Map<String, Any>> ?: emptyList()
            else -> doc.get("batchAdjustmentDetail") as? List<Map<String, Any>> ?: emptyList()
        }
        val batchRefs = detailBatch.mapNotNull { detail ->
            detail["idBatch"]?.toString()?.takeIf { it.isNotBlank() }?.let { firestore.collection("BatchStok").document(it) }
        }
        val dibuatPada = nowTimestamp()
        val user = cariPengguna(userAuthId)
        val idPembatal = user?.idDokumen ?: userAuthId
        val namaPembatal = user?.nama ?: "Pengguna"
        val kunciTanggal = doc.getString("kunciTanggal").orEmpty().ifBlank { dayKeyFromTimestamp(doc.getTimestamp("tanggalPenyesuaian")) }

        firestore.runTransaction { trx ->
            val adjSnap = trx.get(ref)
            check(adjSnap.exists()) { "Data penyesuaian tidak ditemukan" }
            check(!adjSnap.penyesuaianDibatalkan()) { "Penyesuaian sudah dibatalkan" }
            val produkSnap = trx.get(produkRef)
            check(produkSnap.exists()) { "Produk tidak ditemukan" }
            val batchSnaps = batchRefs.map { it to trx.get(it) }
            val stok = produkSnap.getLong("stokSaatIni") ?: 0L
            val stokSesudah = stok + qty

            trx.update(produkRef, mapOf("stokSaatIni" to stokSesudah, "diperbaruiPada" to dibuatPada))

            detailBatch.forEach { detail ->
                val idBatch = detail["idBatch"]?.toString().orEmpty()
                val qtyKembali = (detail["qtyKeluar"] as? Number)?.toLong()
                    ?: (detail["qtyDibuang"] as? Number)?.toLong()
                    ?: detail["qtyKeluar"]?.toString()?.toLongOrNull()
                    ?: detail["qtyDibuang"]?.toString()?.toLongOrNull()
                    ?: 0L
                val pair = batchSnaps.firstOrNull { it.first.id == idBatch }
                if (pair != null && qtyKembali > 0L) {
                    val snap = pair.second
                    val qtySisa = snap.getLong("qtySisa") ?: 0L
                    val expiryKey = snap.getString("kunciTanggalKadaluarsa").orEmpty()
                        .ifBlank { dayKeyFromTimestamp(snap.getTimestamp("tanggalKadaluarsa")) }
                    val qtyBaru = qtySisa + qtyKembali
                    trx.update(pair.first, mapOf("qtySisa" to qtyBaru, "statusBatch" to statusBatchDariTanggal(expiryKey, qtyBaru), "diperbaruiPada" to dibuatPada))
                }
            }

            trx.update(
                ref,
                mapOf(
                    "statusPenyesuaian" to "BATAL",
                    "dibatalkan" to true,
                    "alasanPembatalan" to alasan.trim(),
                    "dibatalkanOlehId" to idPembatal,
                    "dibatalkanOlehNama" to namaPembatal,
                    "dibatalkanPada" to dibuatPada,
                    "diperbaruiPada" to dibuatPada
                )
            )

            // Pembatalan adjustment mengikuti pola batal transaksi: data utama dan riwayat aslinya
            // ditandai BATAL, tanpa membuat kartu mutasi pembatalan baru.
        }.await()

        tandaiRiwayatStokDibatalkan(id, "PenyesuaianStok", alasan.trim(), idPembatal, namaPembatal, dibuatPada)
        perbaruiRingkasanHarian(kunciTanggal)
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
        timeOnly: String = "",
        productId: String,
        qty: Int,
        note: String,
        userAuthId: String
    ): String {
        require(qty > 0) { "Jumlah stok kedaluwarsa harus lebih dari 0" }

        val today = tanggalKeySaatIni()
        val ref = firestore.collection("PenyesuaianStok").document(newId("adj"))
        val produkRef = produkRef(productId)
        val user = cariPengguna(userAuthId)
        val dibuatPada = nowTimestamp()
        val kunciTanggal = dateOnly.ifBlank { Formatter.currentDateOnly() }
        val jamPenyesuaian = timeOnly.ifBlank { Formatter.currentTimeOnly() }
        val tanggalPenyesuaian = Timestamp(Formatter.parseDate("${kunciTanggal}T${jamPenyesuaian}:00"))

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

        check(expiredBatchRefs.isNotEmpty()) { "Tidak ada stok kedaluwarsa untuk produk ini" }

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

            check(sisaBuang <= 0L) { "Jumlah melebihi stok kedaluwarsa yang tersedia" }

            val stok = produkSnap.getLong("stokSaatIni") ?: 0L
            // Data lama kadang punya BatchStok kadaluarsa yang lebih besar dari stokSaatIni.
            // Buang ED tetap mengikuti batch kadaluarsa, lalu stok produk diselaraskan minimal 0 agar transaksi tidak crash.
            val nextStok = (stok - qty.toLong()).coerceAtLeast(0L)

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
                    "alasanPenyesuaian" to note.ifBlank { "Buang stok kedaluwarsa" },
                    "catatan" to note.ifBlank { "Buang stok kedaluwarsa" },
                    "batchKadaluarsaDetail" to detailBatchTerbuang,
                    "statusPenyesuaian" to "AKTIF",
                    "dibatalkan" to false,
                    "alasanPembatalan" to "",
                    "dibatalkanOlehId" to "",
                    "dibatalkanOlehNama" to "",
                    "dibatalkanPada" to null,
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
                    catatan = note.ifBlank { "Buang stok kedaluwarsa" },
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
            jenisMutasi.contains("ADJUSTMENT_TAMBAH", ignoreCase = true) -> "Penyesuaian Tambah"
            jenisMutasi.contains("ADJUSTMENT_KURANG", ignoreCase = true) -> "Penyesuaian Kurang"
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
                jenisMutasi.contains("ADJUSTMENT_TAMBAH") -> "Penyesuaian tambah"
                jenisMutasi.contains("ADJUSTMENT_KURANG") -> "Penyesuaian kurang"
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
        customerName: String = "",
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
        val totalQtyByProduct = cartItems
            .groupBy { it.productId }
            .mapValues { entry -> entry.value.sumOf { it.qty } }
        if (metode == "TUNAI" && uangDiterima < total) {
            throw IllegalArgumentException("Uang diterima kurang dari total belanja")
        }
        if (metode == "QRIS") {
            require(paymentGateway.isNotBlank()) { "Data pembayaran QRIS belum lengkap" }
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
        val batchRefsByProduct = muatBatchStokFefoRefs(totalQtyByProduct.keys)
        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()

        firestore.runTransaction { trx ->
            val localRiwayat = mutableListOf<DraftRiwayatStok>()
            val localAlokasiBatch = mutableMapOf<String, List<AlokasiBatch>>()

            // 1. Semua READ dulu
            val produkSnapshots = totalQtyByProduct.keys.associate { productId ->
                val ref = produkRef(productId)
                productId to trx.get(ref)
            }

            // 2. Validasi + siapkan draft per produk agar stok tidak dobel saat satu produk punya beberapa baris.
            totalQtyByProduct.forEach { (productId, totalQtyProduk) ->
                val snap = produkSnapshots[productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")
                val produk = produkTransaksiAman(products, productId, snap)

                val stok = snap.getLong("stokSaatIni") ?: 0L
                check(stok >= totalQtyProduk) { "Stok ${produk.name} tidak mencukupi" }
                val alokasiBatch = siapkanAlokasiBatchFefo(
                    trx = trx,
                    productId = produk.id,
                    productName = produk.name,
                    qtyDiminta = totalQtyProduk.toLong(),
                    batchRefsByProduct = batchRefsByProduct
                )
                validasiAlokasiBatchAtauStokLegacy(
                    produk = produk,
                    stokFisik = stok,
                    qtyDiminta = totalQtyProduk.toLong(),
                    alokasiBatch = alokasiBatch,
                    konteks = "jual"
                )
                localAlokasiBatch[produk.id] = alokasiBatch

                val stokSesudah = stok - totalQtyProduk.toLong()

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
                    qtyKeluar = totalQtyProduk.toLong(),
                    stokSebelum = stok,
                    stokSesudah = stokSesudah,
                    catatan = "$nomorPenjualan • ${labelMetodePembayaran(metode)}",
                    idPembuat = user?.idDokumen ?: userAuthId,
                    namaPembuat = user?.nama ?: "Kasir"
                )
            }

            // 3. Semua WRITE setelah semua read selesai
            terapkanAlokasiBatchFefo(trx, localAlokasiBatch.values.flatten(), dibuatPada)

            totalQtyByProduct.forEach { (productId, totalQtyProduk) ->
                val snap = produkSnapshots[productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")
                val stok = snap.getLong("stokSaatIni") ?: 0L
                val stokSesudah = stok - totalQtyProduk.toLong()

                trx.update(
                    produkRef(productId),
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
                    "namaPelanggan" to customerName.trim(),
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
                val snap = produkSnapshots[item.productId]
                    ?: throw IllegalStateException("Snapshot produk rincian tidak ditemukan")
                val produk = produkTransaksiAman(products, item.productId, snap)
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
        paymentAmount: Long,
        customerName: String = ""
    ): String {
        require(cartItems.isNotEmpty()) { "Keranjang masih kosong" }
        require(paymentGateway.isNotBlank()) { "Data pembayaran QRIS belum lengkap" }
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
                val snap = produkSnapshots[item.productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")
                val produk = produkTransaksiAman(products, item.productId, snap)
                val stok = snap.getLong("stokSaatIni") ?: 0L
                check(stok >= item.qty) { "Stok ${produk.name} tidak mencukupi" }
                check(stokLayakTransaksi(produk, stok) >= item.qty) {
                    "Stok layak jual ${produk.name} tidak mencukupi. Stok kedaluwarsa tidak bisa dijual."
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
                    "namaPelanggan" to customerName.trim(),
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
                val snap = produkSnapshots[item.productId]
                    ?: throw IllegalStateException("Snapshot produk rincian tidak ditemukan")
                val produk = produkTransaksiAman(products, item.productId, snap)
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

    suspend fun tandaiQrisTidakTerbayarJikaKadaluarsa(id: String): Boolean {
        val saleRef = firestore.collection("Penjualan").document(id)
        val saleDoc = saleRef.get().await()
        if (!saleDoc.exists()) return false

        val statusPenjualan = saleDoc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }
        val statusPembayaran = saleDoc.getString("statusPembayaran").orEmpty()
        if (!statusPenjualan.equals("PENDING", ignoreCase = true) &&
            !statusPembayaran.equals("PENDING_PAYMENT", ignoreCase = true)
        ) return false

        val expiresAt = saleDoc.getLong("paymentQrExpiresAtMillis") ?: 0L
        if (expiresAt <= 0L || expiresAt > System.currentTimeMillis()) return false

        val now = nowTimestamp()
        saleRef.update(
            mapOf(
                "statusPenjualan" to "TIDAK_TERBAYAR",
                "statusPembayaran" to "TIDAK_TERBAYAR",
                "statusTransaksiKasir" to "TIDAK_TERBAYAR",
                "paymentStatus" to "EXPIRED",
                "catatanPenjualan" to "QRIS kedaluwarsa dan tidak terbayar",
                "diperbaruiPada" to now
            )
        ).await()
        return true
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
                val snap = produkSnapshots[productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")
                val produk = produkTransaksiAman(products, productId, snap)
                val stok = snap.getLong("stokSaatIni") ?: 0L
                check(stok >= qty) { "Stok ${produk.name} tidak mencukupi" }

                val alokasiBatch = siapkanAlokasiBatchFefo(
                    trx = trx,
                    productId = produk.id,
                    productName = produk.name,
                    qtyDiminta = qty,
                    batchRefsByProduct = batchRefsByProduct
                )
                validasiAlokasiBatchAtauStokLegacy(
                    produk = produk,
                    stokFisik = stok,
                    qtyDiminta = qty,
                    alokasiBatch = alokasiBatch,
                    konteks = "jual"
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
        val totalQtyByProduct = draftItems
            .groupBy { it.productId }
            .mapValues { entry -> entry.value.sumOf { it.qty } }
        val nomorPenjualan = "PJL-${kunciTanggal.replace("-", "")}-${saleRef.id.takeLast(4).uppercase()}"
        val batchRefsByProduct = muatBatchStokFefoRefs(totalQtyByProduct.keys)
        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()

        firestore.runTransaction { trx ->
            val localRiwayat = mutableListOf<DraftRiwayatStok>()
            val localAlokasiBatch = mutableMapOf<String, List<AlokasiBatch>>()

            // 1. Semua READ dulu
            val produkSnapshots = totalQtyByProduct.keys.associate { productId ->
                val ref = produkRef(productId)
                productId to trx.get(ref)
            }

            // 2. Validasi + draft per produk agar rekap multi-kanal tidak mengurangi stok secara salah.
            totalQtyByProduct.forEach { (productId, totalQtyProduk) ->
                val snap = produkSnapshots[productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")
                val produk = produkTransaksiAman(products, productId, snap)

                val stok = snap.getLong("stokSaatIni") ?: 0L
                check(stok >= totalQtyProduk) { "Stok ${produk.name} tidak mencukupi" }
                val alokasiBatch = siapkanAlokasiBatchFefo(
                    trx = trx,
                    productId = produk.id,
                    productName = produk.name,
                    qtyDiminta = totalQtyProduk.toLong(),
                    batchRefsByProduct = batchRefsByProduct
                )
                validasiAlokasiBatchAtauStokLegacy(
                    produk = produk,
                    stokFisik = stok,
                    qtyDiminta = totalQtyProduk.toLong(),
                    alokasiBatch = alokasiBatch,
                    konteks = "jual"
                )
                localAlokasiBatch[produk.id] = alokasiBatch

                val stokSesudah = stok - totalQtyProduk.toLong()
                val channelLabel = draftItems.firstOrNull { it.productId == productId }?.channelLabel.orEmpty().ifBlank { "Pasar" }

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
                    qtyKeluar = totalQtyProduk.toLong(),
                    stokSebelum = stok,
                    stokSesudah = stokSesudah,
                    catatan = "$nomorPenjualan • Rekap $channelLabel",
                    idPembuat = user?.idDokumen ?: userAuthId,
                    namaPembuat = user?.nama ?: "Admin"
                )
            }

            // 3. Semua WRITE
            terapkanAlokasiBatchFefo(trx, localAlokasiBatch.values.flatten(), dibuatPada)

            totalQtyByProduct.forEach { (productId, totalQtyProduk) ->
                val snap = produkSnapshots[productId]
                    ?: throw IllegalStateException("Snapshot produk tidak ditemukan")
                val stok = snap.getLong("stokSaatIni") ?: 0L
                val stokSesudah = stok - totalQtyProduk.toLong()

                trx.update(
                    produkRef(productId),
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
                val snap = produkSnapshots[item.productId]
                    ?: throw IllegalStateException("Snapshot produk rincian tidak ditemukan")
                val produk = produkTransaksiAman(products, item.productId, snap)
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

    suspend fun muatRingkasanPenjualan(sourceFilter: String? = null): RingkasanPenjualan = coroutineScope {
        val today = Formatter.currentDateOnly()
        val snapshot = firestore.collection("Penjualan")
            .whereEqualTo("kunciTanggal", today)
            .get()
            .await()

        val dokumenHariIni = snapshot.documents.filter { doc ->
            doc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }.equals("SELESAI", ignoreCase = true) &&
                    cocokSumberPenjualan(doc.getString("sumberTransaksi"), sourceFilter)
        }

        val rows = muatRiwayatPenjualan(limit = 5, sourceFilter = sourceFilter)

        val detailQty = dokumenHariIni.mapAsyncLimited { sale ->
            totalItemPenjualan(sale)
        }

        RingkasanPenjualan(
            totalHariIni = dokumenHariIni.sumOf { it.getLong("totalBelanja") ?: 0L },
            totalKasirHariIni = dokumenHariIni
                .filter { (it.getString("sumberTransaksi") ?: "").uppercase(Locale.US) == "KASIR" }
                .sumOf { it.getLong("totalBelanja") ?: 0L },
            totalRekapHariIni = dokumenHariIni
                .filter { (it.getString("sumberTransaksi") ?: "").uppercase(Locale.US) != "KASIR" }
                .sumOf { it.getLong("totalBelanja") ?: 0L },
            jumlahTransaksiHariIni = dokumenHariIni.size,
            totalItemHariIni = detailQty.sum(),
            recentRows = rows.take(5),
            topProducts = muatProdukTerlarisPenjualanHariIni(sourceFilter = sourceFilter, limit = 5)
        )
    }

    suspend fun muatGrafikPenjualan7Hari(sourceFilter: String? = null): List<TitikGrafikPenjualan> = coroutineScope {
        val today = Formatter.currentDateOnly()
        val calendar = Calendar.getInstance().apply {
            time = Formatter.parseDate("${today}T00:00:00")
            add(Calendar.DAY_OF_MONTH, -6)
        }
        val startKey = formatTanggal.format(calendar.time)

        val keys = (0..6).map { offset ->
            Calendar.getInstance().apply {
                time = Formatter.parseDate("${startKey}T00:00:00")
                add(Calendar.DAY_OF_MONTH, offset)
            }.let { formatTanggal.format(it.time) }
        }

        val docs = runCatching {
            firestore.collection("Penjualan")
                .whereGreaterThanOrEqualTo("kunciTanggal", startKey)
                .whereLessThanOrEqualTo("kunciTanggal", today)
                .get()
                .await()
                .documents
        }.getOrElse {
            firestore.collection("Penjualan").get().await().documents
        }.filter { doc ->
            val key = kunciTanggalDoc(doc, "kunciTanggal", "tanggalPenjualan")
            key in keys &&
                    doc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }.equals("SELESAI", ignoreCase = true) &&
                    cocokSumberPenjualan(doc.getString("sumberTransaksi"), sourceFilter)
        }

        data class AgregatHari(var nominal: Long = 0L, var transaksi: Int = 0, var item: Int = 0)
        val agregat = keys.associateWith { AgregatHari() }.toMutableMap()

        docs.mapAsyncLimited { doc ->
            val key = kunciTanggalDoc(doc, "kunciTanggal", "tanggalPenjualan")
            val jumlahItem = totalItemPenjualan(doc)
            doc to jumlahItem
        }.forEach { (doc, jumlahItem) ->
            val key = kunciTanggalDoc(doc, "kunciTanggal", "tanggalPenjualan")
            val bucket = agregat[key] ?: return@forEach
            bucket.nominal += doc.getLong("totalBelanja") ?: 0L
            bucket.transaksi += 1
            bucket.item += jumlahItem
        }

        keys.map { key ->
            val bucket = agregat[key] ?: AgregatHari()
            TitikGrafikPenjualan(
                kunciTanggal = key,
                labelTanggal = Formatter.readableShortDate(key),
                totalNominal = bucket.nominal,
                totalTransaksi = bucket.transaksi,
                totalItem = bucket.item
            )
        }
    }

    suspend fun muatProdukTerlarisPenjualanHariIni(sourceFilter: String? = null, limit: Int = 5): List<ItemDashboard> = coroutineScope {
        val today = Formatter.currentDateOnly()
        val snapshot = firestore.collection("Penjualan")
            .whereEqualTo("kunciTanggal", today)
            .get()
            .await()

        data class AgregatProduk(
            val id: String,
            val nama: String,
            var qty: Int,
            var nominal: Long
        )

        val agregat = linkedMapOf<String, AgregatProduk>()
        val sales = snapshot.documents.filter { doc ->
            doc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }.equals("SELESAI", ignoreCase = true) &&
                    cocokSumberPenjualan(doc.getString("sumberTransaksi"), sourceFilter)
        }

        sales.mapAsyncLimited { saleDoc ->
            saleDoc.reference.collection("rincian").get().await().documents
        }.flatten().forEach { detail ->
            val productId = detail.getString("idProduk").orEmpty().ifBlank { detail.id }
            val namaProduk = detail.getString("namaProduk").orEmpty().ifBlank { "Produk" }
            val qty = (detail.getLong("jumlah") ?: 0L).toInt()
            val subtotal = detail.getLong("subtotal") ?: (qty.toLong() * (detail.getLong("hargaSatuan") ?: 0L))
            val current = agregat[productId]
            if (current == null) {
                agregat[productId] = AgregatProduk(productId, namaProduk, qty, subtotal)
            } else {
                current.qty += qty
                current.nominal += subtotal
            }
        }

        agregat.values
            .sortedWith(compareByDescending<AgregatProduk> { it.qty }.thenByDescending { it.nominal })
            .take(limit.coerceAtLeast(1))
            .mapIndexed { index, produk ->
                ItemDashboard(
                    id = produk.id,
                    title = produk.nama,
                    subtitle = "${Formatter.ribuan(produk.qty.toLong())} pcs terjual hari ini",
                    amount = Formatter.currency(produk.nominal),
                    badge = "Top ${index + 1}",
                    tanggalIso = today
                )
            }
    }

    suspend fun muatRingkasanKasir(kasirAuthUid: String? = null): RingkasanKasir = coroutineScope {
        val todayKey = Formatter.currentDateOnly()
        val filterKasirTertentu = kasirAuthUid != null
        val kandidatKasir = kandidatIdKasir(kasirAuthUid)
        if (filterKasirTertentu && kandidatKasir.isEmpty()) {
            return@coroutineScope RingkasanKasir(
                totalHariIni = 0L,
                jumlahTransaksiHariIni = 0,
                totalItemHariIni = 0,
                topProducts = emptyList(),
                recentRows = emptyList()
            )
        }

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

        val dokumenTerurut = salesSnapshot.documents
            .filter { cocokKasirPenjualan(it, kandidatKasir) }
            .sortedByDescending {
                (it.getTimestamp("tanggalPenjualan") ?: it.getTimestamp("dibuatPada"))?.toDate()
            }

        val hasilDetail = dokumenTerurut.mapAsyncLimited { saleDoc ->
            val detailDocs = saleDoc.reference.collection("rincian").get().await().documents
            saleDoc to detailDocs
        }

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
            totalItemHariIni = hasilDetail.sumOf { (_, detailDocs) -> detailDocs.sumOf { (it.getLong("jumlah") ?: 0L).toInt() } },
            topProducts = topProducts,
            recentRows = recentRows.take(5)
        )
    }

    suspend fun muatRiwayatPenjualan(
        limit: Long? = null,
        sourceFilter: String? = null,
        kasirAuthUid: String? = null
    ): List<ItemBarisPenjualan> = coroutineScope {
        val filterKasirTertentu = kasirAuthUid != null
        val kandidatKasir = kandidatIdKasir(kasirAuthUid)
        if (filterKasirTertentu && kandidatKasir.isEmpty()) return@coroutineScope emptyList()

        val fetchLimit = if (limit != null && (!sourceFilter.isNullOrBlank() || kandidatKasir.isNotEmpty())) {
            (limit * RECENT_FETCH_MULTIPLIER).coerceAtLeast(limit)
        } else {
            limit
        }

        val query = firestore.collection("Penjualan")
            .orderBy("dibuatPada", Query.Direction.DESCENDING)
            .let { base -> if (fetchLimit != null) base.limit(fetchLimit) else base }

        val snapshot = query.get().await()
        val docs = snapshot.documents
            .filter { doc ->
                cocokSumberPenjualan(doc.getString("sumberTransaksi"), sourceFilter) &&
                        cocokKasirPenjualan(doc, kandidatKasir)
            }
            .let { filtered -> if (limit != null) filtered.take(limit.toInt()) else filtered }

        docs.mapAsyncLimited { doc ->
            val jumlahItem = totalItemPenjualan(doc)
            val tanggalIso = isoFromTimestamp(
                doc.getTimestamp("dibuatPada") ?: doc.getTimestamp("tanggalPenjualan")
            )
            val status = doc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }

            ItemBarisPenjualan(
                id = doc.id,
                title = doc.getString("nomorPenjualan").orEmpty().ifBlank { doc.id },
                subtitle = "${Formatter.readableDateTime(tanggalIso)} • ${Formatter.ribuan(jumlahItem.toLong())} pcs",
                badge = labelSumberPenjualan(doc.getString("sumberTransaksi")),
                amount = Formatter.currency(doc.getLong("totalBelanja") ?: 0L),
                tanggalIso = tanggalIso,
                statusPenjualan = status
            )
        }
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
            "TIDAK_TERBAYAR" -> "Belum Terbayar"
            "BATAL" -> "Dibatalkan"
            else -> statusRaw
        }
        val metodeLabel = labelMetodePembayaran(saleDoc.getString("metodePembayaran"))
        val namaPelanggan = saleDoc.getString("namaPelanggan").orEmpty().trim()
        val pelangganLine = if (namaPelanggan.isNotBlank()) "Pelanggan      : $namaPelanggan\n" else ""
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
${pelangganLine}────────────────────────
DETAIL ITEM
$detailText
────────────────────────
$pembayaranText
$pembatalanBlock
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
        var riwayatDrafts: List<DraftRiwayatStok> = emptyList()

        firestore.runTransaction { trx ->
            val produkSnapshots = detailSnapshot.documents.associate { detail ->
                val productId = detail.getString("idProduk").orEmpty()
                if (productId.isBlank()) {
                    throw IllegalStateException("Produk pada rincian penjualan tidak valid")
                }
                productId to trx.get(produkRef(productId))
            }

            val reverseDrafts = mutableListOf<DraftRiwayatStok>()
            detailSnapshot.documents.forEach { detail ->
                val productId = detail.getString("idProduk").orEmpty()
                val produkSnap = produkSnapshots[productId]
                    ?: throw IllegalStateException("Produk $productId tidak ditemukan saat rollback stok")

                if (!produkSnap.exists()) {
                    throw IllegalStateException("Produk $productId tidak ditemukan saat rollback stok")
                }

                val stok = produkSnap.getLong("stokSaatIni") ?: 0L
                val qty = detail.getLong("jumlah") ?: 0L
                val stokSesudah = stok + qty
                val kodeProduk = produkSnap.getString("kodeProduk") ?: productId
                val namaProduk = produkSnap.getString("namaProduk") ?: detail.getString("namaProduk").orEmpty().ifBlank { "Produk" }

                trx.update(
                    produkRef(productId),
                    mapOf(
                        "stokSaatIni" to stokSesudah,
                        "diperbaruiPada" to dibuatPada
                    )
                )
                reverseDrafts += DraftRiwayatStok(
                    tanggalMutasi = dibuatPada,
                    kunciTanggal = dayKeyFromTimestamp(dibuatPada),
                    idProduk = productId,
                    kodeProduk = kodeProduk,
                    namaProduk = namaProduk,
                    jenisMutasi = "PEMBATALAN_PENJUALAN_MASUK",
                    sumberMutasi = "Penjualan",
                    referensiId = id,
                    qtyMasuk = qty,
                    qtyKeluar = 0L,
                    stokSebelum = stok,
                    stokSesudah = stokSesudah,
                    catatan = "Pembatalan penjualan: ${alasan.trim()}",
                    idPembuat = user?.idDokumen ?: userAuthId,
                    namaPembuat = user?.nama ?: "Pengguna"
                )
            }
            riwayatDrafts = reverseDrafts

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

        catatRiwayatStok(riwayatDrafts)
        tandaiRiwayatStokDibatalkan(id, "Penjualan", alasan.trim(), user?.idDokumen ?: userAuthId, user?.nama ?: "Pengguna", dibuatPada)
        perbaruiRingkasanHarian(kunciTanggal)
    }

    suspend fun hapusPenjualan(id: String) {
        batalkanPenjualan(id, "Dibatalkan dari riwayat penjualan", "")
    }

    suspend fun muatPengeluaranById(id: String): Pengeluaran? {
        if (id.isBlank()) return null
        val doc = firestore.collection("Pengeluaran").document(id).get().await()
        if (!doc.exists()) return null
        val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalPengeluaran") ?: doc.getTimestamp("dibuatPada"))
        return Pengeluaran(
            id = doc.id,
            date = tanggalIso,
            category = doc.getString("kategori").orEmpty().ifBlank { "Operasional" },
            amount = doc.getLong("nominal") ?: 0L,
            note = doc.getString("catatan").orEmpty(),
            createdBy = doc.getString("dibuatOlehId").orEmpty()
        )
    }

    suspend fun simpanPengeluaran(
        existingId: String?,
        dateOnly: String,
        timeOnly: String = "",
        category: String,
        amount: Long,
        note: String,
        userAuthId: String
    ): String {
        require(dateOnly.isNotBlank()) { "Tanggal pengeluaran wajib diisi" }
        require(category.trim().isNotBlank()) { "Nama pengeluaran wajib diisi" }
        require(amount > 0L) { "Nominal pengeluaran harus lebih dari 0" }

        val ref = if (existingId.isNullOrBlank()) {
            firestore.collection("Pengeluaran").document(newId("exp"))
        } else {
            firestore.collection("Pengeluaran").document(existingId)
        }
        val existingDoc = if (existingId.isNullOrBlank()) null else ref.get().await()
        val oldDateKey = existingDoc?.takeIf { it.exists() }?.let {
            kunciTanggalDoc(it, "kunciTanggal", "tanggalPengeluaran")
        }.orEmpty()

        val user = cariPengguna(userAuthId)
        val dibuatPada = existingDoc?.takeIf { it.exists() }?.getTimestamp("dibuatPada") ?: nowTimestamp()
        val diperbaruiPada = nowTimestamp()
        val inputTanggal = dateOnly.trim()
        val kunciTanggal = normalisasiKunciTanggal(inputTanggal).ifBlank { Formatter.currentDateOnly() }
        val jamDariInput = inputTanggal.substringAfter("T", "").take(5).takeIf { Regex("\\d{2}:\\d{2}").matches(it) }
        val jam = timeOnly.trim().take(5).takeIf { Regex("\\d{2}:\\d{2}").matches(it) }
            ?: jamDariInput
            ?: Formatter.currentTimeOnly()
        val tanggalPengeluaran = Timestamp(Formatter.parseDate("${kunciTanggal}T${jam}:00"))

        ref.set(
            mapOf(
                "tanggalPengeluaran" to tanggalPengeluaran,
                "kunciTanggal" to kunciTanggal,
                "kategori" to category.trim(),
                "nominal" to amount,
                "catatan" to note.trim(),
                "dibuatOlehId" to (user?.idDokumen ?: userAuthId),
                "dibuatOlehNama" to (user?.nama ?: "Pengguna"),
                "dibuatPada" to dibuatPada,
                "diperbaruiPada" to diperbaruiPada
            )
        ).await()

        perbaruiRingkasanHarian(kunciTanggal)
        if (oldDateKey.isNotBlank() && oldDateKey != kunciTanggal) {
            perbaruiRingkasanHarian(oldDateKey)
        }
        return ref.id
    }

    suspend fun muatRiwayatPengeluaran(): List<ItemBarisPengeluaran> {
        val snapshot = firestore.collection("Pengeluaran")
            .get()
            .await()

        return snapshot.documents.filter { !it.dataDihapus() }.map { doc ->
            val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalPengeluaran") ?: doc.getTimestamp("dibuatPada"))
            val kategori = doc.getString("kategori").orEmpty().ifBlank { "Operasional" }
            val nominal = doc.getLong("nominal") ?: 0L
            val pembuat = doc.getString("dibuatOlehNama").orEmpty().ifBlank { "Pengguna" }
            val catatan = doc.getString("catatan").orEmpty()

            ItemBarisPengeluaran(
                id = doc.id,
                title = kategori,
                subtitle = "${Formatter.readableDateTime(tanggalIso)} • $pembuat",
                badge = "Pengeluaran",
                amount = "-${Formatter.currency(nominal)}",
                tanggalIso = tanggalIso,
                kategori = kategori,
                nominal = nominal,
                catatan = catatan
            )
        }.sortedByDescending { Formatter.parseDate(it.tanggalIso) }
    }

    suspend fun buildExpenseDetailText(id: String): String {
        val doc = firestore.collection("Pengeluaran").document(id).get().await()
        if (!doc.exists()) return "Detail pengeluaran tidak ditemukan"

        val tanggal = isoFromTimestamp(doc.getTimestamp("tanggalPengeluaran") ?: doc.getTimestamp("dibuatPada"))
        val kategori = doc.getString("kategori").orEmpty().ifBlank { "Operasional" }
        val nominal = doc.getLong("nominal") ?: 0L
        val catatan = doc.getString("catatan").orEmpty().ifBlank { "-" }
        val pembuat = doc.getString("dibuatOlehNama").orEmpty().ifBlank { "-" }

        return """
            DETAIL PENGELUARAN
            ID: ${doc.id}
            Tanggal: ${Formatter.readableDateTime(tanggal)}
            Nama Pengeluaran: $kategori
            Nominal: ${Formatter.currency(nominal)}
            Catatan: $catatan
            Dicatat Oleh: $pembuat
        """.trimIndent()
    }

    suspend fun hapusPengeluaran(id: String) {
        val ref = firestore.collection("Pengeluaran").document(id)
        val doc = ref.get().await()
        if (!doc.exists()) throw IllegalStateException("Data pengeluaran tidak ditemukan")
        val dateKey = kunciTanggalDoc(doc, "kunciTanggal", "tanggalPengeluaran")
        ref.update(
            mapOf(
                "dihapus" to true,
                "aktif" to false,
                "dihapusPada" to nowTimestamp(),
                "diperbaruiPada" to nowTimestamp()
            )
        ).await()
        perbaruiRingkasanHarian(dateKey)
    }

    suspend fun muatLaporan(rangeKey: String): RingkasanLaporanFirebase = coroutineScope {
        val normalizedRange = rangeKeyNormal(rangeKey)

        val salesAll = ambilDokumenKunciTanggal("Penjualan", "kunciTanggal", "tanggalPenjualan", normalizedRange)
        val expenses = ambilDokumenKunciTanggal("Pengeluaran", "kunciTanggal", "tanggalPengeluaran", normalizedRange)
            .filter { !it.dataDihapus() }
        val production = ambilDokumenKunciTanggal("CatatanProduksi", "kunciTanggal", "tanggalProduksi", normalizedRange)
            .filter { !it.produksiDibatalkan() && !it.dataDihapus() }

        val sales = salesAll.filter { doc ->
            doc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }.equals("SELESAI", true)
        }

        data class AgregatProduk(
            val id: String,
            var nama: String,
            var qty: Int,
            var nominal: Long
        )

        val detailSales = sales.mapAsyncLimited { saleDoc ->
            saleDoc to saleDoc.reference.collection("rincian").get().await().documents
        }

        val produkMap = linkedMapOf<String, AgregatProduk>()
        var totalItemTerjual = 0
        detailSales.forEach { (_, detailDocs) ->
            detailDocs.forEach { item ->
                val productId = item.getString("idProduk").orEmpty().ifBlank { item.id }
                val namaProduk = item.getString("namaProduk").orEmpty().ifBlank { "Produk" }
                val qty = (item.getLong("jumlah") ?: 0L).toInt()
                val subtotal = item.getLong("subtotal") ?: (qty.toLong() * (item.getLong("hargaSatuan") ?: 0L))
                totalItemTerjual += qty

                val current = produkMap[productId]
                if (current == null) {
                    produkMap[productId] = AgregatProduk(productId, namaProduk, qty, subtotal)
                } else {
                    current.qty += qty
                    current.nominal += subtotal
                }
            }
        }

        val kategoriMap = linkedMapOf<String, Long>()
        expenses.forEach { doc ->
            val kategori = doc.getString("kategori").orEmpty().ifBlank { "Operasional" }
            kategoriMap[kategori] = (kategoriMap[kategori] ?: 0L) + (doc.getLong("nominal") ?: 0L)
        }

        val produkTerlaris = produkMap.values
            .sortedWith(compareByDescending<AgregatProduk> { it.qty }.thenByDescending { it.nominal })
            .take(8)
            .map {
                ItemAnalitikLaporan(
                    id = it.id,
                    title = it.nama,
                    subtitle = "${Formatter.ribuan(it.qty.toLong())} pcs terjual",
                    amount = Formatter.currency(it.nominal),
                    badge = "Produk",
                    qty = it.qty,
                    nominal = it.nominal
                )
            }

        val kategoriPengeluaran = kategoriMap.entries
            .sortedByDescending { it.value }
            .take(8)
            .map { entry ->
                ItemAnalitikLaporan(
                    id = entry.key,
                    title = entry.key,
                    subtitle = "Nama pengeluaran",
                    amount = "-${Formatter.currency(entry.value)}",
                    badge = "Biaya",
                    nominal = entry.value
                )
            }

        val totalPenjualan = sales.sumOf { it.getLong("totalBelanja") ?: 0L }
        val totalPengeluaran = expenses.sumOf { it.getLong("nominal") ?: 0L }
        val totalProduksi = production.sumOf { (it.getLong("jumlahHasil") ?: 0L).toInt() }

        RingkasanLaporanFirebase(
            rangeKey = normalizedRange,
            rangeLabel = labelRentang(normalizedRange),
            totalPenjualan = totalPenjualan,
            totalPengeluaran = totalPengeluaran,
            totalProduksi = totalProduksi,
            totalTransaksi = sales.size,
            totalItemTerjual = totalItemTerjual,
            labaRugi = totalPenjualan - totalPengeluaran,
            produkTerlaris = produkTerlaris,
            kategoriPengeluaran = kategoriPengeluaran,
            transaksiTerbaru = muatRiwayatTransaksi(normalizedRange, limit = 10)
        )
    }

    suspend fun muatRiwayatTransaksi(rangeKey: String = "semua", limit: Int? = null): List<BarisRiwayatTransaksi> = coroutineScope {
        val normalizedRange = rangeKeyNormal(rangeKey)
        val rows = mutableListOf<BarisRiwayatTransaksi>()

        val salesSnapshot = ambilDokumenKunciTanggal("Penjualan", "kunciTanggal", "tanggalPenjualan", normalizedRange, limit)
        val salesRows = salesSnapshot.mapAsyncLimited { doc ->
            val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalPenjualan") ?: doc.getTimestamp("dibuatPada"))
            val detailQty = totalItemPenjualan(doc)
            val status = doc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }
            val source = labelSumberPenjualan(doc.getString("sumberTransaksi"))
            val userId = doc.getString("idKasir").orEmpty()
                .ifBlank { doc.getString("dibuatOlehId").orEmpty() }
            val userName = doc.getString("namaKasir").orEmpty()
                .ifBlank { doc.getString("dibuatOlehNama").orEmpty() }
                .ifBlank { if (source.equals("Pasar", true)) "Admin" else "Kasir" }
            BarisRiwayatTransaksi(
                id = doc.id,
                jenis = "Penjualan",
                title = doc.getString("nomorPenjualan").orEmpty().ifBlank { doc.id },
                subtitle = "${Formatter.readableDateTime(tanggalIso)} • ${Formatter.ribuan(detailQty.toLong())} pcs • $source • $userName",
                amount = Formatter.currency(doc.getLong("totalBelanja") ?: 0L),
                badge = source,
                tanggalIso = tanggalIso,
                status = when (status.uppercase()) {
                    "BATAL" -> "Batal"
                    "PENDING" -> "Pending"
                    "TIDAK_TERBAYAR" -> "Belum Terbayar"
                    else -> "Selesai"
                },
                userId = userId,
                userName = userName
            )
        }
        rows.addAll(salesRows)

        ambilDokumenKunciTanggal("CatatanProduksi", "kunciTanggal", "tanggalProduksi", normalizedRange, limit).forEach { doc ->
            if (doc.dataDihapus()) return@forEach
            val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalProduksi") ?: doc.getTimestamp("dibuatPada"))
            val dateKey = kunciTanggalDoc(doc, "kunciTanggal", "tanggalProduksi")
            if (!dalamRentangKunciTanggal(dateKey, normalizedRange)) return@forEach
            val batal = doc.produksiDibatalkan()
            val jenis = doc.getString("jenisProduksi").orEmpty()
            val isOlahan = jenis.equals("OLAHAN", true)
            val title = if (isOlahan) {
                "${doc.getString("namaProdukAsal").orEmpty().ifBlank { "Bahan" }} → ${doc.getString("namaProdukHasil").orEmpty().ifBlank { "Hasil" }}"
            } else {
                doc.getString("namaProdukHasil").orEmpty().ifBlank { "Produksi Dasar" }
            }
            val qty = doc.getLong("jumlahHasil") ?: 0L
            val userId = doc.getString("dibuatOlehId").orEmpty()
            val userName = doc.getString("dibuatOlehNama").orEmpty().ifBlank { "Pengguna" }
            rows += BarisRiwayatTransaksi(
                id = doc.id,
                jenis = if (isOlahan) "Produk Olahan" else "Produksi",
                title = title,
                subtitle = "${Formatter.readableDateTime(tanggalIso)} • $userName" + if (batal) " • DIBATALKAN" else "",
                amount = if (batal) "BATAL" else "+${Formatter.ribuan(qty)} ${doc.getString("satuanHasil").orEmpty().ifBlank { "pcs" }}",
                badge = if (isOlahan) "Produk Olahan" else "Produksi",
                tanggalIso = tanggalIso,
                status = if (batal) "Batal" else "Selesai",
                userId = userId,
                userName = userName
            )
        }

        ambilDokumenKunciTanggal("Pengeluaran", "kunciTanggal", "tanggalPengeluaran", normalizedRange, limit).forEach { doc ->
            if (doc.dataDihapus()) return@forEach
            val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalPengeluaran") ?: doc.getTimestamp("dibuatPada"))
            val dateKey = kunciTanggalDoc(doc, "kunciTanggal", "tanggalPengeluaran")
            if (!dalamRentangKunciTanggal(dateKey, normalizedRange)) return@forEach
            val kategori = doc.getString("kategori").orEmpty().ifBlank { "Operasional" }
            val userId = doc.getString("dibuatOlehId").orEmpty()
            val userName = doc.getString("dibuatOlehNama").orEmpty().ifBlank { "Pengguna" }
            rows += BarisRiwayatTransaksi(
                id = doc.id,
                jenis = "Pengeluaran",
                title = kategori,
                subtitle = "${Formatter.readableDateTime(tanggalIso)} • $userName",
                amount = "-${Formatter.currency(doc.getLong("nominal") ?: 0L)}",
                badge = "Pengeluaran",
                tanggalIso = tanggalIso,
                status = "Tercatat",
                userId = userId,
                userName = userName
            )
        }

        ambilDokumenKunciTanggal("PenyesuaianStok", "kunciTanggal", "tanggalPenyesuaian", normalizedRange, limit).forEach { doc ->
            val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalPenyesuaian") ?: doc.getTimestamp("dibuatPada"))
            val dateKey = kunciTanggalDoc(doc, "kunciTanggal", "tanggalPenyesuaian")
            if (!dalamRentangKunciTanggal(dateKey, normalizedRange)) return@forEach
            val batal = doc.penyesuaianDibatalkan()
            val jenisAdjustment = doc.getString("jenisPenyesuaian").orEmpty().ifBlank { "KURANG" }
            val userId = doc.getString("idPembuat").orEmpty()
                .ifBlank { doc.getString("dibuatOlehId").orEmpty() }
            val userName = doc.getString("namaPembuat").orEmpty()
                .ifBlank { doc.getString("dibuatOlehNama").orEmpty() }
                .ifBlank { "Pengguna" }
            rows += BarisRiwayatTransaksi(
                id = doc.id,
                jenis = "Penyesuaian",
                title = doc.getString("namaProduk").orEmpty().ifBlank { "Produk" },
                subtitle = "${Formatter.readableDateTime(tanggalIso)} • $jenisAdjustment • $userName" + if (batal) " • DIBATALKAN" else "",
                amount = if (batal) "BATAL" else Formatter.ribuan(doc.getLong("jumlah") ?: 0L),
                badge = "Penyesuaian",
                tanggalIso = tanggalIso,
                status = if (batal) "Batal" else jenisAdjustment.lowercase(Locale.US).replaceFirstChar { it.uppercase() },
                userId = userId,
                userName = userName
            )
        }

        rows.sortedByDescending { Formatter.parseDate(it.tanggalIso) }
            .let { sorted -> if (limit != null) sorted.take(limit) else sorted }
    }

    suspend fun buildLaporanCsv(rangeKey: String): String {
        val laporan = muatLaporan(rangeKey)
        val transaksi = muatRiwayatTransaksi(laporan.rangeKey)
        return buildString {
            appendLine("Laporan SI Tahu,${csvEscape(laporan.rangeLabel)}")
            appendLine("Kategori,Nilai")
            appendLine("Penjualan,${laporan.totalPenjualan}")
            appendLine("Pengeluaran,${laporan.totalPengeluaran}")
            appendLine("Laba/Rugi,${laporan.labaRugi}")
            appendLine("Produksi,${laporan.totalProduksi}")
            appendLine("Transaksi Penjualan,${laporan.totalTransaksi}")
            appendLine("Item Terjual,${laporan.totalItemTerjual}")
            appendLine()
            appendLine("ID,Tanggal,Jenis,Judul,Status,Nominal")
            transaksi.forEach {
                appendLine(listOf(it.id, Formatter.readableDateTime(it.tanggalIso), it.jenis, it.title, it.status, it.amount).joinToString(",") { value -> csvEscape(value) })
            }
        }
    }

    suspend fun buildLaporanExcelXml(rangeKey: String): String {
        val laporan = muatLaporan(rangeKey)
        val transaksi = muatRiwayatTransaksi(laporan.rangeKey)

        fun row(vararg cells: String): String {
            return cells.joinToString(prefix = "<Row>", postfix = "</Row>") { cell ->
                "<Cell><Data ss:Type=\"String\">${xmlEscape(cell)}</Data></Cell>"
            }
        }

        return buildString {
            appendLine("<?xml version=\"1.0\"?>")
            appendLine("<?mso-application progid=\"Excel.Sheet\"?>")
            appendLine("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:o=\"urn:schemas-microsoft-com:office:office\" xmlns:x=\"urn:schemas-microsoft-com:office:excel\" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:html=\"http://www.w3.org/TR/REC-html40\">")
            appendLine("<Worksheet ss:Name=\"Ringkasan\"><Table>")
            appendLine(row("Laporan SI Tahu", laporan.rangeLabel))
            appendLine(row("Penjualan", laporan.totalPenjualan.toString()))
            appendLine(row("Pengeluaran", laporan.totalPengeluaran.toString()))
            appendLine(row("Laba/Rugi", laporan.labaRugi.toString()))
            appendLine(row("Produksi", laporan.totalProduksi.toString()))
            appendLine(row("Transaksi Penjualan", laporan.totalTransaksi.toString()))
            appendLine(row("Item Terjual", laporan.totalItemTerjual.toString()))
            appendLine(row(""))
            appendLine(row("Produk Terlaris Hari Ini", "Qty", "Nominal"))
            laporan.produkTerlaris.forEach { appendLine(row(it.title, it.qty.toString(), it.nominal.toString())) }
            appendLine(row(""))
            appendLine(row("Nama Pengeluaran", "Nominal"))
            laporan.kategoriPengeluaran.forEach { appendLine(row(it.title, it.nominal.toString())) }
            appendLine("</Table></Worksheet>")
            appendLine("<Worksheet ss:Name=\"Riwayat\"><Table>")
            appendLine(row("ID", "Tanggal", "Jenis", "Judul", "Badge", "Status", "Nominal"))
            transaksi.forEach { appendLine(row(it.id, Formatter.readableDateTime(it.tanggalIso), it.jenis, it.title, it.badge, it.status, it.amount)) }
            appendLine("</Table></Worksheet>")
            appendLine("</Workbook>")
        }
    }

    private data class BarisBukuHarianExport(
        val tanggalIso: String,
        val uraian: String,
        val user: String,
        val debit: Long,
        val kredit: Long
    )

    private data class BukuHarianExportData(
        val label: String,
        val rows: List<BarisBukuHarianExport>,
        val saldoAwal: Long
    )

    private data class BarisMutasiStokExport(
        val tanggalIso: String,
        val uraian: String,
        val user: String,
        val masuk: Long,
        val keluar: Long,
        val saldo: Long,
        val catatan: String
    )

    private fun nominalMutasiExport(value: Long): String {
        return if (value == 0L) "0,00" else "${Formatter.ribuan(value)},00"
    }

    private fun excelA4LandscapeFitOptions(): String = """
<WorksheetOptions xmlns="urn:schemas-microsoft-com:office:excel">
  <PageSetup>
    <Layout x:Orientation="Landscape"/>
    <PaperSizeIndex>9</PaperSizeIndex>
  </PageSetup>
  <FitToPage/>
  <Print>
    <FitWidth>1</FitWidth>
    <FitHeight>0</FitHeight>
    <LeftMargin>0.35</LeftMargin>
    <RightMargin>0.35</RightMargin>
    <TopMargin>0.45</TopMargin>
    <BottomMargin>0.45</BottomMargin>
    <ValidPrinterInfo/>
  </Print>
</WorksheetOptions>
""".trimIndent()

    private fun safeWorksheetName(raw: String, fallback: String): String {
        val cleaned = raw.ifBlank { fallback }
            .replace(Regex("[\\[\\]\\*\\?/\\:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { fallback }
        return cleaned.take(31)
    }


    private fun cleanXmlValue(value: String): String {
        return buildString(value.length) {
            value.forEach { char ->
                val code = char.toInt()
                if (char == '\t' || char == '\n' || char == '\r' || code >= 32) {
                    append(char)
                }
            }
        }
    }

    private fun xlsxEscape(value: String): String = xmlEscape(cleanXmlValue(value))

    private fun xlsxColumnName(indexZeroBased: Int): String {
        var value = indexZeroBased + 1
        val builder = StringBuilder()
        while (value > 0) {
            val remainder = (value - 1) % 26
            builder.insert(0, ('A'.toInt() + remainder).toChar())
            value = (value - 1) / 26
        }
        return builder.toString()
    }

    private fun addZipText(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildXlsxWorkbook(sheets: List<Pair<String, List<List<String>>>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val sheetCount = sheets.size.coerceAtLeast(1)
            val contentTypesSheets = (1..sheetCount).joinToString("\n") { index ->
                "<Override PartName=\"/xl/worksheets/sheet$index.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            }
            addZipText(zip, "[Content_Types].xml", """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
$contentTypesSheets
</Types>
""".trimIndent())

            addZipText(zip, "_rels/.rels", """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>
""".trimIndent())

            addZipText(zip, "docProps/app.xml", """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
<Application>SI Tahu</Application>
</Properties>
""".trimIndent())

            addZipText(zip, "docProps/core.xml", """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<dc:creator>SI Tahu</dc:creator>
<cp:lastModifiedBy>SI Tahu</cp:lastModifiedBy>
</cp:coreProperties>
""".trimIndent())

            val workbookSheets = sheets.mapIndexed { index, pair ->
                val safeName = safeWorksheetName(pair.first, "Sheet ${index + 1}")
                "<sheet name=\"${xlsxEscape(safeName)}\" sheetId=\"${index + 1}\" r:id=\"rId${index + 1}\"/>"
            }.joinToString("\n")
            addZipText(zip, "xl/workbook.xml", """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
$workbookSheets
</sheets>
</workbook>
""".trimIndent())

            val relationships = sheets.mapIndexed { index, _ ->
                "<Relationship Id=\"rId${index + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${index + 1}.xml\"/>"
            }.joinToString("\n") + "\n<Relationship Id=\"rId${sheets.size + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            addZipText(zip, "xl/_rels/workbook.xml.rels", """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$relationships
</Relationships>
""".trimIndent())

            addZipText(zip, "xl/styles.xml", """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font><sz val="10"/><name val="Calibri"/></font><font><b/><sz val="10"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>
""".trimIndent())

            sheets.forEachIndexed { sheetIndex, pair ->
                val rows = pair.second
                val maxColumns = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                val columnXml = (0 until maxColumns).joinToString("\n") { columnIndex ->
                    val maxTextLength = rows.mapNotNull { it.getOrNull(columnIndex) }.maxOfOrNull { it.length } ?: 10
                    val width = maxTextLength.coerceIn(8, 42)
                    "<col min=\"${columnIndex + 1}\" max=\"${columnIndex + 1}\" width=\"$width\" customWidth=\"1\"/>"
                }
                val rowXml = rows.mapIndexed { rowIndex, row ->
                    val cellXml = row.mapIndexed { columnIndex, value ->
                        val ref = "${xlsxColumnName(columnIndex)}${rowIndex + 1}"
                        val style = if (rowIndex == 0 || value.startsWith("BUKU HARIAN") || value.startsWith("EXPORT") || value.startsWith("MUTASI")) " s=\"1\"" else ""
                        "<c r=\"$ref\" t=\"inlineStr\"$style><is><t xml:space=\"preserve\">${xlsxEscape(value)}</t></is></c>"
                    }.joinToString("")
                    "<row r=\"${rowIndex + 1}\">$cellXml</row>"
                }.joinToString("\n")
                addZipText(zip, "xl/worksheets/sheet${sheetIndex + 1}.xml", """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheetViews><sheetView workbookViewId="0"/></sheetViews>
<sheetFormatPr defaultRowHeight="18"/>
<cols>
$columnXml
</cols>
<sheetData>
$rowXml
</sheetData>
<pageMargins left="0.35" right="0.35" top="0.45" bottom="0.45" header="0.2" footer="0.2"/>
<pageSetup paperSize="9" orientation="landscape" fitToWidth="1" fitToHeight="0"/>
</worksheet>
""".trimIndent())
            }
        }
        return output.toByteArray()
    }

    suspend fun buildBukuHarianXlsx(rangeKey: String): ByteArray {
        val data = muatBarisBukuHarianExport(rangeKey)
        var saldo = data.saldoAwal
        val sheetRows = mutableListOf<List<String>>()
        sheetRows += listOf("BUKU HARIAN SI TAHU")
        sheetRows += listOf("Tanggal Laporan", Formatter.readableDateTime(formatIso.format(Date())))
        sheetRows += listOf("Periode Transaksi", data.label)
        sheetRows += listOf("Jenis Data", "Pemasukan penjualan dan pengeluaran")
        sheetRows += listOf("Saldo Awal", nominalMutasiExport(data.saldoAwal))
        sheetRows += emptyList<String>()
        sheetRows += listOf("Tanggal Transaksi", "Uraian Transaksi", "User", "Debit/Pengeluaran", "Kredit/Pemasukan", "Saldo")
        data.rows.forEach { item ->
            saldo += item.kredit - item.debit
            sheetRows += listOf(
                Formatter.readableDateTime(item.tanggalIso),
                item.uraian,
                item.user,
                nominalMutasiExport(item.debit),
                nominalMutasiExport(item.kredit),
                nominalMutasiExport(saldo)
            )
        }
        sheetRows += emptyList<String>()
        sheetRows += listOf("Saldo Akhir", nominalMutasiExport(saldo))
        return buildXlsxWorkbook(listOf("Buku Harian" to sheetRows))
    }

    suspend fun buildStokProdukXlsx(rangeKey: String): ByteArray {
        val (label, produk, grouped) = muatMutasiStokExport(rangeKey)
        val sheets = mutableListOf<Pair<String, List<List<String>>>>()
        val summaryRows = mutableListOf<List<String>>()
        summaryRows += listOf("EXPORT STOK PRODUK SI TAHU")
        summaryRows += listOf("Tanggal Laporan", Formatter.readableDateTime(formatIso.format(Date())))
        summaryRows += listOf("Periode Mutasi", label)
        summaryRows += emptyList<String>()
        summaryRows += listOf("Kode", "Nama Produk", "Kategori", "Stok Fisik", "Layak Jual", "ED Hari Ini", "Hampir Kedaluwarsa", "Kedaluwarsa", "Stok Minimum")
        produk.forEach { item ->
            summaryRows += listOf(
                item.code,
                item.name,
                item.category,
                item.stock.toString(),
                (item.safeStock + item.nearExpiredStock + item.edTodayStock).toString(),
                item.edTodayStock.toString(),
                item.nearExpiredStock.toString(),
                item.expiredStock.toString(),
                item.minStock.toString()
            )
        }
        sheets += "Ringkasan Stok" to summaryRows

        val usedSheetNames = mutableSetOf("ringkasan stok")
        fun uniqueSheetName(base: String, fallback: String): String {
            val first = safeWorksheetName(base, fallback)
            var candidate = first
            var index = 2
            while (!usedSheetNames.add(candidate.lowercase(Locale.US))) {
                val suffix = " $index"
                candidate = safeWorksheetName(first.take((31 - suffix.length).coerceAtLeast(1)) + suffix, fallback)
                index++
            }
            return candidate
        }

        produk.forEachIndexed { index, item ->
            val rows = mutableListOf<List<String>>()
            rows += listOf("MUTASI STOK PRODUK")
            rows += listOf("Produk", item.name)
            rows += listOf("Kode/Kategori", "${item.code} / ${item.category}")
            rows += listOf("Periode", label)
            rows += listOf("Stok Fisik Saat Ini", item.stock.toString())
            rows += listOf("Layak Jual", (item.safeStock + item.nearExpiredStock + item.edTodayStock).toString())
            rows += listOf("Rincian ED", "ED Hari Ini ${item.edTodayStock}, Hampir Kedaluwarsa ${item.nearExpiredStock}, Kedaluwarsa ${item.expiredStock}")
            rows += emptyList<String>()
            rows += listOf("Tanggal Transaksi", "Uraian Transaksi", "User", "Masuk", "Keluar", "Saldo", "Catatan")
            val mutasi = grouped[item.id].orEmpty()
            if (mutasi.isEmpty()) {
                rows += listOf("Belum ada mutasi stok pada periode ini.")
            } else {
                mutasi.forEach { baris ->
                    rows += listOf(
                        Formatter.readableDateTime(baris.tanggalIso),
                        baris.uraian,
                        baris.user,
                        Formatter.ribuan(baris.masuk),
                        Formatter.ribuan(baris.keluar),
                        Formatter.ribuan(baris.saldo),
                        baris.catatan
                    )
                }
            }
            sheets += uniqueSheetName(item.name, "Produk ${index + 1}") to rows
        }
        return buildXlsxWorkbook(sheets)
    }

    private suspend fun hitungSaldoAwalBukuHarian(normalizedRange: String): Long {
        val startKey = awalRentangKunciTanggal(normalizedRange) ?: return 0L
        if (rangeKeyNormal(normalizedRange) == "semua") return 0L

        val salesDocs = runCatching { firestore.collection("Penjualan").get().await().documents }.getOrElse { emptyList() }
        val expenseDocs = runCatching { firestore.collection("Pengeluaran").get().await().documents }.getOrElse { emptyList() }

        val pemasukanSebelumnya = salesDocs
            .filter { doc -> !doc.dataDihapus() }
            .filter { doc -> kunciTanggalDoc(doc, "kunciTanggal", "tanggalPenjualan") < startKey }
            .filter { doc -> doc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }.equals("SELESAI", true) }
            .sumOf { doc -> doc.getLong("totalBelanja") ?: 0L }

        val pengeluaranSebelumnya = expenseDocs
            .filter { doc -> !doc.dataDihapus() }
            .filter { doc -> kunciTanggalDoc(doc, "kunciTanggal", "tanggalPengeluaran") < startKey }
            .sumOf { doc -> doc.getLong("nominal") ?: 0L }

        return pemasukanSebelumnya - pengeluaranSebelumnya
    }

    private suspend fun muatBarisBukuHarianExport(rangeKey: String): BukuHarianExportData {
        val normalizedRange = rangeKeyNormal(rangeKey)
        val salesAll = ambilDokumenKunciTanggal("Penjualan", "kunciTanggal", "tanggalPenjualan", normalizedRange)
        val expenses = ambilDokumenKunciTanggal("Pengeluaran", "kunciTanggal", "tanggalPengeluaran", normalizedRange)
            .filter { !it.dataDihapus() }

        val rows = mutableListOf<BarisBukuHarianExport>()

        salesAll
            .filter { doc -> !doc.dataDihapus() }
            .filter { doc -> doc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }.equals("SELESAI", true) }
            .forEach { doc ->
                val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalPenjualan") ?: doc.getTimestamp("dibuatPada"))
                val nomor = doc.getString("nomorPenjualan").orEmpty().ifBlank { doc.id }
                val sumber = labelSumberPenjualan(doc.getString("sumberTransaksi"))
                val metode = labelMetodePembayaran(doc.getString("metodePembayaran"))
                val pelanggan = doc.getString("namaPelanggan").orEmpty().trim()
                val user = doc.getString("namaKasir").orEmpty()
                    .ifBlank { doc.getString("dibuatOlehNama").orEmpty() }
                    .ifBlank { if (sumber.equals("Pasar", true)) "Admin" else "Kasir" }
                rows += BarisBukuHarianExport(
                    tanggalIso = tanggalIso,
                    uraian = buildString {
                        append("Penjualan $sumber $nomor ($metode)")
                        if (pelanggan.isNotBlank()) append(" - Pelanggan: $pelanggan")
                    },
                    user = user,
                    debit = 0L,
                    kredit = doc.getLong("totalBelanja") ?: 0L
                )
            }

        expenses.forEach { doc ->
            val tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalPengeluaran") ?: doc.getTimestamp("dibuatPada"))
            val kategori = doc.getString("kategori").orEmpty().ifBlank { "Pengeluaran" }
            val catatan = doc.getString("catatan").orEmpty()
            val user = doc.getString("dibuatOlehNama").orEmpty().ifBlank { "Pengguna" }
            rows += BarisBukuHarianExport(
                tanggalIso = tanggalIso,
                uraian = if (catatan.isBlank()) "Pengeluaran $kategori" else "Pengeluaran $kategori - $catatan",
                user = user,
                debit = doc.getLong("nominal") ?: 0L,
                kredit = 0L
            )
        }

        return BukuHarianExportData(
            label = labelPeriodeTanggal(normalizedRange),
            rows = rows.sortedBy { Formatter.parseDate(it.tanggalIso) },
            saldoAwal = hitungSaldoAwalBukuHarian(normalizedRange)
        )
    }

    private suspend fun muatMutasiStokExport(rangeKey: String): Triple<String, List<Produk>, Map<String, List<BarisMutasiStokExport>>> {
        val normalizedRange = rangeKeyNormal(rangeKey)
        val produk = muatSemuaProduk().filter { it.active && !it.deleted }
            .sortedBy { it.name.lowercase(Locale.US) }
        val produkMap = produk.associateBy { it.id }
        val docs = ambilDokumenKunciTanggal("RiwayatStok", "kunciTanggal", "tanggalMutasi", normalizedRange)

        val grouped = docs.groupBy { doc -> doc.getString("idProduk").orEmpty() }
            .mapValues { (_, items) ->
                items.sortedBy { doc -> Formatter.parseDate(isoFromTimestamp(doc.getTimestamp("tanggalMutasi") ?: doc.getTimestamp("dibuatPada"))) }
                    .map { doc ->
                        val jenis = doc.getString("jenisMutasi").orEmpty().ifBlank { "Mutasi" }
                        val sumber = doc.getString("sumberMutasi").orEmpty()
                        BarisMutasiStokExport(
                            tanggalIso = isoFromTimestamp(doc.getTimestamp("tanggalMutasi") ?: doc.getTimestamp("dibuatPada")),
                            uraian = if (sumber.isBlank()) jenis else "$jenis - $sumber",
                            user = doc.getString("namaPembuat").orEmpty().ifBlank { "Pengguna" },
                            masuk = doc.getLong("qtyMasuk") ?: 0L,
                            keluar = doc.getLong("qtyKeluar") ?: 0L,
                            saldo = doc.getLong("stokSesudah") ?: 0L,
                            catatan = doc.getString("catatan").orEmpty()
                        )
                    }
            }

        val extraProduk = grouped.keys
            .filter { it.isNotBlank() && it !in produkMap }
            .map { productId ->
                val firstDoc = docs.firstOrNull { it.getString("idProduk").orEmpty() == productId }
                Produk(
                    id = productId,
                    code = firstDoc?.getString("kodeProduk").orEmpty().ifBlank { productId },
                    name = firstDoc?.getString("namaProduk").orEmpty().ifBlank { "Produk $productId" },
                    category = "-",
                    unit = "pcs",
                    stock = 0,
                    minStock = 0,
                    active = true,
                    showInCashier = false,
                    photoTone = "",
                    channels = mutableListOf(),
                    deleted = false
                )
            }
            .sortedBy { it.name.lowercase(Locale.US) }

        return Triple(labelRentang(normalizedRange), produk + extraProduk, grouped)
    }

    suspend fun buildBukuHarianExcelXml(rangeKey: String): String {
        val data = muatBarisBukuHarianExport(rangeKey)
        fun row(vararg cells: String): String = cells.joinToString(prefix = "<Row>", postfix = "</Row>") { cell ->
            "<Cell><Data ss:Type=\"String\">${xmlEscape(cell)}</Data></Cell>"
        }

        var saldo = data.saldoAwal
        return buildString {
            appendLine("<?xml version=\"1.0\"?>")
            appendLine("<?mso-application progid=\"Excel.Sheet\"?>")
            appendLine("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:o=\"urn:schemas-microsoft-com:office:office\" xmlns:x=\"urn:schemas-microsoft-com:office:excel\" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:html=\"http://www.w3.org/TR/REC-html40\">")
            appendLine("<Worksheet ss:Name=\"Buku Harian\"><Table>")
            appendLine(row("BUKU HARIAN SI TAHU"))
            appendLine(row("Tanggal Laporan", Formatter.readableDateTime(formatIso.format(Date()))))
            appendLine(row("Periode Transaksi", data.label))
            appendLine(row("Saldo Awal", nominalMutasiExport(data.saldoAwal)))
            appendLine(row(""))
            appendLine(row("Tanggal Transaksi", "Uraian Transaksi", "User", "Debit/Pengeluaran", "Kredit/Pemasukan", "Saldo"))
            data.rows.forEach { item ->
                saldo += item.kredit - item.debit
                appendLine(row(
                    Formatter.readableDateTime(item.tanggalIso),
                    item.uraian,
                    item.user,
                    nominalMutasiExport(item.debit),
                    nominalMutasiExport(item.kredit),
                    nominalMutasiExport(saldo)
                ))
            }
            appendLine(row(""))
            appendLine(row("Saldo Akhir", nominalMutasiExport(saldo)))
            appendLine("</Table>")
            appendLine(excelA4LandscapeFitOptions())
            appendLine("</Worksheet>")
            appendLine("</Workbook>")
        }
    }

    suspend fun buildBukuHarianPdfText(rangeKey: String): String {
        val data = muatBarisBukuHarianExport(rangeKey)
        fun field(value: String): String = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
        var saldo = data.saldoAwal
        return buildString {
            appendLine("@@TYPE=BUKU_HARIAN")
            appendLine("@@TANGGAL=${field(Formatter.readableDateTime(formatIso.format(Date())))}")
            appendLine("@@PERIODE=${field(data.label)}")
            appendLine("@@JENIS=Pemasukan penjualan dan pengeluaran")
            appendLine("@@SALDO_AWAL=${field(nominalMutasiExport(data.saldoAwal))}")
            data.rows.forEach { item ->
                saldo += item.kredit - item.debit
                appendLine(
                    listOf(
                        Formatter.readableDateTime(item.tanggalIso),
                        field(item.uraian),
                        field(item.user),
                        nominalMutasiExport(item.debit),
                        nominalMutasiExport(item.kredit),
                        nominalMutasiExport(saldo)
                    ).joinToString(prefix = "@@ROW=", separator = "\t")
                )
            }
            appendLine("@@SALDO=${field(nominalMutasiExport(saldo))}")
        }
    }

    suspend fun buildStokProdukExcelXml(rangeKey: String): String {
        val (label, produk, grouped) = muatMutasiStokExport(rangeKey)
        fun row(vararg cells: String): String = cells.joinToString(prefix = "<Row>", postfix = "</Row>") { cell ->
            "<Cell><Data ss:Type=\"String\">${xmlEscape(cell)}</Data></Cell>"
        }

        val usedSheetNames = mutableSetOf<String>()
        fun uniqueSheetName(base: String, fallback: String): String {
            val first = safeWorksheetName(base, fallback)
            var candidate = first
            var index = 2
            while (!usedSheetNames.add(candidate.lowercase(Locale.US))) {
                val suffix = " $index"
                candidate = safeWorksheetName(first.take((31 - suffix.length).coerceAtLeast(1)) + suffix, fallback)
                index++
            }
            return candidate
        }

        return buildString {
            appendLine("<?xml version=\"1.0\"?>")
            appendLine("<?mso-application progid=\"Excel.Sheet\"?>")
            appendLine("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:o=\"urn:schemas-microsoft-com:office:office\" xmlns:x=\"urn:schemas-microsoft-com:office:excel\" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:html=\"http://www.w3.org/TR/REC-html40\">")
            appendLine("<Worksheet ss:Name=\"Ringkasan Stok\"><Table>")
            usedSheetNames.add("ringkasan stok")
            appendLine(row("EXPORT STOK PRODUK SI TAHU"))
            appendLine(row("Tanggal Laporan", Formatter.readableDateTime(formatIso.format(Date()))))
            appendLine(row("Periode Mutasi", label))
            appendLine(row(""))
            appendLine(row("Kode", "Nama Produk", "Kategori", "Stok Fisik", "Layak Jual", "ED Hari Ini", "Hampir Kedaluwarsa", "Kedaluwarsa", "Stok Minimum"))
            produk.forEach { item ->
                appendLine(row(
                    item.code,
                    item.name,
                    item.category,
                    item.stock.toString(),
                    (item.safeStock + item.nearExpiredStock + item.edTodayStock).toString(),
                    item.edTodayStock.toString(),
                    item.nearExpiredStock.toString(),
                    item.expiredStock.toString(),
                    item.minStock.toString()
                ))
            }
            appendLine("</Table>")
            appendLine(excelA4LandscapeFitOptions())
            appendLine("</Worksheet>")

            produk.forEachIndexed { index, item ->
                val sheetName = uniqueSheetName(item.name, "Produk ${index + 1}")
                appendLine("<Worksheet ss:Name=\"${xmlEscape(sheetName)}\"><Table>")
                appendLine(row("MUTASI STOK PRODUK"))
                appendLine(row("Produk", item.name))
                appendLine(row("Kode", item.code))
                appendLine(row("Periode", label))
                appendLine(row("Stok Fisik Saat Ini", item.stock.toString()))
                appendLine(row("Layak Jual", (item.safeStock + item.nearExpiredStock + item.edTodayStock).toString()))
                appendLine(row(""))
                appendLine(row("Tanggal Transaksi", "Uraian Transaksi", "User", "Masuk", "Keluar", "Saldo", "Catatan"))
                grouped[item.id].orEmpty().forEach { baris ->
                    appendLine(row(
                        Formatter.readableDateTime(baris.tanggalIso),
                        baris.uraian,
                        baris.user,
                        Formatter.ribuan(baris.masuk),
                        Formatter.ribuan(baris.keluar),
                        Formatter.ribuan(baris.saldo),
                        baris.catatan
                    ))
                }
                appendLine("</Table>")
                appendLine(excelA4LandscapeFitOptions())
                appendLine("</Worksheet>")
            }
            appendLine("</Workbook>")
        }
    }

    suspend fun buildStokProdukPdfText(rangeKey: String): String {
        val (label, produk, grouped) = muatMutasiStokExport(rangeKey)
        fun field(value: String): String = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
        return buildString {
            appendLine("@@TYPE=STOK_PRODUK")
            appendLine("@@TANGGAL=${field(Formatter.readableDateTime(formatIso.format(Date())))}")
            appendLine("@@PERIODE=${field(label)}")
            produk.forEach { item ->
                appendLine("@@PRODUCT_BEGIN")
                appendLine("@@PRODUK=${field(item.name)}")
                appendLine("@@KODE_KATEGORI=${field("${item.code} / ${item.category}")}")
                appendLine("@@STOK_SAAT_INI=${field("${Formatter.ribuan(item.stock.toLong())} ${item.unit}")}")
                appendLine("@@STOK_LAYAK=${field("${Formatter.ribuan((item.safeStock + item.nearExpiredStock + item.edTodayStock).toLong())} ${item.unit}")}")
                appendLine("@@RINCIAN_ED=${field("ED Hari Ini ${item.edTodayStock}, Hampir ED ${item.nearExpiredStock}, Kadaluarsa ${item.expiredStock}")}")
                grouped[item.id].orEmpty().forEach { baris ->
                    appendLine(
                        listOf(
                            Formatter.readableDateTime(baris.tanggalIso),
                            field(baris.uraian),
                            field(baris.user),
                            Formatter.ribuan(baris.masuk),
                            Formatter.ribuan(baris.keluar),
                            Formatter.ribuan(baris.saldo),
                            field(baris.catatan)
                        ).joinToString(prefix = "@@ROW=", separator = "\t")
                    )
                }
                appendLine("@@PRODUCT_END")
            }
        }
    }

    suspend fun muatRingkasanDashboard(): RingkasanDashboard {
        val todayKey = Formatter.currentDateOnly()

        val settingDoc = runCatching {
            firestore.collection("Pengaturan").document("umum").get().await()
        }.getOrNull()


        val produk = muatSemuaProduk()
        val produkAktif = produk.filter { it.active && !it.deleted }
        val lowStock = produkAktif
            .filter { statusProduk(it) != "Aman" || it.edTodayStock > 0 || it.nearExpiredStock > 0 || it.expiredStock > 0 }
            .sortedWith(
                compareBy<Produk> { if (statusProduk(it) == "Aman") 1 else 0 }
                    .thenBy { it.safeStock + it.nearExpiredStock + it.edTodayStock }
                    .thenBy { it.name.lowercase(Locale.US) }
            )
            .take(6)

        val stokMenipis = produkAktif
            .filter { statusProduk(it) == "Habis" || statusProduk(it) == "Menipis" }
            .sortedWith(compareBy<Produk> { if (statusProduk(it) == "Habis") 0 else 1 }.thenBy { it.safeStock + it.nearExpiredStock + it.edTodayStock })
            .take(4)
            .map { item ->
                val stokLayak = item.safeStock + item.nearExpiredStock + item.edTodayStock
                val status = statusProduk(item)
                ItemDashboard(
                    id = item.id,
                    title = item.name,
                    subtitle = "Minimal ${Formatter.ribuan(item.minStock.toLong())} ${item.unit}",
                    amount = "${Formatter.ribuan(stokLayak.toLong())} ${item.unit}",
                    badge = status,
                    tanggalIso = todayKey
                )
            }

        val hampirEd = produkAktif
            .filter { it.edTodayStock > 0 || it.nearExpiredStock > 0 || it.expiredStock > 0 }
            .sortedWith(compareByDescending<Produk> { it.expiredStock }.thenByDescending { it.edTodayStock }.thenByDescending { it.nearExpiredStock }.thenBy { it.name.lowercase(Locale.US) })
            .take(4)
            .map { item ->
                val qtyPerhatian = item.expiredStock + item.edTodayStock + item.nearExpiredStock
                ItemDashboard(
                    id = item.id,
                    title = item.name,
                    subtitle = item.nearestExpiryDate.takeIf { it.isNotBlank() }?.let { "ED terdekat ${Formatter.readableShortDate(it)}" } ?: "Perlu diprioritaskan",
                    amount = "${Formatter.ribuan(qtyPerhatian.toLong())} ${item.unit}",
                    badge = if (item.expiredStock > 0) "Kedaluwarsa" else if (item.edTodayStock > 0) "ED Hari Ini" else "Hampir Kedaluwarsa",
                    tanggalIso = todayKey
                )
            }

        val recentSales = muatRiwayatPenjualan(limit = 4).map {
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

        val recentExpenses = muatRiwayatPengeluaran().take(4).map {
            ItemDashboard(
                id = it.id,
                title = "Pengeluaran",
                subtitle = it.subtitle,
                amount = it.amount,
                badge = it.badge,
                tanggalIso = it.tanggalIso
            )
        }

        val recentItems = (recentSales + recentProduction + recentExpenses)
            .sortedByDescending { Formatter.parseDate(it.tanggalIso) }
            .take(5)

        val laporanHariIni = muatLaporan("hari_ini")
        val topProductsLangsung = runCatching {
            muatProdukTerlarisPenjualanHariIni(limit = 4)
        }.getOrDefault(emptyList())
        val topProductsLaporan = laporanHariIni.produkTerlaris.take(4).map {
            ItemDashboard(
                id = it.id,
                title = it.title,
                subtitle = it.subtitle,
                amount = it.amount,
                badge = it.badge,
                tanggalIso = todayKey
            )
        }
        val topProducts = topProductsLangsung.ifEmpty { topProductsLaporan }
        val expenseCategories = laporanHariIni.kategoriPengeluaran.take(3).map {
            ItemDashboard(
                id = it.id,
                title = it.title,
                subtitle = it.subtitle,
                amount = it.amount,
                badge = it.badge,
                tanggalIso = todayKey
            )
        }

        val totalPenjualan = laporanHariIni.totalPenjualan
        val totalPengeluaran = laporanHariIni.totalPengeluaran
        val totalTransaksi = laporanHariIni.totalTransaksi
        val totalItemTerjual = laporanHariIni.totalItemTerjual
        val totalLaba = laporanHariIni.labaRugi
        val totalProduksi = laporanHariIni.totalProduksi

        return RingkasanDashboard(
            namaUsaha = settingDoc?.getString("namaTampilanToko").orEmpty().ifBlank { "SI Tahu" },
            tanggalRingkasan = todayKey,
            totalPenjualan = totalPenjualan,
            totalProduksi = totalProduksi,
            totalPengeluaran = totalPengeluaran,
            totalTransaksi = totalTransaksi,
            totalItemTerjual = totalItemTerjual,
            totalLaba = totalLaba,
            totalProdukAktif = produkAktif.size,
            lowStock = lowStock,
            stokMenipis = stokMenipis,
            hampirEd = hampirEd,
            recentItems = recentItems,
            topProducts = topProducts,
            expenseCategories = expenseCategories
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

            normalized.contains("PENGELUARAN") ||
                    normalized.contains("BIAYA") ||
                    normalized.contains("EXPENSE") -> {
                buildExpenseDetailText(id)
            }

            normalized.contains("ADJUSTMENT") ||
                    normalized.contains("PENYESUAIAN") -> {
                buildAdjustmentDetailText(id)
            }
            else -> "Detail aktivitas belum tersedia"
        }
    }

    suspend fun perbaruiRingkasanHarian(kunciTanggal: String) {
        if (kunciTanggal.isBlank()) return

        val sales = ambilDokumenKunciTanggal("Penjualan", "kunciTanggal", "tanggalPenjualan", "custom:$kunciTanggal:$kunciTanggal")
            .filter { doc -> doc.getString("statusPenjualan").orEmpty().ifBlank { "SELESAI" }.equals("SELESAI", true) }

        val expenses = ambilDokumenKunciTanggal("Pengeluaran", "kunciTanggal", "tanggalPengeluaran", "custom:$kunciTanggal:$kunciTanggal")
            .filter { !it.dataDihapus() }

        val produksi = ambilDokumenKunciTanggal("CatatanProduksi", "kunciTanggal", "tanggalProduksi", "custom:$kunciTanggal:$kunciTanggal")
            .filter { !it.produksiDibatalkan() && !it.dataDihapus() }

        val totalPenjualan = sales.sumOf { it.getLong("totalBelanja") ?: 0L }
        val totalTransaksi = sales.size
        val totalItemTerjual = sales.mapAsyncLimited { sale ->
            totalItemPenjualan(sale)
        }.sum()
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
                    "totalLabaRugi" to (totalPenjualan - totalPengeluaran),
                    "totalTransaksi" to totalTransaksi,
                    "totalItemTerjual" to totalItemTerjual,
                    "totalProduksiDasar" to totalProduksiDasar,
                    "totalProduksiOlahan" to totalProduksiOlahan,
                    "diperbaruiPada" to nowTimestamp()
                )
            ).await()
    }
}