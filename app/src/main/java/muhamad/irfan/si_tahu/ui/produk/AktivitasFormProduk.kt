package muhamad.irfan.si_tahu.ui.product

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.databinding.ActivityProductFormBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.AdapterSpinner

class AktivitasFormProduk : AktivitasDasar() {

    private lateinit var binding: ActivityProductFormBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val categories = listOf("DASAR", "OLAHAN")
    private var editingProductId: String? = null
    private var cashierPriceDocId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Produk", "Tambah atau edit produk")

        binding.spCategory.adapter = AdapterSpinner.stringAdapter(this, categories)

        // Field lama disembunyikan
        binding.spPhotoTone.visibility = View.GONE

        editingProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)

        if (editingProductId.isNullOrBlank()) {
            binding.cbActive.isChecked = true
            binding.cbShowCashier.isChecked = true
        } else {
            loadProduct(editingProductId!!)
        }

        binding.btnSave.setOnClickListener {
            saveProduct()
        }
    }

    private fun loadProduct(productId: String) {
        firestore.collection("produk")
            .document(productId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    showMessage("Data produk tidak ditemukan.")
                    return@addOnSuccessListener
                }

                binding.etCode.setText(doc.getString("kodeProduk").orEmpty())
                binding.etName.setText(doc.getString("namaProduk").orEmpty())
                binding.spCategory.setSelection(
                    categories.indexOf(doc.getString("jenisProduk")).takeIf { it >= 0 } ?: 0
                )
                binding.etUnit.setText(doc.getString("satuan").orEmpty())
                binding.etStock.setText((doc.getLong("stokSaatIni") ?: 0L).toString())
                binding.etMinStock.setText((doc.getLong("stokMinimum") ?: 0L).toString())
                binding.cbActive.isChecked = doc.getBoolean("aktifDijual") ?: true
                binding.cbShowCashier.isChecked = doc.getBoolean("tampilDiKasir") ?: true

                loadCashierPrice(productId)
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk: ${e.message}")
            }
    }

    private fun loadCashierPrice(productId: String) {
        firestore.collection("produk")
            .document(productId)
            .collection("hargaJual")
            .whereEqualTo("kanalHarga", "KASIR")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val priceDoc = snapshot.documents.firstOrNull() ?: return@addOnSuccessListener
                cashierPriceDocId = priceDoc.id
                binding.etDefaultPrice.setText((priceDoc.getLong("hargaSatuan") ?: 0L).toString())
            }
    }

    private fun saveProduct() {
        val kodeProduk = binding.etCode.text?.toString()?.trim().orEmpty()
        val namaProduk = binding.etName.text?.toString()?.trim().orEmpty()
        val jenisProduk = categories[binding.spCategory.selectedItemPosition]
        val satuan = binding.etUnit.text?.toString()?.trim().orEmpty()
        val stokSaatIni = binding.etStock.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val stokMinimum = binding.etMinStock.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val hargaKasir = binding.etDefaultPrice.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val aktifDijual = binding.cbActive.isChecked
        val tampilDiKasir = binding.cbShowCashier.isChecked

        if (kodeProduk.isBlank()) {
            binding.etCode.error = "Kode produk wajib diisi"
            binding.etCode.requestFocus()
            return
        }

        if (namaProduk.isBlank()) {
            binding.etName.error = "Nama produk wajib diisi"
            binding.etName.requestFocus()
            return
        }

        if (satuan.isBlank()) {
            binding.etUnit.error = "Satuan wajib diisi"
            binding.etUnit.requestFocus()
            return
        }

        val now = Timestamp.now()
        val productId = editingProductId ?: firestore.collection("produk").document().id

        val data = hashMapOf<String, Any>(
            "kodeProduk" to kodeProduk,
            "namaProduk" to namaProduk,
            "jenisProduk" to jenisProduk,
            "satuan" to satuan,
            "stokSaatIni" to stokSaatIni,
            "stokMinimum" to stokMinimum,
            "tampilDiKasir" to tampilDiKasir,
            "aktifDijual" to aktifDijual,
            "urlFoto" to "",
            "diperbaruiPada" to now
        )

        if (editingProductId == null) {
            data["dibuatPada"] = now
        }

        val task = if (editingProductId == null) {
            firestore.collection("produk").document(productId).set(data)
        } else {
            firestore.collection("produk").document(productId).update(data)
        }

        task.addOnSuccessListener {
            if (hargaKasir > 0L) {
                upsertCashierPrice(productId, hargaKasir)
            } else {
                showMessage("Produk berhasil disimpan.")
                startActivity(Intent(this, AktivitasDaftarProduk::class.java))
                finish()
            }
        }.addOnFailureListener { e ->
            showMessage("Gagal menyimpan produk: ${e.message}")
        }
    }

    private fun upsertCashierPrice(productId: String, hargaKasir: Long) {
        val now = Timestamp.now()
        val subCollection = firestore.collection("produk")
            .document(productId)
            .collection("hargaJual")

        val priceId = cashierPriceDocId ?: subCollection.document().id

        val data = hashMapOf<String, Any>(
            "kanalHarga" to "KASIR",
            "namaHarga" to "Harga Kasir",
            "hargaSatuan" to hargaKasir,
            "hargaUtama" to true,
            "aktif" to true,
            "diperbaruiPada" to now
        )

        if (cashierPriceDocId == null) {
            data["dibuatPada"] = now
        }

        val task = if (cashierPriceDocId == null) {
            subCollection.document(priceId).set(data)
        } else {
            subCollection.document(priceId).update(data)
        }

        task.addOnSuccessListener {
            showMessage("Produk berhasil disimpan.")
            startActivity(Intent(this, AktivitasDaftarProduk::class.java))
            finish()
        }.addOnFailureListener { e ->
            showMessage("Produk tersimpan, tapi harga kasir gagal disimpan: ${e.message}")
            startActivity(Intent(this, AktivitasDaftarProduk::class.java))
            finish()
        }
    }
}