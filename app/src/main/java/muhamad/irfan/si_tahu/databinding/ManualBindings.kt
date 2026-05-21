package muhamad.irfan.si_tahu.databinding

import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import muhamad.irfan.si_tahu.util.WarnaBaris

private fun button(label: String) = ComposeButtonState(label)
private fun text(label: String = "") = ComposeTextState(label)
private fun input(hint: String, keyboard: KeyboardType = KeyboardType.Text): ComposeEditTextState =
    ComposeEditTextState(hint).apply { keyboardType = keyboard }

class ActivityListScreenBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState()
    val searchContainer = ComposeComponent()
    val etSearch = input("Cari data...")
    val btnOpenFilters = button("Filter")
    val btnResetFilter = button("Reset")
    val cardDateFilter = ComposeComponent()
    val tvFilterBadge = text()
    val cardPrimaryFilter = ComposeComponent()
    val spPrimaryFilter = ComposeSpinnerState()
    val cardSecondaryFilter = ComposeComponent()
    val spSecondaryFilter = ComposeSpinnerState()
    val cardProductSelector = ComposeComponent()
    val tvProductSelectorLabel = text("Produk")
    val tvProductSelectorLeading = text("P")
    val tvSelectedProductName = text("Pilih produk")
    val tvSelectedProductInfo = text("Pilih produk")
    val buttonRow = ComposeComponent()
    val btnPrimary = button("Aksi Utama")
    val btnSecondary = button("Aksi")
    val rvList = ComposeRecyclerState()
    val tvEmpty = text("Belum ada data yang ditampilkan")
    val paginationContainer = ComposeComponent()
    val btnPagePrev = button("Sebelumnya")
    val btnPageNext = button("Berikutnya")
    val tvPageInfo = text()
    val fabAdd = button("Tambah")

    companion object {
        fun inflate(inflater: LayoutInflater): ActivityListScreenBinding {
            lateinit var binding: ActivityListScreenBinding
            val root = bindingRoot(inflater.context) { binding.Content() }
            binding = ActivityListScreenBinding(root)
            binding.btnOpenFilters.isVisible = false
            binding.tvFilterBadge.isVisible = false
            binding.btnResetFilter.isVisible = false
            binding.cardDateFilter.isVisible = false
            binding.cardPrimaryFilter.isVisible = false
            binding.spPrimaryFilter.isVisible = false
            binding.cardSecondaryFilter.isVisible = false
            binding.spSecondaryFilter.isVisible = false
            binding.cardProductSelector.isVisible = false
            binding.buttonRow.isVisible = false
            binding.paginationContainer.isVisible = false
            binding.fabAdd.isVisible = false
            return binding
        }
    }

    @Composable
    private fun Content() {
        ScreenFrame(
            toolbar = toolbar,
            scroll = false,
            floatingAction = {
                if (fabAdd.isVisible) {
                    val view = LocalView.current
                    FloatingActionButton(
                        onClick = { fabAdd.performClick(view) },
                        containerColor = SiTahuColors.Primary,
                        contentColor = Color.White
                    ) { Icon(Icons.Default.Add, contentDescription = "Tambah") }
                }
            }
        ) {
            if (searchContainer.isVisible) SearchPanel()
            if (buttonRow.isVisible) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ButtonView(btnPrimary, Modifier.weight(1f), primary = true)
                    ButtonView(btnSecondary, Modifier.weight(1f), primary = false)
                }
            }
            if (tvEmpty.isVisible) EmptyState(tvEmpty.text.toString())
            ListHandleView(rvList, Modifier.weight(1f))
            PaginationView(paginationContainer, tvPageInfo, btnPagePrev, btnPageNext)
        }
    }

    @Composable
    private fun SearchPanel() {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pencarian Pintar", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text("Temukan data, filter status, dan pilih produk lebih cepat", color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                if (tvFilterBadge.isVisible) SmallPill(tvFilterBadge.text.toString(), WarnaBaris.GOLD)
            }
            FieldView(etSearch)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (cardPrimaryFilter.isVisible) DropdownView(spPrimaryFilter, "Filter utama", Modifier.weight(1f))
                if (cardSecondaryFilter.isVisible) DropdownView(spSecondaryFilter, "Filter tambahan", Modifier.weight(1f))
            }
            if (cardProductSelector.isVisible) ProductPickerCard(cardProductSelector, tvProductSelectorLabel, tvProductSelectorLeading, tvSelectedProductName, tvSelectedProductInfo)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (btnOpenFilters.isVisible) ButtonView(btnOpenFilters, Modifier.weight(1f), primary = false)
                if (btnResetFilter.isVisible) ButtonView(btnResetFilter, Modifier.weight(1f), primary = false)
            }
        }
    }
}

class ActivityProductFormBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState()
    val etName = input("Nama produk")
    val spCategory = ComposeSpinnerState()
    val etUnit = input("Satuan")
    val etMinStock = input("Stok minimum", KeyboardType.Number)
    val etShelfLifeDays = input("Masa simpan (hari)", KeyboardType.Number)
    val etNearExpiryDays = input("Hampir kedaluwarsa (hari)", KeyboardType.Number)
    val spPhotoTone = ComposeSpinnerState()
    val btnDelete = button("Hapus Produk")
    val cbActive = ComposeCheckState("Aktif dijual")
    val cbShowCashier = ComposeCheckState("Tampilkan di kasir")
    val btnSave = button("Simpan Produk")
    companion object { fun inflate(inflater: LayoutInflater): ActivityProductFormBinding { lateinit var b: ActivityProductFormBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityProductFormBinding(root); b.spPhotoTone.isVisible=false; b.btnDelete.isVisible=false; return b } }
    @Composable private fun Content() = ScreenFrame(toolbar) { FormHero("Produk", "Kelola produk, harga, stok minimum, dan visibilitas kasir"); PremiumFormSection("Detail Produk") { FieldView(etName); DropdownView(spCategory, "Kategori"); FieldView(etUnit); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { FieldView(etMinStock, Modifier.weight(1f)); FieldView(etShelfLifeDays, Modifier.weight(1f)) }; FieldView(etNearExpiryDays); CheckView(cbActive); CheckView(cbShowCashier) }; ButtonView(btnSave, Modifier.fillMaxWidth()); ButtonView(btnDelete, Modifier.fillMaxWidth(), primary=false) }
}

class ActivityPriceFormBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState(); val spProduct = ComposeSpinnerState(); val etLabel = input("Label harga"); val etPrice = input("Harga", KeyboardType.Number); val cbActive = ComposeCheckState("Aktif"); val cbDefault = ComposeCheckState("Default kasir"); val btnSave = button("Simpan Harga")
    companion object { fun inflate(inflater: LayoutInflater): ActivityPriceFormBinding { lateinit var b: ActivityPriceFormBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityPriceFormBinding(root); return b } }
    @Composable private fun Content() = ScreenFrame(toolbar) { FormHero("Harga", "Atur kanal harga jual dengan tampilan kasir yang rapi"); PremiumFormSection("Harga Jual") { DropdownView(spProduct, "Produk"); FieldView(etLabel); FieldView(etPrice); CheckView(cbActive); CheckView(cbDefault) }; ButtonView(btnSave, Modifier.fillMaxWidth()) }
}

class ActivityParameterFormBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState(); val spProduct = ComposeSpinnerState(); val etResultPerBatch = input("Hasil per batch", KeyboardType.Number); val etNote = input("Catatan"); val cbActive = ComposeCheckState("Aktif"); val btnSave = button("Simpan Parameter")
    companion object { fun inflate(inflater: LayoutInflater): ActivityParameterFormBinding { lateinit var b: ActivityParameterFormBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityParameterFormBinding(root); return b } }
    @Composable private fun Content() = ScreenFrame(toolbar) { FormHero("Parameter Produksi", "Tetapkan hasil produksi standar supaya stok lebih presisi"); PremiumFormSection("Standar Produksi") { DropdownView(spProduct, "Produk"); FieldView(etResultPerBatch); FieldView(etNote); CheckView(cbActive) }; ButtonView(btnSave, Modifier.fillMaxWidth()) }
}

class ActivityExpenseFormBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState(); val etDate = input("Tanggal"); val etCategory = input("Kategori"); val etAmount = input("Nominal", KeyboardType.Number); val etNote = input("Catatan"); val btnSave = button("Simpan Pengeluaran")
    companion object { fun inflate(inflater: LayoutInflater): ActivityExpenseFormBinding { lateinit var b: ActivityExpenseFormBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityExpenseFormBinding(root); return b } }
    @Composable private fun Content() = ScreenFrame(toolbar) { FormHero("Pengeluaran", "Catat biaya operasional dengan cepat dan rapi"); PremiumFormSection("Detail Biaya") { FieldView(etDate); FieldView(etCategory); FieldView(etAmount); FieldView(etNote) }; ButtonView(btnSave, Modifier.fillMaxWidth()) }
}

class ActivityPengaturanUsahaBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState(); val tvLogoTextPreview = text("ST"); val tvBusinessPreview = text("SiTahu"); val tvBusinessAddressPreview = text("Alamat usaha"); val etBusinessName = input("Nama usaha"); val etAddress = input("Alamat"); val etPhone = input("Telepon"); val etLogoText = input("Logo teks"); val etReceiptFooter = input("Footer nota"); val etBusinessNote = input("Catatan usaha"); val btnSave = button("Simpan Pengaturan")
    companion object { fun inflate(inflater: LayoutInflater): ActivityPengaturanUsahaBinding { lateinit var b: ActivityPengaturanUsahaBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityPengaturanUsahaBinding(root); return b } }
    @Composable private fun Content() = ScreenFrame(toolbar) { HeroCard(tvBusinessPreview.text.toString(), tvBusinessAddressPreview.text.toString(), tvLogoTextPreview.text.toString()); PremiumFormSection("Profil Usaha") { FieldView(etBusinessName); FieldView(etAddress); FieldView(etPhone); FieldView(etLogoText); FieldView(etReceiptFooter); FieldView(etBusinessNote) }; ButtonView(btnSave, Modifier.fillMaxWidth()) }
}

class ActivityUserFormBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState(); val etName = input("Nama pengguna"); val etEmail = input("Email"); val etPhone = input("Nomor HP"); val etPassword = input("Password"); val spRole = ComposeSpinnerState(); val cbActive = ComposeCheckState("Aktif"); val btnResetPassword = button("Reset Password"); val btnSave = button("Simpan Pengguna")
    companion object { fun inflate(inflater: LayoutInflater): ActivityUserFormBinding { lateinit var b: ActivityUserFormBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityUserFormBinding(root); return b } }
    @Composable private fun Content() = ScreenFrame(toolbar) { FormHero("Pengguna", "Kelola akses admin, kasir, dan status akun"); PremiumFormSection("Akun") { FieldView(etName); FieldView(etEmail); FieldView(etPhone); FieldView(etPassword); DropdownView(spRole, "Peran"); CheckView(cbActive); ButtonView(btnResetPassword, Modifier.fillMaxWidth(), primary=false) }; ButtonView(btnSave, Modifier.fillMaxWidth()) }
}

class ActivityReportBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState(); val spRange = ComposeSpinnerState(); val tvReportSales = text("Rp0"); val tvReportExpenses = text("Rp0"); val tvReportProduction = text("0"); val tvReportProfit = text("Rp0"); val tvReportTransactions = text("0"); val rvMix = ComposeRecyclerState(); val btnShareCsv = button("Bagikan CSV"); val btnTransactions = button("Lihat Transaksi")
    companion object { fun inflate(inflater: LayoutInflater): ActivityReportBinding { lateinit var b: ActivityReportBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityReportBinding(root); return b } }
    @Composable private fun Content() = ScreenFrame(toolbar) { HeroCard("Laporan", "Pantau performa bisnis Anda 👋", "RP"); DropdownView(spRange, "Rentang laporan"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Omzet", tvReportSales.text.toString(), "Naik"); MetricCard("Produksi", tvReportProduction.text.toString(), "Masak") }; Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Pengeluaran", tvReportExpenses.text.toString()); MetricCard("Laba Bersih", tvReportProfit.text.toString(), "Transaksi ${tvReportTransactions.text}") } }; PremiumChartCard("Tren Penjualan", tvReportSales.text.toString(), "Periode terpilih"); SectionCard { Text("Komposisi Produk Terjual", fontWeight = FontWeight.Black); ListHandleView(rvMix) }; InsightPanel(); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { ButtonView(btnShareCsv, Modifier.weight(1f), primary=false); ButtonView(btnTransactions, Modifier.weight(1f), primary=true) } }
}

