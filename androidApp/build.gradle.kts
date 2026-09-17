import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
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
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

/**
 * The one place either platform's version number lives.
 *
 * `version.properties` is also `#include`d by `iosApp/Configuration/Config.xcconfig`, so the two
 * platforms cannot drift — they used to hold the same two numbers in two files, which is a pair
 * that only stays in step for as long as someone remembers.
 */
val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "pl.lejdi.plannerkmp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "pl.lejdi.plannerkmp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersion.getProperty("CURRENT_PROJECT_VERSION").toInt()
        versionName = appVersion.getProperty("MARKETING_VERSION")
    }
    /**
     * Signing from the environment, when the environment has it.
     *
     * Absent — a local build, or CI, which only wants to know that R8 runs — the release variant
     * stays unsigned exactly as before, so nothing needs a keystore to build. What this replaces is
     * nothing at all: cutting a release was an out-of-band manual step with no description of it
     * checked in anywhere.
     */
    val releaseKeystore = System.getenv("PLANNER_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("PLANNER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PLANNER_KEY_ALIAS")
                keyPassword = System.getenv("PLANNER_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 was disabled, so release builds shipped every class and every string of every
            // dependency — including the whole icon set — unshrunk and unobfuscated.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}