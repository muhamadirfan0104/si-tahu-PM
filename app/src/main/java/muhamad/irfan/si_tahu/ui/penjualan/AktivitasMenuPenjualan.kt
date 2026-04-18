package muhamad.irfan.si_tahu.ui.penjualan

import android.os.Bundle
import muhamad.irfan.si_tahu.R
import muhamad.irfan.si_tahu.ui.dasar.AktivitasDasar
import muhamad.irfan.si_tahu.ui.utama.AktivitasUtamaAdmin

class AktivitasMenuPenjualan : AktivitasDasar() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireLoginOrRedirect()) return

        startActivity(
            AktivitasUtamaAdmin.intent(
                context = this,
                tabId = R.id.nav_admin_sales,
                clearTop = true
            )
        )
        finish()
    }
}