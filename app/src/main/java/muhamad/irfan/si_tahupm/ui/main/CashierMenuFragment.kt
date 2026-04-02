package muhamad.irfan.si_tahupm.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.FragmentCashierMenuBinding
import muhamad.irfan.si_tahupm.ui.base.BaseFragment
import muhamad.irfan.si_tahupm.ui.login.LoginActivity
import muhamad.irfan.si_tahupm.ui.settings.BusinessSettingsActivity

class CashierMenuFragment : BaseFragment(R.layout.fragment_cashier_menu) {
    private var _binding: FragmentCashierMenuBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCashierMenuBinding.bind(view)
        binding.btnBusinessSettings.setOnClickListener { startActivity(Intent(requireContext(), BusinessSettingsActivity::class.java)) }
        binding.btnSwitchAdmin.setOnClickListener {
            DemoRepository.loginAs(muhamad.irfan.si_tahupm.data.UserRole.ADMIN)
            startActivity(AdminMainActivity.intent(requireContext()))
        }
        binding.btnResetDemo.setOnClickListener {
            DemoRepository.resetDemo()
            showMessage(view, "Data dummy berhasil direset.")
            refreshHeader()
        }
        binding.btnLogout.setOnClickListener {
            DemoRepository.logout()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHeader()
    }

    private fun refreshHeader() {
        val user = DemoRepository.sessionUser() ?: DemoRepository.loginAs(muhamad.irfan.si_tahupm.data.UserRole.KASIR)
        binding.tvUserName.text = user.name
        binding.tvUserRole.text = user.role.name
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
