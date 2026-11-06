# Keep stack traces readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# BlackBox core — must not be obfuscated (reflection + native hooks depend on exact names)
-keep class top.niunaijun.blackbox.** { *; }
-keep class top.niunaijun.jnihook.** { *; }
-keep class mirror.** { *; }

# Android framework classes referenced by hooks
-keep class android.** { *; }
-keep class com.android.** { *; }

# black-reflection annotation processor
-keep class top.niunaijun.blackreflection.** { *; }
-keep @top.niunaijun.blackreflection.annotation.BClass class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BClassName class * { *; }
-keep @top.niunaijun.blackreflection.annotation.BClassNameNotProcess class * { *; }
-keepclasseswithmembernames class * {
    @top.niunaijun.blackreflection.annotation.BField.* <methods>;
    @top.niunaijun.blackreflection.annotation.BFieldNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BFieldSetNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BFieldCheckNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BMethod.* <methods>;
    @top.niunaijun.blackreflection.annotation.BStaticField.* <methods>;
    @top.niunaijun.blackreflection.annotation.BStaticMethod.* <methods>;
    @top.niunaijun.blackreflection.annotation.BMethodCheckNotProcess.* <methods>;
    @top.niunaijun.blackreflection.annotation.BConstructor.* <methods>;
    @top.niunaijun.blackreflection.annotation.BConstructorNotProcess.* <methods>;
}

# App UI — only keep what's needed for ViewBinding and serialization
-keep class top.niunaijun.blackboxa.bean.** { *; }

# Suppress noisy warnings from third-party libs
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