class ActivityLoginBinding private constructor(val root: ComposeView) {
    val tvLogo = text("ST"); val tvBusinessName = text("SiTahu"); val tvTitle = text("Masuk ke aplikasi"); val etEmail = input("Email"); val etPassword = input("Password"); val btnLogin = button("Masuk")
    companion object { fun inflate(inflater: LayoutInflater): ActivityLoginBinding { lateinit var b: ActivityLoginBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityLoginBinding(root); b.etEmail.singleLine=true; b.etPassword.singleLine=true; return b } }
    @Composable private fun Content() = ScreenFrame(scroll = true) { Spacer(Modifier.height(18.dp)); LoginHero(tvBusinessName.text.toString(), tvTitle.text.toString(), tvLogo.text.toString()); SectionCard { Text("Selamat datang kembali", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge); Text("Masuk untuk melanjutkan operasional toko hari ini.", color = SiTahuColors.TextSecondary); FieldView(etEmail); FieldView(etPassword); ButtonView(btnLogin, Modifier.fillMaxWidth()) } }
}

class ActivityCashierSaleCatalogBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState()
    val etSearch = input("Cari nama / kode produk")
    val btnOpenFilters = button("Reset")
    val tvFilterBadge = text()
    val spCategory = ComposeSpinnerState()
    val spStockMode = ComposeSpinnerState()
    val tvProductSummary = text("Produk siap dijual")
    val rvProducts = ComposeRecyclerState()
    val tvEmptyProducts = text("Produk tidak ditemukan")
    val paginationContainer = ComposeComponent()
    val btnPagePrev = button("Prev")
    val btnPageNext = button("Next")
    val tvPageInfo = text()
    val layoutCartAction = ComposeComponent()
    val btnCartMinus = button("-")
    val btnCartPlus = button("+")
    val btnCheckout = button("Checkout")
    val tvCartBadge = text("0")
    val tvCartLabel = text("Keranjang kosong")
    val tvCartAmount = text("Rp0")

    companion object {
        fun inflate(inflater: LayoutInflater): ActivityCashierSaleCatalogBinding {
            lateinit var b: ActivityCashierSaleCatalogBinding
            val root = bindingRoot(inflater.context) { b.Content() }
            b = ActivityCashierSaleCatalogBinding(root)
            b.tvFilterBadge.isVisible = false
            return b
        }
    }

    @Composable
    private fun Content() = ScreenFrame(toolbar, scroll = false) {
        CashierWorkflowCard()
        CashierFilterPanel(etSearch, spCategory, spStockMode, btnOpenFilters, tvFilterBadge, tvProductSummary)
        if (tvEmptyProducts.isVisible) EmptyState(tvEmptyProducts.text.toString())
        ListHandleView(rvProducts, Modifier.weight(1f))
        PaginationView(paginationContainer, tvPageInfo, btnPagePrev, btnPageNext)
        CartBar(tvCartLabel, tvCartBadge, tvCartAmount, btnCartMinus, btnCartPlus, btnCheckout, layoutCartAction)
    }
}

class ActivityCashierCheckoutBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState()
    val tvCheckoutSummary = text()
    val tvTotalBelanja = text("Rp0")
    val rvCart = ComposeRecyclerState()
    val spPayment = ComposeSpinnerState()
    val tilCashPaid = ComposeComponent()
    val etCashPaid = input("Uang diterima", KeyboardType.Number)
    val tvKembalian = text()
    val tvPaidLabel = text("Dibayar")
    val tvPaidAmount = text()
    val tvKasirHint = text()
    val btnSaveTransaction = button("Proses Pembayaran")
    val cardXenditQris = ComposeComponent()
    val tvXenditStatus = text()
    val tvXenditTotal = text()
    val tvXenditOrderId = text()
    val tvXenditTimer = text()
    val ivXenditQr = ComposeImageState()
    val rowXenditActions = ComposeComponent()
    val btnXenditShowQris = button("Lihat QRIS")
    val btnXenditCheckStatus = button("Cek Pembayaran")
    val btnXenditCancel = button("Batalkan")
    val btnXenditSimulate = button("Konfirmasi Pembayaran")

    companion object {
        fun inflate(inflater: LayoutInflater): ActivityCashierCheckoutBinding {
            lateinit var b: ActivityCashierCheckoutBinding
            val root = bindingRoot(inflater.context) { b.Content() }
            b = ActivityCashierCheckoutBinding(root)
            b.cardXenditQris.isVisible = false
            return b
        }
    }

    @Composable
    private fun Content() = ScreenFrame(toolbar, scroll = true) {
        CheckoutFlowHeader()
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ringkasan Keranjang", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SmallPill(tvCheckoutSummary.text.toString().ifBlank { "0 item" }, WarnaBaris.GOLD)
            }
            ListHandleView(rvCart)
        }
        CheckoutSummaryCard()
        SectionCard(visible = cardXenditQris.isVisible) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pembayaran QRIS", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SmallPill(tvXenditStatus.text.toString().ifBlank { "Menunggu" }, WarnaBaris.BLUE)
            }
            Text(tvXenditTotal.text.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = SiTahuColors.PrimaryDark)
            if (tvXenditOrderId.isVisible) Text(tvXenditOrderId.text.toString(), color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (tvXenditTimer.isVisible) SmallPill("Sisa waktu ${tvXenditTimer.text}", WarnaBaris.ORANGE)
            BitmapImageView(ivXenditQr, Modifier.align(Alignment.CenterHorizontally))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ButtonView(btnXenditShowQris, Modifier.weight(1f), primary = false)
                ButtonView(btnXenditCheckStatus, Modifier.weight(1f), primary = false)
            }
            if (rowXenditActions.isVisible) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ButtonView(btnXenditCancel, Modifier.weight(1f), primary = false)
                    ButtonView(btnXenditSimulate, Modifier.weight(1f), primary = false)
                }
            }
        }
        Text(tvKasirHint.text.toString(), color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        ButtonView(btnSaveTransaction, Modifier.fillMaxWidth())
    }

    @Composable
    private fun CheckoutSummaryCard() {
        SectionCard {
            Text("Pembayaran", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text(tvTotalBelanja.text.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = SiTahuColors.PrimaryDark)
            DropdownView(spPayment, "Metode pembayaran")
            if (tilCashPaid.isVisible) FieldView(etCashPaid)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tvPaidLabel.isVisible) SmallPill("${tvPaidLabel.text}: ${tvPaidAmount.text}", WarnaBaris.DEFAULT)
                if (tvKembalian.text.isNotBlank()) SmallPill("Kembali ${tvKembalian.text}", WarnaBaris.GREEN)
            }
        }
    }
}

class ActivityMarketRecapBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState(); val btnToggleDateTime = button("Tanggal & jam"); val layoutDateTimeAdvanced = ComposeComponent(); val etDate = input("Tanggal"); val etTime = input("Jam"); val cardProductPicker = ComposeComponent(); val tvProductPickerLabel = text("Produk"); val tvProductLeading = text("P"); val tvSelectedProductName = text("Pilih produk"); val tvSelectedProductMeta = text("Ketuk untuk memilih"); val spPrice = ComposeSpinnerState(); val tvSelectedPrice = text(); val tvSelectedStock = text(); val tvProductInfo = text(); val etQty = input("Jumlah", KeyboardType.Number); val btnAddItem = button("Tambah"); val rvItems = ComposeRecyclerState(); val tvDraftEmpty = text("Belum ada item"); val tvDraftSummary = text(); val tvTotal = text("Rp0"); val btnSaveRecap = button("Simpan Rekap"); val sheetDraft = ComposeComponent()
    companion object { fun inflate(inflater: LayoutInflater): ActivityMarketRecapBinding { lateinit var b: ActivityMarketRecapBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityMarketRecapBinding(root); b.layoutDateTimeAdvanced.isVisible=false; return b } }
    @Composable private fun Content() = ScreenFrame(toolbar, scroll=false) { FormHero("Rekap Pasar", "Catat rekap penjualan pasar dengan visual lebih cepat"); SectionCard { ButtonView(btnToggleDateTime, primary=false); if (layoutDateTimeAdvanced.isVisible) { FieldView(etDate); FieldView(etTime) }; ProductPickerCard(cardProductPicker, tvProductPickerLabel, tvProductLeading, tvSelectedProductName, tvSelectedProductMeta); DropdownView(spPrice, "Harga"); SmallPill(tvSelectedPrice.text.toString().ifBlank { "Harga belum dipilih" }, WarnaBaris.GOLD); SmallPill(tvSelectedStock.text.toString().ifBlank { "Stok tersedia" }, WarnaBaris.GREEN); Text(tvProductInfo.text.toString(), color = SiTahuColors.TextSecondary); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FieldView(etQty, Modifier.weight(1f)); ButtonView(btnAddItem, Modifier.weight(1f), primary=true) } }; SectionCard { Text(tvDraftSummary.text.toString(), fontWeight = FontWeight.Black); Text(tvTotal.text.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); if (tvDraftEmpty.visibility == View.VISIBLE) Text(tvDraftEmpty.text.toString(), color = SiTahuColors.TextSecondary); ListHandleView(rvItems) }; ButtonView(btnSaveRecap, Modifier.fillMaxWidth()) }
}

class ActivityBasicProductionBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState(); val btnToggleDateTime = button("Tanggal & jam"); val btnToggleNote = button("Catatan"); val layoutDate = ComposeComponent(); val layoutTime = ComposeComponent(); val layoutNote = ComposeComponent(); val etDate = input("Tanggal"); val etTime = input("Jam"); val cardProductPicker = ComposeComponent(); val tvProductPickerLabel = text("Produk"); val tvProductLeading = text("P"); val tvSelectedProductName = text("Pilih produk"); val tvSelectedProductMeta = text("Ketuk untuk memilih"); val etBatches = input("Jumlah batch", KeyboardType.Decimal); val tvParameterInfo = text(); val tvEstimation = text(); val etNote = input("Catatan"); val btnSave = button("Simpan Produksi")
    companion object { fun inflate(inflater: LayoutInflater): ActivityBasicProductionBinding { lateinit var b: ActivityBasicProductionBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityBasicProductionBinding(root); b.layoutDate.isVisible=false; b.layoutTime.isVisible=false; b.layoutNote.isVisible=false; return b } }
    @Composable private fun Content() = ScreenFrame(toolbar) { FormHero("Produksi", "Catat produksi tahu dasar dengan estimasi otomatis"); PremiumFormSection("Input Produksi") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ButtonView(btnToggleDateTime, Modifier.weight(1f), primary=false); ButtonView(btnToggleNote, Modifier.weight(1f), primary=false) }; if (layoutDate.isVisible) FieldView(etDate); if (layoutTime.isVisible) FieldView(etTime); ProductPickerCard(cardProductPicker, tvProductPickerLabel, tvProductLeading, tvSelectedProductName, tvSelectedProductMeta); FieldView(etBatches); Text(tvParameterInfo.text.toString(), color = SiTahuColors.TextSecondary); Text(tvEstimation.text.toString(), fontWeight = FontWeight.Black); if (layoutNote.isVisible) FieldView(etNote) }; ButtonView(btnSave, Modifier.fillMaxWidth()) }
}

class ActivityConversionBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState(); val btnToggleDateTime = button("Tanggal & jam"); val btnToggleNote = button("Catatan"); val layoutDate = ComposeComponent(); val layoutTime = ComposeComponent(); val layoutNote = ComposeComponent(); val etDate = input("Tanggal"); val etTime = input("Jam"); val cardFromProductPicker = ComposeComponent(); val tvFromProductPickerLabel = text("Dari produk"); val tvFromProductLeading = text("D"); val tvSelectedFromProductName = text("Pilih produk asal"); val tvSelectedFromProductMeta = text("Ketuk untuk memilih"); val cardToProductPicker = ComposeComponent(); val tvToProductPickerLabel = text("Ke produk"); val tvToProductLeading = text("K"); val tvSelectedToProductName = text("Pilih produk tujuan"); val tvSelectedToProductMeta = text("Ketuk untuk memilih"); val etInputQty = input("Jumlah asal", KeyboardType.Number); val etOutputQty = input("Jumlah hasil", KeyboardType.Number); val tvConversionInfo = text(); val etNote = input("Catatan"); val btnSave = button("Simpan Konversi")
    companion object { fun inflate(inflater: LayoutInflater): ActivityConversionBinding { lateinit var b: ActivityConversionBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityConversionBinding(root); b.layoutDate.isVisible=false; b.layoutTime.isVisible=false; b.layoutNote.isVisible=false; return b } }
    @Composable private fun Content() = ScreenFrame(toolbar) { FormHero("Produksi Olahan", "Ubah stok bahan menjadi produk olahan"); PremiumFormSection("Alur Konversi") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ButtonView(btnToggleDateTime, Modifier.weight(1f), primary=false); ButtonView(btnToggleNote, Modifier.weight(1f), primary=false) }; if (layoutDate.isVisible) FieldView(etDate); if (layoutTime.isVisible) FieldView(etTime); ProductPickerCard(cardFromProductPicker, tvFromProductPickerLabel, tvFromProductLeading, tvSelectedFromProductName, tvSelectedFromProductMeta); ProductPickerCard(cardToProductPicker, tvToProductPickerLabel, tvToProductLeading, tvSelectedToProductName, tvSelectedToProductMeta); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { FieldView(etInputQty, Modifier.weight(1f)); FieldView(etOutputQty, Modifier.weight(1f)) }; Text(tvConversionInfo.text.toString(), color = SiTahuColors.TextSecondary); if (layoutNote.isVisible) FieldView(etNote) }; ButtonView(btnSave, Modifier.fillMaxWidth()) }
}

class ActivityStockAdjustmentBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState(); val btnToggleDateTime = button("Tanggal & jam"); val layoutDateTimeAdvanced = ComposeComponent(); val etDate = input("Tanggal"); val etTime = input("Jam"); val cardProductPicker = ComposeComponent(); val tvProductPickerLabel = text("Produk"); val tvProductLeading = text("P"); val tvSelectedProductName = text("Pilih produk"); val tvSelectedProductMeta = text("Ketuk untuk memilih"); val tvStockInfo = text(); val etQty = input("Jumlah", KeyboardType.Number); val etNote = input("Catatan"); val btnSave = button("Simpan Penyesuaian")
    companion object { fun inflate(inflater: LayoutInflater): ActivityStockAdjustmentBinding { lateinit var b: ActivityStockAdjustmentBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityStockAdjustmentBinding(root); b.layoutDateTimeAdvanced.isVisible=false; return b } }
    @Composable private fun Content() = ScreenFrame(toolbar) { FormHero("Penyesuaian Stok", "Catat koreksi, stok masuk, atau stok kedaluwarsa"); PremiumFormSection("Koreksi Stok") { ButtonView(btnToggleDateTime, primary=false); if (layoutDateTimeAdvanced.isVisible) { FieldView(etDate); FieldView(etTime) }; ProductPickerCard(cardProductPicker, tvProductPickerLabel, tvProductLeading, tvSelectedProductName, tvSelectedProductMeta); Text(tvStockInfo.text.toString(), color = SiTahuColors.TextSecondary); FieldView(etQty); FieldView(etNote) }; ButtonView(btnSave, Modifier.fillMaxWidth()) }
}

class ActivityStockDetailBinding private constructor(val root: ComposeView) {
    val toolbar = ComposeToolbarState(); val tvProductName = text(); val tvProductMeta = text(); val tvStatus = text(); val tvStockNow = text(); val tvMinStock = text(); val tvBatchSummary = text(); val btnAdjustStock = button("Penyesuaian"); val btnDisposeExpiredStock = button("Buang Kedaluwarsa"); val rvMovement = ComposeRecyclerState(); val tvEmptyMovement = text("Belum ada mutasi"); val paginationContainer = ComposeComponent(); val btnPagePrev = button("Prev"); val btnPageNext = button("Next"); val tvPageInfo = text()
    companion object { fun inflate(inflater: LayoutInflater): ActivityStockDetailBinding { lateinit var b: ActivityStockDetailBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityStockDetailBinding(root); return b } }
    @Composable private fun Content() = ScreenFrame(toolbar, scroll=false) { HeroCard(tvProductName.text.toString(), tvProductMeta.text.toString(), "S"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Stok", tvStockNow.text.toString()); MetricCard("Minimum", tvMinStock.text.toString()) }; Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Status", tvStatus.text.toString()); MetricCard("Masak", tvBatchSummary.text.toString()) } }; Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { ButtonView(btnAdjustStock, Modifier.weight(1f)); ButtonView(btnDisposeExpiredStock, Modifier.weight(1f), primary=false) }; if (tvEmptyMovement.visibility == View.VISIBLE) EmptyState(tvEmptyMovement.text.toString()); ListHandleView(rvMovement, Modifier.weight(1f)); PaginationView(paginationContainer, tvPageInfo, btnPagePrev, btnPageNext) }
}

