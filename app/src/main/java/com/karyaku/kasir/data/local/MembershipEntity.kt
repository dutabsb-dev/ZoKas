package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memberships")
data class MembershipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Long,
    val quotaCount: Int,
    val validDays: Int
)