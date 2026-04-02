package muhamad.irfan.si_tahupm.data

object DemoSeed {
    private const val TODAY_SEED = "2026-03-31"

    private fun iso(date: String, time: String): String = "${date}T${time}"

    fun create(): DemoDatabase {
        return DemoDatabase(
            settings = BusinessSettings(
                businessName = "UMKM Tahu Berkah",
                address = "Jl. Melati No. 18, Bantul, Yogyakarta",
                phone = "0812-3456-7890",
                logoText = "TAHU",
                receiptFooter = "Terima kasih. Tahu fresh setiap hari.",
                note = "Buka 06.00 - 17.00. Melayani ecer, pasar, dan reseller."
            ),
            products = mutableListOf(
                Product(
                    id = "prd-putih",
                    code = "TD001",
                    name = "Tahu Putih",
                    category = "DASAR",
                    unit = "pcs",
                    stock = 127,
                    minStock = 50,
                    active = true,
                    showInCashier = true,
                    photoTone = "green",
                    channels = mutableListOf(
                        ChannelPrice("chn-putih-k", "KASIR", 2000, true, true),
                        ChannelPrice("chn-putih-p", "PASAR", 1800, true, false),
                        ChannelPrice("chn-putih-r", "RESELLER", 1700, true, false)
                    )
                ),
                Product(
                    id = "prd-kuning",
                    code = "TD002",
                    name = "Tahu Kuning",
                    category = "DASAR",
                    unit = "pcs",
                    stock = 300,
                    minStock = 80,
                    active = true,
                    showInCashier = true,
                    photoTone = "gold",
                    channels = mutableListOf(
                        ChannelPrice("chn-kuning-k", "KASIR", 2500, true, true),
                        ChannelPrice("chn-kuning-p", "PASAR", 2200, true, false)
                    )
                ),
                Product(
                    id = "prd-goreng",
                    code = "TO001",
                    name = "Tahu Goreng",
                    category = "OLAHAN",
                    unit = "pcs",
                    stock = 30,
                    minStock = 20,
                    active = true,
                    showInCashier = true,
                    photoTone = "orange",
                    channels = mutableListOf(
                        ChannelPrice("chn-goreng-k", "KASIR", 1000, true, true),
                        ChannelPrice("chn-goreng-p", "PASAR", 800, true, false)
                    )
                ),
                Product(
                    id = "prd-bacem",
                    code = "TO002",
                    name = "Tahu Bacem",
                    category = "OLAHAN",
                    unit = "pcs",
                    stock = 12,
                    minStock = 10,
                    active = true,
                    showInCashier = true,
                    photoTone = "soft",
                    channels = mutableListOf(
                        ChannelPrice("chn-bacem-k", "KASIR", 1500, true, true),
                        ChannelPrice("chn-bacem-p", "PASAR", 1200, true, false)
                    )
                ),
                Product(
                    id = "prd-isi",
                    code = "TO003",
                    name = "Tahu Isi",
                    category = "OLAHAN",
                    unit = "pcs",
                    stock = 24,
                    minStock = 12,
                    active = true,
                    showInCashier = true,
                    photoTone = "blue",
                    channels = mutableListOf(ChannelPrice("chn-isi-k", "KASIR", 2500, true, true))
                ),
                Product(
                    id = "prd-keripik",
                    code = "TO004",
                    name = "Keripik Tahu",
                    category = "OLAHAN",
                    unit = "pack",
                    stock = 16,
                    minStock = 8,
                    active = true,
                    showInCashier = true,
                    photoTone = "gold",
                    channels = mutableListOf(
                        ChannelPrice("chn-keripik-k", "KASIR", 7000, true, true),
                        ChannelPrice("chn-keripik-r", "RESELLER", 6000, true, false)
                    )
                )
            ),
            parameters = mutableListOf(
                ProductionParameter("prm-001", "prd-putih", 98, "Standar 1 masak pagi", true),
                ProductionParameter("prm-002", "prd-kuning", 100, "Standar 1 masak siang", true)
            ),
            users = mutableListOf(
                UserAccount("usr-admin", "Pemilik", "pemilik@tahuberkah.id", UserRole.ADMIN, "admin123", true),
                UserAccount("usr-kasir-1", "Kasir 1", "kasir1@tahuberkah.id", UserRole.KASIR, "kasir123", true),
                UserAccount("usr-kasir-2", "Kasir 2", "kasir2@tahuberkah.id", UserRole.KASIR, "kasir123", true)
            ),
            productionLogs = mutableListOf(
                ProductionLog("PROD-20260331-001", iso(TODAY_SEED, "04:30:00"), "prd-putih", 1, 98, "Masak pagi", "usr-admin"),
                ProductionLog("PROD-20260330-002", iso("2026-03-30", "05:00:00"), "prd-kuning", 2, 200, "Persiapan pasar", "usr-admin")
            ),
            conversions = mutableListOf(
                ConversionLog("KONV-20260331-001", iso(TODAY_SEED, "09:00:00"), "prd-putih", "prd-goreng", 20, 18, "Batch goreng pagi", "usr-admin"),
                ConversionLog("KONV-20260331-002", iso(TODAY_SEED, "10:30:00"), "prd-putih", "prd-bacem", 15, 12, "Batch bacem siang", "usr-admin")
            ),
            expenses = mutableListOf(
                Expense("EXP-20260331-001", iso(TODAY_SEED, "07:10:00"), "Minyak Goreng", 120000, "Pembelian 5 liter", "usr-admin"),
                Expense("EXP-20260331-002", iso(TODAY_SEED, "06:40:00"), "Gas", 80000, "Isi ulang tabung", "usr-admin"),
                Expense("EXP-20260330-003", iso("2026-03-30", "08:15:00"), "Transport Pasar", 40000, "Antar pasar", "usr-admin")
            ),
            sales = mutableListOf(
                Sale(
                    id = "TRX-20260331-001",
                    date = iso(TODAY_SEED, "09:30:00"),
                    source = "KASIR",
                    cashierId = "usr-kasir-1",
                    paymentMethod = "Tunai",
                    cashPaid = 20000,
                    total = 10000,
                    items = mutableListOf(SaleItem("prd-goreng", 10, 1000))
                ),
                Sale(
                    id = "TRX-20260331-002",
                    date = iso(TODAY_SEED, "11:05:00"),
                    source = "KASIR",
                    cashierId = "usr-kasir-1",
                    paymentMethod = "Tunai",
                    cashPaid = 20000,
                    total = 15000,
                    items = mutableListOf(SaleItem("prd-bacem", 5, 1500), SaleItem("prd-goreng", 5, 1000))
                ),
                Sale(
                    id = "REKAP-20260331-001",
                    date = iso(TODAY_SEED, "13:00:00"),
                    source = "PASAR",
                    cashierId = "usr-admin",
                    paymentMethod = "Rekap",
                    cashPaid = 14000,
                    total = 14000,
                    items = mutableListOf(SaleItem("prd-goreng", 10, 800), SaleItem("prd-bacem", 5, 1200))
                ),
                Sale(
                    id = "TRX-20260330-003",
                    date = iso("2026-03-30", "10:10:00"),
                    source = "KASIR",
                    cashierId = "usr-kasir-2",
                    paymentMethod = "QRIS",
                    cashPaid = 24000,
                    total = 24000,
                    items = mutableListOf(
                        SaleItem("prd-putih", 4, 2000),
                        SaleItem("prd-kuning", 4, 2500),
                        SaleItem("prd-goreng", 6, 1000)
                    )
                )
            ),
            stockAdjustments = mutableListOf(
                StockAdjustment("ADJ-20260330-001", iso("2026-03-30", "14:20:00"), "prd-keripik", "add", 6, "Restok kemasan", "usr-admin"),
                StockAdjustment("ADJ-20260331-002", iso(TODAY_SEED, "15:15:00"), "prd-isi", "subtract", 2, "Sample tester", "usr-admin")
            ),
            activities = mutableListOf(
                ActivityLog("act-001", "Produksi Tahu Putih tersimpan 98 pcs.", iso(TODAY_SEED, "04:30:00"), "green"),
                ActivityLog("act-002", "Konversi Tahu Putih menjadi Tahu Goreng berhasil.", iso(TODAY_SEED, "09:00:00"), "gold"),
                ActivityLog("act-003", "Kasir 1 menyelesaikan transaksi TRX-20260331-002.", iso(TODAY_SEED, "11:05:00"), "blue"),
                ActivityLog("act-004", "Rekap pasar PASAR tersimpan Rp 14.000.", iso(TODAY_SEED, "13:00:00"), "orange")
            )
        )
    }
}
