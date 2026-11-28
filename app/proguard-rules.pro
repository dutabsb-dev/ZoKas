# Room database & entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Jetpack Compose & Kotlin metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# Apache POI (XLSX bookkeeping/product export/import)
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn javax.xml.stream.**
-dontwarn org.w3c.dom.**
-keep class org.apache.poi.** { *; }

# ZXing & ML Kit
-dontwarn com.google.zxing.**
-dontwarn com.google.mlkit.**
-keep class com.journeyapps.barcodescanner.** { *; }

# Keep native methods intact
-keepclasseswithmembernames class * {
    native <methods>;
}
