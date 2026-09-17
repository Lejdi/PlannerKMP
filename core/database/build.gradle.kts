import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.sqldelight)
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
        namespace = "pl.lejdi.plannerkmp.core.database"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: safeQuery's signature takes CoroutineDispatchers
            // and returns AppResult, both from core:common, so consumers need them on
            // their compile classpath transitively.
            api(project(":core:common"))
            // api: DatabaseDriverFactory.createDriver takes a SqlSchema and returns a SqlDriver,
            // both from the runtime. Both features call it and compiled only because each had
            // separately declared the same artifact.
            api(libs.sqldelight.runtime)
            // api: asAppResult returns a Flow. It resolved through core:common's own api(…),
            // i.e. through somebody else's dependency.
            api(libs.kotlinx.coroutines.core)
            // api: this module's Koin module is part of its public API — `commonModule`,
            // `databaseModule`, `networkModule()`, `tasksModule` and `groceryModule` are all typed
            // `org.koin.core.module.Module`, and :shared names every one of them. As
            // `implementation` they compiled only because :shared happened to declare koin-core
            // itself, which is exactly the accident the rule exists to prevent.
            api(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
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
        // KeyValueDatabase, not Database: this module owns exactly one schema — the key-value
        // store — and `Database` is far too generic a name for a public generated type that every
        // feature module can see.
        create("KeyValueDatabase") {
            packageName.set("pl.lejdi.plannerkmp.core.database")
            // Exports the current schema as a .db file and fails the build when a schema change
            // arrives without a matching .sqm migration, so an upgrade can never reach users as a
            // runtime crash.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
