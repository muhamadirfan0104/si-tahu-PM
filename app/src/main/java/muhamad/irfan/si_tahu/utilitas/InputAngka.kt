package muhamad.irfan.si_tahu.util

import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.textfield.TextInputEditText

object InputAngka {

    fun pasang(editText: TextInputEditText, desimal: Boolean = false) {
        editText.addTextChangedListener(object : TextWatcher {
            private var sedangFormat = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (sedangFormat) return

                val raw = s?.toString().orEmpty()
                if (raw.isBlank()) return

                sedangFormat = true
                val formatted = if (desimal) {
                    formatDesimalInput(raw)
                } else {
                    formatInput(raw)
                }

                if (formatted != raw) {
                    editText.setText(formatted)
                    editText.setSelection(formatted.length)
                }

                sedangFormat = false
            }
        })
    }

    fun ambilLong(editText: TextInputEditText): Long {
        return parseLong(editText.text?.toString())
    }

    fun ambilInt(editText: TextInputEditText): Int {
        return ambilLong(editText).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun ambilDouble(editText: TextInputEditText): Double {
        return parseDouble(editText.text?.toString())
    }

    fun setNilai(editText: TextInputEditText, value: Long) {
        editText.setText(if (value <= 0L) "" else Formatter.ribuan(value))
    }

    fun setNilai(editText: TextInputEditText, value: Int) {
        setNilai(editText, value.toLong())
    }

    fun setNilaiDesimal(editText: TextInputEditText, value: Double) {
        if (value <= 0.0) {
            editText.setText("")
            return
        }

        val nilaiBulat = value.toLong()
        val pecahan = value - nilaiBulat
        val text = if (pecahan == 0.0) {
            Formatter.ribuan(nilaiBulat)
        } else {
            val pecahanText = value.toString()
                .substringAfter(".", "")
                .trimEnd('0')
            "${Formatter.ribuan(nilaiBulat)},$pecahanText"
        }
        editText.setText(text)
    }

    fun parseLong(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val clean = value
            .replace(".", "")
            .replace(",", "")
            .replace(" ", "")
            .trim()
        return clean.toLongOrNull() ?: 0L
    }

    fun parseDouble(value: String?): Double {
        if (value.isNullOrBlank()) return 0.0

        val raw = value.trim().replace(" ", "")
        val separatorIndex = lastDecimalSeparatorIndex(raw)
        val normalized = if (separatorIndex >= 0) {
            val integerPart = raw.substring(0, separatorIndex).replace(".", "").replace(",", "")
            val decimalPart = raw.substring(separatorIndex + 1).replace(".", "").replace(",", "")
            "$integerPart.$decimalPart"
        } else {
            raw.replace(".", "").replace(",", "")
        }

        return normalized.toDoubleOrNull() ?: 0.0
    }

    fun formatInput(value: String?): String {
        val angka = parseLong(value)
        return if (angka == 0L && value.isNullOrBlank()) "" else Formatter.ribuan(angka)
    }

    private fun formatDesimalInput(value: String?): String {
        if (value.isNullOrBlank()) return ""

        val raw = value.trim().replace(" ", "")
        val separatorIndex = lastDecimalSeparatorIndex(raw)

        if (separatorIndex >= 0) {
            val integerRaw = raw.substring(0, separatorIndex)
            val decimalRaw = raw.substring(separatorIndex + 1)
                .replace(".", "")
                .replace(",", "")

            val integerValue = integerRaw
                .replace(".", "")
                .replace(",", "")
                .ifBlank { "0" }
                .toLongOrNull() ?: 0L

            val separator = ","
            return "${Formatter.ribuan(integerValue)}$separator$decimalRaw"
        }

        val angka = parseLong(raw)
        return if (angka == 0L && raw.isBlank()) "" else Formatter.ribuan(angka)
    }

    private fun lastDecimalSeparatorIndex(value: String): Int {
        val lastComma = value.lastIndexOf(',')
        if (lastComma >= 0) return lastComma

        val lastDot = value.lastIndexOf('.')
        if (lastDot < 0) return -1

        val separatorCount = value.count { it == '.' || it == ',' }
        val digitsAfter = value.substring(lastDot + 1).count { it.isDigit() }

        // Untuk field desimal, titik dianggap desimal hanya untuk input pendek seperti 0.5 atau 10.25.
        // Nilai 1.000, 10.000, atau input lanjutan 1.0000 tetap dianggap ribuan.
        return if (separatorCount == 1 && digitsAfter in 0..2) lastDot else -1
    }
}
