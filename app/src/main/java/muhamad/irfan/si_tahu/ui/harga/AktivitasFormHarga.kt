package muhamad.irfan.si_tahu.ui.price

import android.content.Intent
import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.databinding.ActivityPriceFormBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.AdapterSpinner

class AktivitasFormHarga : AktivitasDasar() {

    private lateinit var binding: ActivityPriceFormBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var products: List<OpsiProduk> = emptyList()
    private var editingPriceId: String? = null
    private var requestedProductId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPriceFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Harga Kanal", "Tambah atau edit harga kanal")

        requestedProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)
        editingPriceId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRICE_ID)

        binding.cbActive.isChecked = true

        binding.btnSave.setOnClickListener {
            savePrice()
        }

        loadProducts()
    }

    private fun loadProducts() {
        firestore.collection("produk")
            .get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents.map { doc ->
                    OpsiProduk(
                        id = doc.id,
                        name = doc.getString("namaProduk").orEmpty()
                    )
                }.sortedBy { it.name }

                if (products.isEmpty()) {
                    binding.spProduct.adapter =
                        AdapterSpinner.stringAdapter(this, listOf("Belum ada produk"))
                    showMessage("Belum ada produk. Tambahkan produk dulu.")
                    return@addOnSuccessListener
                }

                binding.spProduct.adapter =
                    AdapterSpinner.stringAdapter(this, products.map { it.name })

                val initialIndex = requestedProductId?.let { targetId ->
                    products.indexOfFirst { it.id == targetId }.takeIf { it >= 0 } ?: 0
                } ?: 0
                binding.spProduct.setSelection(initialIndex)

                if (!editingPriceId.isNullOrBlank()) {
                    loadEditingPrice(products[initialIndex].id, editingPriceId!!)
                }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk: ${e.message}")
            }
    }

    private fun loadEditingPrice(productId: String, priceId: String) {
        firestore.collection("produk")
            .document(productId)
            .collection("hargaJual")
            .document(priceId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                binding.etLabel.setText(doc.getString("namaHarga").orEmpty())
                binding.etPrice.setText((doc.getLong("hargaSatuan") ?: 0L).toString())
                binding.cbActive.isChecked = doc.getBoolean("aktif") ?: true
                binding.cbDefault.isChecked = doc.getBoolean("hargaUtama") ?: false
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat harga kanal: ${e.message}")
            }
    }

    private fun savePrice() {
        val selectedProduct = products.getOrNull(binding.spProduct.selectedItemPosition)
        if (selectedProduct == null) {
            showMessage("Produk belum tersedia.")
            return
        }

        val labelInput = binding.etLabel.text?.toString()?.trim().orEmpty()
        val hargaSatuan = binding.etPrice.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val aktif = binding.cbActive.isChecked
        val hargaUtama = binding.cbDefault.isChecked

        if (labelInput.isBlank()) {
            binding.etLabel.error = "Label harga wajib diisi"
            binding.etLabel.requestFocus()
            return
        }

        if (hargaSatuan <= 0L) {
            binding.etPrice.error = "Harga harus lebih dari 0"
            binding.etPrice.requestFocus()
            return
        }

        val kanalHarga = normalizeChannel(labelInput)
        if (kanalHarga == null) {
            binding.etLabel.error = "Isi label dengan KASIR, PASAR, atau RESELLER"
            binding.etLabel.requestFocus()
            return
        }

        val now = Timestamp.now()
        val docId = editingPriceId ?: firestore.collection("produk")
            .document(selectedProduct.id)
            .collection("hargaJual")
            .document()
            .id

        val data = hashMapOf<String, Any>(
            "kanalHarga" to kanalHarga,
            "namaHarga" to labelInput,
            "hargaSatuan" to hargaSatuan,
            "hargaUtama" to hargaUtama,
            "aktif" to aktif,
            "diperbaruiPada" to now
        )

        if (editingPriceId == null) {
            data["dibuatPada"] = now
        }

        val priceRef = firestore.collection("produk")
            .document(selectedProduct.id)
            .collection("hargaJual")
            .document(docId)

        val task = if (editingPriceId == null) {
            priceRef.set(data)
        } else {
            priceRef.update(data)
        }

        task.addOnSuccessListener {
            if (hargaUtama) {
                clearOtherPrimaryPrices(selectedProduct.id, docId)
            } else {
                goBackToList(selectedProduct.id)
            }
        }.addOnFailureListener { e ->
            showMessage("Gagal menyimpan harga kanal: ${e.message}")
        }
    }

    private fun clearOtherPrimaryPrices(productId: String, currentDocId: String) {
        firestore.collection("produk")
            .document(productId)
            .collection("hargaJual")
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()

                snapshot.documents.forEach { doc ->
                    if (doc.id != currentDocId && (doc.getBoolean("hargaUtama") == true)) {
                        batch.update(doc.reference, "hargaUtama", false)
                    }
                }

                batch.commit()
                    .addOnSuccessListener { goBackToList(productId) }
                    .addOnFailureListener { e ->
                        showMessage("Harga tersimpan, tapi sinkron utama gagal: ${e.message}")
                        goBackToList(productId)
                    }
            }
            .addOnFailureListener { e ->
                showMessage("Harga tersimpan, tapi validasi utama gagal: ${e.message}")
                goBackToList(productId)
            }
    }

    private fun goBackToList(productId: String) {
        showMessage("Harga kanal berhasil disimpan.")
        startActivity(
            Intent(this, AktivitasDaftarHarga::class.java)
                .putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId)
        )
        finish()
    }

    private fun normalizeChannel(input: String): String? {
        val cleaned = input.trim().uppercase()
            .removePrefix("HARGA ")
            .trim()

        return when (cleaned) {
            "KASIR" -> "KASIR"
            "PASAR" -> "PASAR"
            "RESELLER" -> "RESELLER"
            else -> null
        }
    }
}

private data class OpsiProduk(
    val id: String,
    val name: String
)