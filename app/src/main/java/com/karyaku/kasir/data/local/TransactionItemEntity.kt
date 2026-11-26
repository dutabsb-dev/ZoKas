package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE // Jika transaksi dihapus, item ikut terhapus
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL // Izinkan hapus produk tanpa menghapus riwayat item struk
        )
    ],
    indices = [Index(value = ["transactionId"]), Index(value = ["productId"])]
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val productId: Long? = null,
    val productName: String,
    val price: Long,
    val costPrice: Long = 0,
    val quantity: Int,
    val subtotal: Long,
    val notes: String = "",
    val serialNumber: String = "",
    val assignedStaff: String = "",
    val isService: Boolean = false 
)