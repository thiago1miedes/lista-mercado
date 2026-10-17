package com.thiago.listamercado.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "stores")
data class Store(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "products",
    indices = [Index(value = ["normalizedName"], unique = true)],
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    /** "Sempre compro": entra na comparação de preços e tem prioridade ao importar encartes. */
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "shopping_lists")
data class ShoppingList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "list_items",
    foreignKeys = [
        ForeignKey(ShoppingList::class, ["id"], ["listId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Product::class, ["id"], ["productId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("listId"), Index("productId")],
)
data class ListItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val productId: Long,
    val quantity: Double = 1.0,
    val checked: Boolean = false,
)

@Entity(
    tableName = "purchases",
    foreignKeys = [ForeignKey(Store::class, ["id"], ["storeId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("storeId")],
)
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val storeId: Long?,
    /** Dia da compra (LocalDate.toEpochDay()). */
    val date: Long,
    val totalCents: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(Purchase::class, ["id"], ["purchaseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Product::class, ["id"], ["productId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("purchaseId"), Index("productId")],
)
data class PurchaseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val quantity: Double,
    val unitPriceCents: Long,
)

@Entity(
    tableName = "promotions",
    foreignKeys = [
        ForeignKey(Product::class, ["id"], ["productId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Store::class, ["id"], ["storeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("productId"), Index("storeId")],
)
data class Promotion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val storeId: Long,
    val priceCents: Long,
    val regularPriceCents: Long? = null,
    /** Último dia da oferta (epochDay). Sem data = preço informado que vale até ser atualizado. */
    val validUntil: Long? = null,
    /** "manual", "pdf", "imagem" ou "texto". */
    val source: String = SOURCE_MANUAL,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_PDF = "pdf"
        const val SOURCE_IMAGE = "imagem"
        const val SOURCE_TEXT = "texto"
    }
}

// ---- Resultados de consultas ----

data class ListSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
    val checkedCount: Int,
)

data class ListItemRow(
    val id: Long,
    val listId: Long,
    val productId: Long,
    val quantity: Double,
    val checked: Boolean,
    val productName: String,
    val favorite: Boolean,
)

data class PurchaseSummary(
    val id: Long,
    val storeId: Long?,
    val storeName: String?,
    val date: Long,
    val totalCents: Long,
    val itemCount: Int,
)

data class PurchaseItemRow(
    val id: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val unitPriceCents: Long,
)

data class PurchasePriceRow(
    val productId: Long,
    val storeId: Long,
    val priceCents: Long,
    val day: Long,
    val stamp: Long,
)

data class ProductPurchaseRow(
    val purchaseId: Long,
    val storeName: String?,
    val date: Long,
    val quantity: Double,
    val unitPriceCents: Long,
)

data class PromotionRow(
    val id: Long,
    val productId: Long,
    val productName: String,
    val favorite: Boolean,
    val storeId: Long,
    val storeName: String,
    val priceCents: Long,
    val regularPriceCents: Long?,
    val validUntil: Long?,
    val source: String,
    val createdAt: Long,
)

data class ProductCount(val productId: Long, val times: Int)
