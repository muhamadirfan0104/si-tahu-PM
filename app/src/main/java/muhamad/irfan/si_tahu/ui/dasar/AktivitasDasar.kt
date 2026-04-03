package muhamad.irfan.si_tahu.ui.base

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.ui.login.AktivitasMasuk
import muhamad.irfan.si_tahu.util.PembantuModal
import muhamad.irfan.si_tahu.util.PembantuCetak
import java.io.File

open class AktivitasDasar : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        RepositoriLokal.init(applicationContext)
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.screen_bg_light)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.card_surface)
    }

    override fun onContentChanged() {
        super.onContentChanged()
        applySystemBarInsets()
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(android.R.id.content) ?: return
        val toolbar = root.findViewById<MaterialToolbar?>(R.id.toolbar)
        val bottomNavigation = root.findViewById<BottomNavigationView?>(R.id.bottomNavigation)

        if (toolbar != null) {
            val initialTop = toolbar.paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(top = initialTop + systemBars.top)
                insets
            }
            ViewCompat.requestApplyInsets(toolbar)
        } else {
            val initialTop = root.paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(top = initialTop + systemBars.top)
                insets
            }
            ViewCompat.requestApplyInsets(root)
        }

        if (bottomNavigation != null) {
            val initialBottom = bottomNavigation.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(bottom = initialBottom + systemBars.bottom)
                insets
            }
            ViewCompat.requestApplyInsets(bottomNavigation)
        }
    }

    protected fun bindToolbar(
        toolbar: MaterialToolbar,
        title: String,
        subtitle: String? = null,
        showBack: Boolean = true
    ) {
        toolbar.title = title
        toolbar.subtitle = subtitle
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(showBack)
        toolbar.setNavigationOnClickListener {
            if (showBack) onBackPressedDispatcher.onBackPressed()
        }
    }

    protected fun showMessage(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }

    protected fun currentUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    }

    protected fun requireLoginOrRedirect(): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) return true

        startActivity(Intent(this, AktivitasMasuk::class.java))
        finish()
        return false
    }

    protected fun showDetailModal(
        title: String,
        message: String,
        neutralLabel: String? = null,
        onNeutral: (() -> Unit)? = null,
        negativeLabel: String? = null,
        onNegative: (() -> Unit)? = null,
        monospace: Boolean = true
    ) {
        PembantuModal.showDetailModal(
            context = this,
            title = title,
            message = message,
            neutralLabel = neutralLabel,
            onNeutral = onNeutral,
            negativeLabel = negativeLabel,
            onNegative = onNegative,
            monospace = monospace
        )
    }

    protected fun showConfirmationModal(
        title: String,
        message: String,
        confirmLabel: String = "Hapus",
        onConfirm: () -> Unit
    ) {
        PembantuModal.showConfirmationModal(this, title, message, confirmLabel, onConfirm)
    }

    protected fun showReceiptModal(
        title: String,
        receiptText: String,
        printLabel: String = "Cetak Struk"
    ) {
        showDetailModal(
            title = title,
            message = receiptText,
            neutralLabel = "Bagikan",
            onNeutral = { sharePlainText(title, receiptText) },
            negativeLabel = printLabel,
            onNegative = { PembantuCetak.printPlainText(this, title, receiptText) },
            monospace = true
        )
    }

    protected fun sharePlainText(title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    protected fun shareCacheFile(title: String, fileName: String, mimeType: String, content: String) {
        val file = File(cacheDir, fileName)
        file.writeText(content)
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
    }
}