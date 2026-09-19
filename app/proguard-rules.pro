# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK's proguard-android-optimize.txt.

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.studytrack.app.**$$serializer { *; }
-keepclassmembers class com.studytrack.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.studytrack.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
