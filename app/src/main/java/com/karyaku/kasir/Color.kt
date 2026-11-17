package com.karyaku.kasir

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// =====================================================================
// KONSTANTA WARNA BRAND (dipakai di Theme.kt sebagai base color)
// JANGAN dipakai langsung untuk teks di Composable baru — pakai helper
// di bawah agar otomatis adaptif light/dark.
// =====================================================================
val EmeraldGreen = Color(0xFF059669)
val EmeraldGreenDark = Color(0xFF047857)
val EmeraldGreenLight = Color(0xFF34D399)

val SlateGrey = Color(0xFF64748B)
val SlateGreyDark = Color(0xFF334155)
val SlateGreyLight = Color(0xFF94A3B8)
val SlateSurface = Color(0xFFF1F5F9)

val AmberAccent = Color(0xFFF59E0B)
val AmberAccentDark = Color(0xFFD97706)

val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)

val BackgroundDark = Color(0xFF0F172A)
val SurfaceDark = Color(0xFF1E293B)
val SurfaceVariantDark = Color(0xFF334155)

val ErrorRed = Color(0xFFEF4444)

// Legacy mapping (backward compat)
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

// =====================================================================
// FIX #6 — COMPOSABLE COLOR HELPERS (WAJIB DIPAKAI DI COMPOSABLE BARU)
// =====================================================================
// SEBELUM (buruk di dark mode):
//   Text("Dapur Bersih!", color = SlateGrey)  ← abu-abu gelap, tenggelam
//
// SESUDAH (adaptif):
//   Text("Dapur Bersih!", color = secondaryTextColor())
//
// Aturan pakai:
//   - Teks sekunder / hint / caption  → secondaryTextColor()
//   - Teks aksen (promo, warning)     → accentTextColor()
//   - Teks sukses (hijau)             → successTextColor()
//   - Teks error / hapus              → dangerTextColor()
//   - Teks "redup" untuk placeholder  → mutedTextColor()
//   - Garis pemisah                   → subtleDividerColor()
//   - Warna tombol container          → MaterialTheme.colorScheme.primary
//                                       (bukan EmeraldGreen langsung — di dark
//                                        jadi EmeraldGreenLight yang lebih cerah)
// =====================================================================

@Composable
fun secondaryTextColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun accentTextColor(): Color = MaterialTheme.colorScheme.tertiary

@Composable
fun successTextColor(): Color = MaterialTheme.colorScheme.primary

@Composable
fun dangerTextColor(): Color = MaterialTheme.colorScheme.error

@Composable
fun mutedTextColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

@Composable
fun subtleDividerColor(): Color = MaterialTheme.colorScheme.outlineVariant