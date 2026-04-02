package muhamad.irfan.si_tahupm.ui.receipt

import android.os.Bundle
import muhamad.irfan.si_tahupm.data.DemoRepository
import muhamad.irfan.si_tahupm.databinding.ActivityReceiptBinding
import muhamad.irfan.si_tahupm.ui.base.BaseActivity
import muhamad.irfan.si_tahupm.util.AppExtras

class ReceiptActivity : BaseActivity() {
    private lateinit var binding: ActivityReceiptBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Struk Transaksi", "Preview receipt")

        val saleId = intent.getStringExtra(AppExtras.EXTRA_SALE_ID).orEmpty()
        val content = DemoRepository.buildReceiptText(saleId)
        binding.tvReceipt.text = content
        binding.btnClose.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { sharePlainText("Struk $saleId", content) }
    }
}
