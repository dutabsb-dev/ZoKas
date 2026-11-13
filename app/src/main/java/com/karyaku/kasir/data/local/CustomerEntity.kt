package com.karyaku.kasir.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * MODUL G — CRM Lanjutan
 *
 * Field baru:
 *   - birthDate    : timestamp tanggal lahir (0 = tidak diisi). Untuk
 *                    auto-discount ulang tahun. Cek hanya MONTH + DAY_OF_MONTH.
 *   - lastVisitDate: timestamp kunjungan terakhir (0 = belum pernah).
 *   - totalSpending: akumulasi total belanja pelanggan (Rupiah).
 *
 * Tier dihitung dari `points` (computed, tidak disimpan):
 *   Regular  : 0–99
 *   Silver   : 100–499
 *   Gold     : 500–999
 *   Platinum : 1000+
 */
@Entity(
    tableName = "customers",
    indices = [Index(value = ["name"]), Index(value = ["points"])]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "-",
    val points: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val birthDate: Long = 0L,
    val lastVisitDate: Long = 0L,
    val totalSpending: Long = 0L
)