package muhamad.irfan.si_tahu.ui.utama

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.PenggunaFirestoreCompat
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.masuk.AktivitasMasuk
import muhamad.irfan.si_tahu.ui.pengaturan.AktivitasPengaturanUsaha
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasPenjualanRumahan
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.PembantuCetak

class AktivitasUtamaKasir : AktivitasDasar() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // State Data User
    private var namaLogin by mutableStateOf("Kasir")

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
                    onSwitchAdmin = {
                        startActivity(AktivitasUtamaAdmin.intent(this, TabIds.ADMIN_DASHBOARD, clearTop = true))
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
                "Akun" -> TabAccount(namaLogin, primaryColor, dangerColor, surfaceColor, borderColor, textColor, mutedColor, onLogout, onSwitchAdmin)
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

    LaunchedEffect(kasirAuthUid) {
        isLoading = true
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
                        Text("Selamat bertugas,", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
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
                            Text("Buka Kasir", fontWeight = FontWeight.Black, color = textColor, style = MaterialTheme.typography.titleLarge)
                            Text("Mulai transaksi penjualan baru", color = mutedColor, style = MaterialTheme.typography.labelMedium)
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SummaryMetricCard(
                            title = "Menu Terjual", value = "${Formatter.ribuan((data?.totalItemHariIni ?: 0).toLong())} pcs", icon = Icons.Rounded.ShoppingCart,
                            modifier = Modifier.weight(1f), primaryColor = primaryColor, surfaceColor = surfaceColor, textColor = textColor, mutedColor = mutedColor, borderColor = borderColor
                        )
                        SummaryMetricCard(
                            title = "Rata-rata", value = Formatter.currency(if ((data?.jumlahTransaksiHariIni ?: 0) > 0) (data?.totalHariIni ?: 0L) / (data?.jumlahTransaksiHariIni ?: 1) else 0L), icon = Icons.Rounded.PointOfSale,
                            modifier = Modifier.weight(1f), primaryColor = primaryColor, surfaceColor = surfaceColor, textColor = textColor, mutedColor = mutedColor, borderColor = borderColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // DAFTAR PRODUK TERLARIS
            Text("Menu Terlaris", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
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
                        Text("Belum ada penjualan hari ini.", color = mutedColor, style = MaterialTheme.typography.bodyMedium)
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

    fun loadDetailIfNeeded(item: RepositoriFirebaseUtama.ItemBarisPenjualan) {
        if (detailCache.containsKey(item.id) || loadingDetailId == item.id) return
        loadingDetailId = item.id
        coroutineScope.launch {
            val detail = runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }
                .getOrElse { error -> error.message ?: "Gagal memuat detail struk" }
            detailCache = detailCache + (item.id to detail)
            loadingDetailId = null
        }
    }

    fun shareText(title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    LaunchedEffect(kasirAuthUid) {
        isLoading = true
        rows = runCatching { RepositoriFirebaseUtama.muatRiwayatPenjualan(limit = 50, sourceFilter = "KASIR", kasirAuthUid = kasirAuthUid) }.getOrDefault(emptyList())
        expandedId = null
        detailCache = emptyMap()
        isLoading = false
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text("Riwayat Kasir Saya", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.headlineMedium)
            Text("Ketuk transaksi untuk melihat detail struk langsung di sini. Hanya transaksi akun kasir ini yang tampil.", color = mutedColor, style = MaterialTheme.typography.bodyLarge)
        }

        if (isLoading) {
            // TAMPILAN SKELETON UNTUK LIST RIWAYAT TRANSAKSI
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(4) {
                    HistoryItemSkeletonCard(surfaceColor, borderColor)
                }
            }
        } else if (rows.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.History, null, tint = primaryColor, modifier = Modifier.size(32.dp))
                    }
                    Text("Belum ada riwayat kasir", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
                    Text("Transaksi dari akun ini akan muncul di sini.", color = mutedColor, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rows, key = { it.id }) { item ->
                    val isExpanded = expandedId == item.id
                    HistoryDetailCard(
                        item = item, isExpanded = isExpanded, detailText = detailCache[item.id], isDetailLoading = loadingDetailId == item.id,
                        primaryColor = primaryColor, surfaceColor = surfaceColor, borderColor = borderColor, textColor = textColor, mutedColor = mutedColor,
                        onToggle = { if (isExpanded) expandedId = null else { expandedId = item.id; loadDetailIfNeeded(item) } },
                        onPrint = { detail -> PembantuCetak.printNota(context, "Struk ${item.title}", detail) },
                        onDownload = { detail -> PembantuCetak.downloadStrukPdf(context, "Struk ${item.title}", detail) },
                        onShare = { detail -> PembantuCetak.shareStrukPdf(context, "Struk ${item.title}", detail) }
                    )
                }
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
    onPrint: (String) -> Unit,
    onDownload: (String) -> Unit,
    onShare: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ReceiptLong, null, tint = primaryColor, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(item.subtitle, color = mutedColor, style = MaterialTheme.typography.labelMedium)
                    if (!item.statusPenjualan.equals("SELESAI", true)) {
                        Text(item.statusPenjualan, color = primaryColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(item.amount, color = textColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (isExpanded) "Tutup" else "Detail", color = primaryColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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
                    val detail = detailText.orEmpty().ifBlank { "Detail struk belum tersedia" }
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(16.dp), color = primaryColor.copy(alpha = 0.06f), border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = detail, color = textColor, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()).padding(14.dp))
                        }

                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onPrint(detail) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Color.White)
                            ) {
                                Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cetak", fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { onDownload(detail) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, borderColor), colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
                                ) {
                                    Text("Download", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onShare(detail) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, borderColor), colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
    onLogout: () -> Unit, onSwitchAdmin: () -> Unit
) {
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.padding(top = 16.dp)) {
            Text("Informasi Akun", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.headlineMedium)
            Text(namaLogin, color = primaryColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        // Seksi Pengaturan
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pengaturan Sistem", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                MenuRowItem(Icons.Rounded.Settings, "Pengaturan Usaha", textColor, mutedColor) {
                    context.startActivity(Intent(context, AktivitasPengaturanUsaha::class.java))
                }
            }
        }

        // Seksi Akses
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Akses Penuh", fontWeight = FontWeight.Bold, color = textColor, style = MaterialTheme.typography.titleMedium)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                MenuRowItem(Icons.Rounded.AdminPanelSettings, "Beralih ke Menu Admin", textColor, mutedColor, isLast = true) {
                    onSwitchAdmin()
                }
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
            Text("Keluar Sistem", fontWeight = FontWeight.Bold)
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