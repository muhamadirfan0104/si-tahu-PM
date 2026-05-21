package muhamad.irfan.si_tahu.ui.utama

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.util.DialogPilihBulanRiwayat
import muhamad.irfan.si_tahu.data.PenggunaFirestoreCompat
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.masuk.AktivitasMasuk
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasPenjualanRumahan
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.PembantuCetak
import muhamad.irfan.si_tahu.util.PembuatQrBitmap
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class AktivitasUtamaKasir : AktivitasDasar() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // State Data User
    private var namaLogin by mutableStateOf("Kasir")
    private var isAdminRole by mutableStateOf(false)

    // State Navigasi
    private var selectedTab by mutableStateOf("Kasir")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Proteksi Akses
        if (auth.currentUser == null) {
            startActivity(Intent(this, AktivitasMasuk::class.java))
            finish()
            return
        }

        loadNamaLogin()

        setContent {
            SiTahuProTheme {
                CashierDashboardScreen(
                    namaLogin = namaLogin,
                    kasirAuthUid = auth.currentUser?.uid.orEmpty(),
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onLogout = {
                        auth.signOut()
                        startActivity(Intent(this, AktivitasMasuk::class.java))
                        finish()
                    },
                    isAdminRole = isAdminRole,
                    onSwitchAdmin = {
                        startActivity(AktivitasUtamaAdmin.intent(this, clearTop = true))
                        finish()
                    }
                )
            }
        }
    }

    private fun loadNamaLogin() {
        val uid = auth.currentUser?.uid ?: return
        PenggunaFirestoreCompat.findByAuthUid(
            firestore = firestore,
            authUid = uid,
            onFound = { doc ->
                namaLogin = doc.getString("namaPengguna") ?: doc.getString("namaLengkap") ?: "Kasir"
                isAdminRole = doc.getString("peranAsli")?.trim()?.uppercase(Locale.US) == "ADMIN"
            },
            onNotFound = {},
            onError = {}
        )
    }

    companion object {
        fun intent(context: Context, clearTop: Boolean = false): Intent {
            return Intent(context, AktivitasUtamaKasir::class.java).apply {
                if (clearTop) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }
}

// === EKSTENSI PEMBANTU ===
private fun String.shortName(): String {
    val clean = trim().substringBefore('@').ifBlank { "Kasir" }
    return clean.split(' ', '.', '_', '-').firstOrNull { it.isNotBlank() } ?: clean
}

private data class KasirQrisDialogData(
    val item: RepositoriFirebaseUtama.ItemBarisPenjualan,
    val info: RepositoriFirebaseUtama.QrisPendingInfo,
    val bitmap: android.graphics.Bitmap
)

// === MODIFIER SHIMMER (SKELETON LOADING) ===
fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offsetX"
    )

    val isDark = isSystemInDarkTheme()
    val baseColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
    val highlightColor = if (isDark) Color(0xFF4B5563) else Color(0xFFF3F4F6)

    background(
        brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned { size = it.size }
}

// === KOMPONEN UTAMA UI COMPOSE ===
@Composable
private fun CashierDashboardScreen(
    namaLogin: String,
    kasirAuthUid: String,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onLogout: () -> Unit,
    isAdminRole: Boolean,
    onSwitchAdmin: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surfaceColor = if (isDark) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val primaryColor = if (isDark) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val textColor = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val mutedColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val borderColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
    val dangerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFDC2626)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = surfaceColor,
                contentColor = mutedColor,
                tonalElevation = 16.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.PointOfSale, contentDescription = "Kasir") },
                    label = { Text("Kasir", fontWeight = FontWeight.Bold) },
                    selected = selectedTab == "Kasir",
                    onClick = { onTabSelected("Kasir") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = primaryColor, selectedTextColor = primaryColor, indicatorColor = primaryColor.copy(alpha = 0.15f))
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.History, contentDescription = "Riwayat") },
                    label = { Text("Riwayat", fontWeight = FontWeight.Bold) },
                    selected = selectedTab == "Riwayat",
                    onClick = { onTabSelected("Riwayat") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = primaryColor, selectedTextColor = primaryColor, indicatorColor = primaryColor.copy(alpha = 0.15f))
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Person, contentDescription = "Akun") },
                    label = { Text("Akun", fontWeight = FontWeight.Bold) },
                    selected = selectedTab == "Akun",
                    onClick = { onTabSelected("Akun") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = primaryColor, selectedTextColor = primaryColor, indicatorColor = primaryColor.copy(alpha = 0.15f))
                )
            }
        },
        containerColor = bgColor
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                "Kasir" -> TabPointOfSale(namaLogin, kasirAuthUid, bgColor, primaryColor, surfaceColor, textColor, mutedColor, borderColor)
                "Riwayat" -> TabHistory(kasirAuthUid, primaryColor, surfaceColor, borderColor, textColor, mutedColor)
                "Akun" -> TabAccount(namaLogin, primaryColor, dangerColor, surfaceColor, borderColor, textColor, mutedColor, onLogout, isAdminRole, onSwitchAdmin)
            }
        }
    }
}

