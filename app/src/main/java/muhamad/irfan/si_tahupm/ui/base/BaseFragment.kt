package muhamad.irfan.si_tahupm.ui.base

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.data.UserRole
import muhamad.irfan.si_tahupm.util.ModalHelper
import com.google.android.material.snackbar.Snackbar

open class BaseFragment(layoutRes: Int) : Fragment(layoutRes) {
    override fun onCreate(savedInstanceState: Bundle?) {
        DemoRepository.init(requireContext().applicationContext)
        super.onCreate(savedInstanceState)
    }

    protected fun showMessage(view: View, message: String) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show()
    }

    protected fun currentUserId(): String {
        return DemoRepository.sessionUser()?.id ?: DemoRepository.loginAs(UserRole.ADMIN).id
    }

    protected fun showDetailModal(title: String, message: String) {
        ModalHelper.showDetailModal(requireContext(), title, message)
    }
}
