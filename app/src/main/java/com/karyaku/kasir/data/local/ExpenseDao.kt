package com.karyaku.kasir.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insertExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    // TAMBAHAN: Filter per cabang
    @Query("SELECT * FROM expenses WHERE branchId = :branchId ORDER BY timestamp DESC")
    fun getExpensesByBranch(branchId: Long): Flow<List<ExpenseEntity>>
}