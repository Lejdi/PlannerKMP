import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.detekt)
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

detekt {
    // Configured here rather than from the root through `allprojects { }`: configuring another
    // project from the root script is what Gradle's isolated-projects mode forbids. The source
    // sets are listed because the KMP plugin's own are not what detekt discovers by default.
    //
    // Deliberately still duplicated, twice attempted and twice reverted. A convention plugin from
    // an included build breaks the generated `libs.*` accessors in every consuming script; the same
    // plugin from `buildSrc` puts `kotlin-dsl`'s own Kotlin Gradle plugin on every project's build
    // classpath, where it shadows this build's 2.4.10 and `KotlinMultiplatformExtension` fails to
    // resolve — excluding the transitives does not help, because `kotlin-dsl` itself contributes
    // it. A script applied with `apply(from = ...)` is compiled without the applying project's
    // plugin classpath and so cannot name `DetektExtension` at all. Revisit on a newer Gradle.
    parallel = true
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml").takeIf { it.exists() }
    source.setFrom(
        files(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/iosMain/kotlin",
            "src/commonTest/kotlin",
            "src/androidHostTest/kotlin",
            "src/main/kotlin",
        ),
    )
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    // No iosX64: Compose Multiplatform 1.11.1 and the JetBrains lifecycle/navigation artifacts
    // publish iosArm64 and iosSimulatorArm64 only, so declaring it fails dependency resolution
    // rather than producing an Intel build. Apple Silicon only, upstream's choice not ours.

    android {
        namespace = "pl.lejdi.plannerkmp.core.testing"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // Everything here is api: these fixtures exist to be named in other modules' tests, so
            // the types in their signatures — CoroutineDispatchers, Logger, TodayProvider,
            // KeyValueCache, SqlDriver — are all part of this module's own public API.
            api(project(":core:common"))
            api(project(":core:database"))
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.sqldelight.runtime)
        }
        androidMain.dependencies {
            // The JVM driver, because the only place an "android" test actually runs here is
            // androidHostTest — a plain JVM unit test. This module is never a production dependency
            // of anything, so it does not put a JDBC driver into any shipped artifact.
            implementation(libs.sqldelight.sqliteDriver)
        }
        iosMain.dependencies {
            // api, not implementation: the native driver's cinterop carries the `-lsqlite3` linker
            // option, and a consumer's *test binary* only gets it if the dependency is exported.
            // Without that, :shared's test executable fails to link with every sqlite3_* symbol
            // undefined.
            api(libs.sqldelight.nativeDriver)
        }
    }
}
