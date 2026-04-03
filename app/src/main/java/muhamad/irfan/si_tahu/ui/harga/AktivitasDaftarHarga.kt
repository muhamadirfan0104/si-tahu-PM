package muhamad.irfan.si_tahu.ui.price

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.ui.base.AktivitasDaftarDasar
import muhamad.irfan.si_tahu.ui.product.AktivitasDaftarProduk
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

class AktivitasDaftarHarga : AktivitasDaftarDasar() {
    private var initialSelectionIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureScreen("Harga Kanal", "Atur harga per kanal")
        hideSearch()
        hideSecondaryFilter()

        val products = RepositoriLokal.allProducts()
        if (products.isEmpty()) {
            hidePrimaryFilter()
            setPrimaryButton("Data Produk") {
                startActivity(Intent(this, AktivitasDaftarProduk::class.java))
            }
            hideSecondaryButton()
            submitRows(listOf(infoBelumAdaProduk()))
            return
        }

        val initialProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)
        initialSelectionIndex = products.indexOfFirst { it.id == initialProductId }.takeIf { it >= 0 } ?: 0
        setPrimaryFilter(products.map { it.name }, initialSelectionIndex) { refresh() }
        setPrimaryButton("Tambah Harga") {
            selectedProductId()?.let { productId ->
                startActivity(
                    Intent(this, AktivitasFormHarga::class.java)
                        .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
                )
            }
        }
        setSecondaryButton("Data Produk") {
            startActivity(Intent(this, AktivitasDaftarProduk::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun selectedProductId(): String? {
        val selectedName = primarySelection()
        return RepositoriLokal.allProducts().firstOrNull { it.name == selectedName }?.id
            ?: RepositoriLokal.allProducts().firstOrNull()?.id
    }

    private fun refresh() {
        val products = RepositoriLokal.allProducts()
        if (products.isEmpty()) {
            submitRows(listOf(infoBelumAdaProduk()))
            return
        }

        val product = RepositoriLokal.getProduct(selectedProductId()) ?: return
        val rows = RepositoriLokal.visibleChannels(product).map {
            ItemBaris(
                id = it.id,
                title = it.label,
                subtitle = if (it.active) "Kanal aktif" else "Kanal nonaktif",
                badge = if (it.defaultCashier) "Default" else "Harga",
                amount = Formatter.currency(it.price),
                actionLabel = "Hapus",
                tone = if (it.defaultCashier) WarnaBaris.GREEN else WarnaBaris.GOLD
            )
        }

        if (rows.isEmpty()) {
            submitRows(
                listOf(
                    ItemBaris(
                        id = "info_harga_kosong",
                        title = "Belum ada harga kanal",
                        subtitle = "Tambahkan harga kanal untuk produk ${product.name}.",
                        badge = "Info",
                        amount = "",
                        tone = WarnaBaris.BLUE
                    )
                )
            )
            return
        }

        submitRows(rows)
    }

    private fun infoBelumAdaProduk(): ItemBaris {
        return ItemBaris(
            id = "info_produk_kosong",
            title = "Belum ada produk",
            subtitle = "Tambahkan data produk terlebih dahulu sebelum mengatur harga kanal.",
            badge = "Info",
            amount = "",
            tone = WarnaBaris.BLUE
        )
    }

    override fun onRowClick(item: ItemBaris) {
        val productId = selectedProductId() ?: return
        startActivity(
            Intent(this, AktivitasFormHarga::class.java)
                .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
                .putExtra(EkstraAplikasi.EXTRA_PRICE_ID, item.id)
        )
    }

    override fun onRowAction(item: ItemBaris) {
        val productId = selectedProductId() ?: return
        showConfirmationModal(
            title = "Hapus harga kanal?",
            message = "Harga kanal ${item.title} akan di-soft delete. Transaksi lama tetap tersimpan. Lanjutkan?"
        ) {
            runCatching { RepositoriLokal.softDeleteChannel(productId, item.id) }
                .onSuccess {
                    showMessage("Harga kanal berhasil dihapus secara soft delete.")
                    refresh()
                }
                .onFailure { showMessage(it.message ?: "Gagal menghapus harga kanal") }
        }
    }
}
