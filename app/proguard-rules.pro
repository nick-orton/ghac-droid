# R8 rules for the release build.

# ── kotlinx.serialization ────────────────────────────────────────────────────
# The compiler plugin generates a $$serializer class for each @Serializable
# type and a serializer() method on its companion. R8 cannot see the link
# between them and the runtime lookup, so both are kept explicitly. Without
# this, parsing a SnapCast status response fails only in release builds — the
# kind of bug that never shows up in debug testing.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keep,includedescriptorclasses class com.nickorton.ghac.**$$serializer { *; }
-keepclassmembers class com.nickorton.ghac.** {
    *** Companion;
}
-keepclasseswithmembers class com.nickorton.ghac.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Exception messages ───────────────────────────────────────────────────────
# Connection errors are surfaced verbatim in the reconnect banner, so keep
# enough of the original names for those strings to stay intelligible.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
