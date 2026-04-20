package muhamad.irfan.si_tahu.util

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.util.Calendar

object PembantuPilihTanggalWaktu {
    fun showDatePicker(context: Context, currentDate: String?, onSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance().apply {
            try {
                time = Formatter.parseDate(currentDate)
            } catch (_: Exception) {
            }
        }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onSelected(String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showTimePicker(context: Context, currentDateTime: String?, onSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance().apply {
            try {
                time = Formatter.parseDate(currentDateTime)
            } catch (_: Exception) {
            }
        }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onSelected(String.format("%02d:%02d", hourOfDay, minute))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }
}
