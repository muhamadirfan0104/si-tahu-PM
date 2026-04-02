package muhamad.irfan.si_tahupm.ui.user

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.data.UserRole
import muhamad.irfan.si_tahupm.databinding.ActivityUserFormBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.SpinnerAdapters

class UserFormActivity : BaseActivity() {
    private lateinit var binding: ActivityUserFormBinding
    private val roles = listOf(UserRole.ADMIN, UserRole.KASIR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Pengguna", "Tambah atau edit akun")

        binding.spRole.adapter = SpinnerAdapters.stringAdapter(this, roles.map { it.name })
        val editing = DemoRepository.getUser(intent.getStringExtra(AppExtras.EXTRA_USER_ID))
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
                DemoRepository.saveUser(
                    existingId = editing?.id,
                    name = binding.etName.text?.toString().orEmpty(),
                    email = binding.etEmail.text?.toString().orEmpty(),
                    role = roles[binding.spRole.selectedItemPosition],
                    password = binding.etPassword.text?.toString().orEmpty(),
                    active = binding.cbActive.isChecked
                )
            }.onSuccess {
                showMessage("Pengguna berhasil disimpan.")
                startActivity(Intent(this, UserListActivity::class.java))
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan pengguna")
            }
        }
    }
}
