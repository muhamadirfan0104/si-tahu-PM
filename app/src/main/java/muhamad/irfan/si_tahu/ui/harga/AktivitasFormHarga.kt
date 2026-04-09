package muhamad.irfan.si_tahu.ui.harga

import android.content.Intent
import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import muhamad.irfan.si_tahu.databinding.ActivityPriceFormBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.EkstraAplikasi

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

        if (!editingPriceId.isNullOrBlank()) {
            lockProductFieldForEdit()
        }

        binding.btnSave.setOnClickListener {
            savePrice()
        }

        loadProducts()
    }

    private fun lockProductFieldForEdit() {
        binding.spProduct.isEnabled = false
        binding.spProduct.isClickable = false
    }

    private fun loadProducts() {
        firestore.collection("produk")
            .whereEqualTo("dihapus", false)
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

                binding.etLabel.setText(doc.getString("kanalHarga").orEmpty())
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

        val kanalInput = binding.etLabel.text?.toString()?.trim().orEmpty()
        val kanalKey = normalizeChannelKey(kanalInput)
        val hargaSatuan = binding.etPrice.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val aktif = binding.cbActive.isChecked
        val hargaUtama = binding.cbDefault.isChecked

        if (kanalInput.isBlank()) {
            binding.etLabel.error = "Nama kanal wajib diisi"
            binding.etLabel.requestFocus()
            return
        }

        if (hargaSatuan <= 0L) {
            binding.etPrice.error = "Harga harus lebih dari 0"
            binding.etPrice.requestFocus()
            return
        }

        val hargaRef = firestore.collection("produk")
            .document(selectedProduct.id)
            .collection("hargaJual")

        hargaRef.whereEqualTo("kanalKey", kanalKey)
            .whereEqualTo("dihapus", false)
            .get()
            .addOnSuccessListener { snapshot ->
                val duplicateExists = snapshot.documents.any { it.id != editingPriceId }

                if (duplicateExists) {
                    binding.etLabel.error = "Nama kanal sudah dipakai untuk produk ini"
                    binding.etLabel.requestFocus()
                    return@addOnSuccessListener
                }

                if (editingPriceId == null) {
                    generateNextPriceId(
                        productId = selectedProduct.id,
                        onResult = { newPriceId ->
                            persistPrice(
                                productId = selectedProduct.id,
                                priceId = newPriceId,
                                kanalInput = kanalInput,
                                kanalKey = kanalKey,
                                hargaSatuan = hargaSatuan,
                                aktif = aktif,
                                hargaUtama = hargaUtama,
                                isNew = true
                            )
                        },
                        onError = { e ->
                            showMessage("Gagal membuat ID harga: ${e.message}")
                        }
                    )
                } else {
                    persistPrice(
                        productId = selectedProduct.id,
                        priceId = editingPriceId!!,
                        kanalInput = kanalInput,
                        kanalKey = kanalKey,
                        hargaSatuan = hargaSatuan,
                        aktif = aktif,
                        hargaUtama = hargaUtama,
                        isNew = false
                    )
                }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memeriksa nama kanal: ${e.message}")
            }
    }

    private fun persistPrice(
        productId: String,
        priceId: String,
        kanalInput: String,
        kanalKey: String,
        hargaSatuan: Long,
        aktif: Boolean,
        hargaUtama: Boolean,
        isNew: Boolean
    ) {
        val now = Timestamp.now()

        val data = hashMapOf<String, Any?>(
            "kanalHarga" to kanalInput,
            "kanalKey" to kanalKey,
            "hargaSatuan" to hargaSatuan,
            "hargaUtama" to hargaUtama,
            "aktif" to aktif,
            "dihapus" to false,
            "dihapusPada" to null,
            "diperbaruiPada" to now
        )

        if (isNew) {
            data["dibuatPada"] = now
        }

        firestore.collection("produk")
            .document(productId)
            .collection("hargaJual")
            .document(priceId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                finalizePrimaryAfterSave(
                    productId = productId,
                    currentDocId = priceId,
                    requestedPrimary = hargaUtama
                )
            }
            .addOnFailureListener { e ->
                showMessage("Gagal menyimpan harga kanal: ${e.message}")
            }
    }

    private fun generateNextPriceId(
        productId: String,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val counterRef = firestore.collection("produk")
            .document(productId)
            .collection("meta")
            .document("counterHarga")

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            val lastNumber = snapshot.getLong("hargaTerakhir") ?: 0L
            val nextNumber = lastNumber + 1L

            transaction.set(
                counterRef,
                mapOf("hargaTerakhir" to nextNumber),
                SetOptions.merge()
            )

            "hrg_%03d".format(nextNumber)
        }.addOnSuccessListener { newId ->
            onResult(newId)
        }.addOnFailureListener { e ->
            onError(e)
        }
    }

    private fun finalizePrimaryAfterSave(
        productId: String,
        currentDocId: String,
        requestedPrimary: Boolean
    ) {
        val hargaRef = firestore.collection("produk")
            .document(productId)
            .collection("hargaJual")

        hargaRef.whereEqualTo("dihapus", false)
            .whereEqualTo("aktif", true)
            .get()
            .addOnSuccessListener { snapshot ->
                val activeDocs = snapshot.documents

                if (activeDocs.isEmpty()) {
                    hargaRef.document(currentDocId)
                        .update(
                            mapOf(
                                "aktif" to true,
                                "hargaUtama" to true
                            )
                        )
                        .addOnSuccessListener {
                            showMessage("Minimal harus ada satu harga aktif dan default.")
                            goBackToList(productId)
                        }
                        .addOnFailureListener { e ->
                            showMessage("Harga tersimpan, tapi validasi default gagal: ${e.message}")
                            goBackToList(productId)
                        }
                    return@addOnSuccessListener
                }

                if (requestedPrimary) {
                    clearOtherPrimaryPrices(productId, currentDocId)
                    return@addOnSuccessListener
                }

                val hasOtherPrimary = activeDocs.any { doc ->
                    doc.id != currentDocId && (doc.getBoolean("hargaUtama") == true)
                }

                if (hasOtherPrimary) {
                    goBackToList(productId)
                } else {
                    val fallbackDoc = activeDocs.firstOrNull { it.id == currentDocId } ?: activeDocs.first()

                    hargaRef.document(fallbackDoc.id)
                        .update("hargaUtama", true)
                        .addOnSuccessListener {
                            showMessage("Karena belum ada default, salah satu harga dijadikan default.")
                            goBackToList(productId)
                        }
                        .addOnFailureListener { e ->
                            showMessage("Harga tersimpan, tapi gagal menetapkan default: ${e.message}")
                            goBackToList(productId)
                        }
                }
            }
            .addOnFailureListener { e ->
                showMessage("Harga tersimpan, tapi validasi default gagal: ${e.message}")
                goBackToList(productId)
            }
    }

    private fun clearOtherPrimaryPrices(productId: String, currentDocId: String) {
        firestore.collection("produk")
            .document(productId)
            .collection("hargaJual")
            .whereEqualTo("dihapus", false)
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
        setResult(RESULT_OK, Intent().putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId))
        finish()
    }

    private fun normalizeChannelKey(input: String): String {
        return input.trim()
            .uppercase()
            .replace("\\s+".toRegex(), " ")
    }
}

private data class OpsiProduk(
    val id: String,
    val name: String
)