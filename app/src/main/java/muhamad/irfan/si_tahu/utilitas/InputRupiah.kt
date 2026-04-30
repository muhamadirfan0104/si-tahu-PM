package muhamad.irfan.si_tahu.util

import android.text.Editable
import android.text.TextWatcher
import muhamad.irfan.si_tahu.databinding.ComposeEditTextState
import androidx.compose.ui.text.input.KeyboardType

object InputRupiah {

    fun pasang(editText: ComposeEditTextState) {
        editText.keyboardType = KeyboardType.Number
        editText.addTextChangedListener(object : TextWatcher {
            private var sedangFormat = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (sedangFormat) return
                val raw = s?.toString().orEmpty()
                if (raw.isBlank()) return
                sedangFormat = true
                val formatted = Formatter.formatRupiahInput(raw)
                if (formatted != raw) {
                    editText.setText(formatted)
                    editText.setSelection(formatted.length)
                }
                sedangFormat = false
            }
        })
    }

    fun ambilNilai(editText: ComposeEditTextState): Long = Formatter.parseRupiah(editText.text?.toString())

    fun setNilai(editText: ComposeEditTextState, value: Long) {
        editText.setText(if (value <= 0L) "" else Formatter.ribuan(value))
    }

}
