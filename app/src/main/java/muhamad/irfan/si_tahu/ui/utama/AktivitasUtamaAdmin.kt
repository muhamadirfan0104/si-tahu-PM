package muhamad.irfan.si_tahu.ui.utama

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import muhamad.irfan.si_tahu.data.PenggunaFirestoreCompat
import muhamad.irfan.si_tahu.data.RepositoriFirebaseUtama
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.harga.AktivitasDaftarHarga
import muhamad.irfan.si_tahu.ui.laporan.AktivitasLaporan
import muhamad.irfan.si_tahu.ui.laporan.AktivitasRiwayatTransaksi
import muhamad.irfan.si_tahu.ui.masuk.AktivitasMasuk
import muhamad.irfan.si_tahu.ui.parameter.AktivitasDaftarParameter
import muhamad.irfan.si_tahu.ui.pengaturan.AktivitasPengaturanUsaha
import muhamad.irfan.si_tahu.ui.pengeluaran.AktivitasDaftarPengeluaran
import muhamad.irfan.si_tahu.ui.pengguna.AktivitasDaftarPengguna
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasPenjualanRumahan
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasRekapPasar
import muhamad.irfan.si_tahu.ui.penjualan.AktivitasRiwayatPenjualan
import muhamad.irfan.si_tahu.ui.produk.AktivitasDaftarProduk
import muhamad.irfan.si_tahu.ui.produksi.AktivitasKonversiProduk
import muhamad.irfan.si_tahu.ui.produksi.AktivitasProduksiTahuDasar
import muhamad.irfan.si_tahu.ui.produksi.AktivitasRiwayatProduksi
import muhamad.irfan.si_tahu.ui.stok.AktivitasDetailStok
import muhamad.irfan.si_tahu.ui.stok.AktivitasMonitoringStok
import muhamad.irfan.si_tahu.ui.stok.AktivitasRiwayatSemuaStok
import muhamad.irfan.si_tahu.ui.stok.AktivitasStockAdjustment
import muhamad.irfan.si_tahu.util.Formatter
import java.text.SimpleDateFormat
import java.util.Locale

internal data class MainTab(val id: Int, val label: String, val icon: ImageVector)
internal data class SpeedDialItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)
private val adminTabs = listOf(
    MainTab(TabIds.ADMIN_DASHBOARD, "Dasbor", Icons.Rounded.Dashboard),
    MainTab(TabIds.ADMIN_PRODUCTION, "Produksi", Icons.Rounded.Build),
    MainTab(TabIds.ADMIN_SALES, "Penjualan", Icons.Rounded.ShoppingCart),
    MainTab(TabIds.ADMIN_STOCK, "Stok", Icons.Rounded.List),
    MainTab(TabIds.ADMIN_MENU, "Menu", Icons.Rounded.Menu)
)
private fun titleForAdminTab(tabId: Int): String = when (tabId) {
    TabIds.ADMIN_PRODUCTION -> "Pusat Produksi"
    TabIds.ADMIN_SALES -> "Pusat Penjualan"
    TabIds.ADMIN_STOCK -> "Pusat Inventori"
    TabIds.ADMIN_MENU -> "Pengaturan Sistem"
    else -> "Ringkasan Admin"
}
private fun String.shortHeaderName(): String = trim().substringBefore('@').split(' ', '.', '_', '-').firstOrNull { it.isNotBlank() } ?: "User"
private fun String.initials(): String = split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "A" }

// === AKTIVITAS UTAMA ===
class AktivitasUtamaAdmin : AktivitasDasar() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var namaLogin by mutableStateOf("Admin / Pemilik")
    private var selectedTabId by mutableIntStateOf(TabIds.ADMIN_DASHBOARD)
    private var dashboardRefreshKey by mutableIntStateOf(0)
    private var productionRefreshKey by mutableIntStateOf(0)
    private var salesRefreshKey by mutableIntStateOf(0)
    private var stockRefreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (auth.currentUser == null) {
            startActivity(Intent(this, AktivitasMasuk::class.java))
            finish()
            return
        }
        selectedTabId = consumePendingAdminTab() ?: (intent?.getIntExtra(EXTRA_TAB_ID, TabIds.ADMIN_DASHBOARD) ?: TabIds.ADMIN_DASHBOARD)
        setContent {
            SiTahuProTheme {
                AdminMainScreen(
                    namaLogin = namaLogin, selectedTabId = selectedTabId,
                    dashboardRefreshKey = dashboardRefreshKey, productionRefreshKey = productionRefreshKey,
                    salesRefreshKey = salesRefreshKey, stockRefreshKey = stockRefreshKey,
                    onTabSelected = { selectedTabId = it },
                    onLogout = { auth.signOut(); startActivity(Intent(this, AktivitasMasuk::class.java)); finish() },
                    onSwitchCashier = { startActivity(AktivitasUtamaKasir.intent(this, clearTop = true)); finish() }
                )
            }
        }
        loadNamaLogin()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectedTabId = consumePendingAdminTab() ?: intent.getIntExtra(EXTRA_TAB_ID, selectedTabId)
    }
    override fun onResume() {
        super.onResume()
        consumePendingAdminTab()?.let { selectedTabId = it }
        dashboardRefreshKey++; productionRefreshKey++; salesRefreshKey++; stockRefreshKey++
    }
    private fun consumePendingAdminTab(): Int? {
        val prefs = getSharedPreferences(NAV_PREF_NAME, MODE_PRIVATE)
        if (!prefs.contains(NAV_KEY_ADMIN_TAB)) return null
        val tabId = prefs.getInt(NAV_KEY_ADMIN_TAB, TabIds.ADMIN_DASHBOARD)
        prefs.edit().remove(NAV_KEY_ADMIN_TAB).apply()
        return tabId
    }
    private fun loadNamaLogin() {
        val uid = auth.currentUser?.uid ?: return
        PenggunaFirestoreCompat.findByAuthUid(firestore = firestore, authUid = uid,
            onFound = { doc ->
                PenggunaFirestoreCompat.migrateLegacyDocIfNeeded(firestore = firestore, doc = doc, authUid = uid,
                    onComplete = { syncedDoc ->
                        namaLogin = listOf(syncedDoc.getString("namaPengguna"), syncedDoc.getString("namaLengkap"), syncedDoc.getString("nama"), auth.currentUser?.displayName, auth.currentUser?.email, "Admin / Pemilik").firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
                    },
                    onError = { namaLogin = auth.currentUser?.email ?: "Admin / Pemilik" }
                )
            },
            onNotFound = { namaLogin = auth.currentUser?.email ?: "Admin / Pemilik" }, onError = { namaLogin = auth.currentUser?.email ?: "Admin / Pemilik" }
        )
    }
    companion object {
        private const val EXTRA_TAB_ID = "extra_tab_id"
        private const val NAV_PREF_NAME = "si_tahu_navigation"
        private const val NAV_KEY_ADMIN_TAB = "next_admin_tab"
        fun intent(context: Context, tabId: Int = TabIds.ADMIN_DASHBOARD, clearTop: Boolean = false): Intent {
            return Intent(context, AktivitasUtamaAdmin::class.java).putExtra(EXTRA_TAB_ID, tabId).apply { if (clearTop) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
        }
    }
}

// === TEMA DAN SKELETON ===
internal object ProTheme {
    val primary: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF3B82F6) else Color(0xFF2563EB)
    val primaryLight: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1E3A8A) else Color(0xFFDBEAFE)
    val background: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF111827) else Color(0xFFF3F4F6)
    val surface: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1F2937) else Color(0xFFFFFFFF)
    val text: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF9FAFB) else Color(0xFF111827)
    val muted: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val border: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF374151) else Color(0xFFE5E7EB)
    val success: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF10B981) else Color(0xFF059669)
    val successLight: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF065F46) else Color(0xFFD1FAE5)
    val warning: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF59E0B) else Color(0xFFD97706)
    val danger: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFEF4444) else Color(0xFFDC2626)
    val pro: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6366F1) else Color(0xFF4F46E5)
}

