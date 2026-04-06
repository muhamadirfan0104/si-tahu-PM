package muhamad.irfan.si_tahu.util

enum class WarnaBaris { DEFAULT, GREEN, GOLD, ORANGE, BLUE }

data class ItemBaris(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String = "",
    val amount: String = "",
    val actionLabel: String? = null,
    val deleteLabel: String? = null,
    val tone: WarnaBaris = WarnaBaris.DEFAULT
)