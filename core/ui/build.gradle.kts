import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
        namespace = "pl.lejdi.plannerkmp.core.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

        // This module owns a composeResources/ tree, and the AGP KMP library plugin keeps Android
        // resource/asset processing off by default. Without this the generated .cvr payload is
        // assembled for the iOS targets only and never reaches the APK, so every stringResource()
        // on Android has nothing to read and throws at first composition.
        androidResources {
            enable = true
        }

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // api: these types appear in this module's own public signatures — @Composable
            // (runtime), Modifier (ui) and the ColorScheme returned by dynamicColorScheme
            // (material3). material-icons-extended is gone: nothing here draws an icon.
            api(libs.compose.runtime)
            api(libs.compose.material3)
            api(libs.compose.ui)
            implementation(libs.compose.foundation)
            // CollectEffects is lifecycle-aware, so it needs repeatOnLifecycle + LocalLifecycleOwner.
            api(libs.kotlinx.coroutines.core)
            api(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // api: LocalDate/LocalTime and Month/DayOfWeek appear in the date-formatting signatures.
            api(libs.kotlinx.datetime)
            // The generic action vocabulary (Retry, Undo, OK) that every feature was re-declaring.
            implementation(libs.compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "pl.lejdi.plannerkmp.core.ui.resources"
    generateResClass = always
}
