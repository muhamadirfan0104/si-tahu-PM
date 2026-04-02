package muhamad.irfan.si_tahupm.ui.login

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.data.UserAccount
import muhamad.irfan.si_tahupm.data.UserRole
import muhamad.irfan.si_tahupm.databinding.ActivityLoginBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.ui.main.AdminMainActivity
import muhamad.irfan.si_tahupm.ui.main.CashierMainActivity

class LoginActivity : BaseActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val settings = DemoRepository.db().settings
        binding.tvLogo.text = settings.logoText
        binding.tvTitle.text = settings.businessName

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            val user = DemoRepository.login(email, password)
            if (user == null) {
                showMessage("Login gagal. Cek email atau password demo.")
            } else {
                openHome(user)
            }
        }

        binding.btnAdminDemo.setOnClickListener {
            openHome(DemoRepository.loginAs(UserRole.ADMIN))
        }

        binding.btnCashierDemo.setOnClickListener {
            openHome(DemoRepository.loginAs(UserRole.KASIR))
        }
    }

    private fun openHome(user: UserAccount) {
        val intent = when (user.role) {
            UserRole.ADMIN -> Intent(this, AdminMainActivity::class.java)
            UserRole.KASIR -> Intent(this, CashierMainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}
