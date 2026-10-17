package com.thiago.listamercado.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Store::class,
        Product::class,
        ShoppingList::class,
        ListItem::class,
        Purchase::class,
        PurchaseItem::class,
        Promotion::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun productDao(): ProductDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun promotionDao(): PromotionDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lista-mercado.db").build()
    }
}