@Composable
internal fun SiTahuProTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(), content = content)
}

private fun Modifier.adminShimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(), targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "shimmer_offsetX"
    )
    val isDark = isSystemInDarkTheme()
    val baseColor = if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)
    val highlightColor = if (isDark) Color(0xFF4B5563) else Color(0xFFF3F4F6)
    background(brush = Brush.linearGradient(colors = listOf(baseColor, highlightColor, baseColor), start = Offset(startOffsetX, 0f), end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat()))).onGloballyPositioned { size = it.size }
}

// === FUNGSI PEMBANTU GRAFIK ===
private fun totalProduksiGrafikLabel(grafik: List<RepositoriFirebaseUtama.TitikGrafikProduksi>, summary: RepositoriFirebaseUtama.RingkasanProduksi?): String {
    val total = if (grafik.isNotEmpty()) grafik.sumOf { it.total } else { (summary?.totalProduksiDasarHariIni ?: 0) + (summary?.totalProduksiOlahanHariIni ?: 0) }
    return "${Formatter.ribuan(total.toLong())} pcs"
}

private fun trenProduksiLabel(grafik: List<RepositoriFirebaseUtama.TitikGrafikProduksi>): String {
    if (grafik.size < 2) return "7 hari"
    val hariIni = grafik.last().total
    val kemarin = grafik.getOrNull(grafik.lastIndex - 1)?.total ?: 0
    return when { kemarin <= 0 && hariIni > 0 -> "Baru"; kemarin <= 0 -> "0%"; else -> { val persen = ((hariIni - kemarin) * 100.0 / kemarin).toInt(); if (persen >= 0) "+$persen%" else "$persen%" } }
}

private fun grafikProduksiPoints(grafik: List<RepositoriFirebaseUtama.TitikGrafikProduksi>): List<Float> {
    val totals = grafik.map { it.total }
    val maxValue = totals.maxOrNull()?.takeIf { it > 0 } ?: return listOf(0.05f, 0.05f, 0.05f, 0.05f, 0.05f, 0.05f, 0.05f)
    return totals.map { (it.toFloat() / maxValue.toFloat()).coerceIn(0.08f, 1f) }
}

private fun grafikProduksiLabels(grafik: List<RepositoriFirebaseUtama.TitikGrafikProduksi>): List<String> {
    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayFormat = SimpleDateFormat("EEE", Locale("id", "ID"))
    val dateFormat = SimpleDateFormat("dd/MM", Locale("id", "ID"))
    return grafik.map { point -> val date = runCatching { inputFormat.parse(point.kunciTanggal) }.getOrNull(); if (date != null) "${dayFormat.format(date)}\n${dateFormat.format(date)}" else "Hari\n${point.labelTanggal}" }
}

private fun totalPenjualanGrafikLabel(grafik: List<RepositoriFirebaseUtama.TitikGrafikPenjualan>, summary: RepositoriFirebaseUtama.RingkasanPenjualan?): String {
    val total = if (grafik.isNotEmpty()) grafik.sumOf { it.totalNominal } else summary?.totalHariIni ?: 0L
    return Formatter.currency(total)
}

private fun trenPenjualanLabel(grafik: List<RepositoriFirebaseUtama.TitikGrafikPenjualan>): String {
    if (grafik.size < 2) return "7 hari"
    val hariIni = grafik.last().totalNominal
    val kemarin = grafik.getOrNull(grafik.lastIndex - 1)?.totalNominal ?: 0L
    return when { kemarin <= 0L && hariIni > 0L -> "Baru"; kemarin <= 0L -> "0%"; else -> { val persen = ((hariIni - kemarin) * 100.0 / kemarin).toInt(); if (persen >= 0) "+$persen%" else "$persen%" } }
}

private fun grafikPenjualanPoints(grafik: List<RepositoriFirebaseUtama.TitikGrafikPenjualan>): List<Float> {
    val totals = grafik.map { it.totalNominal }
    val maxValue = totals.maxOrNull()?.takeIf { it > 0L } ?: return listOf(0.05f, 0.05f, 0.05f, 0.05f, 0.05f, 0.05f, 0.05f)
    return totals.map { (it.toFloat() / maxValue.toFloat()).coerceIn(0.08f, 1f) }
}

private fun grafikPenjualanLabels(grafik: List<RepositoriFirebaseUtama.TitikGrafikPenjualan>): List<String> {
    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayFormat = SimpleDateFormat("EEE", Locale("id", "ID"))
    val dateFormat = SimpleDateFormat("dd/MM", Locale("id", "ID"))
    return grafik.map { point ->
        val date = runCatching { inputFormat.parse(point.kunciTanggal) }.getOrNull()
        if (date != null) "${dayFormat.format(date)}\n${dateFormat.format(date)}" else "Hari\n${point.labelTanggal}"
    }
}

private fun grafikPenjualanYLabels(grafik: List<RepositoriFirebaseUtama.TitikGrafikPenjualan>): List<String> {
    val maxValue = grafik.maxOfOrNull { it.totalNominal }?.takeIf { it > 0L } ?: 0L
    val midValue = maxValue / 2L
    return listOf(formatNominalGrafikSingkat(maxValue), formatNominalGrafikSingkat(midValue), "0")
}

private fun formatNominalGrafikSingkat(value: Long): String = when {
    value >= 1_000_000L -> "${value / 1_000_000L}jt"
    value >= 1_000L -> "${value / 1_000L}rb"
    value > 0L -> value.toString()
    else -> "0"
}

// === LAYAR KERANGKA (APP SHELL) ===
@Composable
private fun AdminMainScreen(
    namaLogin: String, selectedTabId: Int, dashboardRefreshKey: Int, productionRefreshKey: Int,
    salesRefreshKey: Int, stockRefreshKey: Int, onTabSelected: (Int) -> Unit,
    onLogout: () -> Unit, onSwitchCashier: () -> Unit
) {
    val context = LocalContext.current
    var notifikasiAdmin by remember { mutableStateOf<List<RepositoriFirebaseUtama.NotifikasiAdmin>>(emptyList()) }
    var isNotificationLoading by remember { mutableStateOf(false) }

    LaunchedEffect(dashboardRefreshKey, productionRefreshKey, salesRefreshKey, stockRefreshKey) {
        isNotificationLoading = true
        runCatching { RepositoriFirebaseUtama.muatNotifikasiAdmin() }.onSuccess { notifikasiAdmin = it }.onFailure { notifikasiAdmin = emptyList() }
        isNotificationLoading = false
    }

    val currentFabItems = when (selectedTabId) {
        TabIds.ADMIN_SALES -> listOf(
            SpeedDialItem("Catat Rekap Pasar", Icons.Rounded.Edit) { context.startActivity(Intent(context, AktivitasRekapPasar::class.java)) },
            SpeedDialItem("Riwayat Pasar", Icons.Rounded.DateRange) {
                context.startActivity(Intent(context, AktivitasRiwayatPenjualan::class.java).putExtra(AktivitasRiwayatPenjualan.EXTRA_SCREEN_TITLE, "Riwayat Rekap Pasar").putExtra(AktivitasRiwayatPenjualan.EXTRA_SCREEN_SUBTITLE, "Hanya transaksi dari rekap pasar").putExtra(AktivitasRiwayatPenjualan.EXTRA_DEFAULT_FILTER, AktivitasRiwayatPenjualan.FILTER_PASAR).putExtra(AktivitasRiwayatPenjualan.EXTRA_LOCK_FILTER, true))
            }
        )
        TabIds.ADMIN_PRODUCTION -> listOf(
            SpeedDialItem("Produksi Tahu", Icons.Rounded.Add) { context.startActivity(Intent(context, AktivitasProduksiTahuDasar::class.java)) },
            SpeedDialItem("Konversi Produk", Icons.Rounded.Refresh) { context.startActivity(Intent(context, AktivitasKonversiProduk::class.java)) },
            SpeedDialItem("Riwayat Produksi", Icons.Rounded.DateRange) { context.startActivity(Intent(context, AktivitasRiwayatProduksi::class.java)) }
        )
        TabIds.ADMIN_STOCK -> listOf(
            SpeedDialItem("Monitoring Stok", Icons.Rounded.Search) { context.startActivity(Intent(context, AktivitasMonitoringStok::class.java)) },
            SpeedDialItem("Opname Stok", Icons.Rounded.CheckCircle) { context.startActivity(Intent(context, AktivitasStockAdjustment::class.java)) },
            SpeedDialItem("Riwayat Mutasi", Icons.Rounded.DateRange) { context.startActivity(Intent(context, AktivitasRiwayatSemuaStok::class.java)) }
        )
        else -> emptyList()
    }

    ProAppShell(
        title = titleForAdminTab(selectedTabId), subtitle = namaLogin, tabs = adminTabs, selectedTabId = selectedTabId,
        onTabSelected = onTabSelected, fabItems = currentFabItems, notifikasiAdmin = notifikasiAdmin, isNotificationLoading = isNotificationLoading,
        onNotificationClick = { item -> if(item.tujuan == "harga") context.startActivity(Intent(context, AktivitasDaftarHarga::class.java)) else context.startActivity(Intent(context, AktivitasMonitoringStok::class.java)) }
    ) {
        when (selectedTabId) {
            TabIds.ADMIN_PRODUCTION -> AdminProductionPage(productionRefreshKey)
            TabIds.ADMIN_SALES -> AdminSalesPage(salesRefreshKey)
            TabIds.ADMIN_STOCK -> AdminStockPage(stockRefreshKey)
            TabIds.ADMIN_MENU -> AdminMenuPage(onLogout, onSwitchCashier)
            else -> AdminDashboardPage(dashboardRefreshKey, onSwitchCashier)
        }
    }
}

