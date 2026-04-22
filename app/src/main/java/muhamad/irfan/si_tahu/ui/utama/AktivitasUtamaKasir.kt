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

class AktivitasUtamaKasir : AktivitasDasar() {

    private lateinit var binding: ActivityRoleMainBinding

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var namaLogin: String = "Kasir"
    private var selectedTabId: Int = R.id.nav_cashier_dashboard
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
        binding.bottomNavigation.inflateMenu(R.menu.menu_bottom_cashier)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val bypassDebounce = suppressBottomNavDebounce
            suppressBottomNavDebounce = false

            if (!bypassDebounce && shouldIgnoreRapidTabChange(minIntervalMs = 120L)) {
                return@setOnItemSelectedListener true
            }

            selectedTabId = item.itemId
            showTab(item.itemId, bypassDebounce = true)
            true
        }

        binding.bottomNavigation.setOnItemReselectedListener { item ->
            showTab(item.itemId, bypassDebounce = true)
        }

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

    private fun handleIntentTab(intent: Intent?, firstCreate: Boolean) {
        val startTab = intent?.getIntExtra(EXTRA_TAB_ID, R.id.nav_cashier_dashboard)
            ?: R.id.nav_cashier_dashboard

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
            namaLogin = "Kasir"
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
                        val namaPengguna = syncedDoc.getString("namaPengguna").orEmpty()
                        namaLogin = if (namaPengguna.isBlank()) "Kasir" else namaPengguna
                        updateToolbarSubtitle()
                    },
                    onError = {
                        namaLogin = "Kasir"
                        updateToolbarSubtitle()
                    }
                )
            },
            onNotFound = {
                namaLogin = "Kasir"
                updateToolbarSubtitle()
            },
            onError = {
                namaLogin = "Kasir"
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
            R.id.nav_cashier_sale -> Pair("Kasir", "tab_cashier_sale")
            R.id.nav_cashier_history -> Pair("Riwayat", "tab_cashier_history")
            R.id.nav_cashier_menu -> Pair("Menu", "tab_cashier_menu")
            else -> Pair("Beranda", "tab_cashier_dashboard")
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
        binding.toolbar.subtitle = if (itemId == R.id.nav_cashier_sale) "" else namaLogin

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
            R.id.nav_cashier_sale -> FragmenKasirSale()
            R.id.nav_cashier_history -> FragmenKasirHistory()
            R.id.nav_cashier_menu -> FragmenMenuKasir()
            else -> FragmenDasborKasir()
        }
    }

    private fun shouldIgnoreRapidTabChange(minIntervalMs: Long): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isRapidTap = (now - lastTabSwitchAt) < minIntervalMs
        if (isRapidTap) return true

        lastTabSwitchAt = now
        return false
    }

    companion object {
        private const val EXTRA_TAB_ID = "extra_tab_id"

        fun intent(
            context: Context,
            tabId: Int = R.id.nav_cashier_dashboard,
            clearTop: Boolean = false
        ): Intent {
            return Intent(context, AktivitasUtamaKasir::class.java)
                .putExtra(EXTRA_TAB_ID, tabId)
                .apply {
                    if (clearTop) {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                }
        }
    }
}