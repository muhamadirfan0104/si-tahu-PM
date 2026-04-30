package muhamad.irfan.si_tahu.ui.penjualan

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import muhamad.irfan.si_tahu.data.ItemKeranjang
import muhamad.irfan.si_tahu.data.Produk
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.data.SessionKeranjangRumahan
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.SiTahuProTheme
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.InputAngka
import muhamad.irfan.si_tahu.util.PembuatQrBitmap
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

// Model data Xendit
data class XenditQris(
    val saleId: String, val externalId: String, val qrId: String, val total: Long, val status: String,
    val qrString: String, val createdAtMillis: Long, val expiresAtMillis: Long
) {
    fun remainingMs(): Long = expiresAtMillis - System.currentTimeMillis()
    fun isExpired(): Boolean = remainingMs() <= 0L
}

data class XenditStatus(
    val paid: Boolean, val status: String, val paymentId: String, val source: String, val receiptId: String, val paidAt: String, val amount: Long
)

class AktivitasPenjualanRumahan : AktivitasDasar() {

    companion object {
        const val MODE_ALL = "Semua Stok"
        const val MODE_READY = "Siap Dijual"
        const val MODE_EMPTY = "Habis"

        const val STATUS_PRODUCED_TODAY = "Produksi Hari Ini"
        const val STATUS_LEFTOVER = "Stok Sisa"
        const val STATUS_ED_TODAY = "ED Hari Ini"
        const val STATUS_NEAR_EXPIRED = "Hampir Kadaluarsa"
        const val STATUS_EXPIRED = "Kadaluarsa"
        const val STATUS_EMPTY = "Habis"

        private const val XENDIT_API_BASE = "https://xendit-sitahu-api.vercel.app"
        private const val XENDIT_TEST_MODE = false
        private const val QRIS_EXPIRE_MS = 15 * 60 * 1000L
    }