class ActivityMidtransPaymentWebviewBinding private constructor(val root: ComposeView, context: android.content.Context) {
    val toolbar = ComposeToolbarState(); val tvPaymentTitle = text("Pembayaran"); val tvPaymentSubtitle = text(); val progressLoading = ComposeProgressState(); val webPayment = ComposeWebViewState(context); val btnBackCheckout = button("Kembali"); val btnCheckStatus = button("Cek Pembayaran")
    companion object { fun inflate(inflater: LayoutInflater): ActivityMidtransPaymentWebviewBinding { lateinit var b: ActivityMidtransPaymentWebviewBinding; val root = bindingRoot(inflater.context){ b.Content() }; b = ActivityMidtransPaymentWebviewBinding(root, inflater.context); return b } }
    @Composable private fun Content() = ScreenFrame(toolbar, scroll=false) { SectionCard { Text(tvPaymentTitle.text.toString(), fontWeight = FontWeight.Black); Text(tvPaymentSubtitle.text.toString(), color = SiTahuColors.TextSecondary); if (progressLoading.isVisible) LinearProgressIndicator(progress = { progressLoading.progress / 100f }, modifier = Modifier.fillMaxWidth()) }; WebViewBox(webPayment, Modifier.weight(1f)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { ButtonView(btnBackCheckout, Modifier.weight(1f), primary=false); ButtonView(btnCheckStatus, Modifier.weight(1f), primary=true) } }
}

class ActivityRoleMainBinding private constructor(val root: ComposeView) { val toolbar = ComposeToolbarState(); val container = ComposeComponent(); val bottomNavigation = ComposeComponent(); companion object { fun inflate(inflater: LayoutInflater): ActivityRoleMainBinding { lateinit var b: ActivityRoleMainBinding; val root = bindingRoot(inflater.context){ ScreenFrame(b.toolbar){ EmptyState("Konten utama") } }; b = ActivityRoleMainBinding(root); return b } } }

@Composable private fun FormHero(title: String, subtitle: String) = HeroCard(title, subtitle)

@Composable private fun PremiumFormSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(SiTahuColors.Primary, SiTahuColors.Primary2))), contentAlignment = Alignment.Center) { Text("✦", color = Color.White, fontWeight = FontWeight.Black) }
            Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@Composable private fun LoginHero(title: String, subtitle: String, mark: String) {
    Card(shape = RoundedCornerShape(36.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFFF8EA), Color(0xFFFFF3D6), Color.White))).padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                BrandMark(72.dp, mark)
                Spacer(Modifier.height(14.dp))
                Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium, color = SiTahuColors.TextPrimary)
                Text(subtitle, color = SiTahuColors.TextSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable private fun CashierWorkflowCard() {
    SectionCard {
        Text("Alur Kasir", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Text("Pilih produk → cek keranjang → checkout → bayar → cetak/bagikan nota", color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallPill("1 Produk", WarnaBaris.GOLD)
            SmallPill("2 Keranjang", WarnaBaris.BLUE)
            SmallPill("3 Bayar", WarnaBaris.GREEN)
        }
    }
}

@Composable private fun CheckoutFlowHeader() {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(SiTahuColors.Primary, SiTahuColors.Primary2))), contentAlignment = Alignment.Center) {
                Text("2", color = Color.White, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text("Pembayaran & Pembayaran", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("Pastikan item benar, pilih metode, lalu cetak nota setelah transaksi sukses.", color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallPill("Keranjang", WarnaBaris.GOLD)
            SmallPill("Pembayaran", WarnaBaris.BLUE)
            SmallPill("Nota", WarnaBaris.GREEN)
        }
    }
}

@Composable private fun CashierFilterPanel(search: ComposeEditTextState, category: ComposeSpinnerState, stockMode: ComposeSpinnerState, filter: ComposeButtonState, badge: ComposeTextState, summary: ComposeTextState) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(SiTahuColors.Primary, SiTahuColors.Primary2))), contentAlignment = Alignment.Center) {
                Text("K", color = Color.White, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text("Cari Produk", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text("Produk aktif dengan harga kasir ditampilkan otomatis", color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            if (badge.isVisible) SmallPill(badge.text.toString(), WarnaBaris.GOLD)
        }
        FieldView(search)
        if (category.isVisible || stockMode.isVisible) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (category.isVisible) DropdownView(category, "Kategori", Modifier.weight(1f))
                if (stockMode.isVisible) DropdownView(stockMode, "Stok", Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(summary.text.toString().ifBlank { "Produk siap dijual" }, color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            ButtonView(filter, Modifier.width(104.dp), primary=false)
        }
    }
}

@Composable private fun InsightPanel() {
    SectionCard {
        Text("✨ Insight & Rekomendasi", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        InsightRow("Penjualan meningkat", "Pertahankan jam ramai dan stok produk favorit.", WarnaBaris.GREEN)
        InsightRow("Produk favorit", "Pastikan varian terlaris selalu aman.", WarnaBaris.GOLD)
        InsightRow("Waktu promo", "Coba promo ringan saat transaksi mulai turun.", WarnaBaris.BLUE)
    }
}

@Composable private fun InsightRow(title: String, subtitle: String, tone: WarnaBaris) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val colors = when (tone) { WarnaBaris.GREEN -> SiTahuColors.Green to SiTahuColors.GreenSoft; WarnaBaris.GOLD -> SiTahuColors.PrimaryDark to SiTahuColors.PrimarySoft; WarnaBaris.BLUE -> SiTahuColors.Primary to SiTahuColors.PrimarySoft; WarnaBaris.ORANGE -> SiTahuColors.Primary to SiTahuColors.PrimarySoft; WarnaBaris.RED -> SiTahuColors.Danger to SiTahuColors.DangerSoft; WarnaBaris.DEFAULT -> SiTahuColors.TextSecondary to SiTahuColors.CardSoft }
        Box(Modifier.size(38.dp).clip(CircleShape).background(colors.second), contentAlignment = Alignment.Center) { Text("↗", color = colors.first, fontWeight = FontWeight.Black) }
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(subtitle, color = SiTahuColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        Text("›", fontWeight = FontWeight.Black, color = SiTahuColors.TextSecondary)
    }
}

@Composable private fun CartBar(label: ComposeTextState, badge: ComposeTextState, amount: ComposeTextState, minus: ComposeButtonState, plus: ComposeButtonState, checkout: ComposeButtonState, container: ComposeComponent) {
    if (!container.isVisible) return
    val view = LocalView.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = container.isEnabled) { container.performClick(view) },
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 9.dp)
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.horizontalGradient(listOf(Color.White, SiTahuColors.CardSoft, Color.White)))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.86f)), RoundedCornerShape(30.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(SiTahuColors.Primary, SiTahuColors.Primary2))), contentAlignment = Alignment.Center) {
                Text(badge.text.toString().take(2).ifBlank { "0" }, color = Color.White, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text(label.text.toString(), fontWeight = FontWeight.Black, maxLines = 1)
                Text(amount.text.toString(), color = SiTahuColors.PrimaryDark, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                ButtonView(minus, Modifier.width(44.dp), primary=false)
                ButtonView(plus, Modifier.width(44.dp), primary=false)
            }
            ButtonView(checkout, Modifier.width(132.dp), primary=true)
        }
    }
}

