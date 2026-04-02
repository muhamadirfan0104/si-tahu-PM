package muhamad.irfan.si_tahupm.ui.expense

import android.content.Intent
import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityExpenseFormBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.util.AppExtras
import muhamad.irfan.si_tahupm.util.Formatters

class ExpenseFormActivity : BaseActivity() {
    private lateinit var binding: ActivityExpenseFormBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpenseFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Form Pengeluaran", "Tambah atau edit pengeluaran")

        val editing = DemoRepository.getExpense(intent.getStringExtra(AppExtras.EXTRA_EXPENSE_ID))
        if (editing != null) {
            binding.etDate.setText(Formatters.toDateOnly(editing.date))
            binding.etCategory.setText(editing.category)
            binding.etAmount.setText(editing.amount.toString())
            binding.etNote.setText(editing.note)
        } else {
            binding.etDate.setText(DemoRepository.latestDateOnly())
        }

        binding.btnSave.setOnClickListener {
            runCatching {
                DemoRepository.saveExpense(
                    existingId = editing?.id,
                    dateOnly = binding.etDate.text?.toString().orEmpty(),
                    category = binding.etCategory.text?.toString().orEmpty(),
                    amount = binding.etAmount.text?.toString()?.toLongOrNull() ?: 0L,
                    note = binding.etNote.text?.toString().orEmpty(),
                    userId = currentUserId()
                )
            }.onSuccess {
                showMessage("Pengeluaran berhasil disimpan.")
                startActivity(Intent(this, ExpenseListActivity::class.java))
                finish()
            }.onFailure {
                showMessage(it.message ?: "Gagal menyimpan pengeluaran")
            }
        }
    }
}
