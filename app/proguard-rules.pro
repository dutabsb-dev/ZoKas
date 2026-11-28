# Room database & entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Jetpack Compose & Kotlin metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# Apache POI, XMLBeans, Saxon, Log4j, OSGi & Java AWT/XML desktop dependencies
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-dontwarn net.sf.saxon.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.osgi.framework.**
-dontwarn org.slf4j.**
-dontwarn org.apache.commons.**
-dontwarn javax.xml.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.beans.**
-dontwarn java.rmi.**
-dontwarn java.security.**
-dontwarn sun.misc.**

-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }

# ZXing & ML Kit
-dontwarn com.google.zxing.**
-dontwarn com.google.mlkit.**
-keep class com.journeyapps.barcodescanner.** { *; }

# Keep native methods intact
-keepclasseswithmembernames class * {
    native <methods>;
}