// Fragment binding facades. They are Compose-backed and keep old fragment logic working without XML.
class FragmentAdminDashboardBinding private constructor(val root: View) { val btnGoProduction = button("Produksi"); val btnGoSales = button("Penjualan"); val btnGoStock = button("Stok"); val btnNewExpense = button("Pengeluaran"); val rvLowStock = ComposeRecyclerState(); val rvRecentTransactions = ComposeRecyclerState(); val rvAnalytics = ComposeRecyclerState(); companion object { fun bind(view: View): FragmentAdminDashboardBinding { val b = FragmentAdminDashboardBinding(view); (view as? ComposeView)?.setContent { SiTahuBindingTheme { b.Content() } }; return b } } @Composable private fun Content() = ScreenFrame(scroll=false) { HeroCard("Beranda Admin", "Ringkasan usaha hari ini"); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { ButtonView(btnGoProduction, Modifier.weight(1f)); ButtonView(btnGoSales, Modifier.weight(1f)); ButtonView(btnGoStock, Modifier.weight(1f), primary=false) }; ButtonView(btnNewExpense, Modifier.fillMaxWidth(), primary=false); PremiumChartCard("Analitik", "Hari ini"); SectionCard { Text("Stok perlu perhatian", fontWeight=FontWeight.Black); ListHandleView(rvLowStock) }; SectionCard { Text("Transaksi terbaru", fontWeight=FontWeight.Black); ListHandleView(rvRecentTransactions) } } }
class FragmentCashierDashboardBinding private constructor(val root: View) { val btnNewSale = button("Mulai Penjualan"); val rvTopProducts = ComposeRecyclerState(); val rvRecentCashierSales = ComposeRecyclerState(); companion object { fun bind(view: View): FragmentCashierDashboardBinding { val b = FragmentCashierDashboardBinding(view); (view as? ComposeView)?.setContent { SiTahuBindingTheme { b.Content() } }; return b } } @Composable private fun Content() = ScreenFrame(scroll=false) { HeroCard("Beranda Kasir", "Ringkasan penjualan"); ButtonView(btnNewSale, Modifier.fillMaxWidth()); SectionCard { Text("Produk terlaris", fontWeight=FontWeight.Black); ListHandleView(rvTopProducts) }; SectionCard { Text("Penjualan terbaru", fontWeight=FontWeight.Black); ListHandleView(rvRecentCashierSales) } } }
class FragmentCashierHistoryBinding private constructor(val root: View) { val etSearch=input("Cari riwayat"); val btnOpenFilters=button("Filter"); val tvFilterBadge=text(); val rvHistory=ComposeRecyclerState(); val tvEmpty=text("Belum ada riwayat"); val paginationContainer=ComposeComponent(); val btnPagePrev=button("Prev"); val btnPageNext=button("Next"); val tvPageInfo=text(); companion object { fun bind(view: View): FragmentCashierHistoryBinding { val b = FragmentCashierHistoryBinding(view); (view as? ComposeView)?.setContent { SiTahuBindingTheme { b.Content() } }; b.tvFilterBadge.isVisible=false; return b } } @Composable private fun Content() = ScreenFrame(scroll=false) { SectionCard { FieldView(etSearch); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { ButtonView(btnOpenFilters, Modifier.weight(1f), primary=false); if (tvFilterBadge.isVisible) SmallPill(tvFilterBadge.text.toString()) } }; if (tvEmpty.isVisible) EmptyState(tvEmpty.text.toString()); ListHandleView(rvHistory, Modifier.weight(1f)); PaginationView(paginationContainer,tvPageInfo,btnPagePrev,btnPageNext) } }
class FragmentCashierSaleBinding private constructor(val root: View) { val etSearch=input("Cari produk"); val btnOpenFilters=button("Filter"); val tvFilterBadge=text(); val spCategory=ComposeSpinnerState(); val spStockMode=ComposeSpinnerState(); val tvProductSummary=text(); val rvProducts=ComposeRecyclerState(); val tvEmptyProducts=text("Produk tidak ditemukan"); val paginationContainer=ComposeComponent(); val btnPagePrev=button("Prev"); val btnPageNext=button("Next"); val tvPageInfo=text(); val cardBottomCart=ComposeComponent(); val layoutCartHeader=ComposeComponent(); val viewCartHandle=ComposeComponent(); val rvCart=ComposeRecyclerState(); val tvEmptyCart=text("Keranjang kosong"); val tvCartTitle=text("Keranjang"); val tvCartSubtitle=text(); val tvCartTotal=text("Rp0"); val btnCheckout=button("Checkout"); companion object { fun bind(view: View): FragmentCashierSaleBinding { val b = FragmentCashierSaleBinding(view); (view as? ComposeView)?.setContent { SiTahuBindingTheme { b.Content() } }; b.tvFilterBadge.isVisible=false; return b } } @Composable private fun Content() = ScreenFrame(scroll=false){ CashierFilterPanel(etSearch, spCategory, spStockMode, btnOpenFilters, tvFilterBadge, tvProductSummary); if(tvEmptyProducts.isVisible) EmptyState(tvEmptyProducts.text.toString()); ListHandleView(rvProducts, Modifier.weight(1f)); PaginationView(paginationContainer,tvPageInfo,btnPagePrev,btnPageNext); SectionCard{ Text(tvCartTitle.text.toString(), fontWeight=FontWeight.Black); Text(tvCartSubtitle.text.toString(), color=SiTahuColors.TextSecondary); if(tvEmptyCart.isVisible) Text(tvEmptyCart.text.toString(), color=SiTahuColors.TextSecondary); ListHandleView(rvCart); Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically){ Text(tvCartTotal.text.toString(), Modifier.weight(1f), fontWeight=FontWeight.Black); ButtonView(btnCheckout)}} } }
class FragmentAdminMenuBinding private constructor(val root: View) { val btnProducts=button("Produk"); val btnPrices=button("Harga"); val btnParameters=button("Parameter"); val btnExpenses=button("Pengeluaran"); val btnReport=button("Laporan"); val btnTransactions=button("Transaksi"); val btnUsers=button("Pengguna"); val btnSettings=button("Pengaturan"); val btnSwitchCashier=button("Halaman Kasir"); val btnLogout=button("Keluar"); companion object { fun bind(view: View): FragmentAdminMenuBinding { val b = FragmentAdminMenuBinding(view); (view as? ComposeView)?.setContent { SiTahuBindingTheme{ b.Content() } }; return b } } @Composable private fun Content() = ScreenFrame { HeroCard("Menu Admin", "Kelola master data dan laporan"); MenuGrid(listOf(btnProducts,btnPrices,btnParameters,btnExpenses,btnReport,btnTransactions,btnUsers,btnSettings)); ButtonView(btnSwitchCashier, Modifier.fillMaxWidth(), primary=false); ButtonView(btnLogout, Modifier.fillMaxWidth(), primary=false) } }
class FragmentCashierMenuBinding private constructor(val root: View) { val tvUserName=text("-"); val tvUserRole=text("-"); val btnBusinessSettings=button("Pengaturan Usaha"); val btnSwitchAdmin=button("Mode Admin"); val btnLogout=button("Keluar"); companion object { fun bind(view: View): FragmentCashierMenuBinding { val b = FragmentCashierMenuBinding(view); (view as? ComposeView)?.setContent { SiTahuBindingTheme{ b.Content() } }; return b } } @Composable private fun Content() = ScreenFrame { HeroCard(tvUserName.text.toString(), "Mode Kasir", "K"); SectionCard { Text("Akun ini hanya dapat mengakses transaksi kasir dan riwayat penjualan miliknya.", color = SiTahuColors.TextSecondary) }; ButtonView(btnLogout, Modifier.fillMaxWidth(), primary=false) } }
class FragmentProductionMenuBinding private constructor(val root: View) { val btnBasicProduction=button("Produksi Dasar"); val btnConversion=button("Produksi Olahan"); val btnHistory=button("Riwayat Produksi"); val fabToggleProductionMenu=ComposeComponent(); val productionFabMenu=ComposeComponent(); val productionActionScrim=ComposeComponent(); val contentProduction=ComposeComponent(); val rvProductionRecent=ComposeRecyclerState(); companion object { fun bind(view: View): FragmentProductionMenuBinding { val b = FragmentProductionMenuBinding(view); (view as? ComposeView)?.setContent { SiTahuBindingTheme{ b.Content() } }; return b } } @Composable private fun Content() = ScreenFrame(scroll=false){ HeroCard("Produksi", "Catat produksi dan konversi"); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ ButtonView(btnBasicProduction, Modifier.weight(1f)); ButtonView(btnConversion, Modifier.weight(1f), primary=false)}; ButtonView(btnHistory, Modifier.fillMaxWidth(), primary=false); SectionCard{ Text("Riwayat terbaru", fontWeight=FontWeight.Black); ListHandleView(rvProductionRecent)} } }
class FragmentSalesMenuBinding private constructor(val root: View) { val btnHomeSales=button("Penjualan Rumahan"); val btnMarketRecap=button("Rekap Pasar"); val btnSalesHistory=button("Riwayat Penjualan"); val fabToggleSalesMenu=ComposeComponent(); val salesFabMenu=ComposeComponent(); val salesActionScrim=ComposeComponent(); val contentSales=ComposeComponent(); val rvSalesRecent=ComposeRecyclerState(); companion object { fun bind(view: View): FragmentSalesMenuBinding { val b = FragmentSalesMenuBinding(view); (view as? ComposeView)?.setContent { SiTahuBindingTheme{ b.Content() } }; return b } } @Composable private fun Content() = ScreenFrame(scroll=false){ HeroCard("Penjualan", "Kelola transaksi dan rekap pasar"); ButtonView(btnHomeSales, Modifier.fillMaxWidth()); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ ButtonView(btnMarketRecap, Modifier.weight(1f), primary=false); ButtonView(btnSalesHistory, Modifier.weight(1f), primary=false)}; SectionCard{ Text("Transaksi terbaru", fontWeight=FontWeight.Black); ListHandleView(rvSalesRecent)} } }
class FragmentStockListBinding private constructor(val root: View) { val etSearch=input("Cari stok"); val btnOpenFilters=button("Filter"); val tvFilterBadge=text(); val rvStock=ComposeRecyclerState(); val tvEmpty=text("Belum ada stok"); val paginationContainer=ComposeComponent(); val btnPrevPage=button("Prev"); val btnNextPage=button("Next"); val tvPageInfo=text(); val btnAdjustment=button("Penyesuaian"); val btnOpenMonitoring=button("Riwayat Stok"); val fabToggleStockMenu=ComposeComponent(); val stockFabMenu=ComposeComponent(); val stockActionScrim=ComposeComponent(); val contentStock=ComposeComponent(); companion object { fun bind(view: View): FragmentStockListBinding { val b = FragmentStockListBinding(view); (view as? ComposeView)?.setContent { SiTahuBindingTheme{ b.Content() } }; b.tvFilterBadge.isVisible=false; return b } } @Composable private fun Content()=ScreenFrame(scroll=false){ SectionCard{ FieldView(etSearch); Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ ButtonView(btnOpenFilters, Modifier.weight(1f), primary=false); if(tvFilterBadge.isVisible) SmallPill(tvFilterBadge.text.toString())}; Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ ButtonView(btnAdjustment, Modifier.weight(1f)); ButtonView(btnOpenMonitoring, Modifier.weight(1f), primary=false)} }; if(tvEmpty.isVisible) EmptyState(tvEmpty.text.toString()); ListHandleView(rvStock, Modifier.weight(1f)); PaginationView(paginationContainer,tvPageInfo,btnPrevPage,btnNextPage)} }

@Composable private fun MenuGrid(buttons: List<ComposeButtonState>) { Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { buttons.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) { row.forEach { ButtonView(it, Modifier.weight(1f), primary = row.indexOf(it)==0) }; if(row.size==1) Spacer(Modifier.weight(1f)) } } } }
