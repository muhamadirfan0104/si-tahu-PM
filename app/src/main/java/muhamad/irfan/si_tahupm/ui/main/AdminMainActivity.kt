package muhamad.irfan.si_tahupm.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityRoleMainBinding
import muhamad.irfan.si_tahupm.data.UserRole
import muhamad.irfan.si_tahupm.ui.base.BaseActivity

class AdminMainActivity : BaseActivity() {
    private lateinit var binding: ActivityRoleMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (DemoRepository.sessionRole() != UserRole.ADMIN) {
            DemoRepository.loginAs(UserRole.ADMIN)
        }

        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(R.menu.menu_bottom_admin)
        binding.bottomNavigation.setOnItemSelectedListener {
            showTab(it.itemId)
            true
        }

        val startTab = intent.getIntExtra(EXTRA_TAB_ID, R.id.nav_admin_dashboard)
        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = startTab
        }
    }

    fun openTab(tabId: Int) {
        binding.bottomNavigation.selectedItemId = tabId
    }

    private fun showTab(itemId: Int) {
        val (fragment, title, subtitle) = when (itemId) {
            R.id.nav_admin_production -> Triple(ProductionMenuFragment(), "Produksi", "Admin / Pemilik")
            R.id.nav_admin_sales -> Triple(SalesMenuFragment(), "Penjualan", "Admin / Pemilik")
            R.id.nav_admin_stock -> Triple(StockListFragment(), "Stok Produk", "Admin / Pemilik")
            R.id.nav_admin_menu -> Triple(AdminMenuFragment(), "Menu Admin", "Admin / Pemilik")
            else -> Triple(AdminDashboardFragment(), "Beranda", "Admin / Pemilik")
        }
        binding.toolbar.title = title
        binding.toolbar.subtitle = subtitle
        supportFragmentManager.commit {
            replace(binding.container.id, fragment, title)
        }
    }

    companion object {
        private const val EXTRA_TAB_ID = "extra_tab_id"

        fun intent(context: Context, tabId: Int = R.id.nav_admin_dashboard): Intent {
            return Intent(context, AdminMainActivity::class.java).putExtra(EXTRA_TAB_ID, tabId)
        }
    }
}
