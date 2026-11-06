package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VoucherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoucher(voucher: VoucherEntity)

    @Query("SELECT * FROM vouchers WHERE code = :code AND isActive = 1 LIMIT 1")
    suspend fun getVoucherByCode(code: String): VoucherEntity?
}