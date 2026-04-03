package muhamad.irfan.si_tahu.ui.user

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.data.PeranPengguna
import muhamad.irfan.si_tahu.databinding.ActivityUserFormBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.util.EkstraAplikasi
import muhamad.irfan.si_tahu.util.AdapterSpinner

class AktivitasFormPengguna : AktivitasDasar() {
    private lateinit var binding: ActivityUserFormBinding
    private val roles = listOf(PeranPengguna.ADMIN, PeranPengguna.KASIR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Pengguna", "Tambah atau edit akun")

        binding.spRole.adapter = AdapterSpinner.stringAdapter(this, roles.map { it.name })
        val editing = RepositoriLokal.getUser(intent.getStringExtra(EkstraAplikasi.EXTRA_USER_ID))
        if (editing != null) {
            binding.etName.setText(editing.name)
            binding.etEmail.setText(editing.email)
            binding.spRole.setSelection(roles.indexOf(editing.role).coerceAtLeast(0))
            binding.etPassword.setText(editing.password)
            binding.cbActive.isChecked = editing.active
        } else {
            binding.cbActive.isChecked = true
        }

        binding.btnSave.setOnClickListener {
            runCatching {
                RepositoriLokal.saveUser(
                    existingId = editing?.id,
                    name = binding.etName.text?.toString().orEmpty(),
                    email = binding.etEmail.text?.toString().orEmpty(),
                    role = roles[binding.spRole.selectedItemPosition],
                    password = binding.etPassword.text?.toString().orEmpty(),
                    active = binding.cbActive.isChecked
                )
            }.onSuccess {
                showMessage("Pengguna berhasil disimpan.")
                startActivity(Intent(this, AktivitasDaftarPengguna::class.java))
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan pengguna")
            }
        }
    }
}
