package muhamad.irfan.si_tahu.ui.settings

import android.os.Bundle
import androidx.core.widget.addTextChangedListener
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.databinding.ActivityBusinessSettingsBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar

class AktivitasPengaturanUsaha : AktivitasDasar() {
    private lateinit var binding: ActivityBusinessSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusinessSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Pengaturan Usaha", "Profil usaha dan preview")

        val settings = RepositoriLokal.db().settings
        binding.etBusinessName.setText(settings.businessName)
        binding.etAddress.setText(settings.address)
        binding.etPhone.setText(settings.phone)
        binding.etLogoText.setText(settings.logoText)
        binding.etReceiptFooter.setText(settings.receiptFooter)
        binding.etBusinessNote.setText(settings.note)
        refreshPreview()

        listOf(
            binding.etBusinessName,
            binding.etAddress,
            binding.etPhone,
            binding.etLogoText,
            binding.etReceiptFooter,
            binding.etBusinessNote
        ).forEach { editText ->
            editText.addTextChangedListener { refreshPreview() }
        }

        binding.btnSave.setOnClickListener {
            RepositoriLokal.saveSettings(
                businessName = binding.etBusinessName.text?.toString().orEmpty(),
                address = binding.etAddress.text?.toString().orEmpty(),
                phone = binding.etPhone.text?.toString().orEmpty(),
                logoText = binding.etLogoText.text?.toString().orEmpty(),
                receiptFooter = binding.etReceiptFooter.text?.toString().orEmpty(),
                note = binding.etBusinessNote.text?.toString().orEmpty()
            )
            showMessage("Pengaturan usaha berhasil disimpan.")
        }
    }

    private fun refreshPreview() {
        binding.tvBusinessPreview.text = binding.etBusinessName.text?.toString().orEmpty()
        binding.tvBusinessAddressPreview.text = binding.etAddress.text?.toString().orEmpty()
    }
}
