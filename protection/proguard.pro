-dontshrink
-dontoptimize
-overloadaggressively
-useuniqueclassmembernames
-adaptclassstrings
-keepattributes Exceptions,InnerClasses,EnclosingMethod,Signature,*Annotation*,Record,PermittedSubclasses,LineNumberTable,SourceFile
# Mixin uses constructor line tables to splice field initializers. The instrumentation pass strips
# debug tables from ordinary classes while preserving mixins as an explicit compatibility exception.
-renamesourcefileattribute Source
-keep class dev.maro.Maro { *; }
-keep class dev.maro.protection.ProtectedGate { public static *; }
-keep class dev.maro.protection.OwnerScreen { *; }
# Fabric/Mixin reflection and native/JNA fields are contracts, not obfuscation targets.
-keep class **.mixin.** { *; }
-keep class **.mixins.** { *; }
-keep class ** implements com.sun.jna.Library { *; }
-keep class ** extends com.sun.jna.Structure { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
# Gson's default reflection serializer relies on field names; retaining all fields also preserves saved configs.
-keepclassmembers class * { <fields>; }
# Public mod APIs and Minecraft overrides retain members. Internal class names and private methods are renamed.
-keepclassmembers class * { public <methods>; protected <methods>; }
# Mixins and service resources must keep their declared class names.
-keepnames class dev.maro.runtime.**
-keepnames class dev.maro.nathan.**
-keepnames class dev.maro.anubis.**
-keepnames class dev.maro.render.**
-keepnames class dev.maro.config.**
-keepnames class dev.maro.module.Module
-keepnames class dev.maro.module.ModuleManager
-keepnames class dev.maro.setting.**
# Optional external integrations are intentionally loaded by name when installed.
-dontwarn meteordevelopment.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
# Signature-polymorphic MethodHandle.invokeExact has a VM-resolved descriptor, not a declared Java member.
-dontwarn dev.maro.compat.CompanionBlockOutlineBuffers
-dontnote
