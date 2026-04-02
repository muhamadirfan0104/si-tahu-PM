package muhamad.irfan.si_tahupm.data

enum class UserRole { ADMIN, KASIR }

data class BusinessSettings(
    var businessName: String,
    var address: String,
    var phone: String,
    var logoText: String,
    var receiptFooter: String,
    var note: String
)

data class ChannelPrice(
    var id: String,
    var label: String,
    var price: Long,
    var active: Boolean,
    var defaultCashier: Boolean,
    var deleted: Boolean = false
)

data class Product(
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
    var channels: MutableList<ChannelPrice>,
    var deleted: Boolean = false
)

data class ProductionParameter(
    var id: String,
    var productId: String,
    var resultPerBatch: Int,
    var note: String,
    var active: Boolean
)

data class UserAccount(
    var id: String,
    var name: String,
    var email: String,
    var role: UserRole,
    var password: String,
    var active: Boolean,
    var deleted: Boolean = false
)

data class ProductionLog(
    var id: String,
    var date: String,
    var productId: String,
    var batches: Int,
    var result: Int,
    var note: String,
    var createdBy: String
)

data class ConversionLog(
    var id: String,
    var date: String,
    var fromProductId: String,
    var toProductId: String,
    var inputQty: Int,
    var outputQty: Int,
    var note: String,
    var createdBy: String
)

data class Expense(
    var id: String,
    var date: String,
    var category: String,
    var amount: Long,
    var note: String,
    var createdBy: String
)

data class SaleItem(
    var productId: String,
    var qty: Int,
    var price: Long
)

data class Sale(
    var id: String,
    var date: String,
    var source: String,
    var cashierId: String,
    var paymentMethod: String,
    var cashPaid: Long,
    var total: Long,
    var items: MutableList<SaleItem>
)

data class StockAdjustment(
    var id: String,
    var date: String,
    var productId: String,
    var type: String,
    var qty: Int,
    var note: String,
    var createdBy: String
)

data class ActivityLog(
    var id: String,
    var message: String,
    var time: String,
    var tone: String
)

data class DemoDatabase(
    var settings: BusinessSettings,
    var products: MutableList<Product>,
    var parameters: MutableList<ProductionParameter>,
    var users: MutableList<UserAccount>,
    var productionLogs: MutableList<ProductionLog>,
    var conversions: MutableList<ConversionLog>,
    var expenses: MutableList<Expense>,
    var sales: MutableList<Sale>,
    var stockAdjustments: MutableList<StockAdjustment>,
    var activities: MutableList<ActivityLog>
)

data class DaySummary(
    val totalSales: Long,
    val totalExpenses: Long,
    val totalProduction: Int,
    val totalProfit: Long,
    val transactionCount: Int
)

data class TopProduct(val productId: String, val name: String, val qty: Int)

data class ReportSummary(
    val totalSales: Long,
    val totalExpenses: Long,
    val totalProduction: Int,
    val totalProfit: Long,
    val transactionCount: Int,
    val mix: List<TopProduct>
)

data class TransactionRow(
    val id: String,
    val date: String,
    val type: String,
    val subtitle: String,
    val valueText: String,
    val source: String,
    val saleId: String? = null
)

data class StockMovement(
    val id: String,
    val date: String,
    val title: String,
    val subtitle: String,
    val qtyText: String,
    val tone: String
)

data class CartItem(
    val productId: String,
    var qty: Int,
    var price: Long
)

data class RecapDraftItem(
    val id: String,
    val productId: String,
    val productName: String,
    var qty: Int,
    var price: Long
)
