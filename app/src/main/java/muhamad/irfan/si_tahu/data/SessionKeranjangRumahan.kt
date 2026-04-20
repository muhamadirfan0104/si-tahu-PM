package muhamad.irfan.si_tahu.data

object SessionKeranjangRumahan {

    private val items = mutableListOf<ItemKeranjang>()
    private var lastTouchedProductId: String? = null

    fun getItems(): List<ItemKeranjang> = items.toList()

    fun setItems(newItems: List<ItemKeranjang>) {
        items.clear()
        items.addAll(newItems.map { it.copy() })
        lastTouchedProductId = items.lastOrNull()?.productId
    }

    fun clear() {
        items.clear()
        lastTouchedProductId = null
    }

    fun isEmpty(): Boolean = items.isEmpty()

    fun totalQty(): Int = items.sumOf { it.qty }

    fun totalAmount(): Long = items.sumOf { it.qty.toLong() * it.price }

    fun currentFocusProductId(): String? {
        val active = lastTouchedProductId
        return if (active != null && items.any { it.productId == active }) {
            active
        } else {
            items.lastOrNull()?.productId
        }
    }

    fun currentFocusQty(): Int {
        val focusId = currentFocusProductId() ?: return 0
        return items.firstOrNull { it.productId == focusId }?.qty ?: 0
    }

    fun addOrIncrease(productId: String, price: Long, maxStock: Int): Boolean {
        val current = items.firstOrNull { it.productId == productId }
        val nextQty = (current?.qty ?: 0) + 1

        if (nextQty > maxStock) return false

        if (current == null) {
            items += ItemKeranjang(productId, 1, price)
        } else {
            current.qty = nextQty
        }

        lastTouchedProductId = productId
        return true
    }

    fun changeQty(productId: String, delta: Int, maxStock: Int): Boolean {
        val current = items.firstOrNull { it.productId == productId } ?: return false
        val nextQty = current.qty + delta

        if (nextQty <= 0) {
            items.removeAll { it.productId == productId }
            lastTouchedProductId = items.lastOrNull()?.productId
            return true
        }

        if (nextQty > maxStock) return false

        current.qty = nextQty
        lastTouchedProductId = productId
        return true
    }

    fun increaseFocused(getStock: (String) -> Int): Boolean {
        val focusId = currentFocusProductId() ?: return false
        val current = items.firstOrNull { it.productId == focusId } ?: return false
        val maxStock = getStock(focusId)
        return addOrIncrease(focusId, current.price, maxStock)
    }

    fun decreaseFocused(): Boolean {
        val focusId = currentFocusProductId() ?: return false
        val current = items.firstOrNull { it.productId == focusId } ?: return false
        return changeQty(focusId, -1, current.qty)
    }

    fun remove(productId: String) {
        items.removeAll { it.productId == productId }
        if (lastTouchedProductId == productId) {
            lastTouchedProductId = items.lastOrNull()?.productId
        }
    }
}