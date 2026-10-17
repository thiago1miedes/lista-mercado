package com.thiago.listamercado.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.thiago.listamercado.core.CurrentPrice
import com.thiago.listamercado.core.Money
import com.thiago.listamercado.core.PriceComparator
import com.thiago.listamercado.core.PriceKey
import com.thiago.listamercado.core.PriceKind
import com.thiago.listamercado.core.PricePoint
import com.thiago.listamercado.core.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class PurchaseItemInput(val productId: Long, val quantity: Double, val unitPriceCents: Long)

data class ImportedOffer(
    /** Produto já cadastrado, ou null para criar um novo com [name]. */
    val productId: Long?,
    val name: String,
    val priceCents: Long,
    val regularPriceCents: Long?,
)

fun today(): Long = LocalDate.now().toEpochDay()

class Repository(private val db: AppDatabase) {
    private val storeDao = db.storeDao()
    private val productDao = db.productDao()
    private val listDao = db.shoppingListDao()
    private val purchaseDao = db.purchaseDao()
    private val promotionDao = db.promotionDao()

    val stores: Flow<List<Store>> = storeDao.observeAll()
    val products: Flow<List<Product>> = productDao.observeAll()
    val purchaseCounts: Flow<Map<Long, Int>> =
        productDao.observePurchaseCounts().map { rows -> rows.associate { it.productId to it.times } }
    val listSummaries: Flow<List<ListSummary>> = listDao.observeSummaries()
    val lists: Flow<List<ShoppingList>> = listDao.observeAll()
    val allListItems: Flow<List<ListProductRow>> = listDao.observeAllItems()
    val purchaseSummaries: Flow<List<PurchaseSummary>> = purchaseDao.observeSummaries()
    val promotionRows: Flow<List<PromotionRow>> = promotionDao.observeRows()

    /** Todos os preços conhecidos (compras + promoções). */
    val pricePoints: Flow<List<PricePoint>> =
        combine(purchaseDao.observePricePoints(), promotionDao.observeAll()) { purchases, promotions ->
            purchases.map {
                PricePoint(it.productId, it.storeId, it.priceCents, it.day, it.stamp, PriceKind.PURCHASE)
            } + promotions.map {
                PricePoint(
                    productId = it.productId,
                    storeId = it.storeId,
                    priceCents = it.priceCents,
                    day = epochDayOf(it.createdAt),
                    stamp = it.createdAt,
                    kind = PriceKind.PROMOTION,
                    validUntilDay = it.validUntil,
                    regularPriceCents = it.regularPriceCents,
                )
            }
        }

    /** Preço atual de cada produto em cada mercado. */
    val currentPrices: Flow<Map<PriceKey, CurrentPrice>> =
        pricePoints.map { PriceComparator.currentPrices(it, today()) }

    // ---------- Mercados ----------

