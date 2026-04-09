package muhamad.irfan.si_tahu.util

enum class WarnaBaris { DEFAULT, GREEN, GOLD, ORANGE, BLUE, RED }

data class ItemBaris(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String = "",
    val amount: String = "",
    val priceStatus: String = "",
    val parameterStatus: String = "",
    val actionLabel: String? = null,
    val editLabel: String? = null,
    val deleteLabel: String? = null,
    val tone: WarnaBaris = WarnaBaris.DEFAULT,
    val priceTone: WarnaBaris = WarnaBaris.DEFAULT,
    val parameterTone: WarnaBaris = WarnaBaris.DEFAULT
)
