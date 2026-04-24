package muhamad.irfan.si_tahu.ui.parameter

import android.content.Intent
import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import muhamad.irfan.si_tahu.databinding.ActivityParameterFormBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.InputAngka

class AktivitasFormParameter : AktivitasDasar() {

    private lateinit var binding: ActivityParameterFormBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var products: List<OpsiProdukDasar> = emptyList()
    private var editingParameterId: String? = null
    private var requestedProductId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParameterFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Parameter", "Tambah atau edit parameter produksi")

        editingParameterId = intent.getStringExtra(EkstraAplikasi.EXTRA_PARAMETER_ID)
        requestedProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)

        binding.cbActive.isChecked = true
        InputAngka.pasang(binding.etResultPerBatch)

        if (!editingParameterId.isNullOrBlank()) {
            lockProductFieldForEdit()
        }

        binding.btnSave.setOnClickListener {
            saveParameter()
        }

        loadBasicProducts()
    }

    private fun parameterCollection(productId: String) =
        firestore.collection("Produk").document(productId).collection("parameterProduksi")

    private fun lockProductFieldForEdit() {
        binding.spProduct.isEnabled = false
        binding.spProduct.isClickable = false
    }

    private fun loadBasicProducts() {
        firestore.collection("Produk")
            .whereEqualTo("jenisProduk", "DASAR")
            .get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents
                    .filter { it.getBoolean("dihapus") != true }
                    .map { doc ->
                    OpsiProdukDasar(
                        id = doc.id,
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        satuan = doc.getString("satuan").orEmpty()
                    )
                }.sortedBy { it.namaProduk.lowercase() }

                if (products.isEmpty()) {
                    binding.spProduct.adapter =
                        AdapterSpinner.stringAdapter(this, listOf("Belum ada produk dasar"))
                    showMessage("Belum ada produk dasar. Tambahkan produk DASAR dulu.")
                    return@addOnSuccessListener
                }

                binding.spProduct.adapter =
                    AdapterSpinner.stringAdapter(this, products.map { it.namaProduk })

                val initialIndex = requestedProductId?.let { targetId ->
                    products.indexOfFirst { it.id == targetId }.takeIf { it >= 0 } ?: 0
                } ?: 0
                binding.spProduct.setSelection(initialIndex)

                if (!editingParameterId.isNullOrBlank()) {
                    loadEditingParameter(editingParameterId!!)
                }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk dasar: ${e.message}")
            }
    }

    private fun loadEditingParameter(parameterId: String) {
        val productId = requestedProductId
        if (!productId.isNullOrBlank()) {
            parameterCollection(productId)
                .document(parameterId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        applyEditingParameter(doc)
                    } else {
                        findEditingParameterAcrossProducts(parameterId)
                    }
                }
                .addOnFailureListener {
                    findEditingParameterAcrossProducts(parameterId)
                }
            return
        }

        findEditingParameterAcrossProducts(parameterId)
    }

    private fun findEditingParameterAcrossProducts(parameterId: String) {
        firestore.collection("Produk")
            .whereEqualTo("jenisProduk", "DASAR")
            .get()
            .addOnSuccessListener { productSnapshot ->
                val productDocs = productSnapshot.documents.filter { it.getBoolean("dihapus") != true }
                if (productDocs.isEmpty()) {
                    showMessage("Data parameter tidak ditemukan.")
                    return@addOnSuccessListener
                }

                cariParameterDiProduk(productDocs, 0, parameterId)
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat parameter: ${e.message}")
            }
    }

    private fun cariParameterDiProduk(
        productDocs: List<com.google.firebase.firestore.DocumentSnapshot>,
        index: Int,
        parameterId: String
    ) {
        if (index >= productDocs.size) {
            showMessage("Data parameter tidak ditemukan.")
            return
        }

        val productId = productDocs[index].id
        parameterCollection(productId)
            .document(parameterId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.getBoolean("dihapus") != true) {
                    applyEditingParameter(doc)
                } else {
                    cariParameterDiProduk(productDocs, index + 1, parameterId)
                }
            }
            .addOnFailureListener {
                cariParameterDiProduk(productDocs, index + 1, parameterId)
            }
    }

    private fun applyEditingParameter(doc: com.google.firebase.firestore.DocumentSnapshot) {
        if (doc.getBoolean("dihapus") == true) {
            showMessage("Parameter ini sudah dihapus.")
            finish()
            return
        }

        val idProduk = doc.getString("idProduk")
            .orEmpty()
            .ifBlank { doc.reference.parent.parent?.id.orEmpty() }
        requestedProductId = idProduk

        val productIndex = products.indexOfFirst { it.id == idProduk }
            .takeIf { it >= 0 } ?: 0
        binding.spProduct.setSelection(productIndex)

        InputAngka.setNilai(binding.etResultPerBatch, doc.getLong("hasilPerProduksi") ?: 0L)
        binding.etNote.setText(doc.getString("catatan").orEmpty())
        binding.cbActive.isChecked = doc.getBoolean("aktif") ?: true
    }

    private fun saveParameter() {
        val product = products.getOrNull(binding.spProduct.selectedItemPosition)
        if (product == null) {
            showMessage("Produk dasar belum tersedia.")
            return
        }

        val hasilPerProduksi = InputAngka.ambilLong(binding.etResultPerBatch)
        val catatan = binding.etNote.text?.toString()?.trim().orEmpty()
        val aktif = binding.cbActive.isChecked

        if (hasilPerProduksi <= 0L) {
            binding.etResultPerBatch.error = "Hasil produksi harus lebih dari 0"
            binding.etResultPerBatch.requestFocus()
            return
        }

        val parameterId = editingParameterId ?: generateNextParameterId()
        persistParameter(
            parameterId = parameterId,
            product = product,
            hasilPerProduksi = hasilPerProduksi,
            catatan = catatan,
            aktif = aktif,
            isNew = editingParameterId.isNullOrBlank()
        )
    }

    private fun persistParameter(
        parameterId: String,
        product: OpsiProdukDasar,
        hasilPerProduksi: Long,
        catatan: String,
        aktif: Boolean,
        isNew: Boolean
    ) {
        val now = Timestamp.now()

        val data = hashMapOf<String, Any?>(
            "idProduk" to product.id,
            "namaProduk" to product.namaProduk,
            "hasilPerProduksi" to hasilPerProduksi,
            "satuanHasil" to product.satuan,
            "aktif" to aktif,
            "catatan" to catatan,
            "dihapus" to false,
            "dihapusPada" to null,
            "diperbaruiPada" to now
        )

        if (isNew) {
            data["dibuatPada"] = now
        }

        parameterCollection(product.id)
            .document(parameterId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                if (aktif) {
                    deactivateOtherParameters(
                        productId = product.id,
                        currentParameterId = parameterId
                    )
                } else {
                    goBackToList(product.id)
                }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal menyimpan parameter: ${e.message}")
            }
    }

    private fun deactivateOtherParameters(
        productId: String,
        currentParameterId: String
    ) {
        parameterCollection(productId)
            .get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents.filter { it.getBoolean("dihapus") != true && it.getBoolean("aktif") != false }
                val batch = firestore.batch()
                val now = Timestamp.now()

                docs.forEach { doc ->
                    if (doc.id != currentParameterId) {
                        batch.update(
                            doc.reference,
                            mapOf(
                                "aktif" to false,
                                "diperbaruiPada" to now
                            )
                        )
                    }
                }

                batch.commit()
                    .addOnSuccessListener {
                        goBackToList(productId)
                    }
                    .addOnFailureListener { e ->
                        showMessage("Parameter tersimpan, tapi sinkron aktif gagal: ${e.message}")
                        goBackToList(productId)
                    }
            }
            .addOnFailureListener { e ->
                showMessage("Parameter tersimpan, tapi validasi aktif gagal: ${e.message}")
                goBackToList(productId)
            }
    }

    private fun generateNextParameterId(): String {
        return "ppm_${System.currentTimeMillis()}"
    }

    private fun goBackToList(productId: String) {
        showMessage("Parameter produksi berhasil disimpan.")
        setResult(RESULT_OK, Intent().putExtra(EkstraAplikasi.EXTRA_PRODUCT_ID, productId))
        finish()
    }
}

private data class OpsiProdukDasar(
    val id: String,
    val namaProduk: String,
    val satuan: String
)
