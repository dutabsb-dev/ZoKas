package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.MembershipEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun PosViewModel.addMembership(name: String, price: Long, quota: Int, days: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            membershipDao.insertMembership(MembershipEntity(name = name, price = price, quotaCount = quota, validDays = days))
        } catch (e: Exception) { e.printStackTrace() }
    }
}