package muhamad.irfan.si_tahu.data

object DataAwal {
    fun create(): BasisDataLokal {
        return BasisDataLokal(
            settings = PengaturanUsaha(
                businessName = "",
                address = "",
                phone = "",
                logoText = "",
                receiptFooter = "",
                note = ""
            ),
            products = mutableListOf(),
            parameters = mutableListOf(),
            users = mutableListOf(),
            productionLogs = mutableListOf(),
            conversions = mutableListOf(),
            expenses = mutableListOf(),
            sales = mutableListOf(),
            stockAdjustments = mutableListOf(),
            activities = mutableListOf()
        )
    }
}