// === 1. TAB KASIR (BERANDA) ===
@Composable
private fun TabPointOfSale(
    namaLogin: String,
    kasirAuthUid: String,
    bgColor: Color,
    primaryColor: Color,
    surfaceColor: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color
) {
    val context = LocalContext.current
    var ringkasan by remember { mutableStateOf<RepositoriFirebaseUtama.RingkasanKasir?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var realtimeTick by remember { mutableIntStateOf(0) }
    val firestoreRealtime = remember { FirebaseFirestore.getInstance() }

    DisposableEffect(kasirAuthUid) {
        val registration = firestoreRealtime.collection("Penjualan").addSnapshotListener { _, _ -> realtimeTick++ }
        onDispose { registration.remove() }
    }

    LaunchedEffect(kasirAuthUid, realtimeTick) {
        isLoading = ringkasan == null
        ringkasan = runCatching { RepositoriFirebaseUtama.muatRingkasanKasir(kasirAuthUid) }.getOrNull()
        isLoading = false
    }

    Column(Modifier.fillMaxSize().background(bgColor).verticalScroll(rememberScrollState())) {

        // HEADER ELEGAN MELAYANG
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        color = primaryColor,
                        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
            )

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 28.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Selamat bekerja,", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
                        Text(namaLogin.shortName(), fontWeight = FontWeight.Black, color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    }
                    Box(Modifier.size(52.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(Modifier.height(28.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { context.startActivity(Intent(context, AktivitasPenjualanRumahan::class.java)) }
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(56.dp).background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PointOfSale, contentDescription = null, tint = primaryColor, modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Mulai Penjualan", fontWeight = FontWeight.Black, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Buat transaksi kasir baru", color = mutedColor, style = MaterialTheme.typography.labelMedium)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = mutedColor)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // BAGIAN RINGKASAN METRIK
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Ringkasan Hari Ini", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)

            if (isLoading) {
                // TAMPILAN SKELETON RINGKASAN PERSIS SEPERTI ASLINYA
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SummaryMetricSkeletonCard(Modifier.weight(1f), surfaceColor, borderColor)
                        SummaryMetricSkeletonCard(Modifier.weight(1f), surfaceColor, borderColor)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SummaryMetricSkeletonCard(Modifier.weight(1f), surfaceColor, borderColor)
                        SummaryMetricSkeletonCard(Modifier.weight(1f), surfaceColor, borderColor)
                    }
                }
            } else {
                val data = ringkasan
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SummaryMetricCard(
                            title = "Omzet", value = Formatter.currency(data?.totalHariIni ?: 0L), icon = Icons.Rounded.AccountBalanceWallet,
                            modifier = Modifier.weight(1f), primaryColor = primaryColor, surfaceColor = surfaceColor, textColor = textColor, mutedColor = mutedColor, borderColor = borderColor
                        )
                        SummaryMetricCard(
                            title = "Transaksi", value = Formatter.ribuan((data?.jumlahTransaksiHariIni ?: 0).toLong()), icon = Icons.Rounded.ReceiptLong,
                            modifier = Modifier.weight(1f), primaryColor = primaryColor, surfaceColor = surfaceColor, textColor = textColor, mutedColor = mutedColor, borderColor = borderColor
                        )
                    }
                    SummaryMetricCard(
                        title = "Produk Terjual", value = "${Formatter.ribuan((data?.totalItemHariIni ?: 0).toLong())} pcs", icon = Icons.Rounded.ShoppingCart,
                        modifier = Modifier.fillMaxWidth(), primaryColor = primaryColor, surfaceColor = surfaceColor, textColor = textColor, mutedColor = mutedColor, borderColor = borderColor
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // DAFTAR PRODUK TERLARIS
            Text("Produk Terlaris", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                val topProducts = ringkasan?.topProducts.orEmpty()

                if (isLoading) {
                    // TAMPILAN SKELETON DAFTAR TERLARIS PERSIS SEPERTI ASLINYA
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        repeat(3) { index ->
                            SimpleInfoRowSkeleton(borderColor, isLast = index == 2)
                        }
                    }
                } else if (topProducts.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Belum ada transaksi kasir hari ini.", color = mutedColor, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        topProducts.take(3).forEachIndexed { index, item ->
                            SimpleInfoRow(
                                leading = "${index + 1}", title = item.title, subtitle = item.subtitle, trailing = item.amount,
                                primaryColor = primaryColor, textColor = textColor, mutedColor = mutedColor
                            )
                            if (index < topProducts.take(3).lastIndex) HorizontalDivider(color = borderColor.copy(alpha = 0.5f), modifier = Modifier.padding(start = 76.dp, end = 24.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}


// === 2. TAB RIWAYAT ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabHistory(
    kasirAuthUid: String,
    primaryColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<RepositoriFirebaseUtama.ItemBarisPenjualan>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var loadingDetailId by remember { mutableStateOf<String?>(null) }
    var detailCache by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var halamanSaatIni by remember { mutableStateOf(1) }
    var realtimeTick by remember { mutableIntStateOf(0) }
    var itemToCancel by remember { mutableStateOf<RepositoriFirebaseUtama.ItemBarisPenjualan?>(null) }
    var isCanceling by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var statusAktif by remember { mutableStateOf("Semua") }
    var rentangMulai by remember { mutableStateOf<String?>(null) }
    var rentangSelesai by remember { mutableStateOf<String?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var qrisDialogData by remember { mutableStateOf<KasirQrisDialogData?>(null) }
    val firestoreRealtime = remember { FirebaseFirestore.getInstance() }
    val itemBesarPerHalaman = 10
    val bgColor = if (isSystemInDarkTheme()) Color(0xFF111827) else Color(0xFFF3F4F6)
    val warningColor = if (isSystemInDarkTheme()) Color(0xFFF59E0B) else Color(0xFFD97706)
    val dangerColor = if (isSystemInDarkTheme()) Color(0xFFEF4444) else Color(0xFFDC2626)

    fun loadDetailIfNeeded(item: RepositoriFirebaseUtama.ItemBarisPenjualan) {
        if (detailCache.containsKey(item.id) || loadingDetailId == item.id) return
        loadingDetailId = item.id
        coroutineScope.launch {
            val detail = runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .getOrElse { error -> error.message ?: "Gagal memuat detail nota" }
            detailCache = detailCache + (item.id to detail)
            loadingDetailId = null
        }
    }

    fun tampilkanQris(item: RepositoriFirebaseUtama.ItemBarisPenjualan) {
        coroutineScope.launch {
            runCatching { RepositoriFirebaseUtama.muatInfoQrisPending(item.id) }
                .onSuccess { info ->
                    if (!info.statusPenjualan.equals("PENDING", true)) {
                        Toast.makeText(context, "Transaksi ini sudah tidak menunggu pembayaran", Toast.LENGTH_SHORT).show()
                        realtimeTick++
                        return@onSuccess
                    }
                    if (info.paymentQrExpiresAtMillis > 0L && info.paymentQrExpiresAtMillis <= System.currentTimeMillis()) {
                        RepositoriFirebaseUtama.tandaiQrisTidakTerbayarJikaKadaluarsa(item.id)
                        Toast.makeText(context, "QRIS sudah habis waktu dan ditandai Belum Terbayar", Toast.LENGTH_SHORT).show()
                        realtimeTick++
                        return@onSuccess
                    }
                    if (info.paymentQrString.isBlank()) {
                        Toast.makeText(context, "Data pembayaran QRIS tidak ditemukan", Toast.LENGTH_SHORT).show()
                        return@onSuccess
                    }
                    qrisDialogData = KasirQrisDialogData(item, info, PembuatQrBitmap.buat(info.paymentQrString, 1000))
                }
                .onFailure { error -> Toast.makeText(context, error.message ?: "Gagal memuat pembayaran QRIS", Toast.LENGTH_SHORT).show() }
        }
    }

    DisposableEffect(kasirAuthUid) {
        val registration = firestoreRealtime.collection("Penjualan").addSnapshotListener { _, _ -> realtimeTick++ }
        onDispose { registration.remove() }
    }

    LaunchedEffect(kasirAuthUid, realtimeTick) {
        isLoading = rows.isEmpty()
        var loaded = runCatching {
            RepositoriFirebaseUtama.muatRiwayatPenjualan(limit = 250, sourceFilter = "KASIR", kasirAuthUid = kasirAuthUid)
        }.getOrDefault(emptyList())

        var adaQrisKadaluarsa = false
        loaded.filter { it.statusPenjualan.equals("PENDING", true) }.forEach { item ->
            val info = runCatching { RepositoriFirebaseUtama.muatInfoQrisPending(item.id) }.getOrNull()
            if (info != null && info.paymentQrExpiresAtMillis > 0L && info.paymentQrExpiresAtMillis <= System.currentTimeMillis()) {
                adaQrisKadaluarsa = runCatching { RepositoriFirebaseUtama.tandaiQrisTidakTerbayarJikaKadaluarsa(item.id) }.getOrDefault(false) || adaQrisKadaluarsa
            }
        }
        if (adaQrisKadaluarsa) {
            loaded = runCatching {
                RepositoriFirebaseUtama.muatRiwayatPenjualan(limit = 250, sourceFilter = "KASIR", kasirAuthUid = kasirAuthUid)
            }.getOrDefault(loaded)
        }

        rows = loaded
        expandedId = null
        detailCache = emptyMap()
        halamanSaatIni = 1
        isLoading = false
    }

    val filteredRows by remember(rows, searchQuery, statusAktif, rentangMulai, rentangSelesai) {
        derivedStateOf {
            rows.filter { item ->
                val labelStatus = labelStatusKasir(item.statusPenjualan)
                val tanggal = Formatter.toDateOnly(item.tanggalIso)
                val cocokKeyword = searchQuery.isBlank() ||
                    item.id.contains(searchQuery, ignoreCase = true) ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.subtitle.contains(searchQuery, ignoreCase = true) ||
                    item.amount.contains(searchQuery, ignoreCase = true) ||
                    labelStatus.contains(searchQuery, ignoreCase = true)
                val cocokStatus = statusAktif == "Semua" || statusAktif == labelStatus
                val cocokTanggal = cocokTanggalKasir(tanggal, rentangMulai, rentangSelesai)
                cocokKeyword && cocokStatus && cocokTanggal
            }
        }
    }

    LaunchedEffect(searchQuery, statusAktif, rentangMulai, rentangSelesai) { halamanSaatIni = 1 }

    val totalHalaman = maxOf(1, ((filteredRows.size - 1) / itemBesarPerHalaman) + 1)
    if (halamanSaatIni > totalHalaman) halamanSaatIni = totalHalaman
    val rowsTampil = filteredRows.drop((halamanSaatIni - 1) * itemBesarPerHalaman).take(itemBesarPerHalaman)
    val hasActiveFilter = statusAktif != "Semua" || rentangMulai != null || rentangSelesai != null

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Riwayat Kasir Saya", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.headlineMedium)
            Text("Cari transaksi, filter status, atau pilih rentang tanggal seperti standar riwayat aplikasi.", color = mutedColor, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari nota / ID / status...", color = mutedColor) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Cari", tint = mutedColor) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(100),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = surfaceColor,
                        unfocusedContainerColor = surfaceColor
                    ),
                    modifier = Modifier.weight(1f).height(54.dp)
                )
                Surface(
                    shape = CircleShape,
                    color = if (hasActiveFilter) primaryColor else surfaceColor,
                    border = if (hasActiveFilter) null else BorderStroke(1.dp, borderColor),
                    modifier = Modifier.size(54.dp).clickable { showFilterDialog = true }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FilterList, contentDescription = "Filter", tint = if (hasActiveFilter) Color.White else textColor)
                        if (hasActiveFilter) Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(8.dp).clip(CircleShape).background(Color.Red))
                    }
                }
            }
            if (hasActiveFilter) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (statusAktif != "Semua") FilterChipVisualKasir(statusAktif, { statusAktif = "Semua" }, primaryColor)
                    if (rentangMulai != null && rentangSelesai != null) {
                        val label = if (rentangMulai == rentangSelesai) Formatter.readableShortDate(rentangMulai) else "${Formatter.readableShortDate(rentangMulai)} - ${Formatter.readableShortDate(rentangSelesai)}"
                        FilterChipVisualKasir(label, { rentangMulai = null; rentangSelesai = null }, primaryColor)
                    }
                }
            }
            Text("Menampilkan ${filteredRows.size} transaksi", color = mutedColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
        }

        if (isLoading) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) { HistoryItemSkeletonCard(surfaceColor, borderColor) }
            }
        } else if (filteredRows.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.History, null, tint = primaryColor, modifier = Modifier.size(32.dp))
                    }
                    Text("Belum ada riwayat yang sesuai", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                    Text("Coba ubah pencarian, filter, atau rentang tanggal.", color = mutedColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rowsTampil, key = { it.id }) { item ->
                    val isExpanded = expandedId == item.id
                    HistoryDetailCard(
                        item = item,
                        isExpanded = isExpanded,
                        detailText = detailCache[item.id],
                        isDetailLoading = loadingDetailId == item.id,
                        primaryColor = primaryColor,
                        surfaceColor = surfaceColor,
                        borderColor = borderColor,
                        textColor = textColor,
                        mutedColor = mutedColor,
                        onToggle = {
                            when {
                                item.statusPenjualan.equals("PENDING", true) -> tampilkanQris(item)
                                isExpanded -> expandedId = null
                                else -> { expandedId = item.id; loadDetailIfNeeded(item) }
                            }
                        },
                        onShowQris = { tampilkanQris(item) },
                        onPrint = { detail -> PembantuCetak.printNota(context, "Nota ${item.title}", detail) },
                        onDownload = { detail -> PembantuCetak.downloadStrukPdf(context, "Nota ${item.title}", detail) },
                        onShare = { detail -> PembantuCetak.shareStrukPdf(context, "Nota ${item.title}", detail) },
                        onCancel = { itemToCancel = item }
                    )
                }
                if (totalHalaman > 1) {
                    item {
                        PaginationListCard(
                            halamanSaatIni = halamanSaatIni,
                            totalHalaman = totalHalaman,
                            primaryColor = primaryColor,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            textColor = textColor,
                            mutedColor = mutedColor,
                            onPrev = { if (halamanSaatIni > 1) halamanSaatIni-- },
                            onNext = { if (halamanSaatIni < totalHalaman) halamanSaatIni++ }
                        )
                    }
                }
            }
        }
    }

    val cancelTarget = itemToCancel
    if (cancelTarget != null) {
        CancelKasirTransactionDialog(
            item = cancelTarget,
            isCanceling = isCanceling,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
            textColor = textColor,
            mutedColor = mutedColor,
            dangerColor = dangerColor,
            onDismiss = { if (!isCanceling) itemToCancel = null },
            onConfirm = { alasan ->
                coroutineScope.launch {
                    isCanceling = true
                    runCatching { RepositoriFirebaseUtama.batalkanPenjualan(cancelTarget.id, alasan, kasirAuthUid) }
                        .onSuccess {
                            Toast.makeText(context, "Transaksi berhasil dibatalkan", Toast.LENGTH_SHORT).show()
                            itemToCancel = null
                            realtimeTick++
                        }
                        .onFailure { error -> Toast.makeText(context, error.message ?: "Gagal membatalkan transaksi", Toast.LENGTH_SHORT).show() }
                    isCanceling = false
                }
            }
        )
    }

    if (showFilterDialog) {
        KasirHistoryFilterDialog(
            initialStatus = statusAktif,
            initialRentangMulai = rentangMulai,
            initialRentangSelesai = rentangSelesai,
            primaryColor = primaryColor,
            surfaceColor = surfaceColor,
            bgColor = bgColor,
            textColor = textColor,
            mutedColor = mutedColor,
            borderColor = borderColor,
            onDismiss = { showFilterDialog = false },
            onReset = {
                statusAktif = "Semua"
                rentangMulai = null
                rentangSelesai = null
                showFilterDialog = false
            },
            onApply = { statusBaru, mulai, selesai ->
                statusAktif = statusBaru
                if (mulai != null && selesai != null && mulai > selesai) {
                    rentangMulai = selesai
                    rentangSelesai = mulai
                } else {
                    rentangMulai = mulai
                    rentangSelesai = selesai
                }
                showFilterDialog = false
            }
        )
    }

    val qrisData = qrisDialogData
    if (qrisData != null) {
        KasirQrisDialog(
            data = qrisData,
            primaryColor = primaryColor,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
            textColor = textColor,
            mutedColor = mutedColor,
            warningColor = warningColor,
            dangerColor = dangerColor,
            onDismiss = { qrisDialogData = null },
            onExpired = {
                coroutineScope.launch {
                    runCatching { RepositoriFirebaseUtama.tandaiQrisTidakTerbayarJikaKadaluarsa(qrisData.item.id) }
                    qrisDialogData = null
                    Toast.makeText(context, "QRIS sudah habis waktu dan ditandai Belum Terbayar", Toast.LENGTH_SHORT).show()
                    realtimeTick++
                }
            },
            onPrintQris = { labelSisa ->
                PembantuCetak.printQris(
                    context = context,
                    jobName = "QRIS ${qrisData.item.title}",
                    title = qrisData.item.title,
                    nominal = Formatter.currency(qrisData.info.totalBelanja),
                    orderId = qrisData.info.paymentOrderId,
                    expiredLabel = labelSisa,
                    qrBitmap = qrisData.bitmap
                )
            },
            onCancel = { qrisDialogData = null; itemToCancel = qrisData.item }
        )
    }
}


