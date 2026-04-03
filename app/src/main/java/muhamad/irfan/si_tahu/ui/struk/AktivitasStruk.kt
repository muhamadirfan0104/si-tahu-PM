package muhamad.irfan.si_tahu.ui.receipt

import android.os.Bundle
import muhamad.irfan.si_tahu.data.RepositoriLokal
import muhamad.irfan.si_tahu.databinding.ActivityReceiptBinding
import muhamad.irfan.si_tahu.ui.base.AktivitasDasar
import muhamad.irfan.si_tahu.util.EkstraAplikasi

class AktivitasStruk : AktivitasDasar() {
    private lateinit var binding: ActivityReceiptBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiptBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindToolbar(binding.toolbar, "Struk Transaksi", "Preview receipt")

        val saleId = intent.getStringExtra(EkstraAplikasi.EXTRA_SALE_ID).orEmpty()
        val content = RepositoriLokal.buildReceiptText(saleId)
        binding.tvReceipt.text = content
        binding.btnClose.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { sharePlainText("Struk $saleId", content) }
    }
}
