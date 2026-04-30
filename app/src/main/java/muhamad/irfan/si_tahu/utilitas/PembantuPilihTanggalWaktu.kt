package muhamad.irfan.si_tahu.util

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.util.Calendar

/**
 * Utilitas untuk memunculkan pemilih tanggal dan waktu bawaan sistem.
 * Sudah diperbarui dengan tema DeviceDefault agar selaras dengan Dark Mode / Light Mode.
 */
object PembantuPilihTanggalWaktu {

    fun showDatePicker(context: Context, currentDate: String?, dialogTitle: String? = null, onSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance().apply {
            try {
                time = Formatter.parseDate(currentDate)
            } catch (_: Exception) {
                // Jika gagal parsing, biarkan menggunakan waktu saat ini
            }
        }

        DatePickerDialog(
            context,
            // Menambahkan tema ini untuk memperbaiki warna di Mode Gelap/Terang
            android.R.style.Theme_DeviceDefault_Dialog_Alert,
            { _, year, month, dayOfMonth ->
                onSelected(String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            if (!dialogTitle.isNullOrBlank()) setTitle(dialogTitle)
        }.show()
    }

    fun showTimePicker(context: Context, currentDateTime: String?, onSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance().apply {
            try {
                time = Formatter.parseDate(currentDateTime)
            } catch (_: Exception) {
                // Jika gagal parsing, biarkan menggunakan waktu saat ini
            }
        }

        TimePickerDialog(
            context,
            // Menambahkan tema ini untuk memperbaiki warna di Mode Gelap/Terang
            android.R.style.Theme_DeviceDefault_Dialog_Alert,
            { _, hourOfDay, minute ->
                onSelected(String.format("%02d:%02d", hourOfDay, minute))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // true untuk format 24 jam
        ).show()
    }
}