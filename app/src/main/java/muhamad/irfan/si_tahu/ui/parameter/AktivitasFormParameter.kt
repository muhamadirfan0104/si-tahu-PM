package muhamad.irfan.si_tahu.ui.parameter

import android.content.Intent
import android.os.Bundle
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.databinding.ActivityParameterFormBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.AdapterSpinner

class AktivitasFormParameter : AktivitasDasar() {

    private lateinit var binding: ActivityParameterFormBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var products: List<OpsiProdukDasar> = emptyList()
    private var editingParameterId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParameterFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Parameter", "Tambah atau edit parameter")

        editingParameterId = intent.getStringExtra(EkstraAplikasi.EXTRA_PARAMETER_ID)
        binding.cbActive.isChecked = true

        binding.btnSave.setOnClickListener {
            saveParameter()
        }

        loadBasicProducts()
    }

    private fun loadBasicProducts() {
        firestore.collection("produk")
            .whereEqualTo("jenisProduk", "DASAR")
            .get()
            .addOnSuccessListener { snapshot ->
                products = snapshot.documents.map { doc ->
                    OpsiProdukDasar(
                        id = doc.id,
                        kodeProduk = doc.getString("kodeProduk").orEmpty(),
                        namaProduk = doc.getString("namaProduk").orEmpty(),
                        satuan = doc.getString("satuan").orEmpty()
                    )
                }.sortedBy { it.namaProduk }

                if (products.isEmpty()) {
                    binding.spProduct.adapter =
                        AdapterSpinner.stringAdapter(this, listOf("Belum ada produk dasar"))
                    showMessage("Belum ada produk dasar. Tambahkan produk DASAR dulu.")
                    return@addOnSuccessListener
                }

                binding.spProduct.adapter =
                    AdapterSpinner.stringAdapter(this, products.map { it.namaProduk })

                if (!editingParameterId.isNullOrBlank()) {
                    loadEditingParameter(editingParameterId!!)
                }
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk dasar: ${e.message}")
            }
    }

    private fun loadEditingParameter(parameterId: String) {
        firestore.collection("parameterProduksi")
            .document(parameterId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                val idProduk = doc.getString("idProduk").orEmpty()
                val productIndex = products.indexOfFirst { it.id == idProduk }.takeIf { it >= 0 } ?: 0
                binding.spProduct.setSelection(productIndex)

                binding.etResultPerBatch.setText((doc.getLong("hasilPerProduksi") ?: 0L).toString())
                binding.etNote.setText(doc.getString("catatan").orEmpty())
                binding.cbActive.isChecked = doc.getBoolean("aktif") ?: true
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat parameter: ${e.message}")
            }
    }

    private fun saveParameter() {
        val product = products.getOrNull(binding.spProduct.selectedItemPosition)
        if (product == null) {
            showMessage("Produk dasar belum tersedia.")
            return
        }

        val hasilPerProduksi = binding.etResultPerBatch.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val catatan = binding.etNote.text?.toString()?.trim().orEmpty()
        val aktif = binding.cbActive.isChecked

        if (hasilPerProduksi <= 0L) {
            binding.etResultPerBatch.error = "Hasil produksi harus lebih dari 0"
            binding.etResultPerBatch.requestFocus()
            return
        }

        val now = Timestamp.now()
        val docId = editingParameterId ?: firestore.collection("parameterProduksi").document().id

        val data = hashMapOf<String, Any>(
            "idProduk" to product.id,
            "kodeProduk" to product.kodeProduk,
            "namaProduk" to product.namaProduk,
            "hasilPerProduksi" to hasilPerProduksi,
            "satuanHasil" to product.satuan,
            "aktif" to aktif,
            "catatan" to catatan,
            "diperbaruiPada" to now
        )

        if (editingParameterId == null) {
            data["dibuatPada"] = now
        }

        val ref = firestore.collection("parameterProduksi").document(docId)
        val task = if (editingParameterId == null) ref.set(data) else ref.update(data)

        task.addOnSuccessListener {
            showMessage("Parameter berhasil disimpan.")
            startActivity(Intent(this, AktivitasDaftarParameter::class.java))
            finish()
        }.addOnFailureListener { e ->
            showMessage("Gagal menyimpan parameter: ${e.message}")
        }
    }
}

private data class OpsiProdukDasar(
    val id: String,
    val kodeProduk: String,
    val namaProduk: String,
    val satuan: String
)