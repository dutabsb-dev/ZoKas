package com.karyaku.kasir

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object TabBezelHelper {
    val screenHorizontalPadding = 2.dp
    val screenVerticalPadding = 2.dp
    val itemSpacing = 3.dp
    val cardInnerPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
}

fun Modifier.slimTabBezel(): Modifier =
    this.padding(
        horizontal = TabBezelHelper.screenHorizontalPadding,
        vertical = TabBezelHelper.screenVerticalPadding
    )
