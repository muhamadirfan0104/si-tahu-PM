package muhamad.irfan.si_tahu.ui.pengaturan

import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.databinding.ActivityPengaturanUsahaBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar

class AktivitasPengaturanUsaha : AktivitasDasar() {

    private lateinit var binding: ActivityPengaturanUsahaBinding
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val docRef by lazy {
        firestore.collection("pengaturan").document("usaha")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPengaturanUsahaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindToolbar(binding.toolbar, "Pengaturan Usaha", "Kelola identitas usaha")

        setupPreviewListener()
        loadBusinessSettings()

        binding.btnSave.setOnClickListener {
            saveBusinessSettings()
        }
    }

    private fun setupPreviewListener() {
        binding.etBusinessName.doAfterTextChanged { updatePreview() }
        binding.etAddress.doAfterTextChanged { updatePreview() }
        binding.etLogoText.doAfterTextChanged { updatePreview() }
    }

    private fun updatePreview() {
        val namaUsaha = binding.etBusinessName.text?.toString()?.trim().orEmpty()
        val alamat = binding.etAddress.text?.toString()?.trim().orEmpty()
        val logoText = binding.etLogoText.text?.toString()?.trim().orEmpty()

        binding.tvBusinessPreview.text =
            if (namaUsaha.isBlank()) "Nama Usaha" else namaUsaha

        binding.tvBusinessAddressPreview.text =
            if (alamat.isBlank()) "Alamat usaha" else alamat

        binding.tvLogoTextPreview.text =
            if (logoText.isBlank()) "LT" else logoText.uppercase()
    }

    private fun loadBusinessSettings() {
        setLoading(true)

        docRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    binding.etBusinessName.setText(doc.getString("namaUsaha").orEmpty())
                    binding.etAddress.setText(doc.getString("alamat").orEmpty())
                    binding.etPhone.setText(doc.getString("nomorTelepon").orEmpty())
                    binding.etLogoText.setText(doc.getString("logoText").orEmpty())
                    binding.etReceiptFooter.setText(doc.getString("footerStruk").orEmpty())
                    binding.etBusinessNote.setText(doc.getString("catatanUsaha").orEmpty())
                }

                updatePreview()
                setLoading(false)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                showMessage("Gagal memuat pengaturan usaha: ${e.message}")
            }
    }

    private fun saveBusinessSettings() {
        clearErrors()

        val namaUsaha = binding.etBusinessName.text?.toString()?.trim().orEmpty()
        val alamat = binding.etAddress.text?.toString()?.trim().orEmpty()
        val nomorTelepon = binding.etPhone.text?.toString()?.trim().orEmpty()
        val logoText = binding.etLogoText.text?.toString()?.trim().orEmpty()
        val footerStruk = binding.etReceiptFooter.text?.toString()?.trim().orEmpty()
        val catatanUsaha = binding.etBusinessNote.text?.toString()?.trim().orEmpty()

        if (namaUsaha.isBlank()) {
            binding.etBusinessName.error = "Nama usaha wajib diisi"
            binding.etBusinessName.requestFocus()
            return
        }

        if (alamat.isBlank()) {
            binding.etAddress.error = "Alamat wajib diisi"
            binding.etAddress.requestFocus()
            return
        }

        if (logoText.length > 8) {
            binding.etLogoText.error = "Logo text maksimal 8 karakter"
            binding.etLogoText.requestFocus()
            return
        }

        setLoading(true)

        docRef.get()
            .addOnSuccessListener { doc ->
                val now = Timestamp.now()

                val data = hashMapOf<String, Any>(
                    "namaUsaha" to namaUsaha,
                    "alamat" to alamat,
                    "nomorTelepon" to nomorTelepon,
                    "logoText" to logoText,
                    "footerStruk" to footerStruk,
                    "catatanUsaha" to catatanUsaha,
                    "diperbaruiPada" to now
                )

                if (!doc.exists()) {
                    data["dibuatPada"] = now
                }

                docRef.set(data)
                    .addOnSuccessListener {
                        setLoading(false)
                        updatePreview()
                        showMessage("Pengaturan usaha berhasil disimpan.")
                    }
                    .addOnFailureListener { e ->
                        setLoading(false)
                        showMessage("Gagal menyimpan pengaturan usaha: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                showMessage("Gagal memeriksa data usaha: ${e.message}")
            }
    }

    private fun clearErrors() {
        binding.etBusinessName.error = null
        binding.etAddress.error = null
        binding.etPhone.error = null
        binding.etLogoText.error = null
        binding.etReceiptFooter.error = null
        binding.etBusinessNote.error = null
    }

    private fun setLoading(isLoading: Boolean) {
        binding.etBusinessName.isEnabled = !isLoading
        binding.etAddress.isEnabled = !isLoading
        binding.etPhone.isEnabled = !isLoading
        binding.etLogoText.isEnabled = !isLoading
        binding.etReceiptFooter.isEnabled = !isLoading
        binding.etBusinessNote.isEnabled = !isLoading
        binding.btnSave.isEnabled = !isLoading
        binding.btnSave.text = if (isLoading) "Menyimpan..." else "Simpan Pengaturan"
    }
}