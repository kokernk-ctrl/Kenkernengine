# KENKERN Engine ProGuard rules.
# Minification is disabled for the current development phase; these rules are
# kept so future enabling of R8 preserves the reflection-free engine core.
-keepattributes SourceFile,LineNumberTable
-keep class com.kenkern.engine.** { *; }
