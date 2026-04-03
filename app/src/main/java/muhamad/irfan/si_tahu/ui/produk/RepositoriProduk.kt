package muhamad.irfan.si_tahu.data.product

interface RepositoriProduk {
    suspend fun getAllProducts(): List<ItemProduk>
    suspend fun getCashierProducts(): List<ItemProduk>
}