@Composable
private fun FilterChipVisualKasir(label: String, onRemove: () -> Unit, primaryColor: Color) {
    Surface(shape = RoundedCornerShape(100), color = primaryColor.copy(alpha = 0.12f), border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.20f))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)
        ) {
            Text(label, color = primaryColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            Icon(Icons.Rounded.Close, contentDescription = "Hapus filter", tint = primaryColor, modifier = Modifier.size(15.dp).clickable { onRemove() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KasirHistoryFilterDialog(
    initialStatus: String,
    initialRentangMulai: String?,
    initialRentangSelesai: String?,
    primaryColor: Color,
    surfaceColor: Color,
    bgColor: Color,
    textColor: Color,
    mutedColor: Color,
    borderColor: Color,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onApply: (status: String, rentangMulai: String?, rentangSelesai: String?) -> Unit
) {
    val statusOptions = listOf("Semua", "Selesai", "Pending", "Belum Terbayar", "Batal")
    var draftStatus by remember { mutableStateOf(initialStatus) }
    var draftMulai by remember { mutableStateOf(initialRentangMulai.orEmpty()) }
    var draftSelesai by remember { mutableStateOf(initialRentangSelesai.orEmpty()) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    fun formatDateToLocalId(dateStr: String): String {
        if (dateStr.isBlank()) return ""
        return runCatching {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)
            SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(d!!)
        }.getOrDefault(dateStr)
    }

    val dateLabel = if (draftMulai.isNotBlank() && draftSelesai.isNotBlank()) {
        if (draftMulai == draftSelesai) formatDateToLocalId(draftMulai)
        else "${formatDateToLocalId(draftMulai)} - ${formatDateToLocalId(draftSelesai)}"
    } else {
        "Pilih rentang tanggal"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = surfaceColor,
        title = { Text("Filter Riwayat Kasir", fontWeight = FontWeight.Bold, color = textColor) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Status Transaksi", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    statusOptions.chunked(2).forEach { rowOptions ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowOptions.forEach { pilihan ->
                                val selected = draftStatus == pilihan
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) primaryColor.copy(alpha = 0.15f) else Color.Transparent,
                                    border = BorderStroke(1.dp, if (selected) primaryColor else borderColor),
                                    modifier = Modifier.weight(1f).clickable { draftStatus = pilihan }
                                ) {
                                    Text(
                                        pilihan,
                                        color = if (selected) primaryColor else textColor,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                }
                            }
                            if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                HorizontalDivider(color = borderColor)
                Text("Rentang Tanggal", fontWeight = FontWeight.SemiBold, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showDateRangePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.DateRange, contentDescription = null, tint = mutedColor)
                        Text(dateLabel, color = if (draftMulai.isNotBlank()) textColor else mutedColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showMonthPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.DateRange, contentDescription = null, tint = mutedColor)
                        Text("Pilih satu bulan penuh", color = textColor, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(draftStatus, draftMulai.ifBlank { null }, draftSelesai.ifBlank { null }) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) { Text("Terapkan", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onReset) { Text("Reset", color = mutedColor) } }
    )

    if (showDateRangePicker) {
        val localFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val utcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun getUtcMillis(dateStr: String?): Long? {
            if (dateStr.isNullOrBlank()) return null
            return runCatching { utcFormat.parse(dateStr)?.time }.getOrNull()
        }

        fun getUtcMillisFromOffset(offsetDays: Int): Long {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, offsetDays)
            val dateStr = localFormat.format(cal.time)
            return utcFormat.parse(dateStr)?.time ?: 0L
        }

        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = getUtcMillis(draftMulai),
            initialSelectedEndDateMillis = getUtcMillis(draftSelesai)
        )
        var selectedPreset by remember { mutableStateOf("") }

        fun applyPreset(preset: String) {
            selectedPreset = preset
            val todayStr = localFormat.format(Date())
            val todayUtcMillis = utcFormat.parse(todayStr)?.time ?: 0L
            when (preset) {
                "Hari Ini" -> dateRangePickerState.setSelection(todayUtcMillis, todayUtcMillis)
                "Kemarin" -> dateRangePickerState.setSelection(getUtcMillisFromOffset(-1), getUtcMillisFromOffset(-1))
                "Minggu Ini" -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    val startStr = localFormat.format(cal.time)
                    cal.add(Calendar.DAY_OF_YEAR, 6)
                    val endStr = localFormat.format(cal.time)
                    val startUtc = utcFormat.parse(startStr)?.time
                    val endUtc = utcFormat.parse(endStr)?.time
                    if (startUtc != null && endUtc != null) dateRangePickerState.setSelection(startUtc, endUtc)
                }
                "Bulan Ini" -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    val startStr = localFormat.format(cal.time)
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    val endStr = localFormat.format(cal.time)
                    val startUtc = utcFormat.parse(startStr)?.time
                    val endUtc = utcFormat.parse(endStr)?.time
                    if (startUtc != null && endUtc != null) dateRangePickerState.setSelection(startUtc, endUtc)
                }
            }
        }

        Dialog(onDismissRequest = { showDateRangePicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.90f), shape = RoundedCornerShape(24.dp), color = surfaceColor) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pilih Rentang Tanggal", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { showDateRangePicker = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Tutup", tint = textColor)
                        }
                    }
                    HorizontalDivider(color = borderColor)
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("Hari Ini", "Kemarin", "Minggu Ini", "Bulan Ini")) { preset ->
                            val selected = selectedPreset == preset
                            Surface(
                                shape = RoundedCornerShape(100),
                                color = if (selected) primaryColor.copy(alpha = 0.15f) else bgColor,
                                border = BorderStroke(1.dp, if (selected) primaryColor else borderColor),
                                modifier = Modifier.clickable { applyPreset(preset) }
                            ) {
                                Text(preset, color = if (selected) primaryColor else textColor, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        DateRangePicker(
                            state = dateRangePickerState,
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            modifier = Modifier.fillMaxSize(),
                            colors = DatePickerDefaults.colors(
                                containerColor = Color.Transparent,
                                dayContentColor = textColor,
                                selectedDayContainerColor = primaryColor,
                                selectedDayContentColor = Color.White,
                                dayInSelectionRangeContainerColor = primaryColor.copy(alpha = 0.2f),
                                dayInSelectionRangeContentColor = primaryColor
                            )
                        )
                    }
                    HorizontalDivider(color = borderColor)
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showDateRangePicker = false }) { Text("Batal", color = mutedColor, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val startMillis = dateRangePickerState.selectedStartDateMillis
                                val endMillis = dateRangePickerState.selectedEndDateMillis
                                val mulaiStr = startMillis?.let { utcFormat.format(Date(it)) } ?: ""
                                val selesaiStr = endMillis?.let { utcFormat.format(Date(it)) } ?: mulaiStr
                                draftMulai = mulaiStr
                                draftSelesai = selesaiStr
                                showDateRangePicker = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) { Text("Simpan", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        DialogPilihBulanRiwayat(
            initialDate = draftMulai.ifBlank { null },
            primaryColor = primaryColor,
            surfaceColor = surfaceColor,
            bgColor = bgColor,
            textColor = textColor,
            mutedColor = mutedColor,
            borderColor = borderColor,
            onDismiss = { showMonthPicker = false },
            onApply = { mulai, selesai, _ ->
                draftMulai = mulai
                draftSelesai = selesai
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun KasirQrisDialog(
    data: KasirQrisDialogData,
    primaryColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    warningColor: Color,
    dangerColor: Color,
    onDismiss: () -> Unit,
    onExpired: () -> Unit,
    onPrintQris: (String) -> Unit,
    onCancel: () -> Unit
) {
    var remainingMs by remember(data.item.id, data.info.paymentQrExpiresAtMillis) {
        mutableLongStateOf((data.info.paymentQrExpiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
    }
    var expiredHandled by remember(data.item.id) { mutableStateOf(false) }
    var selectedTab by remember(data.item.id) { mutableStateOf("QRIS") }

    LaunchedEffect(data.item.id, data.info.paymentQrExpiresAtMillis) {
        while (remainingMs > 0L) {
            delay(1000L)
            remainingMs = (data.info.paymentQrExpiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        if (!expiredHandled) {
            expiredHandled = true
            onExpired()
        }
    }

    val sisaMenit = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
    val sisaDetik = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
    val waktuText = if (remainingMs > 0L) String.format(Locale.US, "Kedaluwarsa dalam: %02d:%02d", sisaMenit, sisaDetik) else "Waktu QRIS telah habis"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(0.9f), shape = RoundedCornerShape(24.dp), color = surfaceColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = primaryColor)
                    Text("Menunggu Pembayaran QRIS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = textColor)
                }
                Text(Formatter.currency(data.info.totalBelanja), fontWeight = FontWeight.Black, color = primaryColor, style = MaterialTheme.typography.headlineMedium)

                KasirQrisSegmentedTabs(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    primaryColor = primaryColor,
                    surfaceColor = surfaceColor,
                    borderColor = borderColor,
                    mutedColor = mutedColor
                )

                if (selectedTab == "QRIS") {
                    if (remainingMs > 0L) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 4.dp, border = BorderStroke(1.dp, borderColor)) {
                            Image(bitmap = data.bitmap.asImageBitmap(), contentDescription = "QRIS", modifier = Modifier.size(240.dp).padding(16.dp))
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(20.dp), color = dangerColor.copy(alpha = 0.10f), border = BorderStroke(1.dp, dangerColor.copy(alpha = 0.20f)), modifier = Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(20.dp)) {
                                Icon(Icons.Rounded.Info, contentDescription = null, tint = dangerColor, modifier = Modifier.size(40.dp))
                                Text("QRIS sudah kedaluwarsa", color = dangerColor, fontWeight = FontWeight.Bold)
                                Text("Status transaksi akan berubah menjadi Belum Terbayar.", color = mutedColor, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    Surface(shape = RoundedCornerShape(20.dp), color = primaryColor.copy(alpha = 0.08f), border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.16f)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Bayar", color = mutedColor, style = MaterialTheme.typography.bodyMedium)
                                Text(Formatter.currency(data.info.totalBelanja), color = textColor, fontWeight = FontWeight.Black)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Status", color = mutedColor, style = MaterialTheme.typography.bodyMedium)
                                Text("Menunggu Pembayaran", color = warningColor, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Sisa Waktu", color = mutedColor, style = MaterialTheme.typography.bodyMedium)
                                Text(waktuText, color = if (remainingMs > 0L) warningColor else dangerColor, fontWeight = FontWeight.Bold)
                            }
                            Text("Gunakan tombol Cetak QRIS untuk diberikan ke pelanggan. Nota hanya tersedia setelah pembayaran sukses.", color = mutedColor, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Text(waktuText, color = if (remainingMs > 0L) warningColor else dangerColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("Nota: ${data.item.title}\nKode: ${data.info.paymentOrderId.ifBlank { "-" }}", color = mutedColor, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)

                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onPrintQris(waktuText) },
                        enabled = remainingMs > 0L,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White, disabledContainerColor = mutedColor.copy(alpha = 0.25f), disabledContentColor = mutedColor)
                    ) {
                        Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cetak QRIS", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = remainingMs > 0L,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, dangerColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = dangerColor)
                    ) { Text("Batalkan Transaksi", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Tutup", color = mutedColor, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun KasirQrisSegmentedTabs(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    primaryColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    mutedColor: Color
) {
    val tabs = listOf("QRIS", "Bayar")
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(borderColor.copy(alpha = 0.45f))
            .padding(4.dp)
    ) {
        tabs.forEach { tab ->
            val selected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) surfaceColor else Color.Transparent)
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (tab == "QRIS") Icons.Rounded.QrCodeScanner else Icons.Rounded.Payments,
                        contentDescription = tab,
                        tint = if (selected) primaryColor else mutedColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(tab, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium, color = if (selected) primaryColor else mutedColor)
                }
            }
        }
    }
}

private fun labelStatusKasir(status: String): String = when (status.uppercase(Locale.US)) {
    "BATAL" -> "Batal"
    "PENDING" -> "Pending"
    "TIDAK_TERBAYAR" -> "Belum Terbayar"
    else -> "Selesai"
}

private fun cocokTanggalKasir(tanggal: String, mulai: String?, selesai: String?): Boolean {
    if (mulai.isNullOrBlank() && selesai.isNullOrBlank()) return true
    val start = mulai ?: selesai ?: return true
    val end = selesai ?: mulai ?: return true
    return tanggal >= start && tanggal <= end
}

@Composable
private fun PaginationListCard(
    halamanSaatIni: Int,
    totalHalaman: Int,
    primaryColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onPrev, enabled = halamanSaatIni > 1) {
                Text("Sebelumnya", color = if (halamanSaatIni > 1) primaryColor else mutedColor, fontWeight = FontWeight.Bold)
            }
            Text("Hal $halamanSaatIni dari $totalHalaman", color = textColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNext, enabled = halamanSaatIni < totalHalaman) {
                Text("Selanjutnya", color = if (halamanSaatIni < totalHalaman) primaryColor else mutedColor, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HistoryDetailCard(
    item: RepositoriFirebaseUtama.ItemBarisPenjualan,
    isExpanded: Boolean,
    detailText: String?,
    isDetailLoading: Boolean,
    primaryColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    onToggle: () -> Unit,
    onShowQris: () -> Unit,
    onPrint: (String) -> Unit,
    onDownload: (String) -> Unit,
    onShare: (String) -> Unit,
    onCancel: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val isBatal = item.statusPenjualan.equals("BATAL", true)
    val isPending = item.statusPenjualan.equals("PENDING", true)
    val isTidakTerbayar = item.statusPenjualan.equals("TIDAK_TERBAYAR", true)
    val successColor = if (isSystemInDarkTheme()) Color(0xFF10B981) else Color(0xFF059669)
    val warningColor = if (isSystemInDarkTheme()) Color(0xFFF59E0B) else Color(0xFFD97706)
    val dangerColor = if (isSystemInDarkTheme()) Color(0xFFEF4444) else Color(0xFFDC2626)
    val neutralColor = if (isSystemInDarkTheme()) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val statusLabel = labelStatusKasir(item.statusPenjualan)
    val statusColor = when {
        isBatal -> dangerColor
        isTidakTerbayar -> neutralColor
        isPending -> warningColor
        else -> successColor
    }
    val cardTextColor = if (isBatal || isTidakTerbayar) mutedColor else textColor
    val cardSourceColor = if (isBatal || isTidakTerbayar) mutedColor else primaryColor
    val bolehCetakStruk = item.statusPenjualan.equals("SELESAI", true)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor.copy(alpha = if (isBatal) 0.65f else 1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(cardSourceColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ReceiptLong, null, tint = cardSourceColor, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = cardTextColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.subtitle, color = mutedColor, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(6.dp), color = cardSourceColor.copy(alpha = 0.1f)) {
                            Text(item.badge, color = cardSourceColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.1f)) {
                            Text(statusLabel, color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(item.amount, color = cardTextColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Opsi", tint = mutedColor)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(surfaceColor)) {
                            if (isPending) {
                                DropdownMenuItem(text = { Text("Lihat atau Cetak QRIS", color = textColor) }, onClick = { showMenu = false; onShowQris() })
                            } else {
                                DropdownMenuItem(text = { Text(if (bolehCetakStruk) "Lihat Detail & Nota" else "Lihat Detail", color = textColor) }, onClick = { showMenu = false; onToggle() })
                            }
                            if (!isBatal && !isTidakTerbayar) {
                                DropdownMenuItem(text = { Text(if (isPending) "Batalkan Transaksi" else "Batalkan Transaksi", color = dangerColor) }, onClick = { showMenu = false; onCancel() })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (isPending) "QRIS" else if (isExpanded) "Tutup" else "Detail", color = primaryColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Icon(imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = borderColor)
                if (isDetailLoading) {
                    // TAMPILAN SKELETON UNTUK ISI STRUK
                    HistoryDetailContentSkeleton(primaryColor, borderColor)
                } else {
                    val detail = detailText.orEmpty().ifBlank { "Detail nota belum tersedia" }
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp), color = primaryColor.copy(alpha = 0.06f), border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = detail, color = textColor, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()).padding(14.dp))
                        }

                        if (bolehCetakStruk) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onPrint(detail) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White)
                                ) {
                                    Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cetak Kwitansi", fontWeight = FontWeight.Bold)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = { onDownload(detail) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, borderColor), colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
                                    ) {
                                        Text("Unduh", fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { onShare(detail) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, borderColor), colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Bagikan", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(14.dp), color = statusColor.copy(alpha = 0.10f), border = BorderStroke(1.dp, statusColor.copy(alpha = 0.18f)), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = if (isPending) "Transaksi QRIS pending belum boleh cetak kwitansi. Cetak QRIS dari tombol QRIS." else "Kwitansi hanya bisa dicetak untuk transaksi selesai.",
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CancelKasirTransactionDialog(
    item: RepositoriFirebaseUtama.ItemBarisPenjualan,
    isCanceling: Boolean,
    surfaceColor: Color,
    borderColor: Color,
    textColor: Color,
    mutedColor: Color,
    dangerColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var alasan by remember(item.id) { mutableStateOf("") }
    val alasanFinal = alasan.trim().ifBlank { "Dibatalkan dari riwayat kasir" }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Batalkan transaksi?", fontWeight = FontWeight.Bold, color = textColor) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nota ${item.title} akan diberi status Batal dan stok akan dikembalikan.", color = mutedColor, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = alasan,
                    onValueChange = { alasan = it },
                    label = { Text("Alasan pembatalan") },
                    placeholder = { Text("Contoh: salah input transaksi") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isCanceling
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(alasanFinal) },
                enabled = !isCanceling,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = dangerColor, contentColor = Color.White)
            ) {
                if (isCanceling) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Batalkan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isCanceling, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, borderColor)) {
                Text("Tutup", color = textColor, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// === KUMPULAN KOMPONEN UI TAMBAHAN & SKELETON ===

@Composable
private fun SummaryMetricCard(
    title: String, value: String, icon: ImageVector, modifier: Modifier,
    primaryColor: Color, surfaceColor: Color, textColor: Color, mutedColor: Color, borderColor: Color
) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(primaryColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = mutedColor, style = MaterialTheme.typography.labelMedium)
                Text(value, color = textColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// 1. SKELETON UNTUK METRIK RINGKASAN
@Composable
private fun SummaryMetricSkeletonCard(modifier: Modifier, surfaceColor: Color, borderColor: Color) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.height(14.dp).fillMaxWidth(0.5f).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Box(Modifier.height(20.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            }
        }
    }
}

@Composable
private fun SimpleInfoRow(
    leading: String, title: String, subtitle: String, trailing: String,
    primaryColor: Color, textColor: Color, mutedColor: Color
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val badgeColor = when(leading) { "1" -> Color(0xFFFFB300) "2" -> Color(0xFF9E9E9E) "3" -> Color(0xFF8D6E63) else -> primaryColor }
        Box(Modifier.size(40.dp).background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text("#$leading", color = badgeColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = mutedColor, style = MaterialTheme.typography.labelMedium)
        }
        Text(trailing, color = textColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
    }
}

// 2. SKELETON UNTUK DAFTAR TERLARIS
@Composable
private fun SimpleInfoRowSkeleton(borderColor: Color, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.height(18.dp).fillMaxWidth(0.7f).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            Box(Modifier.height(14.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        }
        Box(Modifier.width(60.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
    }
    if (!isLast) HorizontalDivider(color = borderColor.copy(alpha = 0.5f), modifier = Modifier.padding(start = 76.dp, end = 24.dp))
}

// 3. SKELETON UNTUK ITEM RIWAYAT
@Composable
private fun HistoryItemSkeletonCard(surfaceColor: Color, borderColor: Color) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = surfaceColor), border = BorderStroke(1.dp, borderColor), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).clip(CircleShape).shimmerEffect())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.height(18.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Box(Modifier.height(14.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.height(20.dp).width(70.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Box(Modifier.height(16.dp).width(50.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            }
        }
    }
}

// 4. SKELETON UNTUK ISI DETAIL STRUK
@Composable
private fun HistoryDetailContentSkeleton(primaryColor: Color, borderColor: Color) {
    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = primaryColor.copy(alpha = 0.06f), border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(8) { Box(Modifier.height(12.dp).fillMaxWidth(if(it % 2 == 0) 0.9f else 0.6f).clip(RoundedCornerShape(4.dp)).shimmerEffect()) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).shimmerEffect())
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).shimmerEffect())
        }
    }
}


// === 3. TAB AKUN ===
@Composable
private fun TabAccount(
    namaLogin: String,
    primaryColor: Color, dangerColor: Color, surfaceColor: Color, borderColor: Color,
    textColor: Color, mutedColor: Color,
    onLogout: () -> Unit,
    isAdminRole: Boolean,
    onSwitchAdmin: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.padding(top = 16.dp)) {
            Text("Informasi Akun", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.headlineMedium)
            Text(namaLogin, color = primaryColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        // Informasi akses kasir
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = primaryColor.copy(alpha = 0.08f)),
            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(shape = CircleShape, color = primaryColor.copy(alpha = 0.14f)) {
                    Icon(Icons.Rounded.PointOfSale, contentDescription = null, tint = primaryColor, modifier = Modifier.padding(10.dp).size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mode Kasir", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isAdminRole) "Akun admin sedang menggunakan mode kasir. Kamu tetap bisa kembali ke mode admin kapan saja." else "Akun ini hanya dapat mengakses transaksi kasir dan riwayat penjualan miliknya.",
                        color = mutedColor,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2
                    )
                }
            }
        }

        if (isAdminRole) {
            Button(
                onClick = onSwitchAdmin,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White)
            ) {
                Icon(Icons.Rounded.AdminPanelSettings, null)
                Spacer(Modifier.width(8.dp))
                Text("Beralih ke Mode Admin", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))

        // Tombol Keluar
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = dangerColor.copy(alpha = 0.15f), contentColor = dangerColor)
        ) {
            Icon(Icons.Rounded.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Keluar Akun", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MenuRowItem(icon: ImageVector, title: String, textColor: Color, mutedColor: Color, isLast: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = mutedColor, modifier = Modifier.size(24.dp))
            Text(title, fontWeight = FontWeight.Medium, color = textColor, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Buka", tint = mutedColor)
        }
        if (!isLast) {
            HorizontalDivider(color = mutedColor.copy(alpha = 0.2f), modifier = Modifier.padding(start = 60.dp, end = 20.dp))
        }
    }
}