package muhamad.irfan.si_tahu.utilitas

import android.app.Dialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import muhamad.irfan.si_tahu.databinding.SiTahuBindingTheme
import muhamad.irfan.si_tahu.databinding.GenericRowsStatic
import muhamad.irfan.si_tahu.util.Formatter
import muhamad.irfan.si_tahu.util.ItemBaris
import muhamad.irfan.si_tahu.util.WarnaBaris

object PembantuPilihProduk {

    fun show(
        activity: AppCompatActivity,
        title: String = "Pilih Produk",
        produk: List<ProdukPilihanUi>,
        selectedId: String? = null,
        kategoriOptions: List<String> = listOf("Semua", "Dasar", "Olahan"),
        onDipilih: (ProdukPilihanUi) -> Unit
    ) {
        val dialog = Dialog(activity)
        val categories = kategoriOptions.ifEmpty { listOf("Semua") }
        dialog.setContentView(
            ComposeView(activity).apply {
                setContent {
                    SiTahuBindingTheme {
                        var keyword by remember { mutableStateOf("") }
                        var kategoriAktif by remember { mutableStateOf(categories.first()) }
                        val filtered = produk.filter { item ->
                            val cocokKeyword = keyword.isBlank() ||
                                item.namaProduk.contains(keyword, true) ||
                                item.jenisProduk.contains(keyword, true)
                            val cocokKategori = when (kategoriAktif.lowercase()) {
                                "dasar" -> item.jenisProduk.equals("DASAR", true)
                                "olahan" -> item.jenisProduk.equals("OLAHAN", true)
                                "siap dijual" -> item.aktifDijual && item.stokSaatIni > 0L
                                "habis" -> item.stokSaatIni <= 0L
                                else -> true
                            }
                            cocokKeyword && cocokKategori
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                                .heightIn(max = 680.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(title, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { dialog.dismiss() }) { Text("Tutup") }
                            }
                            OutlinedTextField(
                                value = keyword,
                                onValueChange = { keyword = it },
                                label = { Text("Cari produk") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                categories.forEach { label ->
                                    AssistChip(
                                        onClick = { kategoriAktif = label },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            if (filtered.isEmpty()) {
                                Card(colors = CardDefaults.cardColors()) { Text("Produk tidak ditemukan", Modifier.padding(16.dp)) }
                            } else {
                                GenericRowsStatic(
                                    rows = filtered.map { item ->
                                        ItemBaris(
                                            id = item.id,
                                            title = item.namaProduk,
                                            subtitle = item.ringkasanSingkat(),
                                            badge = item.jenisProduk.ifBlank { "Produk" },
                                            amount = "${Formatter.ribuan(item.stokSaatIni)} ${item.satuan.ifBlank { "pcs" }}",
                                            priceStatus = if (item.aktifDijual) "Aktif" else "Nonaktif",
                                            parameterStatus = if (item.id == selectedId) "Dipilih" else item.infoTambahan,
                                            tone = if (item.id == selectedId) WarnaBaris.GREEN else WarnaBaris.DEFAULT,
                                            priceTone = if (item.aktifDijual) WarnaBaris.GREEN else WarnaBaris.ORANGE,
                                            parameterTone = if (item.id == selectedId) WarnaBaris.GREEN else WarnaBaris.BLUE
                                        )
                                    },
                                    onClick = { row ->
                                        produk.firstOrNull { it.id == row.id }?.let {
                                            onDipilih(it)
                                            dialog.dismiss()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        )
        dialog.show()
    }
}

data class ProdukPilihanUi(
    val id: String,
    val namaProduk: String,
    val jenisProduk: String,
    val stokSaatIni: Long,
    val satuan: String,
    val aktifDijual: Boolean,
    val infoTambahan: String = ""
) {
    fun ringkasanSingkat(): String {
        val status = if (aktifDijual) "Aktif" else "Nonaktif"
        val jenis = jenisProduk.ifBlank { "Produk" }
        val stok = "${Formatter.ribuan(stokSaatIni)} ${satuan.ifBlank { "pcs" }}"
        return listOf(jenis, status, "Stok $stok", infoTambahan)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }
}
