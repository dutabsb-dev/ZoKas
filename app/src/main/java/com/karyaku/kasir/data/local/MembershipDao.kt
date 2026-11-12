package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MembershipDao {
    @Insert
    suspend fun insertMembership(membership: MembershipEntity)

    @Query("SELECT * FROM memberships")
    fun getAllMemberships(): Flow<List<MembershipEntity>>

    @Insert
    suspend fun insertCustomerMembership(customerMembership: CustomerMembershipEntity)

    @Query("SELECT * FROM customer_memberships WHERE customerId = :customerId AND remainingQuota > 0 AND expiryDate > :currentTime")
    fun getActiveMembershipsForCustomer(customerId: Long, currentTime: Long = System.currentTimeMillis()): Flow<List<CustomerMembershipEntity>>

    @Query("UPDATE customer_memberships SET remainingQuota = :newQuota WHERE id = :id")
    suspend fun updateRemainingQuota(id: Long, newQuota: Int)
}