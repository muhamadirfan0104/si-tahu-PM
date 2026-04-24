package muhamad.irfan.si_tahu.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object Formatter {
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val ribuanFormat = NumberFormat.getNumberInstance(Locale("id", "ID"))
    private val dateTimeParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val dateOnlyParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val longDate = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
    private val shortDate = SimpleDateFormat("dd MMM", Locale("id", "ID"))
    private val shortTime = SimpleDateFormat("HH:mm", Locale("id", "ID"))
    private val longDateTime = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID"))

    init {
        currencyFormat.maximumFractionDigits = 0
        currencyFormat.minimumFractionDigits = 0
        ribuanFormat.maximumFractionDigits = 0
        ribuanFormat.minimumFractionDigits = 0
    }

    fun currency(value: Long): String = currencyFormat.format(value)

    fun ribuan(value: Long): String = ribuanFormat.format(value)

    fun parseRupiah(value: String?): Long {
        if (value.isNullOrBlank()) return 0L

        val clean = value
            .replace("Rp", "", ignoreCase = true)
            .replace(".", "")
            .replace(",", "")
            .replace(" ", "")
            .trim()

        return clean.toLongOrNull() ?: 0L
    }

    fun formatRupiahInput(value: String?): String {
        val angka = parseRupiah(value)
        return if (angka == 0L && value.isNullOrBlank()) "" else ribuan(angka)
    }

    fun parseDate(value: String?): Date {
        if (value.isNullOrBlank()) return Date()
        return try {
            if (value.contains("T")) {
                dateTimeParser.parse(value) ?: Date()
            } else {
                dateOnlyParser.parse(value) ?: Date()
            }
        } catch (_: Exception) {
            Date()
        }
    }

    fun toDateOnly(value: String?): String = dateOnlyParser.format(parseDate(value))
    fun readableDate(value: String?): String = longDate.format(parseDate(value))
    fun readableShortDate(value: String?): String = shortDate.format(parseDate(value))
    fun readableTime(value: String?): String = shortTime.format(parseDate(value))
    fun readableDateTime(value: String?): String = longDateTime.format(parseDate(value))

    fun currentDateOnly(): String = dateOnlyParser.format(Date())
    fun currentTimeOnly(): String = shortTime.format(Date())

    fun isoDate(date: String, time: String): String = "${date}T${time}"

    fun newId(prefix: String): String =
        prefix + "-" + UUID.randomUUID().toString().replace("-", "").take(10)
}