import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "pl.lejdi.plannerkmp.core.navigation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: NavKey and EntryProviderScope (from navigation3-ui)
            // appear in this module's own public API (NavEntryProviderContributor.contribute(),
            // Navigator's constructor/backStack), so consumers like :shared need them on their
            // compile classpath transitively, not just at core:navigation's own compile time.
            api(libs.navigation3.ui)
            api(libs.compose.runtime)
            // api: LocalSharedTransitionScope's type (SharedTransitionScope) is part of this
            // module's own public API, so consumers need it on their compile classpath too.
            api(libs.compose.animation)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
