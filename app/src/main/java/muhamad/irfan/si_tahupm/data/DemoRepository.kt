package muhamad.irfan.si_tahupm.data

import android.content.Context
import android.content.SharedPreferences
import muhamad.irfan.si_tahupm.util.Formatters
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DemoRepository {
    private const val PREFS_NAME = "umkm_tahu_berkah_xml"
    private const val KEY_DB = "db_json"
    private const val KEY_USER_ID = "session_user_id"
    private const val KEY_ROLE = "session_role"
    private const val DEFAULT_ADMIN = "usr-admin"
    private const val DEFAULT_CASHIER = "usr-kasir-1"

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var prefs: SharedPreferences
    private var database: DemoDatabase = DemoSeed.create()
    private var isReady: Boolean = false

    val cart: MutableList<CartItem> = mutableListOf()
    var checkoutMethod: String = "Tunai"
    var cashPaid: Long = 20000

    fun init(context: Context) {
        if (isReady) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DB, null)
        database = if (raw.isNullOrBlank()) {
            DemoSeed.create()
        } else {
            runCatching { gson.fromJson(raw, DemoDatabase::class.java) }.getOrElse { DemoSeed.create() }
        }
        isReady = true
    }

    private fun persist() {
        prefs.edit().putString(KEY_DB, gson.toJson(database)).apply()
    }

    fun resetDemo() {
        database = DemoSeed.create()
        cart.clear()
        checkoutMethod = "Tunai"
        cashPaid = 20000
        prefs.edit().putString(KEY_DB, gson.toJson(database)).apply()
    }

    fun db(): DemoDatabase = database

    fun allProducts(includeDeleted: Boolean = false): List<Product> =
        database.products.filter { includeDeleted || !it.deleted }

    fun allUsers(includeDeleted: Boolean = false): List<UserAccount> =
        database.users.filter { includeDeleted || !it.deleted }

    fun allParameters(): List<ProductionParameter> = database.parameters
    fun allExpenses(): List<Expense> = database.expenses
    fun allSales(): List<Sale> = database.sales.sortedByDescending { Formatters.parseDate(it.date) }
    fun allProductionLogs(): List<ProductionLog> = database.productionLogs.sortedByDescending { Formatters.parseDate(it.date) }
    fun allConversions(): List<ConversionLog> = database.conversions.sortedByDescending { Formatters.parseDate(it.date) }
    fun allAdjustments(): List<StockAdjustment> = database.stockAdjustments.sortedByDescending { Formatters.parseDate(it.date) }

    fun getProduct(id: String?): Product? = database.products.firstOrNull { it.id == id }
    fun getUser(id: String?): UserAccount? = database.users.firstOrNull { it.id == id }
    fun getParameter(id: String?): ProductionParameter? = database.parameters.firstOrNull { it.id == id }
    fun getExpense(id: String?): Expense? = database.expenses.firstOrNull { it.id == id }
    fun getSale(id: String?): Sale? = database.sales.firstOrNull { it.id == id }
    fun getProductionLog(id: String?): ProductionLog? = database.productionLogs.firstOrNull { it.id == id }
    fun getConversionLog(id: String?): ConversionLog? = database.conversions.firstOrNull { it.id == id }
    fun getAdjustment(id: String?): StockAdjustment? = database.stockAdjustments.firstOrNull { it.id == id }

    fun visibleChannels(product: Product?): List<ChannelPrice> =
        product?.channels?.filter { !it.deleted } ?: emptyList()

    fun sessionRole(): UserRole? = prefs.getString(KEY_ROLE, null)?.let {
        runCatching { UserRole.valueOf(it) }.getOrNull()
    }

    fun sessionUser(): UserAccount? = getUser(prefs.getString(KEY_USER_ID, null))?.takeUnless { it.deleted }

    private fun saveSession(user: UserAccount?) {
        if (user == null) {
            prefs.edit().remove(KEY_USER_ID).remove(KEY_ROLE).apply()
        } else {
            prefs.edit().putString(KEY_USER_ID, user.id).putString(KEY_ROLE, user.role.name).apply()
        }
    }

    fun logout() {
        saveSession(null)
        cart.clear()
        checkoutMethod = "Tunai"
        cashPaid = 20000
    }

    fun login(email: String, password: String): UserAccount? {
        val user = database.users.firstOrNull {
            !it.deleted &&
                it.email.equals(email.trim(), ignoreCase = true) &&
                it.password == password &&
                it.active
        }
        saveSession(user)
        return user
    }

    fun loginAs(role: UserRole): UserAccount {
        val user = when (role) {
            UserRole.ADMIN -> getUser(DEFAULT_ADMIN)
            UserRole.KASIR -> getUser(DEFAULT_CASHIER)
        }?.takeUnless { it.deleted } ?: error("Default user missing")
        saveSession(user)
        return user
    }

    fun defaultChannel(product: Product?): ChannelPrice? {
        val channels = visibleChannels(product)
        return channels.firstOrNull { it.defaultCashier && it.active } ?: channels.firstOrNull { it.active }
    }

    fun channelByLabel(product: Product?, label: String): ChannelPrice? {
        return visibleChannels(product).firstOrNull { it.label.equals(label, ignoreCase = true) && it.active }
            ?: defaultChannel(product)
    }

    fun activeParameter(productId: String?): ProductionParameter? {
        return database.parameters.firstOrNull { it.productId == productId && it.active }
    }

    fun productStatus(product: Product): String = when {
        product.stock <= 0 -> "Habis"
        product.stock <= product.minStock -> "Menipis"
        else -> "Aman"
    }

    fun lowStockProducts(): List<Product> {
        return allProducts().filter { it.active && it.stock <= it.minStock }
    }

    fun latestReferenceDate(): Date {
        val dates = mutableListOf<Date>()
        database.sales.mapTo(dates) { Formatters.parseDate(it.date) }
        database.expenses.mapTo(dates) { Formatters.parseDate(it.date) }
        database.productionLogs.mapTo(dates) { Formatters.parseDate(it.date) }
        database.conversions.mapTo(dates) { Formatters.parseDate(it.date) }
        database.stockAdjustments.mapTo(dates) { Formatters.parseDate(it.date) }
        return dates.maxOrNull() ?: Date()
    }

    fun latestDateOnly(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(latestReferenceDate())
    fun latestTimeOnly(): String = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(latestReferenceDate())

    fun daySummary(dateOnly: String = latestDateOnly()): DaySummary {
        val sales = database.sales.filter { Formatters.toDateOnly(it.date) == dateOnly }
        val expenses = database.expenses.filter { Formatters.toDateOnly(it.date) == dateOnly }
        val production = database.productionLogs.filter { Formatters.toDateOnly(it.date) == dateOnly }
        val totalSales = sales.sumOf { it.total }
        val totalExpenses = expenses.sumOf { it.amount }
        val totalProduction = production.sumOf { it.result }
        return DaySummary(totalSales, totalExpenses, totalProduction, totalSales - totalExpenses, sales.size)
    }

    private fun inRange(dateValue: String, rangeKey: String): Boolean {
        if (rangeKey == "semua") return true
        val days = rangeKey.toIntOrNull() ?: 7
        val ref = latestReferenceDate().time
        val target = Formatters.parseDate(dateValue).time
        return ref - target <= days * 24L * 60L * 60L * 1000L
    }

    fun topProducts(limit: Int = 4, source: String? = null): List<TopProduct> {
        val map = linkedMapOf<String, TopProduct>()
        database.sales.filter { source == null || it.source == source }.forEach { sale ->
            sale.items.forEach { item ->
                val product = getProduct(item.productId)
                val old = map[item.productId]
                map[item.productId] = TopProduct(item.productId, product?.name ?: "Produk", (old?.qty ?: 0) + item.qty)
            }
        }
        return map.values.sortedByDescending { it.qty }.take(limit)
    }

    fun reportSummary(rangeKey: String): ReportSummary {
        val sales = database.sales.filter { inRange(it.date, rangeKey) }
        val expenses = database.expenses.filter { inRange(it.date, rangeKey) }
        val production = database.productionLogs.filter { inRange(it.date, rangeKey) }
        val totalSales = sales.sumOf { it.total }
        val totalExpenses = expenses.sumOf { it.amount }
        val totalProduction = production.sumOf { it.result }
        return ReportSummary(totalSales, totalExpenses, totalProduction, totalSales - totalExpenses, sales.size, topProducts(4))
    }

    fun transactions(): List<TransactionRow> {
        val rows = mutableListOf<TransactionRow>()
        database.sales.forEach { sale ->
            rows += TransactionRow(
                id = sale.id,
                date = sale.date,
                type = if (sale.source == "KASIR") "Penjualan" else "Rekap Pasar",
                subtitle = sale.items.joinToString(", ") {
                    val product = getProduct(it.productId)
                    (product?.name ?: "Produk") + " x" + it.qty
                },
                valueText = Formatters.currency(sale.total),
                source = sale.source,
                saleId = sale.id
            )
        }
        database.productionLogs.forEach { item ->
            rows += TransactionRow(
                id = item.id,
                date = item.date,
                type = "Produksi",
                subtitle = (getProduct(item.productId)?.name ?: "Produk") + " • " + item.batches + " masak",
                valueText = item.result.toString() + " pcs",
                source = "PRODUKSI"
            )
        }
        database.conversions.forEach { item ->
            rows += TransactionRow(
                id = item.id,
                date = item.date,
                type = "Konversi",
                subtitle = (getProduct(item.fromProductId)?.name ?: "Bahan") + " -> " + (getProduct(item.toProductId)?.name ?: "Hasil"),
                valueText = item.outputQty.toString() + " pcs",
                source = "KONVERSI"
            )
        }
        database.expenses.forEach { item ->
            rows += TransactionRow(
                id = item.id,
                date = item.date,
                type = "Pengeluaran",
                subtitle = item.category,
                valueText = Formatters.currency(item.amount),
                source = "PENGELUARAN"
            )
        }
        database.stockAdjustments.forEach { item ->
            val product = getProduct(item.productId)
            val qtyLabel = if (item.type == "add") "+${item.qty}" else "-${item.qty}"
            rows += TransactionRow(
                id = item.id,
                date = item.date,
                type = "Adjustment",
                subtitle = (product?.name ?: "Produk") + " • " + qtyLabel + " " + (product?.unit ?: "pcs"),
                valueText = if (item.type == "add") "Tambah" else "Kurang",
                source = "ADJUSTMENT"
            )
        }
        return rows.sortedByDescending { Formatters.parseDate(it.date) }
    }

    fun stockMovements(productId: String): List<StockMovement> {
        val product = getProduct(productId) ?: return emptyList()
        val rows = mutableListOf<StockMovement>()
        database.productionLogs.filter { it.productId == productId }.forEach {
            rows += StockMovement(it.id, it.date, "Produksi masuk", it.note, "+${it.result} ${product.unit}", "green")
        }
        database.conversions.forEach {
            if (it.fromProductId == productId) {
                rows += StockMovement(it.id + "-out", it.date, "Konversi keluar", "Dipakai ke ${getProduct(it.toProductId)?.name ?: "produk"}", "-${it.inputQty} ${product.unit}", "orange")
            }
            if (it.toProductId == productId) {
                rows += StockMovement(it.id + "-in", it.date, "Konversi masuk", "Hasil dari ${getProduct(it.fromProductId)?.name ?: "produk"}", "+${it.outputQty} ${product.unit}", "green")
            }
        }
        database.sales.forEach { sale ->
            sale.items.filter { it.productId == productId }.forEach { item ->
                rows += StockMovement(sale.id + productId, sale.date, "Penjualan keluar", sale.source + " • " + sale.id, "-${item.qty} ${product.unit}", if (sale.source == "KASIR") "gold" else "blue")
            }
        }
        database.stockAdjustments.filter { it.productId == productId }.forEach {
            val sign = if (it.type == "add") "+" else "-"
            rows += StockMovement(it.id, it.date, "Adjustment ${if (it.type == "add") "masuk" else "keluar"}", it.note, "$sign${it.qty} ${product.unit}", if (it.type == "add") "green" else "orange")
        }
        return rows.sortedByDescending { Formatters.parseDate(it.date) }.take(8)
    }

    private fun addActivity(message: String, tone: String) {
        database.activities.add(0, ActivityLog(Formatters.newId("act"), message, nowIso(), tone))
        database.activities = database.activities.take(30).toMutableList()
        persist()
    }

    private fun nowIso(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        return format.format(Date())
    }

    fun addToCart(productId: String) {
        val product = getProduct(productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        if (product.deleted || !product.active || !product.showInCashier) throw IllegalStateException("Produk tidak tampil di kasir")
        if (product.stock <= 0) throw IllegalStateException("Stok produk habis")
        val current = cart.firstOrNull { it.productId == productId }
        val newQty = (current?.qty ?: 0) + 1
        if (newQty > product.stock) throw IllegalStateException("Jumlah melebihi stok tersedia")
        if (current == null) cart += CartItem(productId, 1, defaultChannel(product)?.price ?: 0)
        else current.qty = newQty
        cashPaid = maxOf(cashPaid, cartTotal())
    }

    fun changeCart(productId: String, delta: Int) {
        val item = cart.firstOrNull { it.productId == productId } ?: return
        val product = getProduct(productId) ?: return
        val next = item.qty + delta
        when {
            next <= 0 -> cart.remove(item)
            next <= product.stock -> item.qty = next
            else -> throw IllegalStateException("Jumlah melebihi stok tersedia")
        }
        cashPaid = maxOf(cashPaid, cartTotal())
    }

    fun removeFromCart(productId: String) {
        cart.removeAll { it.productId == productId }
    }

    fun cartCount(): Int = cart.sumOf { it.qty }
    fun cartTotal(): Long = cart.sumOf { it.qty.toLong() * it.price }

    fun checkoutCart(userId: String, paymentMethod: String, cashInput: Long): Sale {
        if (cart.isEmpty()) throw IllegalStateException("Keranjang masih kosong")
        val total = cartTotal()
        if (paymentMethod == "Tunai" && cashInput < total) throw IllegalStateException("Uang bayar belum cukup")
        cart.forEach { item ->
            val product = getProduct(item.productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
            if (product.stock < item.qty) throw IllegalStateException("Stok ${product.name} tidak cukup")
        }
        cart.forEach { item -> getProduct(item.productId)?.let { it.stock -= item.qty } }
        val sale = Sale(
            id = "TRX-" + nowIso().replace("-", "").replace(":", "").replace("T", "").take(14),
            date = nowIso(),
            source = "KASIR",
            cashierId = userId,
            paymentMethod = paymentMethod,
            cashPaid = if (paymentMethod == "Tunai") cashInput else total,
            total = total,
            items = cart.map { SaleItem(it.productId, it.qty, it.price) }.toMutableList()
        )
        database.sales.add(0, sale)
        persist()
        addActivity("Transaksi ${sale.id} selesai ${Formatters.currency(total)}.", "blue")
        cart.clear()
        checkoutMethod = paymentMethod
        cashPaid = total
        return sale
    }

    fun saveMarketRecap(dateOnly: String, source: String, items: List<RecapDraftItem>, userId: String): Sale {
        if (items.isEmpty()) throw IllegalStateException("Belum ada item rekap")
        items.forEach {
            val product = getProduct(it.productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
            if (product.stock < it.qty) throw IllegalStateException("Stok ${product.name} tidak cukup")
        }
        items.forEach { getProduct(it.productId)?.let { product -> product.stock -= it.qty } }
        val total = items.sumOf { it.qty.toLong() * it.price }
        val sale = Sale(
            id = "REKAP-" + nowIso().replace("-", "").replace(":", "").replace("T", "").take(14),
            date = Formatters.isoDate(dateOnly, "13:00:00"),
            source = source,
            cashierId = userId,
            paymentMethod = "Rekap",
            cashPaid = total,
            total = total,
            items = items.map { SaleItem(it.productId, it.qty, it.price) }.toMutableList()
        )
        database.sales.add(0, sale)
        persist()
        addActivity("Rekap ${sale.source} tersimpan ${Formatters.currency(total)}.", "orange")
        return sale
    }

    fun saveProduction(dateTime: String, productId: String, batches: Int, note: String, userId: String): ProductionLog {
        val product = getProduct(productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        require(batches > 0) { "Jumlah masak harus lebih dari 0" }
        val parameter = activeParameter(productId)
        val result = (parameter?.resultPerBatch ?: 100) * batches
        product.stock += result
        val item = ProductionLog(
            id = "PROD-" + nowIso().replace("-", "").replace(":", "").replace("T", "").take(14),
            date = dateTime,
            productId = productId,
            batches = batches,
            result = result,
            note = note,
            createdBy = userId
        )
        database.productionLogs.add(0, item)
        persist()
        addActivity("Produksi ${product.name} bertambah ${result} ${product.unit}.", "green")
        return item
    }

    fun saveConversion(
        dateTime: String,
        fromProductId: String,
        toProductId: String,
        inputQty: Int,
        outputQty: Int,
        note: String,
        userId: String
    ): ConversionLog {
        val fromProduct = getProduct(fromProductId) ?: throw IllegalArgumentException("Produk bahan tidak ditemukan")
        val toProduct = getProduct(toProductId) ?: throw IllegalArgumentException("Produk hasil tidak ditemukan")
        require(fromProductId != toProductId) { "Bahan dan hasil harus berbeda" }
        require(inputQty > 0 && outputQty > 0) { "Jumlah harus lebih dari 0" }
        if (fromProduct.stock < inputQty) throw IllegalStateException("Stok bahan tidak cukup")
        fromProduct.stock -= inputQty
        toProduct.stock += outputQty
        val item = ConversionLog(
            id = "KONV-" + nowIso().replace("-", "").replace(":", "").replace("T", "").take(14),
            date = dateTime,
            fromProductId = fromProductId,
            toProductId = toProductId,
            inputQty = inputQty,
            outputQty = outputQty,
            note = note,
            createdBy = userId
        )
        database.conversions.add(0, item)
        persist()
        addActivity("Konversi ${fromProduct.name} -> ${toProduct.name} berhasil.", "blue")
        return item
    }

    fun adjustStock(dateOnly: String, productId: String, type: String, qty: Int, note: String, userId: String): StockAdjustment {
        val product = getProduct(productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        require(qty > 0) { "Jumlah harus lebih dari 0" }
        if (type == "subtract" && product.stock < qty) throw IllegalStateException("Stok tidak cukup untuk dikurangi")
        product.stock += if (type == "add") qty else -qty
        val item = StockAdjustment(
            id = "ADJ-" + nowIso().replace("-", "").replace(":", "").replace("T", "").take(14),
            date = Formatters.isoDate(dateOnly, "16:00:00"),
            productId = productId,
            type = type,
            qty = qty,
            note = note,
            createdBy = userId
        )
        database.stockAdjustments.add(0, item)
        persist()
        addActivity("Adjustment stok ${product.name} ${if (type == "add") "+" else "-"}$qty ${product.unit}.", if (type == "add") "green" else "orange")
        return item
    }

    fun saveProduct(
        existingId: String?,
        code: String,
        name: String,
        category: String,
        unit: String,
        stock: Int,
        minStock: Int,
        defaultPrice: Long,
        photoTone: String,
        active: Boolean,
        showInCashier: Boolean
    ): Product {
        val product = getProduct(existingId)
        return if (product == null) {
            val created = Product(
                id = Formatters.newId("prd"),
                code = code,
                name = name,
                category = category,
                unit = unit,
                stock = stock,
                minStock = minStock,
                active = active,
                showInCashier = showInCashier,
                photoTone = photoTone,
                channels = mutableListOf(
                    ChannelPrice(Formatters.newId("chn"), "KASIR", defaultPrice, true, true),
                    ChannelPrice(Formatters.newId("chn"), "PASAR", maxOf(defaultPrice - 200, 0), true, false)
                )
            )
            database.products.add(0, created)
            persist()
            addActivity("Produk $name tersimpan.", "green")
            created
        } else {
            product.code = code
            product.name = name
            product.category = category
            product.unit = unit
            product.stock = stock
            product.minStock = minStock
            product.photoTone = photoTone
            product.active = active
            product.showInCashier = showInCashier
            product.deleted = false
            defaultChannel(product)?.price = defaultPrice
            persist()
            addActivity("Produk $name diperbarui.", "green")
            product
        }
    }

    fun softDeleteProduct(id: String): Product {
        val product = getProduct(id) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        product.deleted = true
        product.active = false
        product.showInCashier = false
        product.channels.forEach {
            it.active = false
            it.defaultCashier = false
        }
        persist()
        addActivity("Produk ${product.name} dihapus secara soft delete.", "orange")
        return product
    }

    fun toggleProduct(id: String): Product {
        val product = getProduct(id) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        product.active = !product.active
        persist()
        addActivity("Status produk ${product.name} diubah menjadi ${if (product.active) "aktif" else "nonaktif"}.", if (product.active) "green" else "orange")
        return product
    }

    fun saveChannelPrice(productId: String, existingId: String?, label: String, price: Long, active: Boolean, defaultCashier: Boolean): ChannelPrice {
        val product = getProduct(productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        val priceRow = product.channels.firstOrNull { it.id == existingId }
        val channel = if (priceRow == null) {
            ChannelPrice(Formatters.newId("chn"), label.uppercase(), price, active, defaultCashier).also { product.channels.add(it) }
        } else {
            priceRow.label = label.uppercase()
            priceRow.price = price
            priceRow.active = active
            priceRow.defaultCashier = defaultCashier
            priceRow.deleted = false
            priceRow
        }
        if (channel.defaultCashier) {
            product.channels.forEach { it.defaultCashier = it.id == channel.id }
        }
        persist()
        addActivity("Harga kanal ${channel.label} untuk ${product.name} tersimpan.", "blue")
        return channel
    }

    fun setDefaultChannel(productId: String, channelId: String) {
        val product = getProduct(productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        visibleChannels(product).forEach { it.defaultCashier = it.id == channelId }
        persist()
        addActivity("Default harga kasir untuk ${product.name} diperbarui.", "green")
    }

    fun softDeleteChannel(productId: String, channelId: String): ChannelPrice {
        val product = getProduct(productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        val channel = product.channels.firstOrNull { it.id == channelId } ?: throw IllegalArgumentException("Kanal tidak ditemukan")
        channel.deleted = true
        channel.active = false
        channel.defaultCashier = false
        visibleChannels(product).firstOrNull { it.active }?.defaultCashier = true
        persist()
        addActivity("Harga kanal ${channel.label} untuk ${product.name} dihapus secara soft delete.", "orange")
        return channel
    }

    fun saveParameter(existingId: String?, productId: String, resultPerBatch: Int, note: String, active: Boolean): ProductionParameter {
        val parameter = getParameter(existingId)
        val saved = if (parameter == null) {
            ProductionParameter(Formatters.newId("prm"), productId, resultPerBatch, note, active).also { database.parameters.add(0, it) }
        } else {
            parameter.productId = productId
            parameter.resultPerBatch = resultPerBatch
            parameter.note = note
            parameter.active = active
            parameter
        }
        if (saved.active) {
            database.parameters.filter { it.productId == saved.productId && it.id != saved.id }.forEach { it.active = false }
        }
        persist()
        addActivity("Parameter produksi untuk ${getProduct(saved.productId)?.name ?: "produk"} tersimpan.", "green")
        return saved
    }

    fun deleteParameter(parameterId: String): ProductionParameter {
        if (database.parameters.size <= 1) {
            throw IllegalStateException("Parameter minimal harus tersisa 1 data")
        }

        val parameter = getParameter(parameterId)
            ?: throw IllegalArgumentException("Parameter tidak ditemukan")

        database.parameters.removeAll { it.id == parameterId }

        if (database.parameters.none { it.active }) {
            database.parameters.firstOrNull()?.active = true
        } else if (parameter.active && database.parameters.none { it.productId == parameter.productId && it.active }) {
            val sameProduct = database.parameters.firstOrNull { it.productId == parameter.productId }
            if (sameProduct != null) {
                sameProduct.active = true
            } else {
                database.parameters.firstOrNull()?.active = true
            }
        }

        persist()
        addActivity(
            "Parameter produksi untuk ${getProduct(parameter.productId)?.name ?: "produk"} dihapus.",
            "orange"
        )
        return parameter
    }

    fun saveExpense(existingId: String?, dateOnly: String, category: String, amount: Long, note: String, userId: String): Expense {
        val expense = getExpense(existingId)
        val saved = if (expense == null) {
            Expense(Formatters.newId("exp"), Formatters.isoDate(dateOnly, "07:00:00"), category, amount, note, userId).also { database.expenses.add(0, it) }
        } else {
            expense.date = Formatters.isoDate(dateOnly, "07:00:00")
            expense.category = category
            expense.amount = amount
            expense.note = note
            expense
        }
        persist()
        addActivity("Pengeluaran ${saved.category} tersimpan ${Formatters.currency(saved.amount)}.", "orange")
        return saved
    }

    fun saveSettings(businessName: String, address: String, phone: String, logoText: String, receiptFooter: String, note: String) {
        database.settings.businessName = businessName
        database.settings.address = address
        database.settings.phone = phone
        database.settings.logoText = logoText
        database.settings.receiptFooter = receiptFooter
        database.settings.note = note
        persist()
        addActivity("Pengaturan usaha diperbarui.", "green")
    }

    fun saveUser(existingId: String?, name: String, email: String, role: UserRole, password: String, active: Boolean): UserAccount {
        val user = getUser(existingId)
        val saved = if (user == null) {
            UserAccount(Formatters.newId("usr"), name, email, role, password, active).also { database.users.add(0, it) }
        } else {
            user.name = name
            user.email = email
            user.role = role
            user.password = password
            user.active = active
            user.deleted = false
            user
        }
        persist()
        addActivity("Pengguna ${saved.name} tersimpan.", "blue")
        if (sessionUser()?.id == saved.id) saveSession(saved)
        return saved
    }

    fun softDeleteUser(userId: String): UserAccount {
        if (userId == DEFAULT_ADMIN || userId == DEFAULT_CASHIER) {
            throw IllegalStateException("Akun demo bawaan tidak bisa dihapus")
        }
        val user = getUser(userId) ?: throw IllegalArgumentException("Pengguna tidak ditemukan")
        user.deleted = true
        user.active = false
        if (sessionUser()?.id == user.id) saveSession(null)
        persist()
        addActivity("Pengguna ${user.name} dihapus secara soft delete.", "orange")
        return user
    }

    fun deleteSale(saleId: String): Sale {
        val sale = getSale(saleId) ?: throw IllegalArgumentException("Transaksi penjualan tidak ditemukan")
        sale.items.forEach { item -> getProduct(item.productId)?.let { it.stock += item.qty } }
        database.sales.removeAll { it.id == saleId }
        persist()
        addActivity("Transaksi ${sale.id} dihapus.", "orange")
        return sale
    }

    fun deleteProduction(productionId: String): ProductionLog {
        val item = getProductionLog(productionId) ?: throw IllegalArgumentException("Data produksi tidak ditemukan")
        val product = getProduct(item.productId) ?: throw IllegalArgumentException("Produk produksi tidak ditemukan")
        if (product.stock < item.result) {
            throw IllegalStateException("Stok hasil produksi sudah terpakai, data tidak bisa dihapus")
        }
        product.stock -= item.result
        database.productionLogs.removeAll { it.id == productionId }
        persist()
        addActivity("Produksi ${item.id} dihapus.", "orange")
        return item
    }

    fun deleteConversion(conversionId: String): ConversionLog {
        val item = getConversionLog(conversionId) ?: throw IllegalArgumentException("Data konversi tidak ditemukan")
        val fromProduct = getProduct(item.fromProductId) ?: throw IllegalArgumentException("Produk bahan tidak ditemukan")
        val toProduct = getProduct(item.toProductId) ?: throw IllegalArgumentException("Produk hasil tidak ditemukan")
        if (toProduct.stock < item.outputQty) {
            throw IllegalStateException("Stok hasil konversi sudah terpakai, data tidak bisa dihapus")
        }
        toProduct.stock -= item.outputQty
        fromProduct.stock += item.inputQty
        database.conversions.removeAll { it.id == conversionId }
        persist()
        addActivity("Konversi ${item.id} dihapus.", "orange")
        return item
    }

    fun deleteExpense(expenseId: String): Expense {
        val expense = getExpense(expenseId) ?: throw IllegalArgumentException("Pengeluaran tidak ditemukan")
        database.expenses.removeAll { it.id == expenseId }
        persist()
        addActivity("Pengeluaran ${expense.category} dihapus.", "orange")
        return expense
    }

    fun deleteAdjustment(adjustmentId: String): StockAdjustment {
        val item = getAdjustment(adjustmentId) ?: throw IllegalArgumentException("Adjustment tidak ditemukan")
        val product = getProduct(item.productId) ?: throw IllegalArgumentException("Produk adjustment tidak ditemukan")
        if (item.type == "add") {
            if (product.stock < item.qty) {
                throw IllegalStateException("Stok hasil adjustment sudah terpakai, data tidak bisa dihapus")
            }
            product.stock -= item.qty
        } else {
            product.stock += item.qty
        }
        database.stockAdjustments.removeAll { it.id == adjustmentId }
        persist()
        addActivity("Adjustment ${item.id} dihapus.", "orange")
        return item
    }

    private fun detailLine(label: String, value: String): String = label.padEnd(15, ' ') + ": " + value

    private fun separator(): String = "────────────────────────────"

    fun buildReceiptText(saleId: String): String {
        val sale = getSale(saleId) ?: return "Data transaksi tidak ditemukan"
        val cashier = getUser(sale.cashierId)
        return buildString {
            appendLine("NOTA PENJUALAN")
            appendLine(database.settings.businessName)
            appendLine(database.settings.address)
            appendLine(database.settings.phone)
            appendLine(separator())
            appendLine(detailLine("No. Transaksi", sale.id))
            appendLine(detailLine("Tanggal", Formatters.readableDateTime(sale.date)))
            appendLine(detailLine("Sumber", sale.source))
            appendLine(detailLine("Kasir", cashier?.name ?: "Demo User"))
            appendLine(detailLine("Pembayaran", sale.paymentMethod))
            appendLine(separator())
            appendLine("RINCIAN ITEM")
            sale.items.forEachIndexed { index, item ->
                val product = getProduct(item.productId)
                val subtotal = item.qty.toLong() * item.price
                appendLine("${index + 1}. ${product?.name ?: "Produk"}")
                appendLine("   ${item.qty} x ${Formatters.currency(item.price)}")
                appendLine("   ${detailLine("Subtotal", Formatters.currency(subtotal))}")
            }
            appendLine(separator())
            appendLine(detailLine("Total", Formatters.currency(sale.total)))
            appendLine(detailLine("Bayar", Formatters.currency(sale.cashPaid)))
            appendLine(detailLine("Kembalian", Formatters.currency(maxOf(sale.cashPaid - sale.total, 0))))
            appendLine(separator())
            appendLine(database.settings.receiptFooter)
        }
    }

    fun buildProductionDetailText(productionId: String): String {
        val item = getProductionLog(productionId) ?: return "Detail produksi tidak ditemukan"
        val product = getProduct(item.productId)
        val user = getUser(item.createdBy)
        return buildString {
            appendLine("DETAIL PRODUKSI")
            appendLine(separator())
            appendLine(detailLine("ID Produksi", item.id))
            appendLine(detailLine("Tanggal", Formatters.readableDateTime(item.date)))
            appendLine(detailLine("Produk", product?.name ?: item.productId))
            appendLine(detailLine("Jumlah Masak", item.batches.toString()))
            appendLine(detailLine("Hasil", item.result.toString() + " " + (product?.unit ?: "pcs")))
            appendLine(detailLine("Catatan", item.note.ifBlank { "-" }))
            appendLine(detailLine("Dicatat Oleh", user?.name ?: item.createdBy))
        }
    }

    fun buildConversionDetailText(conversionId: String): String {
        val item = getConversionLog(conversionId) ?: return "Detail produksi turunan tidak ditemukan"
        val from = getProduct(item.fromProductId)
        val to = getProduct(item.toProductId)
        val user = getUser(item.createdBy)
        return buildString {
            appendLine("DETAIL KONVERSI")
            appendLine(separator())
            appendLine(detailLine("ID Konversi", item.id))
            appendLine(detailLine("Tanggal", Formatters.readableDateTime(item.date)))
            appendLine(detailLine("Produk Bahan", from?.name ?: item.fromProductId))
            appendLine(detailLine("Jumlah Bahan", item.inputQty.toString() + " " + (from?.unit ?: "pcs")))
            appendLine(detailLine("Produk Hasil", to?.name ?: item.toProductId))
            appendLine(detailLine("Jumlah Hasil", item.outputQty.toString() + " " + (to?.unit ?: "pcs")))
            appendLine(detailLine("Catatan", item.note.ifBlank { "-" }))
            appendLine(detailLine("Dicatat Oleh", user?.name ?: item.createdBy))
        }
    }

    fun buildExpenseDetailText(expenseId: String): String {
        val item = getExpense(expenseId) ?: return "Detail pengeluaran tidak ditemukan"
        val user = getUser(item.createdBy)
        return buildString {
            appendLine("DETAIL PENGELUARAN")
            appendLine(separator())
            appendLine(detailLine("ID Pengeluaran", item.id))
            appendLine(detailLine("Tanggal", Formatters.readableDateTime(item.date)))
            appendLine(detailLine("Kategori", item.category))
            appendLine(detailLine("Nominal", Formatters.currency(item.amount)))
            appendLine(detailLine("Catatan", item.note.ifBlank { "-" }))
            appendLine(detailLine("Dicatat Oleh", user?.name ?: item.createdBy))
        }
    }

    fun buildAdjustmentDetailText(adjustmentId: String): String {
        val item = getAdjustment(adjustmentId) ?: return "Detail adjustment tidak ditemukan"
        val product = getProduct(item.productId)
        val user = getUser(item.createdBy)
        val direction = if (item.type == "add") "Tambah" else "Kurangi"
        return buildString {
            appendLine("DETAIL ADJUSTMENT STOK")
            appendLine(separator())
            appendLine(detailLine("ID Adjustment", item.id))
            appendLine(detailLine("Tanggal", Formatters.readableDateTime(item.date)))
            appendLine(detailLine("Produk", product?.name ?: item.productId))
            appendLine(detailLine("Jenis", direction))
            appendLine(detailLine("Jumlah", item.qty.toString() + " " + (product?.unit ?: "pcs")))
            appendLine(detailLine("Catatan", item.note.ifBlank { "-" }))
            appendLine(detailLine("Dicatat Oleh", user?.name ?: item.createdBy))
        }
    }

    fun buildTransactionDetailText(rowId: String, type: String): String = when (type) {
        "Penjualan", "Rekap Pasar" -> buildReceiptText(rowId)
        "Produksi" -> buildProductionDetailText(rowId)
        "Konversi" -> buildConversionDetailText(rowId)
        "Pengeluaran" -> buildExpenseDetailText(rowId)
        "Adjustment" -> buildAdjustmentDetailText(rowId)
        else -> "Detail belum tersedia"
    }

    fun buildCsv(rangeKey: String): String {
        val summary = reportSummary(rangeKey)
        return buildString {
            appendLine("Kategori,Nilai")
            appendLine("Penjualan,${summary.totalSales}")
            appendLine("Pengeluaran,${summary.totalExpenses}")
            appendLine("Produksi,${summary.totalProduction}")
            appendLine("Laba Kotor,${summary.totalProfit}")
            appendLine()
            appendLine("ID,Tanggal,Sumber,Total")
            database.sales.forEach { sale -> appendLine("${sale.id},${sale.date},${sale.source},${sale.total}") }
        }
    }
}
