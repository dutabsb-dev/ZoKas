package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Insert
    suspend fun insertCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    // FUNGSI BARU: Update Poin Pelanggan
    @Query("UPDATE customers SET points = :newPoints WHERE id = :customerId")
    suspend fun updateCustomerPoints(customerId: Long, newPoints: Int)
}