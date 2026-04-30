package muhamad.irfan.si_tahu.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SessionKeranjangRumahan {

    private const val PREF_NAME = "si_tahu_keranjang_rumahan"
    private const val KEY_ITEMS = "items"
    private var appContext: Context? = null

    // 1. Variabel internal keranjang
    private val _items = mutableListOf<ItemKeranjang>()

    // 2. Akses baca ke komponen UI
    val items: List<ItemKeranjang> get() = _items.toList()

    private var lastTouchedProductId: String? = null

    // --- SISTEM LISTENER UNTUK MEMICU PEMBARUAN LAYAR KASIR ---
    private val listeners = mutableListOf<() -> Unit>()
    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun triggerUIRefresh() { listeners.forEach { it.invoke() } }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (_items.isNotEmpty()) return
        val json = appContext
            ?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_ITEMS, null)
            .orEmpty()
        if (json.isBlank()) return
        runCatching {
            val array = JSONArray(json)
            _items.clear()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val productId = item.optString("productId")
                val qty = item.optInt("qty", 0)
                val price = item.optLong("price", 0L)
                if (productId.isNotBlank() && qty > 0 && price > 0L) {
                    _items += ItemKeranjang(productId, qty, price)
                }
            }
            lastTouchedProductId = _items.lastOrNull()?.productId
        }
    }

    private fun persist() {
        val context = appContext ?: return
        val array = JSONArray()
        _items.forEach { item ->
            array.put(
                JSONObject()
                    .put("productId", item.productId)
                    .put("qty", item.qty)
                    .put("price", item.price)
            )
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }
    // ----------------------------------------------------------

    fun setItems(newItems: List<ItemKeranjang>) {
        _items.clear()
        _items.addAll(newItems.map { it.copy() })
        lastTouchedProductId = _items.lastOrNull()?.productId
        persist()
        triggerUIRefresh()
    }

    fun clear() {
        _items.clear()
        lastTouchedProductId = null
        persist()
        triggerUIRefresh()
    }

    fun isEmpty(): Boolean = _items.isEmpty()

    fun totalQty(): Int = _items.sumOf { it.qty }

    fun totalAmount(): Long = _items.sumOf { it.qty.toLong() * it.price }

    fun currentFocusProductId(): String? {
        val active = lastTouchedProductId
        return if (active != null && _items.any { it.productId == active }) {
            active
        } else {
            _items.lastOrNull()?.productId
        }
    }

    fun currentFocusQty(): Int {
        val focusId = currentFocusProductId() ?: return 0
        return _items.firstOrNull { it.productId == focusId }?.qty ?: 0
    }

    // --- DIPERBAIKI: Susunan (productId, qty, price) ---
    fun addOrIncrease(productId: String, price: Long, maxStock: Int): Boolean {
        val current = _items.firstOrNull { it.productId == productId }
        val nextQty = (current?.qty ?: 0) + 1

        if (nextQty > maxStock) return false

        if (current == null) {
            _items += ItemKeranjang(productId, 1, price)
        } else {
            current.qty = nextQty
        }

        lastTouchedProductId = productId
        persist()
        triggerUIRefresh()
        return true
    }

    fun changeQty(productId: String, delta: Int, maxStock: Int): Boolean {
        val current = _items.firstOrNull { it.productId == productId } ?: return false
        val nextQty = current.qty + delta

        if (nextQty <= 0) {
            _items.removeAll { it.productId == productId }
            lastTouchedProductId = _items.lastOrNull()?.productId
            persist()
            triggerUIRefresh()
            return true
        }

        if (nextQty > maxStock) return false

        current.qty = nextQty
        lastTouchedProductId = productId
        persist()
        triggerUIRefresh()
        return true
    }

    fun increaseFocused(getStock: (String) -> Int): Boolean {
        val focusId = currentFocusProductId() ?: return false
        val current = _items.firstOrNull { it.productId == focusId } ?: return false
        val maxStock = getStock(focusId)
        return addOrIncrease(focusId, current.price, maxStock)
    }

    fun decreaseFocused(): Boolean {
        val focusId = currentFocusProductId() ?: return false
        val current = _items.firstOrNull { it.productId == focusId } ?: return false
        return changeQty(focusId, -1, current.qty)
    }

    fun remove(productId: String) {
        _items.removeAll { it.productId == productId }
        if (lastTouchedProductId == productId) {
            lastTouchedProductId = _items.lastOrNull()?.productId
        }
        persist()
        triggerUIRefresh()
    }

    // === FUNGSI BARU UNTUK LAYAR COMPOSE ===
    fun getQty(productId: String): Int {
        return _items.firstOrNull { it.productId == productId }?.qty ?: 0
    }

    fun decreaseSpecific(productId: String) {
        val current = _items.firstOrNull { it.productId == productId } ?: return
        changeQty(productId, -1, current.qty)
    }
}