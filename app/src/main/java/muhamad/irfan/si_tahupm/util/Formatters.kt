package muhamad.irfan.si_tahupm.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object Formatters {
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val dateTimeParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    private val dateOnlyParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val longDate = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
    private val shortDate = SimpleDateFormat("dd MMM", Locale("id", "ID"))
    private val shortTime = SimpleDateFormat("HH:mm", Locale("id", "ID"))
    private val longDateTime = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID"))

    fun currency(value: Long): String = currencyFormat.format(value)

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

    fun toDateOnly(value: String?): String {
        val date = parseDate(value)
        return dateOnlyParser.format(date)
    }

    fun readableDate(value: String?): String = longDate.format(parseDate(value))

    fun readableShortDate(value: String?): String = shortDate.format(parseDate(value))

    fun readableTime(value: String?): String = shortTime.format(parseDate(value))

    fun readableDateTime(value: String?): String = longDateTime.format(parseDate(value))

    fun isoDate(date: String, time: String): String = "${date}T${time}"

    fun newId(prefix: String): String = prefix + "-" + UUID.randomUUID().toString().replace("-", "").take(10)
}
