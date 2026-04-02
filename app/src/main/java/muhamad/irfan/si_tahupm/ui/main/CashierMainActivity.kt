package muhamad.irfan.si_tahupm.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityRoleMainBinding
import muhamad.irfan.si_tahupm.data.UserRole
import muhamad.irfan.si_tahupm.ui.base.BaseActivity

class CashierMainActivity : BaseActivity() {
    private lateinit var binding: ActivityRoleMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (DemoRepository.sessionRole() != UserRole.KASIR) {
            DemoRepository.loginAs(UserRole.KASIR)
        }

        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(R.menu.menu_bottom_cashier)
        binding.bottomNavigation.setOnItemSelectedListener {
            showTab(it.itemId)
            true
        }

        val startTab = intent.getIntExtra(EXTRA_TAB_ID, R.id.nav_cashier_dashboard)
        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = startTab
        }
    }

    fun openTab(tabId: Int) {
        binding.bottomNavigation.selectedItemId = tabId
    }

    private fun showTab(itemId: Int) {
        val (fragment, title, subtitle) = when (itemId) {
            R.id.nav_cashier_sale -> Triple(CashierSaleFragment(), "Tambah Penjualan", "Kasir")
            R.id.nav_cashier_history -> Triple(CashierHistoryFragment(), "Riwayat Penjualan", "Kasir")
            R.id.nav_cashier_menu -> Triple(CashierMenuFragment(), "Menu Kasir", "Kasir")
            else -> Triple(CashierDashboardFragment(), "Beranda Kasir", "Kasir")
        }
        binding.toolbar.title = title
        binding.toolbar.subtitle = subtitle
        supportFragmentManager.commit {
            replace(binding.container.id, fragment, title)
        }
    }

    companion object {
        private const val EXTRA_TAB_ID = "extra_tab_id"

        fun intent(context: Context, tabId: Int = R.id.nav_cashier_dashboard): Intent {
            return Intent(context, CashierMainActivity::class.java).putExtra(EXTRA_TAB_ID, tabId)
        }
    }
}