    private val uiRefreshTrigger = MutableStateFlow(0)
    private var productsCache: List<Produk> = emptyList()
    private var pendingQris by mutableStateOf<XenditQris?>(null)
    private var pendingItems: List<ItemKeranjang> = emptyList()
    private var isProcessing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        SessionKeranjangRumahan.init(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                keluarKasirDenganReset()
            }
        })

        setContent {
            SiTahuProTheme {
                CashierPOSScreen(
                    onNavigateBack = { keluarKasirDenganReset() },
                    onShowMessage = { pesan -> showMessage(pesan) }
                )
            }
        }
    }

    private fun keluarKasirDenganReset() {
        SessionKeranjangRumahan.clear()
        pendingQris = null
        pendingItems = emptyList()
        finish()
    }

    override fun onResume() {
        super.onResume()
        uiRefreshTrigger.value++
    }

    // --- FUNGSI BANTUAN LOGIKA BISNIS ---
    fun defaultPrice(product: Produk): Long = product.channels.firstOrNull { it.defaultCashier && it.active }?.price ?: product.channels.firstOrNull { it.active }?.price ?: 0L
    fun hasValidCashierPrice(product: Produk): Boolean = defaultPrice(product) > 0L
    fun stokLayakJual(product: Produk): Int = product.safeStock + product.nearExpiredStock + product.edTodayStock

    fun productStatus(product: Produk): String = when {
        stokLayakJual(product) <= 0 && product.expiredStock > 0 -> STATUS_EXPIRED
        stokLayakJual(product) <= 0 -> STATUS_EMPTY
        product.edTodayStock > 0 -> STATUS_ED_TODAY
        product.nearExpiredStock > 0 -> STATUS_NEAR_EXPIRED
        product.producedToday -> STATUS_PRODUCED_TODAY
        else -> STATUS_LEFTOVER
    }

    private fun labelProduksiTerakhir(product: Produk): String {
        val last = product.lastProductionDate.trim()
        if (last.isBlank()) return ""
        return runCatching {
            val todayMillis = Formatter.parseDate("${Formatter.currentDateOnly()}T00:00:00").time
            val lastMillis = Formatter.parseDate("${last}T00:00:00").time
            val days = TimeUnit.MILLISECONDS.toDays(todayMillis - lastMillis).toInt()
            when (days) {
                0 -> "Produksi terakhir: Hari ini"
                1 -> "Produksi terakhir: Kemarin"
                in 2..Int.MAX_VALUE -> "Produksi terakhir: $days hari lalu"
                else -> "Produksi terakhir: ${Formatter.readableShortDate(last)}"
            }
        }.getOrDefault("Produksi terakhir: ${Formatter.readableShortDate(last)}")
    }

    fun productComparator(): Comparator<Produk> = compareBy<Produk>({ statusRank(productStatus(it)) }, { it.name.lowercase() })

    private fun statusRank(status: String): Int = when (status) {
        STATUS_ED_TODAY -> 0
        STATUS_NEAR_EXPIRED -> 1
        STATUS_PRODUCED_TODAY -> 2
        STATUS_LEFTOVER -> 3
        STATUS_EMPTY -> 4
        STATUS_EXPIRED -> 5
        else -> 5
    }

    // ==========================================
    // TAMPILAN JETPACK COMPOSE UTAMA
    // ==========================================
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CashierPOSScreen(
        onNavigateBack: () -> Unit,
        onShowMessage: (String) -> Unit
    ) {
        val isDark = isSystemInDarkTheme()
        val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
        val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
        val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
        val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
        val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
        val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
        val successColor = if (isDark) Color(0xFF10B981) else Color(0xFF059669)
        val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)
        val warningColor = if (isDark) Color(0xFFF59E0B) else Color(0xFFD97706)

        var products by remember { mutableStateOf<List<Produk>>(emptyList()) }
        var categoryOptions by remember { mutableStateOf(listOf("Semua Kategori")) }
        var isLoading by remember { mutableStateOf(true) }

        // STATE BARU UNTUK MENYEMBUNYIKAN/MENAMPILKAN FILTER
        var isFilterVisible by remember { mutableStateOf(false) }

        var keyword by remember { mutableStateOf("") }
        var activeCategory by remember { mutableStateOf("Semua Kategori") }
        var activeMode by remember { mutableStateOf(MODE_READY) }
        val modeOptions = listOf(MODE_ALL, MODE_READY, MODE_EMPTY)

        var currentPage by remember { mutableStateOf(1) }
        val pageSize = 12

        val lifecycleTrigger by uiRefreshTrigger.collectAsState()
        var localCartTrigger by remember { mutableStateOf(0) }
        val combinedCartTrigger = lifecycleTrigger + localCartTrigger

        val bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
        val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            runCatching { RepositoriFirebaseUtama.muatProdukKasir() }
                .onSuccess { result ->
                    val validProducts = result.filter { hasValidCashierPrice(it) }.sortedWith(productComparator())
                    products = validProducts
                    productsCache = validProducts
                    categoryOptions = listOf("Semua Kategori") + validProducts.map { it.category }.distinct().sorted()
                    isLoading = false
                }
                .onFailure {
                    isLoading = false
                    onShowMessage(it.message ?: "Gagal memuat produk")
                }
        }

        val filteredProducts by remember(products, keyword, activeCategory, activeMode) {
            derivedStateOf {
                val lowKeyword = keyword.trim().lowercase()
                products.filter { p ->
                    val cocokKeyword = lowKeyword.isBlank() || p.name.lowercase().contains(lowKeyword) || p.code.lowercase().contains(lowKeyword)
                    val cocokKategori = activeCategory == "Semua Kategori" || p.category == activeCategory
                    val cocokMode = when (activeMode) {
                        MODE_READY -> stokLayakJual(p) > 0
                        MODE_EMPTY -> stokLayakJual(p) <= 0
                        else -> true
                    }
                    cocokKeyword && cocokKategori && cocokMode
                }.sortedWith(productComparator())
            }
        }

        LaunchedEffect(keyword, activeCategory, activeMode) { currentPage = 1 }
        val totalPages = maxOf(1, ((filteredProducts.size - 1) / pageSize) + 1)
        if (currentPage > totalPages) currentPage = totalPages
        val pagedProducts = filteredProducts.drop((currentPage - 1) * pageSize).take(pageSize)

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            // DIPERBAIKI: Disesuaikan agar tidak tenggelam di bawah navigasi HP
            sheetPeekHeight = 115.dp,
            sheetContainerColor = surfaceColor,
            sheetShadowElevation = 32.dp,
            sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Sistem Kasir", fontWeight = FontWeight.Black, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Pilih pesanan dari menu", style = MaterialTheme.typography.labelMedium, color = mutedColor)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Rounded.ArrowBack, "Kembali", tint = textColor) } },
                    // DITAMBAHKAN: Ikon untuk memunculkan/menyembunyikan filter
                    actions = {
                        IconButton(onClick = { isFilterVisible = !isFilterVisible }) {
                            Icon(
                                imageVector = if (isFilterVisible) Icons.Rounded.FilterListOff else Icons.Rounded.FilterList,
                                contentDescription = "Filter",
                                tint = primaryColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor)
                )
            },
            sheetContent = {
                CheckoutSwipeableContent(
                    trigger = combinedCartTrigger,
                    onCartChanged = { localCartTrigger++ },
                    scaffoldState = scaffoldState,
                    onTransactionSuccess = {
                        coroutineScope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    },
                    surfaceColor = surfaceColor, bgColor = bgColor, textColor = textColor,
                    mutedColor = mutedColor, primaryColor = primaryColor, borderColor = borderColor,
                    successColor = successColor, warningColor = warningColor, dangerColor = dangerColor,
                    onShowMessage = onShowMessage
                )
            },
            containerColor = bgColor
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // DITAMBAHKAN: Animasi sembunyi/muncul untuk filter
                    AnimatedVisibility(
                        visible = isFilterVisible,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        // HEADER PENCARIAN & FILTER
                        Column(Modifier.fillMaxWidth().background(bgColor).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = keyword,
                                onValueChange = { keyword = it },
                                placeholder = { Text("Cari menu atau kode...") },
                                leadingIcon = { Icon(Icons.Rounded.Search, "Cari", tint = mutedColor) },
                                trailingIcon = { if (keyword.isNotEmpty()) { IconButton(onClick = { keyword = "" }) { Icon(Icons.Rounded.Close, "Hapus", tint = mutedColor) } } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = surfaceColor, unfocusedContainerColor = surfaceColor, focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                            )

                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(categoryOptions) { cat ->
                                    val isSelected = activeCategory == cat
                                    FilterChip(
                                        selected = isSelected, onClick = { activeCategory = cat },
                                        label = { Text(cat, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryColor, selectedLabelColor = Color.White)
                                    )
                                }
                            }

                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(modeOptions) { mode ->
                                    val isSelected = activeMode == mode
                                    FilterChip(
                                        selected = isSelected, onClick = { activeMode = mode },
                                        label = { Text(mode, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = mutedColor.copy(alpha = 0.8f), selectedLabelColor = Color.White)
                                    )
                                }
                            }
                        }
                    }

                    // KONTEN GRID PRODUK
                    if (isLoading) {
                        Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = primaryColor) }
                    } else if (pagedProducts.isEmpty()) {
                        Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Fastfood, null, tint = borderColor, modifier = Modifier.size(64.dp))
                                Text("Menu tidak ditemukan", color = mutedColor, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 130.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(pagedProducts) { product ->
                                ProductGridItemCard(
                                    product = product, price = defaultPrice(product), stock = stokLayakJual(product), status = productStatus(product),
                                    surfaceColor = surfaceColor, borderColor = borderColor, textColor = textColor, mutedColor = mutedColor,
                                    primaryColor = primaryColor, successColor = successColor, warningColor = warningColor, dangerColor = dangerColor,
                                    onAddClick = {
                                        val stok = stokLayakJual(product)
                                        if (stok <= 0) {
                                            onShowMessage(if (product.expiredStock > 0) "Stok sudah kadaluarsa" else "Stok habis")
                                        } else {
                                            val success = SessionKeranjangRumahan.addOrIncrease(product.id, defaultPrice(product), stok)
                                            if (success) {
                                                localCartTrigger++
                                            } else onShowMessage("Sisa stok tidak mencukupi")
                                        }
                                    }
                                )
                            }

                            if (totalPages > 1) {
                                item(span = { GridItemSpan(2) }) {
                                    Column {
                                        HorizontalDivider(color = borderColor, modifier = Modifier.padding(vertical = 12.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(onClick = { if (currentPage > 1) currentPage-- }, enabled = currentPage > 1, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, borderColor)) {
                                                Icon(Icons.Rounded.ArrowBack, "Prev", modifier = Modifier.size(16.dp))
                                            }
                                            Text("Hal $currentPage dari $totalPages", color = mutedColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            OutlinedButton(onClick = { if (currentPage < totalPages) currentPage++ }, enabled = currentPage < totalPages, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, borderColor)) {
                                                Icon(Icons.Rounded.ArrowForward, "Next", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // KONTEN SWIPEABLE CHECKOUT (BOTTOM SHEET)
    // ==========================================
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun CheckoutSwipeableContent(
        trigger: Int, onCartChanged: () -> Unit, scaffoldState: BottomSheetScaffoldState, onTransactionSuccess: () -> Unit,
        surfaceColor: Color, bgColor: Color, textColor: Color, mutedColor: Color,
        primaryColor: Color, borderColor: Color, successColor: Color, warningColor: Color, dangerColor: Color,
        onShowMessage: (String) -> Unit
    ) {
        val coroutineScope = rememberCoroutineScope()
        val items = remember(trigger) { SessionKeranjangRumahan.items }
        val totalAmount = remember(trigger) { SessionKeranjangRumahan.totalAmount() }
        val totalQty = remember(trigger) { SessionKeranjangRumahan.totalQty() }
        val isEmpty = items.isEmpty()
        val isExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded

        var customerName by remember { mutableStateOf("") }
        var selectedMethod by remember { mutableStateOf("Tunai") }
        var cashInputString by remember { mutableStateOf("") }
        var showQrisEnlarged by remember { mutableStateOf(false) }

        var remainingQrisTime by remember { mutableLongStateOf(0L) }
        LaunchedEffect(pendingQris) {
            while (pendingQris != null && !pendingQris!!.isExpired()) {
                remainingQrisTime = pendingQris!!.remainingMs()
                delay(1000)
            }
            if (pendingQris?.isExpired() == true) {
                remainingQrisTime = 0L
            }
        }

        val isCartLocked = pendingQris != null
        val cashPaid = InputAngka.parseLong(cashInputString)
        val change = (cashPaid - totalAmount).coerceAtLeast(0L)

        // DIALOG QRIS MEMBESAR
        if (showQrisEnlarged && pendingQris != null) {
            Dialog(onDismissRequest = { showQrisEnlarged = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Card(modifier = Modifier.fillMaxWidth(0.9f), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Scan QRIS Xendit", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = textColor)
                        Text(Formatter.currency(pendingQris!!.total), fontWeight = FontWeight.Bold, color = primaryColor, style = MaterialTheme.typography.headlineMedium)

                        val qrBitmap = remember(pendingQris!!.qrString) { PembuatQrBitmap.buat(pendingQris!!.qrString, 800) }
                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QRIS", modifier = Modifier.size(260.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(8.dp))

                        Text("Order: ${pendingQris!!.externalId}", color = mutedColor, style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { showQrisEnlarged = false }, modifier = Modifier.weight(1f)) { Text("Tutup") }

                            Button(onClick = { showQrisEnlarged = false; cekStatusQrisXendit(onTransactionSuccess, onShowMessage) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) { Text("Cek Status", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            // HEADER KERANJANG (PEEK AREA)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isEmpty) {
                        coroutineScope.launch {
                            if (isExpanded) scaffoldState.bottomSheetState.partialExpand()
                            else scaffoldState.bottomSheetState.expand()
                        }
                    }
                    // DIPERBAIKI: Padding bawah disesuaikan agar isi intipan tidak terpotong navigasi HP
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ShoppingBag, contentDescription = "Keranjang", tint = primaryColor, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (isEmpty) "Pesanan Kosong" else "$totalQty Item", color = mutedColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(Formatter.currency(totalAmount), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = textColor)
                        }
                    }
                    if (!isEmpty) {
                        Icon(if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp, contentDescription = "Buka", tint = mutedColor)
                    }
                }
            }

            HorizontalDivider(color = borderColor)

            // KONTEN EKSPANSI
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Nama Pelanggan (Opsional)") },
                    leadingIcon = { Icon(Icons.Rounded.PersonOutline, "Pelanggan", tint = mutedColor) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor,
                        focusedContainerColor = bgColor, unfocusedContainerColor = bgColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Rincian Pesanan", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items.forEachIndexed { index, item ->
                            val product = productsCache.firstOrNull { it.id == item.productId }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(product?.name ?: "Produk", fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(Formatter.currency(item.price), color = mutedColor, style = MaterialTheme.typography.bodySmall)
                                }

                                if (!isCartLocked) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(bgColor, RoundedCornerShape(8.dp)).padding(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                SessionKeranjangRumahan.decreaseSpecific(item.productId)
                                                onCartChanged()
                                                if (SessionKeranjangRumahan.isEmpty()) {
                                                    coroutineScope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) { Icon(if (item.qty <= 1) Icons.Rounded.DeleteOutline else Icons.Rounded.Remove, null, tint = dangerColor, modifier = Modifier.size(16.dp)) }

                                        Text("${item.qty}", fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(horizontal = 8.dp))

                                        IconButton(
                                            onClick = {
                                                product?.let { p ->
                                                    val stok = stokLayakJual(p)
                                                    if (!SessionKeranjangRumahan.addOrIncrease(p.id, defaultPrice(p), stok)) {
                                                        onShowMessage("Sisa stok tidak mencukupi")
                                                    }
                                                    onCartChanged()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) { Icon(Icons.Rounded.Add, null, tint = primaryColor, modifier = Modifier.size(16.dp)) }
                                    }
                                } else {
                                    Text("${item.qty}x", fontWeight = FontWeight.Bold, color = mutedColor)
                                }
                            }
                            if (index < items.lastIndex) HorizontalDivider(color = borderColor)
                        }
                    }
                }

                Text("Metode Pembayaran", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(borderColor.copy(alpha = 0.5f)).padding(4.dp)) {
                    val methods = listOf("Tunai", "QRIS")
                    methods.forEach { method ->
                        val isSelected = selectedMethod == method
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(if (isSelected) surfaceColor else Color.Transparent)
                                .clickable(enabled = !isCartLocked) {
                                    selectedMethod = method
                                    if (method == "QRIS" && totalAmount > 0) cashInputString = totalAmount.toString()
                                    if (method == "Tunai") cashInputString = ""
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(method, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium, color = if (isSelected) primaryColor else mutedColor) }
                    }
                }

                if (selectedMethod == "Tunai") {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = cashInputString,
                                onValueChange = { input -> cashInputString = InputAngka.formatInput(input) },
                                label = { Text("Uang Diterima") }, prefix = { Text("Rp ", color = textColor) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryColor, unfocusedBorderColor = borderColor, focusedContainerColor = bgColor, unfocusedContainerColor = bgColor),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val shortcuts = listOf(totalAmount, 50000L, 100000L)
                                shortcuts.forEach { nominal ->
                                    if (nominal > 0) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = bgColor, border = BorderStroke(1.dp, borderColor), modifier = Modifier.weight(1f).clickable { cashInputString = InputAngka.formatInput(nominal.toString()) }) {
                                            Text(if (nominal == totalAmount) "Uang Pas" else Formatter.ribuan(nominal), textAlign = TextAlign.Center, color = primaryColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = borderColor)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Kembalian", color = mutedColor, style = MaterialTheme.typography.titleMedium)
                                Text(Formatter.currency(change), fontWeight = FontWeight.Bold, color = if (change > 0) successColor else textColor, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                } else {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (pendingQris == null) {
                                Icon(Icons.Rounded.QrCodeScanner, null, tint = primaryColor, modifier = Modifier.size(64.dp))
                                Text("Pembayaran Non-Tunai", fontWeight = FontWeight.Bold, color = textColor)
                                Text("Tekan Konfirmasi di bawah untuk membuat kode QRIS bagi pelanggan.", textAlign = TextAlign.Center, color = mutedColor, style = MaterialTheme.typography.bodySmall)
                            } else {
                                val isExpired = remainingQrisTime <= 0L
                                Text(if (isExpired) "QRIS Kedaluwarsa" else "Menunggu Pembayaran...", fontWeight = FontWeight.Bold, color = if (isExpired) dangerColor else warningColor)

                                val qrBitmap = remember(pendingQris!!.qrString) { PembuatQrBitmap.buat(pendingQris!!.qrString, 600) }
                                Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QRIS", modifier = Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp).clickable { showQrisEnlarged = true })

                                Text("Sisa Waktu: ${formatDurasi(remainingQrisTime)}", fontWeight = FontWeight.Bold, color = textColor)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { konfirmasiBatalkanQris() }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, dangerColor), colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor)) { Text("Batalkan") }
                                    if (XENDIT_TEST_MODE) { Button(onClick = { simulasiBayarXendit() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = warningColor)) { Text("Simulasi") } }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        saveTransaction(selectedMethod, cashPaid, totalAmount, items, onTransactionSuccess, onShowMessage)
                    },
                    enabled = !isEmpty && !isProcessing && (selectedMethod != "Tunai" || cashPaid >= totalAmount),
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        val btnText = when {
                            selectedMethod == "QRIS" && pendingQris == null -> "Buat QRIS Pembayaran"
                            selectedMethod == "QRIS" && pendingQris != null -> "Cek Status Pembayaran"
                            else -> "Konfirmasi Transaksi"
                        }
                        Text(btnText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }
    }


    // ==========================================
    // KOMPONEN UI KARTU PRODUK ALA KAFE (GRID)
    // ==========================================
    @Composable
    private fun ProductGridItemCard(
        product: Produk, price: Long, stock: Int, status: String,
        surfaceColor: Color, borderColor: Color, textColor: Color, mutedColor: Color,
        primaryColor: Color, successColor: Color, warningColor: Color, dangerColor: Color,
        onAddClick: () -> Unit
    ) {
        val isAvailable = stock > 0
        val (badgeColor, statusIcon) = when (status) {
            STATUS_ED_TODAY -> warningColor to Icons.Rounded.Warning
            STATUS_PRODUCED_TODAY -> successColor to Icons.Rounded.NewReleases
            STATUS_LEFTOVER -> warningColor to Icons.Rounded.AccessTime
            STATUS_NEAR_EXPIRED -> warningColor to Icons.Rounded.Warning
            else -> dangerColor to Icons.Rounded.Block
        }
        val produksiTerakhir = labelProduksiTerakhir(product)
        val infoTambahan = when (status) {
            STATUS_ED_TODAY -> listOf("Prioritaskan dijual", produksiTerakhir).filter { it.isNotBlank() }.joinToString(" • ")
            STATUS_NEAR_EXPIRED -> listOf("Stok mendekati ED", produksiTerakhir).filter { it.isNotBlank() }.joinToString(" • ")
            else -> produksiTerakhir
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth().clickable(enabled = isAvailable, onClick = onAddClick)
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(if (isAvailable) primaryColor.copy(alpha = 0.15f) else mutedColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Text(product.name.firstOrNull()?.toString() ?: "P", color = if (isAvailable) primaryColor else mutedColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
                }

                Spacer(Modifier.height(12.dp))

                Text(product.name, fontWeight = FontWeight.Bold, color = if (isAvailable) textColor else mutedColor, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.height(44.dp))
                Text(Formatter.currency(price), color = if (isAvailable) primaryColor else mutedColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)

                Spacer(Modifier.height(12.dp))

                Surface(shape = RoundedCornerShape(8.dp), color = badgeColor.copy(alpha = 0.1f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(statusIcon, null, tint = badgeColor, modifier = Modifier.size(12.dp))
                        Text(if (isAvailable) "${Formatter.ribuan(stock.toLong())} ${product.unit} • $status" else status, color = badgeColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (infoTambahan.isNotBlank() && isAvailable) {
                    Spacer(Modifier.height(6.dp))
                    Text(infoTambahan, color = mutedColor, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }

    // ==========================================
    // LOGIKA PROSES, FIREBASE, & XENDIT
    // ==========================================

    private fun saveTransaction(method: String, cashPaid: Long, totalAmount: Long, items: List<ItemKeranjang>, onCloseSheet: () -> Unit, showMsg: (String) -> Unit) {
        if (items.isEmpty()) { showMsg("Keranjang kosong"); return }
        validasiKeranjang(items)?.let { showMsg(it); return }

        if (method == "Tunai") {
            if (cashPaid < totalAmount) { showMsg("Uang dibayar masih kurang"); return }

            simpanTransaksiFinal(
                paymentGateway = "", paymentOrderId = "", paymentQrId = "", paymentStatus = "PAID", paymentSource = "",
                paymentReferenceId = "", paymentPaidAt = "", paymentAmount = totalAmount, metode = "Tunai",
                uangDiterima = cashPaid, items = items, onCloseSheet = onCloseSheet, showMsg = showMsg
            )
        } else {
            if (pendingQris == null) buatQrisXendit(items, totalAmount, showMsg) else cekStatusQrisXendit(onCloseSheet, showMsg)
        }
    }

    private fun buatQrisXendit(items: List<ItemKeranjang>, total: Long, showMsg: (String) -> Unit) {
        if (total < 1500L) { showMsg("Minimal QRIS Rp1.500"); return }
        lifecycleScope.launch {
            isProcessing = true
            runCatching {
                val json = JSONObject(postJson("/api/buat-qris-xendit", JSONObject().put("total", total)))
                val now = System.currentTimeMillis()
                val qrisDraft = XenditQris("", json.getString("externalId"), json.optString("qrId"), json.optLong("total", total), json.optString("status", "ACTIVE"), json.getString("qrString"), now, now + QRIS_EXPIRE_MS)

                val saleId = RepositoriFirebaseUtama.buatPenjualanQrisPending(
                    userAuthId = currentUserId(), cartItems = items, products = productsCache, paymentGateway = "XENDIT",
                    paymentOrderId = qrisDraft.externalId, paymentQrId = qrisDraft.qrId, paymentQrString = qrisDraft.qrString,
                    paymentQrCreatedAtMillis = qrisDraft.createdAtMillis, paymentQrExpiresAtMillis = qrisDraft.expiresAtMillis,
                    paymentStatus = qrisDraft.status.ifBlank { "ACTIVE" }, paymentAmount = qrisDraft.total
                )
                qrisDraft.copy(saleId = saleId)
            }.onSuccess { pendingQris = it; pendingItems = items.map { i -> i.copy() }; showMsg("QRIS berhasil dibuat.") }
                .onFailure { showMsg(it.message ?: "Gagal membuat QRIS") }
            isProcessing = false
        }
    }

    private fun cekStatusQrisXendit(onCloseSheet: (() -> Unit)? = null, showMsg: (String) -> Unit) {
        val qris = pendingQris ?: return
        lifecycleScope.launch {
            isProcessing = true
            runCatching {
                val json = JSONObject(postJson("/api/cek-status-xendit", JSONObject().put("externalId", qris.externalId)))
                val payment = json.optJSONObject("payment")
                val details = payment?.optJSONObject("payment_details")
                XenditStatus(json.optBoolean("paid", false), json.optString("status", "PENDING"), payment?.optString("id").orEmpty(), details?.optString("source").orEmpty(), details?.optString("receipt_id").orEmpty(), payment?.optString("created").orEmpty(), payment?.optLong("amount", qris.total) ?: qris.total)
            }.onSuccess { status ->
                if (status.paid && status.status.equals("COMPLETED", ignoreCase = true)) selesaikanTransaksiQrisPending(status, onCloseSheet, showMsg)
                else showMsg("Status: ${status.status.ifBlank { "PENDING" }}${if (qris.isExpired()) " QRIS Kedaluwarsa." else ""}")
            }.onFailure { showMsg(it.message ?: "Gagal cek status") }
            isProcessing = false
        }
    }

    private fun selesaikanTransaksiQrisPending(status: XenditStatus, onCloseSheet: (() -> Unit)?, showMsg: (String) -> Unit) {
        val qris = pendingQris ?: return
        lifecycleScope.launch {
            isProcessing = true
            runCatching {
                val saleId = RepositoriFirebaseUtama.selesaikanPenjualanQrisPending(
                    id = qris.saleId, userAuthId = currentUserId(), products = productsCache, paymentStatus = status.status.uppercase(Locale.US),
                    paymentSource = status.source, paymentReferenceId = status.receiptId.ifBlank { status.paymentId }, paymentPaidAt = status.paidAt, paymentAmount = status.amount
                )
                RepositoriFirebaseUtama.buildReceiptText(saleId)
            }.onSuccess { receipt ->
                pendingQris = null; pendingItems = emptyList(); SessionKeranjangRumahan.clear(); onCloseSheet?.invoke(); showReceiptDialogAndReset(receipt)
            }.onFailure { showMsg(it.message ?: "Gagal menyelesaikan QRIS") }
            isProcessing = false
        }
    }

    private fun simulasiBayarXendit() {
        val qris = pendingQris ?: return
        lifecycleScope.launch {
            isProcessing = true
            runCatching { postJson("/api/simulasi-bayar-xendit", JSONObject().put("externalId", qris.externalId).put("amount", qris.total)) }
                .onSuccess { showMessage("Simulasi berhasil. Tekan Cek Status.") }
                .onFailure { showMessage(it.message ?: "Gagal simulasi") }
            isProcessing = false
        }
    }

    private fun simpanTransaksiFinal(
        metode: String, uangDiterima: Long, items: List<ItemKeranjang>,
        paymentGateway: String, paymentOrderId: String, paymentQrId: String, paymentStatus: String,
        paymentSource: String, paymentReferenceId: String, paymentPaidAt: String, paymentAmount: Long,
        onCloseSheet: () -> Unit, showMsg: (String) -> Unit
    ) {
        lifecycleScope.launch {
            isProcessing = true
            runCatching {
                val saleId = RepositoriFirebaseUtama.simpanPenjualanRumahan(
                    userAuthId = currentUserId(), metodePembayaranUi = metode, uangDiterima = uangDiterima, cartItems = items, products = productsCache,
                    paymentGateway = paymentGateway, paymentOrderId = paymentOrderId, paymentQrId = paymentQrId, paymentStatus = paymentStatus,
                    paymentSource = paymentSource, paymentReferenceId = paymentReferenceId, paymentPaidAt = paymentPaidAt, paymentAmount = paymentAmount
                )
                RepositoriFirebaseUtama.buildReceiptText(saleId)
            }.onSuccess { receipt ->
                runCatching {
                    SessionKeranjangRumahan.clear()
                    onCloseSheet()
                    showReceiptDialogAndReset(receipt)
                }.onFailure { showMsg(it.message ?: "Transaksi tersimpan, tetapi struk gagal ditampilkan") }
            }.onFailure { showMsg(it.message ?: "Gagal menyimpan transaksi") }
            isProcessing = false
        }
    }

    private fun konfirmasiBatalkanQris() {
        if (pendingQris == null) return
        showConfirmationModal("Batalkan QRIS?", "Pembayaran belum disimpan.", "Batalkan QRIS") { batalkanQrisPending() }
    }

    private fun batalkanQrisPending() {
        val qris = pendingQris ?: return
        lifecycleScope.launch {
            isProcessing = true
            runCatching { RepositoriFirebaseUtama.batalkanPenjualan(id = qris.saleId, alasan = "Dibatalkan kasir", userAuthId = currentUserId()) }
                .onSuccess { pendingQris = null; pendingItems = emptyList(); showMessage("QRIS dibatalkan") }
                .onFailure { showMessage(it.message ?: "Gagal membatalkan QRIS") }
            isProcessing = false
        }
    }

    private fun validasiKeranjang(items: List<ItemKeranjang>): String? {
        items.forEach { item ->
            val produk = productsCache.firstOrNull { it.id == item.productId } ?: return "Produk tidak ditemukan"
            if (item.qty <= 0) return "Jumlah tidak valid"
            if (item.price <= 0L) return "Harga belum valid"
            if (stokLayakJual(produk) < item.qty) return "Stok ${produk.name} tidak cukup."
        }
        return null
    }

    private suspend fun postJson(path: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val url = URL(XENDIT_API_BASE.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 30_000; doOutput = true
            setRequestProperty("Content-Type", "application/json"); setRequestProperty("Accept", "application/json")
        }
        try {
            connection.outputStream.use { output -> output.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (connection.responseCode !in 200..299) {
                val message = runCatching {
                    val json = JSONObject(responseText)
                    json.optString("message").ifBlank { json.optJSONObject("xendit")?.optString("message").orEmpty() }.ifBlank { responseText }
                }.getOrElse { responseText }
                throw IllegalStateException(message)
            }
            responseText
        } finally { connection.disconnect() }
    }

    private fun formatDurasi(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safeMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safeMs) % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun showReceiptDialogAndReset(receipt: String) {
        showReceiptModal("Struk Transaksi", receipt) {
            uiRefreshTrigger.value++
        }
    }
}