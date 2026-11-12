package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customer_memberships")
data class CustomerMembershipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val membershipId: Long,
    val membershipName: String,
    val remainingQuota: Int,
    val expiryDate: Long
)