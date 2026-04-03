package muhamad.irfan.si_tahu.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.commit
import com.google.firebase.auth.FirebaseAuth
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.ActivityRoleMainBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.ui.login.AktivitasMasuk

class AktivitasUtamaKasir : AktivitasDasar() {
    private lateinit var binding: ActivityRoleMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, AktivitasMasuk::class.java))
            finish()
            return
        }

        binding = ActivityRoleMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(R.menu.menu_bottom_cashier)
        binding.bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_cashier_menu -> {
                    showTab(it.itemId)
                    true
                }
                else -> {
                    Toast.makeText(this, "nunggu update", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }

        val requestedTab = intent.getIntExtra(EXTRA_TAB_ID, R.id.nav_cashier_menu)
        val startTab = R.id.nav_cashier_menu
        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = startTab
        }
    }

    fun openTab(tabId: Int) {
        binding.bottomNavigation.selectedItemId = tabId
    }

    private fun showTab(itemId: Int) {
        val (fragment, title, subtitle) = when (itemId) {
            R.id.nav_cashier_sale -> Triple(FragmenPenjualanKasir(), "Tambah Penjualan", "Kasir")
            R.id.nav_cashier_history -> Triple(FragmenRiwayatKasir(), "Riwayat Penjualan", "Kasir")
            R.id.nav_cashier_menu -> Triple(FragmenMenuKasir(), "Menu Kasir", "Kasir")
            else -> Triple(FragmenDasborKasir(), "Beranda Kasir", "Kasir")
        }
        binding.toolbar.title = title
        binding.toolbar.subtitle = subtitle
        supportFragmentManager.commit {
            replace(binding.container.id, fragment, title)
        }
    }

    companion object {
        private const val EXTRA_TAB_ID = "extra_tab_id"

        fun intent(context: Context, tabId: Int = R.id.nav_cashier_menu): Intent {
            return Intent(context, AktivitasUtamaKasir::class.java).putExtra(EXTRA_TAB_ID, tabId)
        }
    }
}