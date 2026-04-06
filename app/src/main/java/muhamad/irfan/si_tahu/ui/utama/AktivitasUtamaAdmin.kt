package muhamad.irfan.si_tahu.ui.utama

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.databinding.ActivityRoleMainBinding
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.masuk.AktivitasMasuk

class AktivitasUtamaAdmin : AktivitasDasar() {

    private lateinit var binding: ActivityRoleMainBinding

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var namaLogin: String = "Admin / Pemilik"
    private var selectedTabId: Int = R.id.nav_admin_dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            startActivity(AktivitasUtamaAdmin.intent(this, R.id.nav_admin_menu, clearTop = true))
            finish()
            return
        }

        binding = ActivityRoleMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.menu.clear()
        binding.bottomNavigation.inflateMenu(R.menu.menu_bottom_admin)

        binding.bottomNavigation.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_admin_dashboard,
                R.id.nav_admin_menu -> {
                    selectedTabId = it.itemId
                    showTab(it.itemId)
                    true
                }
                else -> false
            }
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
        binding.bottomNavigation.selectedItemId = tabId
    }

    private fun handleIntentTab(intent: Intent?, firstCreate: Boolean) {
        val startTab = intent?.getIntExtra(EXTRA_TAB_ID, R.id.nav_admin_dashboard)
            ?: R.id.nav_admin_dashboard

        selectedTabId = startTab

        if (firstCreate) {
            binding.bottomNavigation.selectedItemId = startTab
        } else {
            if (binding.bottomNavigation.selectedItemId != startTab) {
                binding.bottomNavigation.selectedItemId = startTab
            } else {
                showTab(startTab)
            }
        }
    }

    private fun loadNamaLogin() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            namaLogin = "Admin / Pemilik"
            updateToolbarSubtitle()
            return
        }

        firestore.collection("pengguna")
            .whereEqualTo("authUid", uid)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                val namaPengguna = doc?.getString("namaPengguna").orEmpty()

                namaLogin = if (namaPengguna.isBlank()) "Admin / Pemilik" else namaPengguna
                updateToolbarSubtitle()
            }
            .addOnFailureListener {
                namaLogin = "Admin / Pemilik"
                updateToolbarSubtitle()
            }
    }

    private fun updateToolbarSubtitle() {
        binding.toolbar.subtitle = namaLogin
    }

    private fun showTab(itemId: Int) {
        val (fragment, title) = when (itemId) {
            R.id.nav_admin_menu -> Pair(FragmenMenuAdmin(), "Menu")
            else -> Pair(FragmenDasborAdmin(), "Beranda")
        }

        binding.toolbar.title = title
        binding.toolbar.subtitle = namaLogin

        supportFragmentManager.commit {
            replace(binding.container.id, fragment, title)
        }
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