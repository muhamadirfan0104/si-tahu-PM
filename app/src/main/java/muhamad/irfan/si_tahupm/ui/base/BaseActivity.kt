package muhamad.irfan.si_tahupm.ui.base

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.data.UserRole
import muhamad.irfan.si_tahupm.util.ModalHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import java.io.File

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        DemoRepository.init(applicationContext)
        super.onCreate(savedInstanceState)
    }

    protected fun bindToolbar(toolbar: MaterialToolbar, title: String, subtitle: String? = null, showBack: Boolean = true) {
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
        return DemoRepository.sessionUser()?.id ?: DemoRepository.loginAs(UserRole.ADMIN).id
    }

    protected fun showDetailModal(title: String, message: String) {
        ModalHelper.showDetailModal(this, title, message)
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
