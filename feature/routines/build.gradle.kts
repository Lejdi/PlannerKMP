import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
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
        namespace = "pl.lejdi.plannerkmp.feature.routines"
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
            // api, not implementation: these modules' types are in this feature's own public
            // signatures — AppResult on every port method, BaseViewModel as each ViewModel's
            // supertype, FeatureTab/the nav contributors as the classes Koin multibinds. As
            // `implementation` they compiled only because :shared happened to declare the same
            // modules itself, which is precisely the accident the rule exists to prevent.
            api(project(":core:common"))
            api(project(":core:mvi"))
            api(project(":core:navigation"))
            // implementation, not api: every type this feature borrows from core:database —
            // the driver factory, KeyValueCache, safeMutation, the row check — is named inside
            // the data layer or inside the Koin module's lambda, and both are internal to this
            // module. Nothing it publishes mentions one, so nothing downstream should inherit it.
            implementation(project(":core:database"))
            implementation(project(":core:ui"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material.iconsCore)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // api: this module's Koin module is part of its public API — `commonModule`,
            // `databaseModule`, `networkModule()`, `tasksModule`, `groceryModule` and
            // `routinesModule` are all typed `org.koin.core.module.Module`, and :shared names
            // every one of them. As
            // `implementation` they compiled only because :shared happened to declare koin-core
            // itself, which is exactly the accident the rule exists to prevent.
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation3.ui)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutinesExtensions)
            implementation(libs.kotlinx.coroutines.core)
            // api, not implementation: LocalDate appears in RoutinesDatasource.updateCompletedOn,
            // in Routine itself and in ToggleRoutineDone — this module's own public signatures.
            api(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            // No platform SQLDelight driver here — see the same note in :feature:tasks. Driver
            // creation belongs to core:database's DatabaseDriverFactory, which declares both.
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // Shared fixtures (dispatchers, loggers, fakes, in-memory drivers).
            implementation(project(":core:testing"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("RoutinesDatabase") {
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
            packageName.set("pl.lejdi.plannerkmp.feature.routines.data")
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "pl.lejdi.plannerkmp.feature.routines.resources"
    generateResClass = always
}
