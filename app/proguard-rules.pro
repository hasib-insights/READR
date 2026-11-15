# READR release ProGuard rules.

# --- kotlinx.serialization (ReadingPosition and preference models) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.readr.app.**$$serializer { *; }
-keepclassmembers class com.readr.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.readr.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enums are looked up by name for stored preferences.
-keepclassmembers enum com.readr.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
