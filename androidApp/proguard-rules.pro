# R8 rules for the release build.
#
# Most of what this app depends on ships its own consumer rules, and R8 applies those automatically.
# What is here is the part no library can declare on the app's behalf.

# Keep enough of the stack trace to be able to read a release crash. Without these, a report comes
# back with obfuscated frames and no line numbers, which — for an app whose only other diagnostic
# is the log — means an unreproducible crash is simply unexplainable. -renamesourcefileattribute
# still hides the original file names.
#
# Verified as load-bearing: neither attribute appears in AGP's own proguard-android-optimize.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The app logs class names reflectively in two places — RestorableViewModel's "discarding unreadable
# saved input for ${this::class.simpleName}" and runStartupWork's "${initializer::class.simpleName}
# failed". KClass.simpleName resolves through the JVM class name, which R8 renames, so in release
# those lines read "discarding unreadable saved input for b". Keeping the *names* (not the members)
# of the two hierarchies involved costs nothing and is the same argument as SourceFile above.
-keepnames class * extends pl.lejdi.plannerkmp.core.mvi.RestorableViewModel
-keepnames class * implements pl.lejdi.plannerkmp.core.common.AppInitializer

# Exception class names end up in logs the same way, and for the same reason survive renaming only
# if asked for. `-keepattributes Exceptions` — which is what this file used to say — keeps the
# `throws` clause on a method descriptor and has nothing to do with class names.
-keepnames class * extends java.lang.Throwable

# Deliberately no kotlinx.serialization keep rules. kotlinx-serialization-core ships
# META-INF/com.android.tools/r8/kotlinx-serialization-common.pro, which R8 applies on its own and
# which already keeps the Companion and serializer() members of every @Serializable class — the
# NavKey subclasses included. The rules that used to be here restated that without the @Serializable
# guard, so they pinned the companion of *every* class under pl.lejdi.plannerkmp.**, serializable or
# not, and stopped R8 shrinking any of them.
#
# Nothing else needs a keep either, checked against the resolved artifacts: Koin here is
# constructor-reference based and keys definitions by KClass, so registration and lookup see the
# same renamed class; SQLDelight's generated queries are reached from statically-resolved lambdas;
# and the polymorphic NavKey registration uses the reified `subclass(T::class)`, whose serializer is
# resolved at compile time with the serial names baked into the generated descriptors.
