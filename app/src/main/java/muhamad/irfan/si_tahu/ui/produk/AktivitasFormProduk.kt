package muhamad.irfan.si_tahu.ui.produk

import android.os.Bundle
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import muhamad.irfan.si_tahu.databinding.ActivityProductFormBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.util.AdapterSpinner
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.InputAngka

class AktivitasFormProduk : AktivitasDasar() {

    private lateinit var binding: ActivityProductFormBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val categories = listOf("DASAR", "OLAHAN")

    private var editingProductId: String? = null
    private var existingKodeProduk: String? = null
    private var isSaving = false
    private var suppressActiveListener = false
    private var lastActiveState = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindToolbar(binding.toolbar, "Form Produk", "Tambah atau edit produk")
        binding.spCategory.adapter = AdapterSpinner.stringAdapter(this, categories)
        binding.spPhotoTone.visibility = View.GONE
        InputAngka.pasang(binding.etMinStock)
        InputAngka.pasang(binding.etShelfLifeDays)
        InputAngka.pasang(binding.etNearExpiryDays)

        editingProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)

        setupInitialState()
        setupActiveToggleRule()
        setupOptionalDeleteButton()

        binding.btnSave.setOnClickListener {
            if (!isSaving) saveProduct()
        }
    }

    private fun setupInitialState() {
        if (editingProductId.isNullOrBlank()) {
            bindSellingState(aktifDijual = true, tampilDiKasir = true)
            InputAngka.setNilai(binding.etShelfLifeDays, 2L)
            InputAngka.setNilai(binding.etNearExpiryDays, 1L)
        } else {
            loadProduct(editingProductId!!)
        }
    }

    private fun setupActiveToggleRule() {
        binding.cbActive.setOnCheckedChangeListener { _, isChecked ->
            if (suppressActiveListener) return@setOnCheckedChangeListener

            val reactivated = !lastActiveState && isChecked
            lastActiveState = isChecked

            if (!isChecked) {
                binding.cbShowCashier.isChecked = false
                binding.cbShowCashier.isEnabled = false
            } else {
                binding.cbShowCashier.isEnabled = true
                if (reactivated) binding.cbShowCashier.isChecked = true
            }
        }
    }

    private fun bindSellingState(aktifDijual: Boolean, tampilDiKasir: Boolean) {
        suppressActiveListener = true
        binding.cbActive.isChecked = aktifDijual
        binding.cbShowCashier.isChecked = if (aktifDijual) tampilDiKasir else false
        binding.cbShowCashier.isEnabled = aktifDijual
        suppressActiveListener = false
        lastActiveState = aktifDijual
    }

    private fun setupOptionalDeleteButton() {
        val deleteButtonId = resources.getIdentifier("btnDelete", "id", packageName)
        if (deleteButtonId == 0) return

        val deleteButton = findViewById<MaterialButton?>(deleteButtonId) ?: return
        deleteButton.visibility = if (editingProductId.isNullOrBlank()) View.GONE else View.VISIBLE

        deleteButton.setOnClickListener {
            val productId = editingProductId ?: return@setOnClickListener
            if (!isSaving) softDeleteProduct(productId)
        }
    }

    private fun setSavingState(saving: Boolean) {
        isSaving = saving
        binding.btnSave.isEnabled = !saving
        binding.btnSave.text = if (saving) "Menyimpan..." else "Simpan Produk"
    }

    private fun loadProduct(productId: String) {
        firestore.collection("Produk")
            .document(productId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    showMessage("Data produk tidak ditemukan.")
                    finish()
                    return@addOnSuccessListener
                }

                existingKodeProduk = doc.getString("kodeProduk").orEmpty()
                binding.etName.setText(doc.getString("namaProduk").orEmpty())

                val jenisProduk = doc.getString("jenisProduk").orEmpty()
                val categoryIndex = categories.indexOf(jenisProduk).takeIf { it >= 0 } ?: 0
                binding.spCategory.setSelection(categoryIndex)

                binding.etUnit.setText(doc.getString("satuan").orEmpty())
                InputAngka.setNilai(binding.etMinStock, doc.getLong("stokMinimum") ?: 0L)
                InputAngka.setNilai(binding.etShelfLifeDays, doc.getLong("masaSimpanHari") ?: 2L)
                InputAngka.setNilai(binding.etNearExpiryDays, doc.getLong("hariHampirKadaluarsa") ?: 1L)

                bindSellingState(
                    aktifDijual = doc.getBoolean("aktifDijual") ?: true,
                    tampilDiKasir = doc.getBoolean("tampilDiKasir") ?: true
                )
            }
            .addOnFailureListener { e ->
                showMessage("Gagal memuat produk: ${e.message}")
                finish()
            }
    }

    private fun saveProduct() {
        binding.etName.error = null
        binding.etUnit.error = null

        val namaProduk = binding.etName.text?.toString()?.trim().orEmpty()
        val jenisProduk = categories[binding.spCategory.selectedItemPosition]
        val satuan = binding.etUnit.text?.toString()?.trim().orEmpty()
        val stokMinimum = InputAngka.ambilLong(binding.etMinStock)
        val masaSimpanHari = InputAngka.ambilLong(binding.etShelfLifeDays).coerceAtLeast(1L)
        val hariHampirKadaluarsa = InputAngka.ambilLong(binding.etNearExpiryDays)
            .coerceAtLeast(0L)
            .coerceAtMost(masaSimpanHari)
        val aktifDijual = binding.cbActive.isChecked
        val tampilDiKasir = binding.cbShowCashier.isChecked

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

        setSavingState(true)
        val now = Timestamp.now()

        fun persistProduct(productId: String, kodeProduk: String) {
            val data = mutableMapOf<String, Any?>(
                "kodeProduk" to kodeProduk,
                "namaProduk" to namaProduk,
                "jenisProduk" to jenisProduk,
                "satuan" to satuan,
                "stokMinimum" to stokMinimum,
                "masaSimpanHari" to masaSimpanHari,
                "hariHampirKadaluarsa" to hariHampirKadaluarsa,
                "tampilDiKasir" to if (aktifDijual) tampilDiKasir else false,
                "aktifDijual" to aktifDijual,
                "urlFoto" to "",
                "dihapus" to false,
                "dihapusPada" to null,
                "diperbaruiPada" to now
            )

            if (editingProductId == null) {
                data["stokSaatIni"] = 0L
                data["dibuatPada"] = now
            }

            val docRef = firestore.collection("Produk").document(productId)
            val task = if (editingProductId == null) {
                docRef.set(data)
            } else {
                docRef.set(data, SetOptions.merge())
            }

            task.addOnSuccessListener {
                setSavingState(false)
                showMessage("Produk berhasil disimpan.")
                setResult(RESULT_OK)
                finish()
            }.addOnFailureListener { e ->
                setSavingState(false)
                showMessage("Gagal menyimpan produk: ${e.message}")
            }
        }

        if (editingProductId == null) {
            generateNextProductIdentity(
                onResult = { newProductId, autoKodeProduk ->
                    persistProduct(newProductId, autoKodeProduk)
                },
                onError = { e ->
                    setSavingState(false)
                    showMessage("Gagal membuat ID produk: ${e.message}")
                }
            )
        } else {
            val finalCode = existingKodeProduk
                .orEmpty()
                .ifBlank { "PRD${System.currentTimeMillis().toString().takeLast(6)}" }
            persistProduct(editingProductId!!, finalCode)
        }
    }

    private fun generateNextProductIdentity(
        onResult: (productId: String, kodeProduk: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val suffix = System.currentTimeMillis().toString().takeLast(6)
            val productId = "prd_${System.currentTimeMillis()}"
            val kodeProduk = "PRD$suffix"
            onResult(productId, kodeProduk)
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun softDeleteProduct(productId: String) {
        val now = Timestamp.now()
        val data = mapOf(
            "dihapus" to true,
            "aktifDijual" to false,
            "tampilDiKasir" to false,
            "dihapusPada" to now,
            "diperbaruiPada" to now
        )

        setSavingState(true)

        firestore.collection("Produk")
            .document(productId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                setSavingState(false)
                showMessage("Produk berhasil dinonaktifkan.")
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                setSavingState(false)
                showMessage("Gagal menonaktifkan produk: ${e.message}")
            }
    }
}