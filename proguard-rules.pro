# ProGuard Configuration for LovelyCheck

# Keep all class/member names for public API and reflection compatibility
-dontshrink
-dontoptimize
-dontnote
-dontwarn

# Keep the main plugin class intact
-keep class org.lovelycheck.spigot.LovelyCheckPlugin { *; }

# Keep Bukkit/Paper event handlers (annotated with @EventHandler)
-keepclassmembers class * {
    @org.bukkit.event.EventHandler <methods>;
}

# Keep all shaded dependencies to prevent runtime ClassNotFound/MethodNotFound exceptions
-keep class org.lovelycheck.shaded.** { *; }
-keep class org.sqlite.** { *; }
-keep class org.yaml.snakeyaml.** { *; }

# Keep all command classes and check managers/definitions that may be referenced
-keep class org.lovelycheck.spigot.checks.commands.** { *; }
-keep class org.lovelycheck.spigot.checks.HackDefinition { *; }
-keep class org.lovelycheck.spigot.commands.** { *; }

# Keep parameter names and annotations for reflection and debugging compatibility
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,EnclosingMethod,InnerClasses

# Keep all enum classes and their members to prevent java.util.EnumMap failures and database/config serialization issues
-keep class * extends java.lang.Enum { *; }