@Composable
internal fun ProAppShell(
    title: String, subtitle: String, tabs: List<MainTab>, selectedTabId: Int,
    onTabSelected: (Int) -> Unit, fabItems: List<SpeedDialItem> = emptyList(),
    notifikasiAdmin: List<RepositoriFirebaseUtama.NotifikasiAdmin> = emptyList(),
    isNotificationLoading: Boolean = false,
    onNotificationClick: (RepositoriFirebaseUtama.NotifikasiAdmin) -> Unit = {},
    content: @Composable () -> Unit
) {
    Surface(color = ProTheme.background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                MainHeader(title = title, subtitle = subtitle, notifikasiAdmin = notifikasiAdmin, isNotificationLoading = isNotificationLoading, onNotificationClick = onNotificationClick)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp)) { content() }
                ProBottomNav(tabs, selectedTabId, onTabSelected)
            }
            if (fabItems.isNotEmpty()) { SpeedDialMenu(items = fabItems) }
        }
    }
}

@Composable
internal fun MainHeader(
    title: String, subtitle: String, notifikasiAdmin: List<RepositoriFirebaseUtama.NotifikasiAdmin> = emptyList(),
    isNotificationLoading: Boolean = false, onNotificationClick: (RepositoriFirebaseUtama.NotifikasiAdmin) -> Unit = {}
) {
    Surface(
        color = if (isSystemInDarkTheme()) ProTheme.surface else ProTheme.primary,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        shadowElevation = if (isSystemInDarkTheme()) 0.dp else 8.dp,
        border = if (isSystemInDarkTheme()) BorderStroke(1.dp, ProTheme.border) else null
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileBubble(subtitle.initials())
            Column(Modifier.weight(1f)) {
                Text("Sistem SiTahu • ${subtitle.shortHeaderName()}", color = if (isSystemInDarkTheme()) ProTheme.muted else ProTheme.primaryLight, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Text(title, color = if (isSystemInDarkTheme()) ProTheme.text else Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            NotificationIcon(items = notifikasiAdmin, isLoading = isNotificationLoading, onNotificationClick = onNotificationClick)
        }
    }
}

@Composable
private fun ProBottomNav(tabs: List<MainTab>, selectedTabId: Int, onTabSelected: (Int) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = ProTheme.surface, shadowElevation = 16.dp) {
        Row(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                val selected = selectedTabId == tab.id
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onTabSelected(tab.id) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(imageVector = tab.icon, contentDescription = tab.label, tint = if (selected) ProTheme.primary else ProTheme.muted, modifier = Modifier.size(24.dp))
                    Text(tab.label, color = if (selected) ProTheme.primary else ProTheme.muted, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun SpeedDialMenu(items: List<SpeedDialItem>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 45f else 0f, label = "fab_rotate")
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable { expanded = false })
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 100.dp, end = 20.dp)) {
            AnimatedVisibility(visible = expanded, enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }), exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 })) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items.forEach { item ->
                        Surface(shape = RoundedCornerShape(100), color = ProTheme.surface, modifier = Modifier.clickable { expanded = false; item.onClick() }) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Icon(imageVector = item.icon, contentDescription = item.label, tint = ProTheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(12.dp))
                                Text(item.label, color = ProTheme.text, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Surface(shape = CircleShape, color = ProTheme.primary, shadowElevation = if (isSystemInDarkTheme()) 0.dp else 6.dp, border = if (isSystemInDarkTheme()) BorderStroke(1.dp, ProTheme.border) else null, modifier = Modifier.size(56.dp).clickable { expanded = !expanded }) {
                Box(contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Rounded.Add, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(28.dp).rotate(rotation)) }
            }
        }
    }
}

// === TAMPILAN DASHBOARD ===
@Composable
private fun AdminDashboardPage(dashboardRefreshKey: Int, onSwitchCashier: () -> Unit) {
    val context = LocalContext.current
    var ringkasan by remember { mutableStateOf<RepositoriFirebaseUtama.RingkasanDashboard?>(null) }
    var grafikPenjualan by remember { mutableStateOf<List<RepositoriFirebaseUtama.TitikGrafikPenjualan>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dashboardRefreshKey) {
        isLoading = true
        errorMessage = null
        runCatching { RepositoriFirebaseUtama.muatRingkasanDashboard() to RepositoriFirebaseUtama.muatGrafikPenjualan7Hari() }
            .onSuccess { (summary, salesChart) -> ringkasan = summary; grafikPenjualan = salesChart; isLoading = false }
            .onFailure { error -> errorMessage = error.message ?: "Gagal memuat ringkasan dasbor"; isLoading = false }
    }

    if (errorMessage != null) InfoStateCard(errorMessage.orEmpty())

    SummaryTodayCard(ringkasan, isLoading)

    Spacer(Modifier.height(16.dp))
    SectionTitle("Aksi Cepat", "Jalan pintas menu operasional")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickTile("Katalog", Icons.Rounded.Category, ProTheme.primary, Modifier.weight(1f)) { context.startActivity(Intent(context, AktivitasDaftarProduk::class.java)) }
        QuickTile("Kasir", Icons.Rounded.PointOfSale, ProTheme.success, Modifier.weight(1f), onSwitchCashier)
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickTile("Laporan", Icons.Rounded.BarChart, ProTheme.pro, Modifier.weight(1f)) { context.startActivity(Intent(context, AktivitasLaporan::class.java)) }
        QuickTile("Pengeluaran", Icons.Rounded.Receipt, ProTheme.warning, Modifier.weight(1f)) { context.startActivity(Intent(context, AktivitasDaftarPengeluaran::class.java)) }
    }

    Spacer(Modifier.height(16.dp))
    SectionTitle("Status Stok", "Produk menipis dan hampir kadaluarsa")
    StockAttentionCard(
        stokMenipis = ringkasan?.stokMenipis.orEmpty(), hampirEd = ringkasan?.hampirEd.orEmpty(), isLoading = isLoading,
        onOpenStock = { productId -> context.startActivity(Intent(context, AktivitasDetailStok::class.java).putExtra(AktivitasMonitoringStok.EXTRA_PRODUCT_ID, productId)) }
    )

    Spacer(Modifier.height(16.dp))
    SectionTitle("Grafik Penjualan", "Semua penjualan 7 hari terakhir")
    val rawSalesValues = grafikPenjualan.map { Formatter.currency(it.totalNominal) }
    SalesChartCard(
        isLoading = isLoading, trend = if (isLoading) "" else trenPenjualanLabel(grafikPenjualan),
        points = grafikPenjualanPoints(grafikPenjualan), labels = grafikPenjualanLabels(grafikPenjualan), yLabels = grafikPenjualanYLabels(grafikPenjualan), rawValues = rawSalesValues
    )

    Spacer(Modifier.height(16.dp))
    SectionTitle("Produk Terlaris", "Produk paling banyak terjual hari ini")
    ProductSalesCard(rows = ringkasan?.topProducts.orEmpty(), isLoading = isLoading, title = "Top Produk Hari Ini", emptyText = "Belum ada produk terjual hari ini")

    Spacer(Modifier.height(16.dp))
    SectionTitle("Pengeluaran Terbesar", "Pengeluaran terbesar hari ini")
    ProductSalesCard(rows = ringkasan?.expenseCategories.orEmpty(), isLoading = isLoading, title = "Kategori Pengeluaran", emptyText = "Belum ada pengeluaran hari ini")

    Spacer(Modifier.height(16.dp))
    RecentActivityCard(ringkasan?.recentItems.orEmpty(), isLoading)
}

