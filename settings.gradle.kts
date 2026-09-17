rootProject.name = "PlannerKMP"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":shared")

include(":core:common")
include(":core:network")
include(":core:database")
include(":core:mvi")
include(":core:ui")
include(":core:navigation")
// Test fixtures only: no production code depends on it, so nothing it contains ships.
include(":core:testing")

include(":feature:grocery")
include(":feature:tasks")