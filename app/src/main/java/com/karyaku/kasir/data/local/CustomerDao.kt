package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Insert
    suspend fun insertCustomer(customer: CustomerEntity)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("UPDATE customers SET points = :newPoints WHERE id = :customerId")
    suspend fun updateCustomerPoints(customerId: Long, newPoints: Int)

    // ================================================================
    // MODUL G — CRM Lanjutan
    // ================================================================

    @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
    suspend fun getCustomerById(customerId: Long): CustomerEntity?

    @Query("SELECT * FROM customers ORDER BY totalSpending DESC LIMIT :limit")
    fun getTopSpenders(limit: Int = 10): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE points >= :minPoints ORDER BY points DESC")
    fun getCustomersByMinPoints(minPoints: Int): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :customerId LIMIT 1")
    suspend fun getCustomerByIdOnce(customerId: Long): CustomerEntity?

    /**
     * MODUL G-2a — Cari pelanggan by name (suspend, untuk dipakai di
     * dalam withTransaction saat refund — hindari Flow.first() deadlock).
     */
    @Query("SELECT * FROM customers WHERE name = :name LIMIT 1")
    suspend fun getCustomerByNameOnce(name: String): CustomerEntity?

    @Query("""
        UPDATE customers 
        SET name = :name, phone = :phone, birthDate = :birthDate 
        WHERE id = :customerId
    """)
    suspend fun updateCustomerProfile(customerId: Long, name: String, phone: String, birthDate: Long)

    @Query("""
        UPDATE customers 
        SET lastVisitDate = :visitTime, 
            totalSpending = totalSpending + :spent,
            points = :newPoints 
        WHERE id = :customerId
    """)
    suspend fun updateCustomerStats(customerId: Long, visitTime: Long, spent: Long, newPoints: Int)

    @Query("UPDATE customers SET totalSpending = MAX(0, totalSpending - :refundAmount) WHERE id = :customerId")
    suspend fun reduceCustomerSpending(customerId: Long, refundAmount: Long)
}