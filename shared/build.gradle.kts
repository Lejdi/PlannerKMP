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
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        // No iosX64: Compose Multiplatform publishes no iosX64 variant, so declaring it fails
        // dependency resolution rather than producing an Intel build.
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        // The test executable links :core:testing, which brings SQLDelight's native driver and its
        // sqliter cinterop. That cinterop needs the system sqlite3, and the linker option does not
        // reach a *test* binary through a transitive dependency — every sqlite3_* symbol comes back
        // undefined. The framework gets it too, harmlessly: iOS ships libsqlite3.
        iosTarget.binaries.all {
            linkerOpts("-lsqlite3")
        }
    }
    
    android {
       namespace = "pl.lejdi.plannerkmp.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        commonMain.dependencies {
            // api: App() is a public @Composable, so compose-runtime is in this module's own public
            // signature. As `implementation` it reached :androidApp only through activity-compose's
            // transitive api, which is the kind of accident that breaks on an unrelated upgrade.
            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.animation)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Supplies rememberViewModelStoreNavEntryDecorator(), which gives every NavEntry its
            // own ViewModelStoreOwner. Without it NavDisplay's default entryDecorators only hold a
            // SaveableStateHolder, so koinViewModel() would resolve one Activity-scoped store and
            // retain a single ViewModel instance across every nav entry.
            implementation(libs.androidx.lifecycle.viewmodelNavigation3)
            // api: initKoin() returns a KoinApplication and takes a KoinAppDeclaration, both of
            // which :androidApp names at its call site.
            api(libs.koin.core)
            // App() names SavedStateConfiguration and builds the back stack's SerializersModule
            // itself. Both used to arrive through core:mvi's and core:navigation's `api`, i.e. this
            // module compiled on somebody else's dependency.
            implementation(libs.androidx.savedstate)
            implementation(libs.kotlinx.serialization.core)
            implementation(project(":core:common"))
            implementation(project(":core:database"))
            implementation(project(":core:mvi"))
            implementation(project(":core:ui"))
            implementation(project(":core:navigation"))
            implementation(project(":feature:tasks"))
            implementation(project(":feature:grocery"))
            implementation(project(":feature:routines"))
            implementation(project(":feature:gym"))
            implementation(libs.koin.compose)
            // App() renders each feature's contributed tab title, which is a StringResource.
            implementation(libs.compose.components.resources)
            // :core:network is deliberately absent. It is built and tested by CI against a future
            // backend, but nothing calls it, and depending on it here put the whole Ktor + OkHttp
            // stack — and OkHttp's 132 KB public-suffix asset — into the release APK for an unused
            // module. Add this line and the networkModule() call in Koin.kt together.
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // In-memory drivers, so the graph test resolves on both platforms.
            implementation(project(":core:testing"))
        }
    }
}
