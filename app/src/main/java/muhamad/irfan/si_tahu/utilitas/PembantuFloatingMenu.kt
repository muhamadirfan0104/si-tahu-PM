package muhamad.irfan.si_tahu.util

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

object PembantuFloatingMenu {

    fun siapkanLatar(scrim: View) {
        scrim.animate().cancel()
        scrim.alpha = 0f
        scrim.visibility = View.GONE
    }

    fun aturLatar(content: View, scrim: View, terbuka: Boolean, animate: Boolean = true) {
        scrim.animate().cancel()
        if (terbuka) {
            terapkanBlur(content, true)
            if (animate) {
                scrim.visibility = View.VISIBLE
                scrim.alpha = 0f
                scrim.animate()
                    .alpha(1f)
                    .setDuration(180L)
                    .start()
            } else {
                scrim.visibility = View.VISIBLE
                scrim.alpha = 1f
            }
        } else {
            if (animate) {
                scrim.animate()
                    .alpha(0f)
                    .setDuration(140L)
                    .withEndAction {
                        if (scrim.alpha == 0f) {
                            scrim.visibility = View.GONE
                        }
                    }
                    .start()
            } else {
                scrim.alpha = 0f
                scrim.visibility = View.GONE
            }
            terapkanBlur(content, false)
        }
    }

    private fun terapkanBlur(content: View, aktif: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            content.setRenderEffect(
                if (aktif) RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP) else null
            )
        } else {
            content.alpha = if (aktif) 0.98f else 1f
        }
    }
}