    suspend fun addStore(name: String): Long? {
        val clean = cleanName(name) ?: return null
        storeDao.getAll().firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it.id }
        return storeDao.insert(Store(name = clean))
    }

    suspend fun renameStore(store: Store, name: String) {
        val clean = cleanName(name) ?: return
        storeDao.update(store.copy(name = clean))
    }

    suspend fun deleteStore(store: Store) = storeDao.delete(store)

    suspend fun getStores(): List<Store> = storeDao.getAll()

    // ---------- Produtos ----------

    suspend fun findOrCreateProduct(name: String, favorite: Boolean = false): Long? {
        val clean = cleanName(name) ?: return null
        val normalized = TextNormalizer.normalize(clean)
        if (normalized.isEmpty()) return null
        productDao.findByNormalizedName(normalized)?.let { return it.id }
        return productDao.insert(Product(name = clean, normalizedName = normalized, favorite = favorite))
    }

    /** Retorna false se já existe outro produto com esse nome. */
    suspend fun renameProduct(product: Product, name: String): Boolean {
        val clean = cleanName(name) ?: return false
        val normalized = TextNormalizer.normalize(clean)
        val existing = productDao.findByNormalizedName(normalized)
        if (existing != null && existing.id != product.id) return false
        return try {
            productDao.update(product.copy(name = clean, normalizedName = normalized))
            true
        } catch (e: SQLiteConstraintException) {
            false
        }
    }

    suspend fun setFavorite(productId: Long, favorite: Boolean) = productDao.setFavorite(productId, favorite)

    suspend fun deleteProduct(product: Product) = productDao.delete(product)

    suspend fun getProducts(): List<Product> = productDao.getAll()

    fun observeProduct(id: Long): Flow<Product?> = productDao.observe(id)

    fun observeProductHistory(id: Long): Flow<List<ProductPurchaseRow>> = purchaseDao.observeProductHistory(id)

    fun observeProductPromotions(id: Long): Flow<List<PromotionRow>> = promotionDao.observeForProduct(id)

    // ---------- Listas ----------

    suspend fun createList(name: String): Long =
        listDao.insert(ShoppingList(name = cleanName(name) ?: "Lista de compras"))

    fun observeList(id: Long): Flow<ShoppingList?> = listDao.observe(id)

    fun observeListItems(listId: Long): Flow<List<ListItemRow>> = listDao.observeItems(listId)

    suspend fun getListItems(listId: Long): List<ListItemRow> = listDao.getItems(listId)

    suspend fun renameList(id: Long, name: String) {
        cleanName(name)?.let { listDao.rename(id, it) }
    }

    suspend fun deleteList(id: Long) = listDao.delete(id)

    suspend fun addItemToList(listId: Long, name: String, quantity: Double = 1.0): Boolean {
        val productId = findOrCreateProduct(name) ?: return false
        addProductToList(listId, productId, quantity)
        return true
    }

    suspend fun addProductToList(listId: Long, productId: Long, quantity: Double = 1.0) {
        val existing = listDao.findItem(listId, productId)
        if (existing == null) {
            listDao.insertItem(ListItem(listId = listId, productId = productId, quantity = quantity))
        } else if (existing.checked) {
            listDao.setChecked(existing.id, false)
        }
    }

    /** Adiciona à lista todos os produtos marcados como "sempre compro". Retorna quantos entraram. */
    suspend fun addFavoritesToList(listId: Long): Int = db.withTransaction {
        var added = 0
        for (product in productDao.getFavorites()) {
            if (listDao.findItem(listId, product.id) == null) {
                listDao.insertItem(ListItem(listId = listId, productId = product.id))
                added++
            }
        }
        added
    }

    suspend fun setItemChecked(itemId: Long, checked: Boolean) = listDao.setChecked(itemId, checked)

    suspend fun setItemQuantity(itemId: Long, quantity: Double) = listDao.setQuantity(itemId, quantity)

    suspend fun deleteListItem(itemId: Long) = listDao.deleteItem(itemId)

    suspend fun removeCheckedItems(listId: Long) = listDao.deleteChecked(listId)

    suspend fun uncheckAll(listId: Long) = listDao.uncheckAll(listId)

    // ---------- Compras ----------

    suspend fun getPurchase(id: Long): Purchase? = purchaseDao.get(id)

    suspend fun getPurchaseItems(id: Long): List<PurchaseItemRow> = purchaseDao.getItems(id)

    suspend fun savePurchase(id: Long, storeId: Long, day: Long, items: List<PurchaseItemInput>): Long =
        db.withTransaction {
            val total = items.sumOf { Money.lineTotal(it.quantity, it.unitPriceCents) }
            val purchaseId = if (id == 0L) {
                purchaseDao.insert(Purchase(storeId = storeId, date = day, totalCents = total))
            } else {
                val old = purchaseDao.get(id)
                purchaseDao.update(
                    Purchase(
                        id = id,
                        storeId = storeId,
                        date = day,
                        totalCents = total,
                        createdAt = old?.createdAt ?: System.currentTimeMillis(),
                    ),
                )
                purchaseDao.deleteItems(id)
                id
            }
            purchaseDao.insertItems(
                items.map { PurchaseItem(purchaseId = purchaseId, productId = it.productId, quantity = it.quantity, unitPriceCents = it.unitPriceCents) },
            )
            purchaseId
        }

    suspend fun deletePurchase(id: Long) = purchaseDao.delete(id)

    // ---------- Promoções ----------

    suspend fun getPromotion(id: Long): Promotion? = promotionDao.get(id)

    suspend fun savePromotion(promotion: Promotion) {
        if (promotion.id == 0L) promotionDao.insert(promotion) else promotionDao.update(promotion)
    }

    suspend fun deletePromotion(id: Long) = promotionDao.delete(id)

    suspend fun deleteExpiredPromotions(): Int = promotionDao.deleteExpired(today())

    /** Salva as ofertas revisadas de um encarte. Retorna quantas foram gravadas. */
    suspend fun importPromotions(
        storeId: Long,
        validUntil: Long?,
        source: String,
        offers: List<ImportedOffer>,
    ): Int = db.withTransaction {
        var saved = 0
        for (offer in offers) {
            val productId = offer.productId ?: findOrCreateProduct(offer.name) ?: continue
            if (promotionDao.countSame(productId, storeId, offer.priceCents, validUntil) > 0) continue
            promotionDao.insert(
                Promotion(
                    productId = productId,
                    storeId = storeId,
                    priceCents = offer.priceCents,
                    regularPriceCents = offer.regularPriceCents?.takeIf { it > offer.priceCents },
                    validUntil = validUntil,
                    source = source,
                ),
            )
            saved++
        }
        saved
    }

    private fun cleanName(name: String): String? =
        name.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }

    private fun epochDayOf(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
}
