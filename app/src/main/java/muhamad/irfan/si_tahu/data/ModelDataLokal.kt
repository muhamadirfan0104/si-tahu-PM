package muhamad.irfan.si_tahu.data

enum class PeranPengguna { ADMIN, KASIR }

data class PengaturanUsaha(
    var businessName: String,
    var address: String,
    var phone: String,
    var logoText: String,
    var receiptFooter: String,
    var note: String
)

data class HargaKanal(
    var id: String,
    var label: String,
    var price: Long,
    var active: Boolean,
    var defaultCashier: Boolean,
    var deleted: Boolean = false
)

data class Produk(
    var id: String,
    var code: String,
    var name: String,
    var category: String,
    var unit: String,
    var stock: Int,
    var minStock: Int,
    var active: Boolean,
    var showInCashier: Boolean,
    var photoTone: String,
    var channels: MutableList<HargaKanal>,
    var deleted: Boolean = false,
    var shelfLifeDays: Int = 2,
    var producedToday: Boolean = false,
    var safeStock: Int = stock,
    var nearExpiredStock: Int = 0,
    var expiredStock: Int = 0,
    var nearestExpiryDate: String = "",
    var stockBatchStatus: String = "Stok Sisa"
)

data class ParameterProduksi(
    var id: String,
    var productId: String,
    var resultPerBatch: Int,
    var note: String,
    var active: Boolean
)

data class AkunPengguna(
    var id: String,
    var name: String,
    var email: String,
    var role: PeranPengguna,
    var password: String,
    var active: Boolean,
    var deleted: Boolean = false
)

data class CatatanProduksi(
    var id: String,
    var date: String,
    var productId: String,
    var batches: Double,
    var result: Int,
    var note: String,
    var createdBy: String
)

data class CatatanKonversi(
    var id: String,
    var date: String,
    var fromProductId: String,
    var toProductId: String,
    var inputQty: Int,
    var outputQty: Int,
    var note: String,
    var createdBy: String
)

data class Pengeluaran(
    var id: String,
    var date: String,
    var category: String,
    var amount: Long,
    var note: String,
    var createdBy: String
)

data class ItemPenjualan(
    var productId: String,
    var qty: Int,
    var price: Long
)

data class Penjualan(
    var id: String,
    var date: String,
    var source: String,
    var cashierId: String,
    var paymentMethod: String,
    var cashPaid: Long,
    var total: Long,
    var items: MutableList<ItemPenjualan>
)

data class PenyesuaianStok(
    var id: String,
    var date: String,
    var productId: String,
    var type: String,
    var qty: Int,
    var note: String,
    var createdBy: String
)

data class CatatanAktivitas(
    var id: String,
    var message: String,
    var time: String,
    var tone: String
)

data class BasisDataLokal(
    var settings: PengaturanUsaha,
    var products: MutableList<Produk>,
    var parameters: MutableList<ParameterProduksi>,
    var users: MutableList<AkunPengguna>,
    var productionLogs: MutableList<CatatanProduksi>,
    var conversions: MutableList<CatatanKonversi>,
    var expenses: MutableList<Pengeluaran>,
    var sales: MutableList<Penjualan>,
    var stockAdjustments: MutableList<PenyesuaianStok>,
    var activities: MutableList<CatatanAktivitas>
)

data class RingkasanHarian(
    val totalSales: Long,
    val totalExpenses: Long,
    val totalProduction: Int,
    val totalProfit: Long,
    val transactionCount: Int
)

data class ProdukTerlaris(val productId: String, val name: String, val qty: Int)

data class RingkasanLaporan(
    val totalSales: Long,
    val totalExpenses: Long,
    val totalProduction: Int,
    val totalProfit: Long,
    val transactionCount: Int,
    val mix: List<ProdukTerlaris>
)

data class BarisTransaksi(
    val id: String,
    val date: String,
    val type: String,
    val subtitle: String,
    val valueText: String,
    val source: String,
    val saleId: String? = null
)

data class PergerakanStok(
    val id: String,
    val date: String,
    val title: String,
    val subtitle: String,
    val qtyText: String,
    val tone: String
)

data class ItemKeranjang(
    val productId: String,
    var qty: Int,
    var price: Long
)

data class ItemDraftRekap(
    val id: String,
    val productId: String,
    val productName: String,
    val channelLabel: String,
    var qty: Int,
    var price: Long
)