package com.thiago.listamercado.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Store>>

    @Query("SELECT * FROM stores ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Store>

    @Insert
    suspend fun insert(store: Store): Long

    @Update
    suspend fun update(store: Store)

    @Delete
    suspend fun delete(store: Store)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observe(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun findByNormalizedName(normalizedName: String): Product?

    @Query("SELECT * FROM products WHERE favorite = 1")
    suspend fun getFavorites(): List<Product>

    @Insert
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("UPDATE products SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("SELECT productId, COUNT(DISTINCT purchaseId) AS times FROM purchase_items GROUP BY productId")
    fun observePurchaseCounts(): Flow<List<ProductCount>>
}

@Dao
interface ShoppingListDao {
    @Query(
        """
        SELECT l.id, l.name, l.createdAt,
            (SELECT COUNT(*) FROM list_items i WHERE i.listId = l.id) AS itemCount,
            (SELECT COUNT(*) FROM list_items i WHERE i.listId = l.id AND i.checked = 1) AS checkedCount
        FROM shopping_lists l
        ORDER BY l.createdAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<ListSummary>>

    @Query("SELECT * FROM shopping_lists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ShoppingList>>

    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    fun observe(id: Long): Flow<ShoppingList?>

    @Insert
    suspend fun insert(list: ShoppingList): Long

    @Query("UPDATE shopping_lists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        SELECT i.id, i.listId, i.productId, i.quantity, i.checked, p.name AS productName, p.favorite
        FROM list_items i JOIN products p ON p.id = i.productId
        WHERE i.listId = :listId
        ORDER BY i.checked, p.name COLLATE NOCASE
        """,
    )
    fun observeItems(listId: Long): Flow<List<ListItemRow>>

    @Query(
        """
        SELECT i.id, i.listId, i.productId, i.quantity, i.checked, p.name AS productName, p.favorite
        FROM list_items i JOIN products p ON p.id = i.productId
        WHERE i.listId = :listId
        ORDER BY p.name COLLATE NOCASE
        """,
    )
    suspend fun getItems(listId: Long): List<ListItemRow>

    @Query("SELECT i.listId AS listId, i.productId AS productId, i.quantity AS quantity FROM list_items i")
    fun observeAllItems(): Flow<List<ListProductRow>>

    @Query("SELECT * FROM list_items WHERE listId = :listId AND productId = :productId LIMIT 1")
    suspend fun findItem(listId: Long, productId: Long): ListItem?

    @Insert
    suspend fun insertItem(item: ListItem): Long

    @Query("UPDATE list_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("UPDATE list_items SET quantity = :quantity WHERE id = :id")
    suspend fun setQuantity(id: Long, quantity: Double)

    @Query("DELETE FROM list_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM list_items WHERE listId = :listId AND checked = 1")
    suspend fun deleteChecked(listId: Long)

    @Query("UPDATE list_items SET checked = 0 WHERE listId = :listId")
    suspend fun uncheckAll(listId: Long)
}

data class ListProductRow(val listId: Long, val productId: Long, val quantity: Double)

@Dao
interface PurchaseDao {
    @Query(
        """
        SELECT p.id, p.storeId, s.name AS storeName, p.date, p.totalCents,
            (SELECT COUNT(*) FROM purchase_items i WHERE i.purchaseId = p.id) AS itemCount
        FROM purchases p LEFT JOIN stores s ON s.id = p.storeId
        ORDER BY p.date DESC, p.createdAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<PurchaseSummary>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun get(id: Long): Purchase?

    @Query(
        """
        SELECT i.id, i.productId, p.name AS productName, i.quantity, i.unitPriceCents
        FROM purchase_items i JOIN products p ON p.id = i.productId
        WHERE i.purchaseId = :purchaseId
        ORDER BY i.id
        """,
    )
    suspend fun getItems(purchaseId: Long): List<PurchaseItemRow>

    @Insert
    suspend fun insert(purchase: Purchase): Long

    @Update
    suspend fun update(purchase: Purchase)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertItems(items: List<PurchaseItem>)

    @Query("DELETE FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun deleteItems(purchaseId: Long)

    @Query(
        """
        SELECT i.productId, p.storeId, i.unitPriceCents AS priceCents, p.date AS day, p.createdAt AS stamp
        FROM purchase_items i JOIN purchases p ON p.id = i.purchaseId
        WHERE p.storeId IS NOT NULL
        """,
    )
    fun observePricePoints(): Flow<List<PurchasePriceRow>>

    @Query(
        """
        SELECT p.id AS purchaseId, s.name AS storeName, p.date, i.quantity, i.unitPriceCents
        FROM purchase_items i
            JOIN purchases p ON p.id = i.purchaseId
            LEFT JOIN stores s ON s.id = p.storeId
        WHERE i.productId = :productId
        ORDER BY p.date DESC, p.createdAt DESC
        """,
    )
    fun observeProductHistory(productId: Long): Flow<List<ProductPurchaseRow>>
}

@Dao
interface PromotionDao {
    @Query(
        """
        SELECT pr.id, pr.productId, p.name AS productName, p.favorite, pr.storeId, s.name AS storeName,
            pr.priceCents, pr.regularPriceCents, pr.validUntil, pr.source, pr.createdAt
        FROM promotions pr
            JOIN products p ON p.id = pr.productId
            JOIN stores s ON s.id = pr.storeId
        ORDER BY p.favorite DESC, p.name COLLATE NOCASE, pr.priceCents
        """,
    )
    fun observeRows(): Flow<List<PromotionRow>>

    @Query(
        """
        SELECT pr.id, pr.productId, p.name AS productName, p.favorite, pr.storeId, s.name AS storeName,
            pr.priceCents, pr.regularPriceCents, pr.validUntil, pr.source, pr.createdAt
        FROM promotions pr
            JOIN products p ON p.id = pr.productId
            JOIN stores s ON s.id = pr.storeId
        WHERE pr.productId = :productId
        ORDER BY pr.createdAt DESC
        """,
    )
    fun observeForProduct(productId: Long): Flow<List<PromotionRow>>

    @Query("SELECT * FROM promotions")
    fun observeAll(): Flow<List<Promotion>>

    @Query("SELECT * FROM promotions WHERE id = :id")
    suspend fun get(id: Long): Promotion?

    @Insert
    suspend fun insert(promotion: Promotion): Long

    @Update
    suspend fun update(promotion: Promotion)

    @Query("DELETE FROM promotions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM promotions WHERE validUntil IS NOT NULL AND validUntil < :today")
    suspend fun deleteExpired(today: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM promotions
        WHERE productId = :productId AND storeId = :storeId AND priceCents = :priceCents
            AND ((validUntil IS NULL AND :validUntil IS NULL) OR validUntil = :validUntil)
        """,
    )
    suspend fun countSame(productId: Long, storeId: Long, priceCents: Long, validUntil: Long?): Int
}
