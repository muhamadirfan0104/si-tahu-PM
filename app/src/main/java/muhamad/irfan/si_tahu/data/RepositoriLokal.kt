package muhamad.irfan.si_tahu.data

import kotlin.math.roundToInt
import android.content.Context
import android.content.SharedPreferences
import muhamad.irfan.si_tahu.util.Formatter
import com.google.gson.GsonBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RepositoriLokal {
    private const val PREFS_NAME = "si_tahu_lokal"
    private const val KEY_DB = "basis_data_lokal_json"
    private const val KEY_USER_ID = "session_user_id"
    private const val KEY_ROLE = "session_role"
        
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var prefs: SharedPreferences
    private var database: BasisDataLokal = DataAwal.create()
    private var isReady: Boolean = false

    val cart: MutableList<ItemKeranjang> = mutableListOf()
    var checkoutMethod: String = "Tunai"
    var cashPaid: Long = 0

    fun init(context: Context) {
        if (isReady) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_DB, null)
        database = if (raw.isNullOrBlank()) {
            DataAwal.create()
        } else {
            runCatching { gson.fromJson(raw, BasisDataLokal::class.java) }.getOrElse { DataAwal.create() }
        }
        isReady = true
    }

    private fun persist() {
        prefs.edit().putString(KEY_DB, gson.toJson(database)).apply()
    }


    fun db(): BasisDataLokal = database

    fun allProducts(includeDeleted: Boolean = false): List<Produk> =
        database.products.filter { includeDeleted || !it.deleted }

    fun allUsers(includeDeleted: Boolean = false): List<AkunPengguna> =
        database.users.filter { includeDeleted || !it.deleted }

    fun allParameters(): List<ParameterProduksi> = database.parameters
    fun allExpenses(): List<Pengeluaran> = database.expenses
    fun allSales(): List<Penjualan> = database.sales.sortedByDescending { Formatter.parseDate(it.date) }
    fun allProductionLogs(): List<CatatanProduksi> = database.productionLogs.sortedByDescending { Formatter.parseDate(it.date) }
    fun allConversions(): List<CatatanKonversi> = database.conversions.sortedByDescending { Formatter.parseDate(it.date) }
    fun allAdjustments(): List<PenyesuaianStok> = database.stockAdjustments.sortedByDescending { Formatter.parseDate(it.date) }

    fun getProduct(id: String?): Produk? = database.products.firstOrNull { it.id == id }
    fun getUser(id: String?): AkunPengguna? = database.users.firstOrNull { it.id == id }
    fun getParameter(id: String?): ParameterProduksi? = database.parameters.firstOrNull { it.id == id }
    fun getExpense(id: String?): Pengeluaran? = database.expenses.firstOrNull { it.id == id }
    fun getSale(id: String?): Penjualan? = database.sales.firstOrNull { it.id == id }
    fun getProductionLog(id: String?): CatatanProduksi? = database.productionLogs.firstOrNull { it.id == id }
    fun getConversionLog(id: String?): CatatanKonversi? = database.conversions.firstOrNull { it.id == id }
    fun getAdjustment(id: String?): PenyesuaianStok? = database.stockAdjustments.firstOrNull { it.id == id }

    fun visibleChannels(product: Produk?): List<HargaKanal> =
        product?.channels?.filter { !it.deleted } ?: emptyList()

    fun sessionRole(): PeranPengguna? = prefs.getString(KEY_ROLE, null)?.let {
        runCatching { PeranPengguna.valueOf(it) }.getOrNull()
    }

    fun sessionUser(): AkunPengguna? = getUser(prefs.getString(KEY_USER_ID, null))?.takeUnless { it.deleted }

    private fun saveSession(user: AkunPengguna?) {
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
        cashPaid = 0
    }

    fun login(email: String, password: String): AkunPengguna? {
        val user = database.users.firstOrNull {
            !it.deleted &&
                it.email.equals(email.trim(), ignoreCase = true) &&
                it.password == password &&
                it.active
        }
        saveSession(user)
        return user
    }

    fun defaultChannel(product: Produk?): HargaKanal? {
        val channels = visibleChannels(product)
        return channels.firstOrNull { it.defaultCashier && it.active } ?: channels.firstOrNull { it.active }
    }

    fun channelByLabel(product: Produk?, label: String): HargaKanal? {
        return visibleChannels(product).firstOrNull { it.label.equals(label, ignoreCase = true) && it.active }
            ?: defaultChannel(product)
    }

    fun activeParameter(productId: String?): ParameterProduksi? {
        return database.parameters.firstOrNull { it.productId == productId && it.active }
    }

    fun productStatus(product: Produk): String = when {
        product.stock <= 0 -> "Habis"
        product.stock <= product.minStock -> "Menipis"
        else -> "Aman"
    }

    fun lowStockProducts(): List<Produk> {
        return allProducts().filter { it.active && it.stock <= it.minStock }
    }

    fun latestReferenceDate(): Date {
        val dates = mutableListOf<Date>()
        database.sales.mapTo(dates) { Formatter.parseDate(it.date) }
        database.expenses.mapTo(dates) { Formatter.parseDate(it.date) }
        database.productionLogs.mapTo(dates) { Formatter.parseDate(it.date) }
        database.conversions.mapTo(dates) { Formatter.parseDate(it.date) }
        database.stockAdjustments.mapTo(dates) { Formatter.parseDate(it.date) }
        return dates.maxOrNull() ?: Date()
    }

    fun latestDateOnly(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(latestReferenceDate())
    fun latestTimeOnly(): String = SimpleDateFormat("HH:mm", Locale("id", "ID")).format(latestReferenceDate())

    fun daySummary(dateOnly: String = latestDateOnly()): RingkasanHarian {
        val sales = database.sales.filter { Formatter.toDateOnly(it.date) == dateOnly }
        val expenses = database.expenses.filter { Formatter.toDateOnly(it.date) == dateOnly }
        val production = database.productionLogs.filter { Formatter.toDateOnly(it.date) == dateOnly }
        val totalSales = sales.sumOf { it.total }
        val totalExpenses = expenses.sumOf { it.amount }
        val totalProduction = production.sumOf { it.result }
        return RingkasanHarian(totalSales, totalExpenses, totalProduction, totalSales - totalExpenses, sales.size)
    }

    private fun inRange(dateValue: String, rangeKey: String): Boolean {
        if (rangeKey == "semua") return true
        val days = rangeKey.toIntOrNull() ?: 7
        val ref = latestReferenceDate().time
        val target = Formatter.parseDate(dateValue).time
        return ref - target <= days * 24L * 60L * 60L * 1000L
    }

    fun topProducts(limit: Int = 4, source: String? = null): List<ProdukTerlaris> {
        val map = linkedMapOf<String, ProdukTerlaris>()
        database.sales.filter { source == null || it.source == source }.forEach { sale ->
            sale.items.forEach { item ->
                val product = getProduct(item.productId)
                val old = map[item.productId]
                map[item.productId] = ProdukTerlaris(item.productId, product?.name ?: "Produk", (old?.qty ?: 0) + item.qty)
            }
        }
        return map.values.sortedByDescending { it.qty }.take(limit)
    }

    fun reportSummary(rangeKey: String): RingkasanLaporan {
        val sales = database.sales.filter { inRange(it.date, rangeKey) }
        val expenses = database.expenses.filter { inRange(it.date, rangeKey) }
        val production = database.productionLogs.filter { inRange(it.date, rangeKey) }
        val totalSales = sales.sumOf { it.total }
        val totalExpenses = expenses.sumOf { it.amount }
        val totalProduction = production.sumOf { it.result }
        return RingkasanLaporan(totalSales, totalExpenses, totalProduction, totalSales - totalExpenses, sales.size, topProducts(4))
    }

    fun transactions(): List<BarisTransaksi> {
        val rows = mutableListOf<BarisTransaksi>()
        database.sales.forEach { sale ->
            rows += BarisTransaksi(
                id = sale.id,
                date = sale.date,
                type = if (sale.source == "KASIR") "Penjualan" else "Rekap Pasar",
                subtitle = sale.items.joinToString(", ") {
                    val product = getProduct(it.productId)
                    (product?.name ?: "Produk") + " x" + Formatter.ribuan(it.qty.toLong())
                },
                valueText = Formatter.currency(sale.total),
                source = sale.source,
                saleId = sale.id
            )
        }
        database.productionLogs.forEach { item ->
            rows += BarisTransaksi(
                id = item.id,
                date = item.date,
                type = "Produksi",
                subtitle = (getProduct(item.productId)?.name ?: "Produk") + " • " + formatBatch(item.batches) + " masak",
                valueText = Formatter.ribuan(item.result.toLong()) + " pcs",
                source = "PRODUKSI"
            )
        }
        database.conversions.forEach { item ->
            rows += BarisTransaksi(
                id = item.id,
                date = item.date,
                type = "Produk Olahan",
                subtitle = (getProduct(item.fromProductId)?.name ?: "Bahan") + " -> " + (getProduct(item.toProductId)?.name ?: "Hasil"),
                valueText = Formatter.ribuan(item.outputQty.toLong()) + " pcs",
                source = "KONVERSI"
            )
        }
        database.expenses.forEach { item ->
            rows += BarisTransaksi(
                id = item.id,
                date = item.date,
                type = "Pengeluaran",
                subtitle = item.category,
                valueText = Formatter.currency(item.amount),
                source = "PENGELUARAN"
            )
        }
        database.stockAdjustments.forEach { item ->
            val product = getProduct(item.productId)
            val qtyLabel = if (item.type == "add") "+${Formatter.ribuan(item.qty.toLong())}" else "-${Formatter.ribuan(item.qty.toLong())}"
            rows += BarisTransaksi(
                id = item.id,
                date = item.date,
                type = "Adjustment",
                subtitle = (product?.name ?: "Produk") + " • " + qtyLabel + " " + (product?.unit ?: "pcs"),
                valueText = if (item.type == "add") "Tambah" else "Kurang",
                source = "ADJUSTMENT"
            )
        }
        return rows.sortedByDescending { Formatter.parseDate(it.date) }
    }

    fun stockMovements(productId: String): List<PergerakanStok> {
        val product = getProduct(productId) ?: return emptyList()
        val rows = mutableListOf<PergerakanStok>()
        database.productionLogs.filter { it.productId == productId }.forEach {
            rows += PergerakanStok(it.id, it.date, "Produksi masuk", it.note, "+${it.result} ${product.unit}", "green")
        }
        database.conversions.forEach {
            if (it.fromProductId == productId) {
                rows += PergerakanStok(it.id + "-out", it.date, "Produk Olahan keluar", "Dipakai ke ${getProduct(it.toProductId)?.name ?: "produk"}", "-${it.inputQty} ${product.unit}", "orange")
            }
            if (it.toProductId == productId) {
                rows += PergerakanStok(it.id + "-in", it.date, "Produk Olahan masuk", "Hasil dari ${getProduct(it.fromProductId)?.name ?: "produk"}", "+${it.outputQty} ${product.unit}", "green")
            }
        }
        database.sales.forEach { sale ->
            sale.items.filter { it.productId == productId }.forEach { item ->
                rows += PergerakanStok(sale.id + productId, sale.date, "Penjualan keluar", sale.source + " • " + sale.id, "-${Formatter.ribuan(item.qty.toLong())} ${product.unit}", if (sale.source == "KASIR") "gold" else "blue")
            }
        }
        database.stockAdjustments.filter { it.productId == productId }.forEach {
            val sign = if (it.type == "add") "+" else "-"
            rows += PergerakanStok(it.id, it.date, "Adjustment ${if (it.type == "add") "masuk" else "keluar"}", it.note, "$sign${Formatter.ribuan(it.qty.toLong())} ${product.unit}", if (it.type == "add") "green" else "orange")
        }
        return rows.sortedByDescending { Formatter.parseDate(it.date) }.take(8)
    }

    private fun addActivity(message: String, tone: String) {
        database.activities.add(0, CatatanAktivitas(Formatter.newId("act"), message, nowIso(), tone))
        database.activities = database.activities.take(30).toMutableList()
        persist()
    }

    private fun nowIso(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        return format.format(Date())
    }

    fun formatBatch(value: Double): String {
        return if (value % 1.0 == 0.0) {
            Formatter.ribuan(value.toLong())
        } else {
            value.toString().replace(".", ",")
        }
    }

    fun addToCart(productId: String) {
        val product = getProduct(productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        if (product.deleted || !product.active || !product.showInCashier) throw IllegalStateException("Produk tidak tampil di kasir")
        if (product.stock <= 0) throw IllegalStateException("Stok produk habis")
        val current = cart.firstOrNull { it.productId == productId }
        val newQty = (current?.qty ?: 0) + 1
        if (newQty > product.stock) throw IllegalStateException("Jumlah melebihi stok tersedia")
        if (current == null) cart += ItemKeranjang(productId, 1, defaultChannel(product)?.price ?: 0)
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

    fun checkoutCart(userId: String, paymentMethod: String, cashInput: Long): Penjualan {
        if (cart.isEmpty()) throw IllegalStateException("Keranjang masih kosong")
        val total = cartTotal()
        if (paymentMethod == "Tunai" && cashInput < total) throw IllegalStateException("Uang bayar belum cukup")
        cart.forEach { item ->
            val product = getProduct(item.productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
            if (product.stock < item.qty) throw IllegalStateException("Stok ${product.name} tidak cukup")
        }
        cart.forEach { item -> getProduct(item.productId)?.let { it.stock -= item.qty } }
        val sale = Penjualan(
            id = "TRX-" + nowIso().replace("-", "").replace(":", "").replace("T", "").take(14),
            date = nowIso(),
            source = "KASIR",
            cashierId = userId,
            paymentMethod = paymentMethod,
            cashPaid = if (paymentMethod == "Tunai") cashInput else total,
            total = total,
            items = cart.map { ItemPenjualan(it.productId, it.qty, it.price) }.toMutableList()
        )
        database.sales.add(0, sale)
        persist()
        addActivity("Transaksi ${sale.id} selesai ${Formatter.currency(total)}.", "blue")
        cart.clear()
        checkoutMethod = paymentMethod
        cashPaid = total
        return sale
    }

    fun saveMarketRecap(dateOnly: String, source: String, items: List<ItemDraftRekap>, userId: String): Penjualan {
        if (items.isEmpty()) throw IllegalStateException("Belum ada item rekap")
        items.forEach {
            val product = getProduct(it.productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
            if (product.stock < it.qty) throw IllegalStateException("Stok ${product.name} tidak cukup")
        }
        items.forEach { getProduct(it.productId)?.let { product -> product.stock -= it.qty } }
        val total = items.sumOf { it.qty.toLong() * it.price }
        val sale = Penjualan(
            id = "REKAP-" + nowIso().replace("-", "").replace(":", "").replace("T", "").take(14),
            date = Formatter.isoDate(dateOnly, "13:00:00"),
            source = source,
            cashierId = userId,
            paymentMethod = "Rekap",
            cashPaid = total,
            total = total,
            items = items.map { ItemPenjualan(it.productId, it.qty, it.price) }.toMutableList()
        )
        database.sales.add(0, sale)
        persist()
        addActivity("Rekap ${sale.source} tersimpan ${Formatter.currency(total)}.", "orange")
        return sale
    }

    fun saveProduction(
        dateTime: String,
        productId: String,
        batches: Double,
        note: String,
        userId: String
    ): CatatanProduksi {
        val product = getProduct(productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        require(batches > 0) { "Jumlah masak harus lebih dari 0" }

        val parameter = activeParameter(productId)
        val resultPerBatch = parameter?.resultPerBatch ?: 100
        val result = (resultPerBatch * batches).roundToInt()

        product.stock += result

        val item = CatatanProduksi(
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
        addActivity(
            "Produksi ${product.name} ${formatBatch(batches)}x masak bertambah ${Formatter.ribuan(result.toLong())} ${product.unit}.",
            "green"
        )
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
    ): CatatanKonversi {
        val fromProduct = getProduct(fromProductId) ?: throw IllegalArgumentException("Produk bahan tidak ditemukan")
        val toProduct = getProduct(toProductId) ?: throw IllegalArgumentException("Produk hasil tidak ditemukan")
        require(fromProductId != toProductId) { "Bahan dan hasil harus berbeda" }
        require(inputQty > 0 && outputQty > 0) { "Jumlah harus lebih dari 0" }
        if (fromProduct.stock < inputQty) throw IllegalStateException("Stok bahan tidak cukup")
        fromProduct.stock -= inputQty
        toProduct.stock += outputQty
        val item = CatatanKonversi(
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
        addActivity("Produk olahan ${fromProduct.name} -> ${toProduct.name} berhasil.", "blue")
        return item
    }

    fun adjustStock(dateOnly: String, productId: String, type: String, qty: Int, note: String, userId: String): PenyesuaianStok {
        val product = getProduct(productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        require(qty > 0) { "Jumlah harus lebih dari 0" }
        if (type == "subtract" && product.stock < qty) throw IllegalStateException("Stok tidak cukup untuk dikurangi")
        product.stock += if (type == "add") qty else -qty
        val item = PenyesuaianStok(
            id = "ADJ-" + nowIso().replace("-", "").replace(":", "").replace("T", "").take(14),
            date = Formatter.isoDate(dateOnly, "16:00:00"),
            productId = productId,
            type = type,
            qty = qty,
            note = note,
            createdBy = userId
        )
        database.stockAdjustments.add(0, item)
        persist()
        addActivity("Adjustment stok ${product.name} ${if (type == "add") "+" else "-"}${Formatter.ribuan(qty.toLong())} ${product.unit}.", if (type == "add") "green" else "orange")
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
    ): Produk {
        val product = getProduct(existingId)
        return if (product == null) {
            val created = Produk(
                id = Formatter.newId("prd"),
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
                    HargaKanal(Formatter.newId("chn"), "KASIR", defaultPrice, true, true),
                    HargaKanal(Formatter.newId("chn"), "PASAR", maxOf(defaultPrice - 200, 0), true, false)
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

    fun softDeleteProduct(id: String): Produk {
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

    fun toggleProduct(id: String): Produk {
        val product = getProduct(id) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        product.active = !product.active
        persist()
        addActivity("Status produk ${product.name} diubah menjadi ${if (product.active) "aktif" else "nonaktif"}.", if (product.active) "green" else "orange")
        return product
    }

    fun saveChannelPrice(productId: String, existingId: String?, label: String, price: Long, active: Boolean, defaultCashier: Boolean): HargaKanal {
        val product = getProduct(productId) ?: throw IllegalArgumentException("Produk tidak ditemukan")
        val priceRow = product.channels.firstOrNull { it.id == existingId }
        val channel = if (priceRow == null) {
            HargaKanal(Formatter.newId("chn"), label.uppercase(), price, active, defaultCashier).also { product.channels.add(it) }
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

    fun softDeleteChannel(productId: String, channelId: String): HargaKanal {
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

    fun saveParameter(existingId: String?, productId: String, resultPerBatch: Int, note: String, active: Boolean): ParameterProduksi {
        val parameter = getParameter(existingId)
        val saved = if (parameter == null) {
            ParameterProduksi(Formatter.newId("prm"), productId, resultPerBatch, note, active).also { database.parameters.add(0, it) }
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

    fun deleteParameter(parameterId: String): ParameterProduksi {
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

    fun saveExpense(existingId: String?, dateOnly: String, category: String, amount: Long, note: String, userId: String): Pengeluaran {
        val expense = getExpense(existingId)
        val saved = if (expense == null) {
            Pengeluaran(Formatter.newId("exp"), Formatter.isoDate(dateOnly, "07:00:00"), category, amount, note, userId).also { database.expenses.add(0, it) }
        } else {
            expense.date = Formatter.isoDate(dateOnly, "07:00:00")
            expense.category = category
            expense.amount = amount
            expense.note = note
            expense
        }
        persist()
        addActivity("Pengeluaran ${saved.category} tersimpan ${Formatter.currency(saved.amount)}.", "orange")
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

    fun saveUser(existingId: String?, name: String, email: String, role: PeranPengguna, password: String, active: Boolean): AkunPengguna {
        val user = getUser(existingId)
        val saved = if (user == null) {
            AkunPengguna(Formatter.newId("usr"), name, email, role, password, active).also { database.users.add(0, it) }
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
    fun softDeleteUser(userId: String): AkunPengguna {
        val user = getUser(userId) ?: throw IllegalArgumentException("Pengguna tidak ditemukan")
        user.deleted = true
        user.active = false
        if (sessionUser()?.id == user.id) saveSession(null)
        persist()
        addActivity("Pengguna ${user.name} dihapus secara soft delete.", "orange")
        return user
    }

    fun deleteSale(saleId: String): Penjualan {
        val sale = getSale(saleId) ?: throw IllegalArgumentException("Transaksi penjualan tidak ditemukan")
        sale.items.forEach { item -> getProduct(item.productId)?.let { it.stock += item.qty } }
        database.sales.removeAll { it.id == saleId }
        persist()
        addActivity("Transaksi ${sale.id} dihapus.", "orange")
        return sale
    }

    fun deleteProduction(productionId: String): CatatanProduksi {
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

    fun deleteConversion(conversionId: String): CatatanKonversi {
        val item = getConversionLog(conversionId) ?: throw IllegalArgumentException("Data produk olahan tidak ditemukan")
        val fromProduct = getProduct(item.fromProductId) ?: throw IllegalArgumentException("Produk bahan tidak ditemukan")
        val toProduct = getProduct(item.toProductId) ?: throw IllegalArgumentException("Produk hasil tidak ditemukan")
        if (toProduct.stock < item.outputQty) {
            throw IllegalStateException("Stok hasil produk olahan sudah terpakai, data tidak bisa dihapus")
        }
        toProduct.stock -= item.outputQty
        fromProduct.stock += item.inputQty
        database.conversions.removeAll { it.id == conversionId }
        persist()
        addActivity("Produk olahan ${item.id} dihapus.", "orange")
        return item
    }

    fun deleteExpense(expenseId: String): Pengeluaran {
        val expense = getExpense(expenseId) ?: throw IllegalArgumentException("Pengeluaran tidak ditemukan")
        database.expenses.removeAll { it.id == expenseId }
        persist()
        addActivity("Pengeluaran ${expense.category} dihapus.", "orange")
        return expense
    }

    fun deleteAdjustment(adjustmentId: String): PenyesuaianStok {
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
            appendLine(detailLine("Tanggal", Formatter.readableDateTime(sale.date)))
            appendLine(detailLine("Kasir", cashier?.name ?: "Pengguna"))
            appendLine(detailLine("Pembayaran", sale.paymentMethod))
            appendLine(separator())
            appendLine("RINCIAN ITEM")
            sale.items.forEachIndexed { index, item ->
                val product = getProduct(item.productId)
                val subtotal = item.qty.toLong() * item.price
                appendLine("${index + 1}. ${product?.name ?: "Produk"}")
                appendLine("   ${Formatter.ribuan(item.qty.toLong())} x ${Formatter.currency(item.price)}")
                appendLine("   ${detailLine("Subtotal", Formatter.currency(subtotal))}")
            }
            appendLine(separator())
            appendLine(detailLine("Total", Formatter.currency(sale.total)))
            appendLine(detailLine("Bayar", Formatter.currency(sale.cashPaid)))
            appendLine(detailLine("Kembalian", Formatter.currency(maxOf(sale.cashPaid - sale.total, 0))))
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
            appendLine(detailLine("Tanggal", Formatter.readableDateTime(item.date)))
            appendLine(detailLine("Produk", product?.name ?: item.productId))
            appendLine(detailLine("Jumlah Masak", formatBatch(item.batches)))
            appendLine(detailLine("Hasil", Formatter.ribuan(item.result.toLong()) + " " + (product?.unit ?: "pcs")))
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
            appendLine(detailLine("ID Produk Olahan", item.id))
            appendLine(detailLine("Tanggal", Formatter.readableDateTime(item.date)))
            appendLine(detailLine("Produk Bahan", from?.name ?: item.fromProductId))
            appendLine(detailLine("Jumlah Bahan", Formatter.ribuan(item.inputQty.toLong()) + " " + (from?.unit ?: "pcs")))
            appendLine(detailLine("Produk Hasil", to?.name ?: item.toProductId))
            appendLine(detailLine("Jumlah Hasil", Formatter.ribuan(item.outputQty.toLong()) + " " + (to?.unit ?: "pcs")))
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
            appendLine(detailLine("Tanggal", Formatter.readableDateTime(item.date)))
            appendLine(detailLine("Kategori", item.category))
            appendLine(detailLine("Nominal", Formatter.currency(item.amount)))
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
            appendLine(detailLine("Tanggal", Formatter.readableDateTime(item.date)))
            appendLine(detailLine("Produk", product?.name ?: item.productId))
            appendLine(detailLine("Jenis", direction))
            appendLine(detailLine("Jumlah", Formatter.ribuan(item.qty.toLong()) + " " + (product?.unit ?: "pcs")))
            appendLine(detailLine("Catatan", item.note.ifBlank { "-" }))
            appendLine(detailLine("Dicatat Oleh", user?.name ?: item.createdBy))
        }
    }

    fun buildTransactionDetailText(rowId: String, type: String): String = when (type) {
        "Penjualan", "Rekap Pasar" -> buildReceiptText(rowId)
        "Produksi" -> buildProductionDetailText(rowId)
        "Produk Olahan" -> buildConversionDetailText(rowId)
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
