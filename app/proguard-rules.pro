# Git Fable VPN — conservative R8 rules (safety-first, partial obfuscation
# by design). Goal: rename/shrink code WITHOUT breaking the VPN services,
# native libs, or manifest components. When in doubt, keep.

# Keep ALL app classes under the creator package: it contains every
# AndroidManifest component (activities, services, receiver, Application),
# classes referenced from layouts/XML, and code driving the native libs.
-keep class jayar.lee.espiritu.** { *; }
-keepclassmembers class jayar.lee.espiritu.** { *; }

# Generated classes
-keep class com.jepongdevxyz.fablevpn.BuildConfig { *; }
-keep class com.jepongdevxyz.fablevpn.R { *; }
-keep class com.jepongdevxyz.fablevpn.R$* { *; }

# Native methods (libovpncli / libpdnsd / libudp / libtun2socks JNI)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Enums used by name in stored profile data (ProfileStore serializes
# Protocol.name()) — never rename enum values.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep annotations and signatures for reflection-using libraries
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# MPAndroidChart / Volley / OkHttp ship their own consumer rules; keep
# their public entry points as a belt-and-braces measure.
-keep class com.github.mikephil.charting.** { *; }
-keep class com.mcxiaoke.volley.** { *; }
-dontwarn com.mcxiaoke.volley.**
-dontwarn okhttp3.**
-dontwarn okio.**