// === TAMPILAN PENJUALAN ===
@Composable
private fun AdminSalesPage(salesRefreshKey: Int) {
    var ringkasan by remember { mutableStateOf<RepositoriFirebaseUtama.RingkasanPenjualan?>(null) }
    var grafik by remember { mutableStateOf<List<RepositoriFirebaseUtama.TitikGrafikPenjualan>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(salesRefreshKey) {
        isLoading = true
        errorMessage = null
        runCatching { RepositoriFirebaseUtama.muatRingkasanPenjualan(sourceFilter = "PASAR") to RepositoriFirebaseUtama.muatGrafikPenjualan7Hari(sourceFilter = "PASAR") }
            .onSuccess { (summary, chart) -> ringkasan = summary; grafik = chart; isLoading = false }
            .onFailure { error -> isLoading = false; errorMessage = error.message ?: "Gagal memuat data penjualan" }
    }

    SectionTitle("Performa Hari Ini", "Data rekap pasar dari Cloud Firestore")
    if (errorMessage != null) InfoStateCard(errorMessage.orEmpty())

    if (isLoading) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCardSkeleton(Modifier.weight(1f))
            KpiCardSkeleton(Modifier.weight(1f))
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Omzet Pasar", Formatter.currency(ringkasan?.totalHariIni ?: 0L), "${Formatter.ribuan((ringkasan?.jumlahTransaksiHariIni ?: 0).toLong())} Nota", ProTheme.success, Modifier.weight(1f))
            KpiCard("Terjual", Formatter.ribuan((ringkasan?.totalItemHariIni ?: 0).toLong()), "Pcs", ProTheme.primary, Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(16.dp))
    SectionTitle("Grafik Penjualan Pasar", "Tren omzet 7 hari terakhir")
    val rawSalesValues = grafik.map { Formatter.currency(it.totalNominal) }
    SalesChartCard(isLoading = isLoading, trend = if(isLoading) "" else trenPenjualanLabel(grafik), chartTitle = "Penjualan Pasar", chartSubtitle = "Tren penjualan pasar 7 hari terakhir", points = grafikPenjualanPoints(grafik), labels = grafikPenjualanLabels(grafik), yLabels = grafikPenjualanYLabels(grafik), rawValues = rawSalesValues)

    Spacer(Modifier.height(16.dp))
    SectionTitle("Produk Terlaris", "Kontributor omzet rekap pasar")
    ProductSalesCard(ringkasan?.topProducts.orEmpty(), isLoading)

    Spacer(Modifier.height(16.dp))
    SectionTitle("Transaksi Terbaru", "Log rekap pasar terbaru")
    RecentSalesActivityCard(ringkasan?.recentRows.orEmpty(), isLoading)
}

// === TAMPILAN PRODUKSI ===
@Composable
private fun AdminProductionPage(productionRefreshKey: Int) {
    var ringkasan by remember { mutableStateOf<RepositoriFirebaseUtama.RingkasanProduksi?>(null) }
    var grafik by remember { mutableStateOf<List<RepositoriFirebaseUtama.TitikGrafikProduksi>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(productionRefreshKey) {
        isLoading = true
        errorMessage = null
        runCatching { RepositoriFirebaseUtama.muatRingkasanProduksi() to RepositoriFirebaseUtama.muatGrafikProduksi7Hari() }
            .onSuccess { (summary, chart) -> ringkasan = summary; grafik = chart; isLoading = false }
            .onFailure { error -> isLoading = false; errorMessage = error.message ?: "Gagal memuat data produksi" }
    }

    SectionTitle("Ringkasan Hari Ini", "Data produksi dari Cloud Firestore")
    if (errorMessage != null) InfoStateCard(errorMessage.orEmpty())
    ProductionSummaryGrid(ringkasan, isLoading)

    Spacer(Modifier.height(16.dp))
    SectionTitle("Grafik Produksi", "Tren jumlah produksi 7 hari terakhir")
    val rawProdValues = grafik.map { Formatter.ribuan(it.total.toLong()) + " pcs" }
    ProductionChartCard(isLoading = isLoading, value = if (isLoading) "" else totalProduksiGrafikLabel(grafik, ringkasan), trend = if(isLoading) "" else trenProduksiLabel(grafik), points = grafikProduksiPoints(grafik), labels = grafikProduksiLabels(grafik), rawValues = rawProdValues)

    Spacer(Modifier.height(16.dp))
    SectionTitle("Aktivitas Dapur", "Log pencatatan produksi terbaru")
    RecentProductionCard(ringkasan?.recentRows.orEmpty(), isLoading)
}

// === TAMPILAN STOK ===
@Composable
private fun AdminStockPage(refreshKey: Int) {
    val context = LocalContext.current
    var ringkasan by remember { mutableStateOf<RepositoriFirebaseUtama.RingkasanStokDashboard?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        errorMessage = null
        runCatching { RepositoriFirebaseUtama.muatRingkasanStokDashboard() }
            .onSuccess { ringkasan = it; isLoading = false }
            .onFailure { errorMessage = it.message ?: "Gagal memuat ringkasan stok"; ringkasan = null; isLoading = false }
    }

    SectionTitle("Ketersediaan", "Ringkasan inventori dari Firebase")
    if (errorMessage != null) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.danger.copy(alpha = 0.08f)), border = BorderStroke(1.dp, ProTheme.danger.copy(alpha = 0.25f))) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Stok gagal dimuat", color = ProTheme.danger, fontWeight = FontWeight.Bold)
                Text(errorMessage.orEmpty(), color = ProTheme.text, style = MaterialTheme.typography.bodySmall)
                ActionButton("Buka Monitoring Stok", ProTheme.primary) { context.startActivity(Intent(context, AktivitasMonitoringStok::class.java)) }
            }
        }
        return
    }

    if (isLoading) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { KpiCardSkeleton(Modifier.weight(1f)); KpiCardSkeleton(Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { KpiCardSkeleton(Modifier.weight(1f)); KpiCardSkeleton(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(24.dp))
        SectionTitle("Stok Perlu Diperhatikan", "Prioritas tindakan gudang")
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
            Column(Modifier.padding(vertical = 8.dp)) { repeat(4) { idx -> StockDashboardItemRowSkeleton(); if (idx < 3) HorizontalDivider(color = ProTheme.border, modifier = Modifier.padding(horizontal = 20.dp)) } }
        }
        return
    }

    val data = ringkasan ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Stok Layak", Formatter.ribuan(data.totalStokLayakJual), "${data.totalProdukAktif} produk aktif", ProTheme.primary, Modifier.weight(1f))
            KpiCard("Stok Fisik", Formatter.ribuan(data.totalStokFisik), "Termasuk ED", ProTheme.pro, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard("Hampir ED", Formatter.ribuan(data.totalHampirKadaluarsa), "Perlu diprioritaskan", ProTheme.warning, Modifier.weight(1f))
            KpiCard("Kadaluarsa", Formatter.ribuan(data.totalKadaluarsa), "Perlu tindakan", ProTheme.danger, Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(24.dp))
    SectionTitle("Stok Perlu Diperhatikan", "Prioritas tindakan gudang")
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            val items = data.produkKritis.ifEmpty { data.produkTerbanyak }
            if (items.isEmpty()) {
                EmptyDataView("Belum ada produk stok", "Tambahkan produk di menu master")
            } else {
                items.forEachIndexed { index, item ->
                    StockDashboardItemRow(item) { context.startActivity(Intent(context, AktivitasDetailStok::class.java).putExtra(AktivitasMonitoringStok.EXTRA_PRODUCT_ID, item.id)) }
                    if (index != items.lastIndex) HorizontalDivider(color = ProTheme.border, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }
    }
}

@Composable
private fun StockDashboardItemRow(item: RepositoriFirebaseUtama.BarisStokDashboard, onClick: () -> Unit) {
    val statusColor = when (item.status) {
        "Aman" -> ProTheme.success
        "Menipis" -> ProTheme.warning
        "Habis" -> ProTheme.danger
        "Hampir ED" -> ProTheme.warning
        "ED Hari Ini" -> ProTheme.warning
        else -> ProTheme.danger
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(item.namaProduk.firstOrNull()?.uppercaseChar()?.toString() ?: "P", color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.namaProduk, fontWeight = FontWeight.Bold, color = ProTheme.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.kategori} • ${item.kodeProduk}", color = ProTheme.muted, style = MaterialTheme.typography.labelSmall)
            Text("Layak ${Formatter.ribuan(item.stokLayakJual.toLong())} ${item.satuan} • Fisik ${Formatter.ribuan(item.stokFisik.toLong())}", color = ProTheme.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.stokEdHariIni > 0 || item.stokHampirKadaluarsa > 0 || item.stokKadaluarsa > 0 || item.edTerdekat.isNotBlank()) {
                Text(buildString {
                    if (item.stokEdHariIni > 0) append("ED Hari Ini ${Formatter.ribuan(item.stokEdHariIni.toLong())}  ")
                    if (item.stokHampirKadaluarsa > 0) append("Hampir ED ${Formatter.ribuan(item.stokHampirKadaluarsa.toLong())}  ")
                    if (item.stokKadaluarsa > 0) append("ED ${Formatter.ribuan(item.stokKadaluarsa.toLong())}  ")
                    if (item.edTerdekat.isNotBlank()) append("ED terdekat ${Formatter.readableShortDate(item.edTerdekat)}")
                }.trim(), color = statusColor, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.1f)) { Text(item.status, color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
    }
}

// === TAMPILAN MENU ===
@Composable
private fun AdminMenuPage(onLogout: () -> Unit, onSwitchCashier: () -> Unit) {
    val context = LocalContext.current
    MenuSection("Data Master") {
        MenuItemRow(Icons.Rounded.Category, "Daftar Produk") { context.startActivity(Intent(context, AktivitasDaftarProduk::class.java)) }
        MenuItemRow(Icons.Rounded.AttachMoney, "Harga Jual") { context.startActivity(Intent(context, AktivitasDaftarHarga::class.java)) }
        MenuItemRow(Icons.Rounded.Tune, "Parameter Sistem", isLast = true) { context.startActivity(Intent(context, AktivitasDaftarParameter::class.java)) }
    }
    Spacer(Modifier.height(24.dp))
    MenuSection("Laporan & Keuangan") {
        MenuItemRow(Icons.Rounded.Receipt, "Catatan Pengeluaran") { context.startActivity(Intent(context, AktivitasDaftarPengeluaran::class.java)) }
        MenuItemRow(Icons.Rounded.History, "Riwayat Transaksi") { context.startActivity(AktivitasRiwayatTransaksi.intent(context)) }
        MenuItemRow(Icons.Rounded.BarChart, "Laporan Penjualan", isLast = true) { context.startActivity(Intent(context, AktivitasLaporan::class.java)) }
    }
    Spacer(Modifier.height(24.dp))
    MenuSection("Akun & Keamanan") {
        MenuItemRow(Icons.Rounded.Group, "Daftar Pengguna") { context.startActivity(Intent(context, AktivitasDaftarPengguna::class.java)) }
        MenuItemRow(Icons.Rounded.Settings, "Pengaturan Usaha", isLast = true) { context.startActivity(Intent(context, AktivitasPengaturanUsaha::class.java)) }
    }
    Spacer(Modifier.height(32.dp))
    ActionButton("Masuk Mode Kasir", ProTheme.surface, onClick = onSwitchCashier)
    Spacer(Modifier.height(12.dp))
    ActionButton("Keluar Sistem", ProTheme.danger, isDestructive = true, onClick = onLogout)
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun MenuSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = ProTheme.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), border = BorderStroke(1.dp, ProTheme.border)) {
            Column { content() }
        }
    }
}

@Composable
private fun MenuItemRow(icon: ImageVector, title: String, isLast: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(ProTheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = title, tint = ProTheme.primary, modifier = Modifier.size(20.dp)) }
            Text(title, fontWeight = FontWeight.Medium, color = ProTheme.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Buka", tint = ProTheme.muted, modifier = Modifier.size(24.dp))
        }
        if (!isLast) { HorizontalDivider(color = ProTheme.border, modifier = Modifier.padding(start = 76.dp, end = 20.dp)) }
    }
}

