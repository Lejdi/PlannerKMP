plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.detekt) apply false
}

// Static analysis is applied by each module to itself — see the `detekt { }` block in any module's
// build script — not from here through `allprojects { }`. Configuring other projects from the root
// build script is what Gradle's isolated-projects mode forbids, and this build already opts into
// the configuration cache; reaching across projects was the one thing standing between it and the
// next step.
//
// (The path this comment used to name, `gradle/detekt.gradle.kts`, does not exist. A pointer to
// nothing is worse than no pointer.)
//
// `./gradlew detekt` still runs every module's task, because Gradle matches an unqualified task
// name across all projects.

/**
 * The module dependency rules, enforced instead of described.
 *
 * `feature -> core, never the reverse`; `core:mvi`, `core:database` and `core:network` see only
 * `core:common`; `:core:testing` is a test-only dependency everywhere. Every one of those was a
 * sentence in CLAUDE.md and a comment in nine build scripts, checked by nobody — and the codebase
 * had already drifted around rules held the same way (a datasource missing its `flowOn`, two
 * datasources disagreeing about the same row check). A rule with no check is a rule with a
 * half-life.
 *
 * This reads the build *scripts* as text rather than the Gradle project model on purpose. Asking
 * another project for its configuration is precisely what isolated-projects mode forbids, which is
 * the same constraint that keeps detekt configured per module; reading a file is not. It is also
 * where the rule is actually written down, so what is checked is what a reviewer would read.
 */
abstract class VerifyModuleDependencies : DefaultTask() {

    @get:InputFiles
    abstract val buildScripts: ConfigurableFileCollection

    /** Module path -> the `project(...)` dependencies its production code may declare. */
    @get:Input
    abstract val allowedProductionDependencies: MapProperty<String, List<String>>

    /** Modules that may only ever be depended on from a test source set. */
    @get:Input
    abstract val testOnlyModules: SetProperty<String>

    @get:Input
    abstract val rootPath: Property<String>

    @TaskAction
    fun verify() {
        val allowed = allowedProductionDependencies.get()
        val testOnly = testOnlyModules.get()
        val violations = mutableListOf<String>()

        buildScripts.files.forEach { script ->
            val module = ":" + script.parentFile.toRelativeString(File(rootPath.get())).replace(File.separatorChar, ':')
            val permitted = allowed[module]
            if (permitted == null) {
                violations += "$module has no declared dependency allowlist — add one in build.gradle.kts"
                return@forEach
            }
            var inTestBlock = false
            script.readLines().forEach { line ->
                // Source-set blocks are one per line in every script here, e.g. `commonTest.dependencies {`.
                DEPENDENCY_BLOCK.find(line)?.let { inTestBlock = it.groupValues[1].contains("Test") }
                val dependency = PROJECT_DEPENDENCY.find(line)?.groupValues?.get(1) ?: return@forEach
                when {
                    inTestBlock && dependency in testOnly -> Unit
                    !inTestBlock && dependency in testOnly ->
                        violations += "$module declares $dependency outside a test source set"
                    dependency !in permitted ->
                        violations += "$module must not depend on $dependency"
                }
            }
        }

        if (violations.isNotEmpty()) {
            error(
                violations.sorted().joinToString(
                    prefix = "Module dependency rules violated:\n  - ",
                    separator = "\n  - ",
                    postfix = "\n\nSee the allowlist in the root build.gradle.kts.",
                ),
            )
        }
    }

    private companion object {
        val DEPENDENCY_BLOCK = Regex("""\b(\w+)\.dependencies\s*\{""")
        val PROJECT_DEPENDENCY = Regex("""project\("(:[^"]+)"\)""")
    }
}

// The rules themselves. A new module is a new entry: an unlisted one fails the task rather than
// being silently unconstrained, which is the failure mode an allowlist usually has.
val moduleDependencyRules: Map<String, List<String>> = buildMap {
    val coreCommon = listOf(":core:common")
    val everyCore = listOf(
        ":core:common",
        ":core:database",
        ":core:mvi",
        ":core:navigation",
        ":core:ui",
    )
    // The leaves of the graph. core:ui and core:navigation are pure Compose scaffolding and do not
    // even see core:common.
    put(":core:common", emptyList())
    put(":core:ui", emptyList())
    put(":core:navigation", emptyList())
    // One dependency each, and never on each other: this is the rule that keeps a domain layer from
    // acquiring a database or a ViewModel by accident.
    put(":core:database", coreCommon)
    put(":core:mvi", coreCommon)
    put(":core:network", coreCommon)
    // The one module allowed to reach several of them, because it provides their fakes.
    put(":core:testing", listOf(":core:common", ":core:database"))
    // feature -> core, never the reverse, and never feature -> feature.
    put(":feature:tasks", everyCore)
    put(":feature:grocery", everyCore)
    put(":feature:routines", everyCore)
    // The composition root is the only place that may name both layers.
    put(":shared", everyCore + listOf(":core:network", ":feature:tasks", ":feature:grocery", ":feature:routines"))
    put(":androidApp", listOf(":shared"))
}

val verifyModuleDependencies by tasks.registering(VerifyModuleDependencies::class) {
    group = "verification"
    description = "Fails when a module declares a project dependency the architecture does not allow."
    // Discovered from disk, not from the allowlist's own keys.
    //
    // Deriving the file list from `moduleDependencyRules.keys` meant this task only ever scanned
    // modules the allowlist already named — so a *new* module, which is the one case an allowlist
    // exists to catch, was not scanned at all and went silently unconstrained. The `permitted ==
    // null` branch below could never fire, and the rule stated in CLAUDE.md ("a new module needs an
    // entry there or the task fails") was not true of the code. Reading the directory is still not
    // reaching into another project's configuration, so isolated projects is unaffected.
    buildScripts.from(
        fileTree(rootDir) {
            // The root build script is not a module and has no dependencies block of its own.
            include("*/build.gradle.kts", "*/*/build.gradle.kts")
            exclude("build/**", "*/build/**", "*/*/build/**", ".claude/**", ".gradle/**")
        },
    )
    allowedProductionDependencies.set(moduleDependencyRules)
    // Nothing ships it, so a production dependency on it would put fakes in the release binary.
    testOnlyModules.set(setOf(":core:testing"))
    rootPath.set(rootDir.absolutePath)
}

// `check` on the root project, so CI's existing static-analysis job picks it up alongside detekt.
tasks.register("architectureCheck") {
    group = "verification"
    description = "Runs every architecture-level check."
    dependsOn(verifyModuleDependencies)
}
