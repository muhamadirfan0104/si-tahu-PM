package muhamad.irfan.si_tahupm.util

enum class RowTone { DEFAULT, GREEN, GOLD, ORANGE, BLUE }

data class RowItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String = "",
    val amount: String = "",
    val actionLabel: String? = null,
    val tone: RowTone = RowTone.DEFAULT
)
