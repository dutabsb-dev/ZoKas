package com.karyaku.kasir

import androidx.compose.ui.graphics.Color

// PRIMARY: EMERALD GREEN (Pusat Identitas ZoKas)
val EmeraldGreen = Color(0xFF059669)
val EmeraldGreenDark = Color(0xFF047857)
val EmeraldGreenLight = Color(0xFF34D399)

// SECONDARY: SLATE GREY (Netral & Profesional)
val SlateGrey = Color(0xFF64748B)
val SlateGreyDark = Color(0xFF334155)
val SlateGreyLight = Color(0xFF94A3B8)
val SlateSurface = Color(0xFFF1F5F9) 

// ACCENT: AMBER (Promo, Peringatan, Highlight)
val AmberAccent = Color(0xFFF59E0B)
val AmberAccentDark = Color(0xFFD97706)

// BACKGROUND & SURFACE (LIGHT MODE)
val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)

// BACKGROUND & SURFACE (DARK MODE)
val BackgroundDark = Color(0xFF0F172A)
val SurfaceDark = Color(0xFF1E293B)
val SurfaceVariantDark = Color(0xFF334155) 

// ERROR & DANGER
val ErrorRed = Color(0xFFEF4444)

// LEGACY MAPPING (Agar file lama tidak error compile)
val ZokasGreen = EmeraldGreen
val ZokasGreenDark = EmeraldGreenDark
val ZokasGreenLight = EmeraldGreenLight
val ZokasOrange = AmberAccent
val ZokasOrangeDark = AmberAccentDark
val LightBackground = BackgroundLight
val LightSurface = SurfaceLight
val LightSurfaceVariant = SlateSurface
val LightText = SlateGreyDark
val DarkBackground = BackgroundDark
val DarkSurface = SurfaceDark
val DarkSurfaceVariant = SurfaceVariantDark
val DarkText = SlateGreyLight