// === KOMPONEN UI TAMBAHAN & REUSABLE ===

@Composable
private fun ProDetailDialog(
    title: String, badge: String, detailText: String, isLoading: Boolean,
    extraActions: @Composable (RowScope.() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = ProTheme.surface,
        titleContentColor = ProTheme.text, textContentColor = ProTheme.text, shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(badge.uppercase(), color = ProTheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (isLoading) {
                    HistoryDetailContentSkeleton(ProTheme.primary, ProTheme.border)
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = if (isSystemInDarkTheme()) Color(0xFF111827) else Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, ProTheme.border), modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = detailText.ifBlank { "Detail belum tersedia." }, color = ProTheme.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()).padding(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                if (extraActions != null && !isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        extraActions()
                    }
                }
                Button(
                    onClick = onDismiss, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ProTheme.primary, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("Tutup", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    )
}

@Composable
private fun DialogActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp)
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha=0.15f)), contentAlignment=Alignment.Center) {
            Icon(icon, contentDescription=label, tint=color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color=color, style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Bold)
    }
}

@Composable
private fun EmptyDataView(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(ProTheme.muted.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Inbox, contentDescription = null, tint = ProTheme.muted, modifier = Modifier.size(28.dp))
        }
        Text(title, color = ProTheme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(subtitle, color = ProTheme.muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun InfoStateCard(message: String) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.warning.copy(alpha = 0.10f)), border = BorderStroke(1.dp, ProTheme.warning.copy(alpha = 0.25f))) {
        Text(text = message, color = ProTheme.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
internal fun SummaryTodayCard(summary: RepositoriFirebaseUtama.RingkasanDashboard?, isLoading: Boolean) {
    val laba = summary?.totalLaba ?: 0L
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ringkasan Hari Ini", fontWeight = FontWeight.Bold, color = ProTheme.text, style = MaterialTheme.typography.titleMedium)
                    if (isLoading) Box(Modifier.height(12.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect()) else Text(summary?.tanggalRingkasan?.let { Formatter.readableShortDate(it) } ?: "Data realtime", color = ProTheme.muted, style = MaterialTheme.typography.labelSmall)
                }
                if (isLoading) Box(Modifier.height(20.dp).width(60.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect()) else Text("Realtime", color = ProTheme.success, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            if (isLoading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { KpiCardSkeleton(Modifier.weight(1f), isCompact = true); KpiCardSkeleton(Modifier.weight(1f), isCompact = true) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { KpiCardSkeleton(Modifier.weight(1f), isCompact = true); KpiCardSkeleton(Modifier.weight(1f), isCompact = true) }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard("Pendapatan", Formatter.currency(summary?.totalPenjualan ?: 0L), "${summary?.totalTransaksi ?: 0} transaksi", ProTheme.primary, Modifier.weight(1f), isCompact = true)
                    KpiCard("Pengeluaran", Formatter.currency(summary?.totalPengeluaran ?: 0L), "Biaya hari ini", ProTheme.warning, Modifier.weight(1f), isCompact = true)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiCard("Laba", Formatter.currency(laba), if (laba >= 0L) "Pendapatan bersih" else "Rugi hari ini", if (laba >= 0L) ProTheme.success else ProTheme.danger, Modifier.weight(1f), isCompact = true)
                    KpiCard("Produk Terjual", Formatter.ribuan((summary?.totalItemTerjual ?: 0).toLong()), "Pcs keluar", ProTheme.success, Modifier.weight(1f), isCompact = true)
                }
            }
        }
    }
}

@Composable
internal fun QuickTile(title: String, icon: ImageVector, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = ProTheme.surface, border = BorderStroke(1.dp, ProTheme.border)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(22.dp)) }
            Text(title, color = ProTheme.text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ActionButton(label: String, colorTheme: Color, isDestructive: Boolean = false, onClick: () -> Unit) {
    val isPrimary = colorTheme == ProTheme.primary || colorTheme == ProTheme.danger
    val containerColor = if (isDestructive) colorTheme.copy(alpha = 0.1f) else colorTheme
    val borderCol = if (isDestructive) colorTheme.copy(alpha = 0.3f) else if (isPrimary) colorTheme else ProTheme.border
    val textColor = if (isDestructive) colorTheme else if (isPrimary) Color.White else ProTheme.text

    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = containerColor, border = BorderStroke(1.dp, borderCol), shadowElevation = if (isPrimary && !isDestructive) 2.dp else 0.dp) {
        Box(Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) { Text(label, fontWeight = FontWeight.Bold, color = textColor) }
    }
}

@Composable
private fun KpiCard(title: String, value: String, badge: String, accent: Color, modifier: Modifier = Modifier, isCompact: Boolean = false) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(if (isCompact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = ProTheme.muted, style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = accent.copy(alpha=0.5f), modifier = Modifier.size(16.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, color = ProTheme.text, fontWeight = FontWeight.Bold, style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
                Surface(shape = RoundedCornerShape(4.dp), color = accent.copy(alpha = 0.1f)) { Text(badge, color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
        }
    }
}

@Composable
private fun SalesChartCard(isLoading: Boolean, trend: String, chartTitle: String = "Penjualan Kasir + Admin", chartSubtitle: String = "Tren 7 hari terakhir", points: List<Float> = listOf(0.2f, 0.4f, 0.3f, 0.6f, 0.5f, 0.8f, 0.7f), labels: List<String> = emptyList(), yLabels: List<String> = emptyList(), rawValues: List<String> = emptyList()) {
    if (isLoading) { ChartCardSkeleton("Grafik Penjualan"); return }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(chartTitle, fontWeight = FontWeight.Bold, color = ProTheme.text, style = MaterialTheme.typography.titleMedium)
                    Text(chartSubtitle, color = ProTheme.muted, style = MaterialTheme.typography.labelMedium)
                }
                if (trend.isNotBlank()) StatusPill(trend, ProTheme.success, ProTheme.successLight)
            }
            MiniChartWithYAxis(modifier = Modifier.fillMaxWidth().height(142.dp), points = points, yLabels = yLabels, rawValues = rawValues)
            if (labels.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Spacer(Modifier.width(54.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                        labels.forEach { label ->
                            val parts = label.split("\n")
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(parts.getOrElse(0) { "-" }, color = ProTheme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                Text(parts.drop(1).joinToString("\n"), color = ProTheme.muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, lineHeight = MaterialTheme.typography.labelSmall.lineHeight)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductionChartCard(isLoading: Boolean, value: String, trend: String, points: List<Float>, labels: List<String>, rawValues: List<String> = emptyList()) {
    if (isLoading) { ChartCardSkeleton("Total Pcs Diproduksi"); return }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Total Pcs Diproduksi", color = ProTheme.muted, style = MaterialTheme.typography.labelMedium)
                    Text(value, fontWeight = FontWeight.Bold, color = ProTheme.text, style = MaterialTheme.typography.headlineSmall)
                }
                StatusPill(trend, ProTheme.primary, ProTheme.primaryLight)
            }
            MiniChart(Modifier.fillMaxWidth().height(120.dp), points, rawValues)
            if (labels.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    labels.forEach { label ->
                        val parts = label.split("\n")
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(parts.getOrElse(0) { "-" }, color = ProTheme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                            Text(parts.getOrElse(1) { "" }, color = ProTheme.muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockAttentionCard(stokMenipis: List<RepositoriFirebaseUtama.ItemDashboard>, hampirEd: List<RepositoriFirebaseUtama.ItemDashboard>, isLoading: Boolean, onOpenStock: (String) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Alert Operasional", fontWeight = FontWeight.Bold, color = ProTheme.text, style = MaterialTheme.typography.titleMedium)
                    Text("Diambil dari stok dan batch ED", color = ProTheme.muted, style = MaterialTheme.typography.labelSmall)
                }
                val totalAlert = stokMenipis.size + hampirEd.size
                if (!isLoading) StatusPill("$totalAlert item", if (totalAlert > 0) ProTheme.warning else ProTheme.success, if (totalAlert > 0) ProTheme.warning.copy(alpha = 0.12f) else ProTheme.success.copy(alpha = 0.12f))
            }
            when {
                isLoading -> repeat(3) { ActivityRowSkeleton() }
                stokMenipis.isEmpty() && hampirEd.isEmpty() -> StockWarningRow("Stok aman", "Belum ada stok menipis atau produk hampir ED", "Aman", "OK", ProTheme.success) {}
                else -> {
                    if (stokMenipis.isNotEmpty()) {
                        Text("Stok Menipis", color = ProTheme.danger, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        stokMenipis.forEach { item -> StockWarningRow(item.title, item.subtitle, item.amount, item.badge, if (item.badge.equals("Habis", ignoreCase = true)) ProTheme.danger else ProTheme.warning) { onOpenStock(item.id) } }
                    }
                    if (hampirEd.isNotEmpty()) {
                        if (stokMenipis.isNotEmpty()) Spacer(Modifier.height(2.dp))
                        Text("Hampir ED / ED Hari Ini", color = ProTheme.warning, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        hampirEd.forEach { item -> StockWarningRow(item.title, item.subtitle, item.amount, item.badge, if (item.badge.equals("Kadaluarsa", ignoreCase = true)) ProTheme.danger else ProTheme.warning) { onOpenStock(item.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockWarningRow(title: String, subtitle: String, amount: String, badge: String, color: Color, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.07f), border = BorderStroke(1.dp, color.copy(alpha = 0.16f))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = ProTheme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = ProTheme.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(amount, color = ProTheme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(badge, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ProductSalesCard(rows: List<RepositoriFirebaseUtama.ItemDashboard>, isLoading: Boolean, title: String = "Top Produk", emptyText: String = "Belum ada penjualan pasar") {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = ProTheme.text, style = MaterialTheme.typography.titleMedium)
            when {
                isLoading -> repeat(3) { ProductRowSkeleton() }
                rows.isEmpty() -> EmptyDataView(emptyText, "Data akan muncul otomatis")
                else -> {
                    val colors = listOf(ProTheme.primary, ProTheme.pro, ProTheme.success, ProTheme.warning, ProTheme.muted)
                    rows.forEachIndexed { index, item ->
                        val progress = when (index) { 0 -> 1f; 1 -> 0.76f; 2 -> 0.54f; 3 -> 0.38f; else -> 0.24f }
                        ProductRow(item.title, "${item.subtitle} • ${item.amount}", progress, colors[index % colors.size])
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(name: String, pcs: String, progress: Float, color: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontWeight = FontWeight.Medium, color = ProTheme.text, style = MaterialTheme.typography.bodyMedium)
            Text(pcs, color = ProTheme.muted, style = MaterialTheme.typography.bodyMedium)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100)).background(ProTheme.border)) { Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(RoundedCornerShape(100)).background(color)) }
    }
}

@Composable
private fun RecentActivityCard(rows: List<RepositoriFirebaseUtama.ItemDashboard>, isLoading: Boolean) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Log Aktivitas Terbaru", fontWeight = FontWeight.Bold, color = ProTheme.text, style = MaterialTheme.typography.titleMedium)
            when {
                isLoading -> repeat(4) { ActivityRowSkeleton() }
                rows.isEmpty() -> EmptyDataView("Belum ada aktivitas", "Transaksi, produksi, dan pengeluaran terbaru akan muncul di sini")
                else -> rows.forEach { item ->
                    val color = when {
                        item.title.contains("Pengeluaran", ignoreCase = true) -> ProTheme.warning
                        item.title.contains("Produksi", ignoreCase = true) || item.badge.contains("Produksi", ignoreCase = true) -> ProTheme.pro
                        else -> ProTheme.success
                    }
                    ActivityRow(title = item.title, subtitle = "${item.badge} • ${item.subtitle}", value = item.amount, dotColor = color)
                }
            }
        }
    }
}

@Composable
private fun RecentSalesActivityCard(rows: List<RepositoriFirebaseUtama.ItemBarisPenjualan>, isLoading: Boolean) {
    val context = LocalContext.current
    var selectedItem by remember { mutableStateOf<RepositoriFirebaseUtama.ItemBarisPenjualan?>(null) }
    var detailText by remember { mutableStateOf("") }
    var isLoadingDetail by remember { mutableStateOf(false) }

    LaunchedEffect(selectedItem?.id) {
        val item = selectedItem ?: return@LaunchedEffect
        detailText = ""; isLoadingDetail = true
        detailText = runCatching { RepositoriFirebaseUtama.buildReceiptText(item.id) }.getOrElse { it.message ?: "Gagal memuat detail penjualan" }
        isLoadingDetail = false
    }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                isLoading -> repeat(4) { ActivityRowSkeleton() }
                rows.isEmpty() -> EmptyDataView("Belum ada rekap pasar", "Transaksi pasar akan muncul di sini")
                else -> rows.forEach { item -> ActivityRow(item.title, item.subtitle, item.amount, ProTheme.success, Modifier.clip(RoundedCornerShape(14.dp)).clickable { selectedItem = item }.padding(8.dp)) }
            }
        }
    }

    val item = selectedItem
    if (item != null) {
        ProDetailDialog(
            title = item.title,
            badge = item.badge,
            detailText = detailText,
            isLoading = isLoadingDetail,
            extraActions = {
                DialogActionButton(Icons.Rounded.Share, "Bagikan PDF", ProTheme.pro) {
                    Toast.makeText(context, "Fitur Bagikan PDF segera hadir", Toast.LENGTH_SHORT).show()
                }
                DialogActionButton(Icons.Rounded.Download, "Download PDF", ProTheme.primary) {
                    Toast.makeText(context, "Fitur Download PDF segera hadir", Toast.LENGTH_SHORT).show()
                }
                DialogActionButton(Icons.Rounded.Print, "Cetak Struk", ProTheme.warning) {
                    Toast.makeText(context, "Fitur Cetak Struk segera hadir", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { selectedItem = null }
        )
    }
}

@Composable
private fun RecentProductionCard(rows: List<RepositoriFirebaseUtama.BarisRiwayatProduksi>, isLoading: Boolean) {
    var selectedItem by remember { mutableStateOf<RepositoriFirebaseUtama.BarisRiwayatProduksi?>(null) }
    var detailText by remember { mutableStateOf("") }
    var isLoadingDetail by remember { mutableStateOf(false) }

    LaunchedEffect(selectedItem?.id) {
        val item = selectedItem ?: return@LaunchedEffect
        detailText = ""; isLoadingDetail = true
        detailText = runCatching { RepositoriFirebaseUtama.buildProductionDetailText(item.id) }.getOrElse { it.message ?: "Gagal memuat detail produksi" }
        isLoadingDetail = false
    }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                isLoading -> repeat(3) { ActivityRowSkeleton() }
                rows.isEmpty() -> EmptyDataView("Belum ada produksi", "Catatan produksi akan muncul di sini")
                else -> rows.forEach { item -> ActivityRow(item.title, item.subtitle, item.amount, if (item.badge.contains("Olahan", true)) ProTheme.warning else ProTheme.success, Modifier.clip(RoundedCornerShape(14.dp)).clickable { selectedItem = item }.padding(8.dp)) }
            }
        }
    }

    val item = selectedItem
    if (item != null) {
        ProDetailDialog(
            title = item.title,
            badge = item.badge,
            detailText = detailText,
            isLoading = isLoadingDetail,
            onDismiss = { selectedItem = null }
        )
    }
}

@Composable
private fun ActivityRow(title: String, subtitle: String, value: String, dotColor: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = ProTheme.text, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, color = ProTheme.muted, style = MaterialTheme.typography.labelSmall)
        }
        Text(value, color = ProTheme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = ProTheme.text, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = ProTheme.muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StatusPill(text: String, color: Color, bg: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = bg) { Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
}

@Composable
private fun NotificationIcon(items: List<RepositoriFirebaseUtama.NotifikasiAdmin>, isLoading: Boolean, onNotificationClick: (RepositoriFirebaseUtama.NotifikasiAdmin) -> Unit) {
    val isDark = isSystemInDarkTheme()
    var showDialog by remember { mutableStateOf(false) }

    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (isDark) ProTheme.background else Color.White.copy(alpha = 0.2f)).clickable { showDialog = true }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Notifications, contentDescription = "Notifikasi", tint = if (isDark) ProTheme.text else Color.White, modifier = Modifier.size(24.dp))
        if (items.isNotEmpty()) {
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(ProTheme.danger), contentAlignment = Alignment.Center) {
                Text(if (items.size > 9) "9+" else items.size.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false }, confirmButton = { TextButton({ showDialog = false }) { Text("Tutup") } },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Notifikasi", fontWeight = FontWeight.Bold, color = ProTheme.text)
                    Text(if (items.isEmpty()) "Tidak ada pemberitahuan penting" else "${items.size} hal perlu diperhatikan", color = ProTheme.muted, style = MaterialTheme.typography.bodySmall)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        isLoading -> repeat(3) { Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp)).adminShimmerEffect()) }
                        items.isEmpty() -> {
                            Surface(shape = RoundedCornerShape(16.dp), color = ProTheme.success.copy(alpha = 0.10f), border = BorderStroke(1.dp, ProTheme.success.copy(alpha = 0.20f))) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = ProTheme.success)
                                    Column {
                                        Text("Semua aman", fontWeight = FontWeight.Bold, color = ProTheme.text)
                                        Text("Tidak ada stok ED, stok menipis, atau harga yang perlu dicek.", color = ProTheme.muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        else -> items.forEach { item ->
                            val tone = when (item.warna) { "danger" -> ProTheme.danger "warning" -> ProTheme.warning "orange" -> ProTheme.warning else -> ProTheme.primary }
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { showDialog = false; onNotificationClick(item) },
                                shape = RoundedCornerShape(16.dp), color = tone.copy(alpha = 0.10f), border = BorderStroke(1.dp, tone.copy(alpha = 0.24f))
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tone.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                        Text(item.jumlah.coerceAtMost(99).toString(), color = tone, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(item.judul, color = ProTheme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(item.isi, color = ProTheme.muted, style = MaterialTheme.typography.bodySmall)
                                        Text(if (item.tujuan == "harga") "Ketuk untuk buka Harga Kanal" else "Ketuk untuk buka Monitoring Stok", color = tone, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Icon(Icons.Rounded.KeyboardArrowRight, null, tint = tone, modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ProfileBubble(initial: String) {
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(if (isSystemInDarkTheme()) ProTheme.background else ProTheme.surface), contentAlignment = Alignment.Center) {
        Text(initial.take(2), color = ProTheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MiniChartWithYAxis(modifier: Modifier, points: List<Float> = listOf(0.2f, 0.4f, 0.3f, 0.6f, 0.5f, 0.8f, 0.7f), yLabels: List<String> = emptyList(), rawValues: List<String> = emptyList()) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.fillMaxHeight().width(46.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            val labels = if (yLabels.size >= 3) yLabels.take(3) else listOf("0", "0", "0")
            labels.forEach { label -> Text(label, color = ProTheme.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End) }
        }
        Spacer(Modifier.width(8.dp))
        MiniChart(Modifier.weight(1f).fillMaxHeight(), points, rawValues)
    }
}

@Composable
private fun MiniChart(modifier: Modifier, points: List<Float> = listOf(0.2f, 0.4f, 0.3f, 0.6f, 0.5f, 0.8f, 0.7f), rawValues: List<String> = emptyList()) {
    val safePoints = if (points.size >= 2) points else listOf(0.05f, 0.05f)
    val borderColor = ProTheme.border
    val primaryColor = ProTheme.primary
    val primaryLightColor = ProTheme.primaryLight
    val surfaceColor = ProTheme.surface
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(points) { selectedIndex = null }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize().pointerInput(safePoints) {
            detectTapGestures { offset ->
                val cellWidth = size.width / safePoints.size
                selectedIndex = (offset.x / cellWidth).toInt().coerceIn(0, safePoints.lastIndex)
            }
        }) {
            val w = size.width; val h = size.height; val cellWidth = w / safePoints.size
            val chartTopPadding = 8.dp.toPx(); val chartBottomPadding = 8.dp.toPx()
            val usableHeight = h - chartTopPadding - chartBottomPadding

            repeat(4) { i -> val y = chartTopPadding + (usableHeight * (i / 3f)); drawLine(borderColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx()) }

            val path = Path(); val areaPath = Path()
            val coordinates = safePoints.mapIndexed { i, pt -> Offset((cellWidth * i) + (cellWidth / 2f), chartTopPadding + usableHeight - (pt * usableHeight)) }

            coordinates.forEachIndexed { i, point ->
                if (i == 0) { path.moveTo(point.x, point.y); areaPath.moveTo(point.x, h - chartBottomPadding); areaPath.lineTo(point.x, point.y) }
                else { path.lineTo(point.x, point.y); areaPath.lineTo(point.x, point.y) }
            }
            areaPath.lineTo(coordinates.last().x, h - chartBottomPadding); areaPath.close()

            drawPath(areaPath, primaryLightColor.copy(alpha = 0.3f))
            drawPath(path, primaryColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            val lastPoint = coordinates.last()
            drawCircle(surfaceColor, 6.dp.toPx(), lastPoint)
            drawCircle(primaryColor, 4.dp.toPx(), lastPoint)

            selectedIndex?.let { idx ->
                val point = coordinates[idx]
                drawLine(color = primaryColor.copy(alpha = 0.5f), start = Offset(point.x, chartTopPadding), end = Offset(point.x, h - chartBottomPadding), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                drawCircle(primaryColor, 8.dp.toPx(), point)
                drawCircle(surfaceColor, 4.dp.toPx(), point)
            }
        }

        if (selectedIndex != null && rawValues.isNotEmpty()) {
            val textVal = rawValues.getOrNull(selectedIndex!!)
            if (textVal != null) {
                Surface(modifier = Modifier.align(Alignment.TopCenter).offset(y = (-10).dp), shape = RoundedCornerShape(8.dp), color = ProTheme.text, shadowElevation = 4.dp) {
                    Text(textVal, color = ProTheme.surface, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

// === SKELETON COMPOSABLES (KOMPONEN KERANGKA LOADING PRESISI) ===
@Composable
private fun KpiCardSkeleton(modifier: Modifier = Modifier, isCompact: Boolean = false) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(if (isCompact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.height(14.dp).fillMaxWidth(0.5f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            Box(Modifier.height(24.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            Box(Modifier.height(12.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
        }
    }
}

@Composable
private fun ChartCardSkeleton(title: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, color = ProTheme.muted, style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.height(24.dp).fillMaxWidth(0.5f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                }
                Box(Modifier.height(24.dp).width(60.dp).clip(RoundedCornerShape(6.dp)).adminShimmerEffect())
            }
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)).adminShimmerEffect())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(7) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.height(10.dp).width(20.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                        Box(Modifier.height(10.dp).width(30.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRowSkeleton() {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.height(14.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            Box(Modifier.height(14.dp).width(40.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100)).adminShimmerEffect())
    }
}

@Composable
private fun ActivityRowSkeleton() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).adminShimmerEffect())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.height(14.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            Box(Modifier.height(10.dp).fillMaxWidth(0.4f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
        }
        Box(Modifier.height(14.dp).width(50.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
    }
}

@Composable
private fun StockDashboardItemRowSkeleton() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).adminShimmerEffect())
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.height(16.dp).fillMaxWidth(0.7f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            Box(Modifier.height(12.dp).fillMaxWidth(0.5f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
            Box(Modifier.height(12.dp).fillMaxWidth(0.9f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect())
        }
        Box(Modifier.height(20.dp).width(50.dp).clip(RoundedCornerShape(8.dp)).adminShimmerEffect())
    }
}

@Composable
private fun ProductionSummaryGrid(summary: RepositoriFirebaseUtama.RingkasanProduksi?, isLoading: Boolean) {
    if (isLoading) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), border = BorderStroke(1.dp, ProTheme.border)) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { GridMetricItemSkeleton(Modifier.weight(1f)); Box(Modifier.width(1.dp).height(72.dp).background(ProTheme.border)); GridMetricItemSkeleton(Modifier.weight(1f)) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(ProTheme.border))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { GridMetricItemSkeleton(Modifier.weight(1f)); Box(Modifier.width(1.dp).height(72.dp).background(ProTheme.border)); GridMetricItemSkeleton(Modifier.weight(1f)) }
            }
        }
        return
    }

    val totalDasar = summary?.totalProduksiDasarHariIni ?: 0
    val totalOlahan = summary?.totalProduksiOlahanHariIni ?: 0
    val totalMasak = summary?.totalBatchHariIni ?: 0
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = ProTheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), border = BorderStroke(1.dp, ProTheme.border)) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GridMetricItem("Total Produksi", Formatter.ribuan((totalDasar + totalOlahan).toLong()), "Pcs", ProTheme.primary, Icons.Rounded.CheckCircle, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(72.dp).background(ProTheme.border))
                GridMetricItem("Total Masak", Formatter.ribuan(totalMasak.toLong()), "Batch", ProTheme.success, Icons.Rounded.Done, Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(ProTheme.border))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GridMetricItem("Tahu Dasar", Formatter.ribuan(totalDasar.toLong()), "Pcs", ProTheme.text, Icons.Rounded.Add, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(72.dp).background(ProTheme.border))
                GridMetricItem("Olahan", Formatter.ribuan(totalOlahan.toLong()), "Pcs", ProTheme.pro, Icons.Rounded.Refresh, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GridMetricItemSkeleton(modifier: Modifier) {
    Row(modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(CircleShape).adminShimmerEffect())
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Box(Modifier.height(12.dp).width(60.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect()); Box(Modifier.height(18.dp).width(80.dp).clip(RoundedCornerShape(4.dp)).adminShimmerEffect()) }
    }
}

@Composable
private fun HistoryDetailContentSkeleton(primaryColor: Color, borderColor: Color) {
    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(16.dp), color = primaryColor.copy(alpha = 0.06f), border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(8) { Box(Modifier.height(12.dp).fillMaxWidth(if(it % 2 == 0) 0.9f else 0.6f).clip(RoundedCornerShape(4.dp)).adminShimmerEffect()) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).adminShimmerEffect()); Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).adminShimmerEffect())
        }
    }
}

@Composable
private fun GridMetricItem(title: String, value: String, unit: String, accentColor: Color, icon: ImageVector, modifier: Modifier) {
    Row(modifier = modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(imageVector = icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(20.dp)) }
        Column {
            Text(title, color = ProTheme.muted, style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, color = ProTheme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(unit, color = accentColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}