package muhamad.irfan.si_tahu.ui.produk

import android.content.Intent
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

class AktivitasFormProduk : AktivitasDasar() {

    private lateinit var binding: ActivityProductFormBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val categories = listOf("DASAR", "OLAHAN")

    private var editingProductId: String? = null
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

        // Harga tidak lagi diatur dari form produk

        editingProductId = intent.getStringExtra(EkstraAplikasi.EXTRA_PRODUCT_ID)

        setupInitialState()
        setupActiveToggleRule()
        setupOptionalDeleteButton()

        binding.btnSave.setOnClickListener {
            if (!isSaving) {
                saveProduct()
            }
        }
    }

    private fun setupInitialState() {
        if (editingProductId.isNullOrBlank()) {
            setupCreateMode()
        } else {
            setupEditMode()
            loadProduct(editingProductId!!)
        }
    }

    private fun setupCreateMode() {
        binding.etCode.setText("")
        binding.etCode.hint = "Kode produk dibuat otomatis"
        lockCodeField()

        bindSellingState(
            aktifDijual = true,
            tampilDiKasir = true
        )
    }

    private fun setupEditMode() {
        lockCodeField()
    }

    private fun lockCodeField() {
        binding.etCode.isFocusable = false
        binding.etCode.isFocusableInTouchMode = false
        binding.etCode.isClickable = false
        binding.etCode.isLongClickable = false
        binding.etCode.isCursorVisible = false
        binding.etCode.keyListener = null
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
                if (reactivated) {
                    binding.cbShowCashier.isChecked = true
                }
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
            if (!isSaving) {
                softDeleteProduct(productId)
            }
        }
    }

    private fun setSavingState(saving: Boolean) {
        isSaving = saving
        binding.btnSave.isEnabled = !saving
        binding.btnSave.text = if (saving) "Menyimpan..." else "Simpan Produk"
    }

    private fun loadProduct(productId: String) {
        firestore.collection("produk")
            .document(productId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    showMessage("Data produk tidak ditemukan.")
                    finish()
                    return@addOnSuccessListener
                }

                binding.etCode.setText(doc.getString("kodeProduk").orEmpty())
                binding.etName.setText(doc.getString("namaProduk").orEmpty())

                val jenisProduk = doc.getString("jenisProduk").orEmpty()
                val categoryIndex = categories.indexOf(jenisProduk).takeIf { it >= 0 } ?: 0
                binding.spCategory.setSelection(categoryIndex)

                binding.etUnit.setText(doc.getString("satuan").orEmpty())
                binding.etStock.setText((doc.getLong("stokSaatIni") ?: 0L).toString())
                binding.etMinStock.setText((doc.getLong("stokMinimum") ?: 0L).toString())

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
        val namaProduk = binding.etName.text?.toString()?.trim().orEmpty()
        val jenisProduk = categories[binding.spCategory.selectedItemPosition]
        val satuan = binding.etUnit.text?.toString()?.trim().orEmpty()
        val stokSaatIni = binding.etStock.text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val stokMinimum = binding.etMinStock.text?.toString()?.trim()?.toLongOrNull() ?: 0L
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
            val finalTampilDiKasir = if (aktifDijual) tampilDiKasir else false

            val data = mutableMapOf<String, Any?>(
                "kodeProduk" to kodeProduk,
                "namaProduk" to namaProduk,
                "jenisProduk" to jenisProduk,
                "satuan" to satuan,
                "stokSaatIni" to stokSaatIni,
                "stokMinimum" to stokMinimum,
                "tampilDiKasir" to finalTampilDiKasir,
                "aktifDijual" to aktifDijual,
                "urlFoto" to "",
                "dihapus" to false,
                "dihapusPada" to null,
                "diperbaruiPada" to now
            )

            if (editingProductId == null) {
                data["dibuatPada"] = now
            }

            val docRef = firestore.collection("produk").document(productId)
            val task = if (editingProductId == null) {
                docRef.set(data)
            } else {
                docRef.set(data, SetOptions.merge())
            }

            task.addOnSuccessListener {
                onSaveCompleted()
            }.addOnFailureListener { e ->
                setSavingState(false)
                showMessage("Gagal menyimpan produk: ${e.message}")
            }
        }

        if (editingProductId == null) {
            generateNextProductIdentity(
                onResult = { newProductId, autoKodeProduk ->
                    binding.etCode.setText(autoKodeProduk)
                    persistProduct(newProductId, autoKodeProduk)
                },
                onError = { e ->
                    setSavingState(false)
                    showMessage("Gagal membuat ID produk: ${e.message}")
                }
            )
        } else {
            val existingCode = binding.etCode.text?.toString()?.trim().orEmpty()
            val finalCode = if (existingCode.isBlank()) {
                editingProductId!!.replace("prd_", "PRD").uppercase()
            } else {
                existingCode
            }
            persistProduct(editingProductId!!, finalCode)
        }
    }

    private fun generateNextProductIdentity(
        onResult: (productId: String, kodeProduk: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val counterRef = firestore.collection("meta").document("counter")

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(counterRef)
            val lastNumber = snapshot.getLong("produkTerakhir") ?: 0L
            val nextNumber = lastNumber + 1L

            transaction.set(
                counterRef,
                mapOf("produkTerakhir" to nextNumber),
                SetOptions.merge()
            )

            val productId = "prd_%03d".format(nextNumber)
            val kodeProduk = "PRD%03d".format(nextNumber)

            Pair(productId, kodeProduk)
        }.addOnSuccessListener { pair ->
            onResult(pair.first, pair.second)
        }.addOnFailureListener { e ->
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

        firestore.collection("produk")
            .document(productId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                setSavingState(false)
                showMessage("Produk berhasil dinonaktifkan.")
                startActivity(Intent(this, AktivitasDaftarProduk::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                setSavingState(false)
                showMessage("Gagal menonaktifkan produk: ${e.message}")
            }
    }

    private fun onSaveCompleted() {
        setSavingState(false)
        showMessage("Produk berhasil disimpan.")
        startActivity(Intent(this, AktivitasDaftarProduk::class.java))
        finish()
    }
}