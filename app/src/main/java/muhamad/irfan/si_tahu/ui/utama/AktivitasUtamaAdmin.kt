package muhamad.irfan.si_tahu.ui.utama

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.data.PenggunaFirestoreCompat
import muhamad.irfan.si_tahu.databinding.ActivityRoleMainBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.masuk.AktivitasMasuk

class AktivitasUtamaAdmin : AktivitasDasar() {

    private lateinit var binding: ActivityRoleMainBinding

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var namaLogin: String = "Admin / Pemilik"
    private var selectedTabId: Int = R.id.nav_admin_dashboard
    private var lastTabSwitchAt: Long = 0L
    private var suppressBottomNavDebounce: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            startActivity(Intent(this, AktivitasMasuk::class.java))
            finish()
            return
        }

        binding = ActivityRoleMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(R.menu.menu_bottom_admin)

        setupBottomNavigation()
        handleIntentTab(intent, savedInstanceState == null)
        loadNamaLogin()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentTab(intent, false)
    }

    fun openTab(tabId: Int) {
        if (binding.bottomNavigation.selectedItemId == tabId) {
            showTab(tabId, bypassDebounce = true)
            return
        }

        if (shouldIgnoreRapidTabChange(minIntervalMs = 120L)) return

        suppressBottomNavDebounce = true
        binding.bottomNavigation.selectedItemId = tabId
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val bypassDebounce = suppressBottomNavDebounce
            suppressBottomNavDebounce = false

            if (!bypassDebounce && shouldIgnoreRapidTabChange(minIntervalMs = 120L)) {
                return@setOnItemSelectedListener true
            }

            showTab(item.itemId, bypassDebounce = true)
            true
        }

        binding.bottomNavigation.setOnItemReselectedListener { item ->
            showTab(item.itemId, bypassDebounce = true)
        }
    }

    private fun handleIntentTab(intent: Intent?, firstCreate: Boolean) {
        val startTab = intent?.getIntExtra(EXTRA_TAB_ID, R.id.nav_admin_dashboard)
            ?: R.id.nav_admin_dashboard

        selectedTabId = startTab

        if (firstCreate) {
            suppressBottomNavDebounce = true
            binding.bottomNavigation.selectedItemId = startTab
        } else if (binding.bottomNavigation.selectedItemId != startTab) {
            openTab(startTab)
        } else {
            showTab(startTab, bypassDebounce = true)
        }
    }

    private fun loadNamaLogin() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            namaLogin = "Admin / Pemilik"
            updateToolbarSubtitle()
            return
        }

        PenggunaFirestoreCompat.findByAuthUid(
            firestore = firestore,
            authUid = uid,
            onFound = { doc ->
                PenggunaFirestoreCompat.migrateLegacyDocIfNeeded(
                    firestore = firestore,
                    doc = doc,
                    authUid = uid,
                    onComplete = { syncedDoc ->
                        val namaPengguna = firstNonBlank(
                            syncedDoc.getString("namaPengguna"),
                            syncedDoc.getString("namaLengkap"),
                            syncedDoc.getString("nama"),
                            auth.currentUser?.displayName,
                            auth.currentUser?.email,
                            "Admin / Pemilik"
                        )

                        namaLogin = namaPengguna
                        updateToolbarSubtitle()
                    },
                    onError = {
                        namaLogin = firstNonBlank(
                            auth.currentUser?.displayName,
                            auth.currentUser?.email,
                            "Admin / Pemilik"
                        )
                        updateToolbarSubtitle()
                    }
                )
            },
            onNotFound = {
                namaLogin = firstNonBlank(
                    auth.currentUser?.displayName,
                    auth.currentUser?.email,
                    "Admin / Pemilik"
                )
                updateToolbarSubtitle()
            },
            onError = {
                namaLogin = firstNonBlank(
                    auth.currentUser?.displayName,
                    auth.currentUser?.email,
                    "Admin / Pemilik"
                )
                updateToolbarSubtitle()
            }
        )
    }

    private fun updateToolbarSubtitle() {
        binding.toolbar.subtitle = namaLogin
    }

    private fun showTab(itemId: Int, bypassDebounce: Boolean = false) {
        if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return
        if (!bypassDebounce && shouldIgnoreRapidTabChange(minIntervalMs = 120L)) return

        val (title, tag) = when (itemId) {
            R.id.nav_admin_dashboard -> Pair("Beranda", "tab_admin_dashboard")
            R.id.nav_admin_production -> Pair("Produksi", "tab_admin_production")
            R.id.nav_admin_sales -> Pair("Penjualan", "tab_admin_sales")
            R.id.nav_admin_stock -> Pair("Stok", "tab_admin_stock")
            R.id.nav_admin_menu -> Pair("Menu", "tab_admin_menu")
            else -> Pair("Beranda", "tab_admin_dashboard")
        }

        val fm = supportFragmentManager
        val currentVisible = fm.primaryNavigationFragment ?: fm.findFragmentById(binding.container.id)
        if (selectedTabId == itemId && currentVisible?.tag == tag && currentVisible.isVisible) {
            binding.toolbar.title = title
            binding.toolbar.subtitle = namaLogin
            return
        }

        val target = fm.findFragmentByTag(tag) ?: createFragmentFor(itemId)

        selectedTabId = itemId
        binding.toolbar.title = title
        binding.toolbar.subtitle = namaLogin

        fm.commit {
            setReorderingAllowed(true)
            fm.fragments
                .filter { it.id == binding.container.id && it.isAdded && !it.isHidden }
                .forEach { hide(it) }

            if (target.isAdded) {
                show(target)
            } else {
                add(binding.container.id, target, tag)
            }
            setPrimaryNavigationFragment(target)
        }
    }

    private fun createFragmentFor(itemId: Int): Fragment {
        return when (itemId) {
            R.id.nav_admin_production -> FragmenProduksi()
            R.id.nav_admin_sales -> FragmenPenjualan()
            R.id.nav_admin_stock -> FragmenStok()
            R.id.nav_admin_menu -> FragmenMenuAdmin()
            else -> FragmenDasborAdmin()
        }
    }

    private fun shouldIgnoreRapidTabChange(minIntervalMs: Long): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isRapidTap = (now - lastTabSwitchAt) < minIntervalMs
        if (isRapidTap) return true

        lastTabSwitchAt = now
        return false
    }

    private fun firstNonBlank(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    }

    companion object {
        private const val EXTRA_TAB_ID = "extra_tab_id"

        fun intent(
            context: Context,
            tabId: Int = R.id.nav_admin_dashboard,
            clearTop: Boolean = false
        ): Intent {
            return Intent(context, AktivitasUtamaAdmin::class.java)
                .putExtra(EXTRA_TAB_ID, tabId)
                .apply {
                    if (clearTop) {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                }
        }
    }
}
