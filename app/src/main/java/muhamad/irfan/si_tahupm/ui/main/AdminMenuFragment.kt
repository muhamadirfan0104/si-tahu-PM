package muhamad.irfan.si_tahupm.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import muhamad.irfan.si_tahupm.R
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.FragmentAdminMenuBinding
import muhamad.irfan.si_tahupm.ui.base.BaseFragment
import muhamad.irfan.si_tahupm.ui.expense.ExpenseListActivity
import muhamad.irfan.si_tahupm.ui.history.TransactionHistoryActivity
import muhamad.irfan.si_tahupm.ui.parameter.ParameterListActivity
import muhamad.irfan.si_tahupm.ui.price.PriceListActivity
import muhamad.irfan.si_tahupm.ui.product.ProductListActivity
import muhamad.irfan.si_tahupm.ui.report.ReportActivity
import muhamad.irfan.si_tahupm.ui.settings.BusinessSettingsActivity
import muhamad.irfan.si_tahupm.ui.user.UserListActivity

class AdminMenuFragment : BaseFragment(R.layout.fragment_admin_menu) {
    private var _binding: FragmentAdminMenuBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminMenuBinding.bind(view)
        binding.btnProducts.setOnClickListener { startActivity(Intent(requireContext(), ProductListActivity::class.java)) }
        binding.btnPrices.setOnClickListener { startActivity(Intent(requireContext(), PriceListActivity::class.java)) }
        binding.btnParameters.setOnClickListener { startActivity(Intent(requireContext(), ParameterListActivity::class.java)) }
        binding.btnExpenses.setOnClickListener { startActivity(Intent(requireContext(), ExpenseListActivity::class.java)) }
        binding.btnReport.setOnClickListener { startActivity(Intent(requireContext(), ReportActivity::class.java)) }
        binding.btnTransactions.setOnClickListener { startActivity(Intent(requireContext(), TransactionHistoryActivity::class.java)) }
        binding.btnSettings.setOnClickListener { startActivity(Intent(requireContext(), BusinessSettingsActivity::class.java)) }
        binding.btnUsers.setOnClickListener { startActivity(Intent(requireContext(), UserListActivity::class.java)) }
        binding.btnSwitchCashier.setOnClickListener {
            DemoRepository.loginAs(muhamad.irfan.si_tahupm.data.UserRole.KASIR)
            startActivity(CashierMainActivity.intent(requireContext()))
        }
        binding.btnResetDemo.setOnClickListener {
            DemoRepository.resetDemo()
            showMessage(view, "Data dummy berhasil direset.")
        }
        binding.btnLogout.setOnClickListener {
            DemoRepository.logout()
            startActivity(Intent(requireContext(), muhamad.irfan.si_tahupm.ui.login.LoginActivity::class.java))
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
