# Tasks + Grocery Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the app's first two real features — a TODO/task list and a grocery list — as full vertical slices (domain, data, usecases, ViewModel, Compose UI, DI, navigation) reproducing the legacy business logic exactly, per the approved design.

**Architecture:** Two new Gradle modules, `:feature:tasks` and `:feature:grocery`, each following the existing `core/*` module pattern (KMP + Android multiplatform library + Compose + SQLDelight), depending on `core:common`, `core:mvi`, `core:database`, `core:navigation`, `core:ui`. `core:common` gains a `TodayProvider` abstraction; `core:database` gains a persisted `KeyValueCache` implementation and a `safeQuery` helper. `:shared` wires both features into Koin and a bottom-nav `App()`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin 4.1.1, SQLDelight 2.3.2, kotlinx-datetime (new), Navigation 3 1.1.1, kotlinx-coroutines 1.11.0.

**Spec:** `docs/superpowers/specs/2026-09-08-tasks-grocery-implementation-design.md` (architecture/behavior decisions) and `docs/specs/2026-09-08-legacy-todo-grocery-business-logic.md` (source business rules). Read both — this plan implements them; it doesn't repeat every rationale.

## Global Constraints

- Follow TDD for every file with real logic: write the failing test, run it, implement, run it green, commit. Pure DI/plugin wiring (Koin modules, Gradle config) has no dedicated test — verify it via the next task's test or a build/run command instead, same as this repo's existing `ioDispatcher`/`DatabaseDriverFactory` actuals.
- Every SQLDelight-backed datasource method goes through `core:database`'s `safeQuery` helper (Task 3), mapping exceptions/timeout to `AppResult.Failure(DomainError.Database(...))`.
- Dates are `kotlinx.datetime.LocalDate`, times-of-day are `kotlinx.datetime.LocalTime`. No `java.util.Date`/`Calendar` anywhere in new code.
- SQLDelight table names are suffixed `Entity` (`taskEntity`, `groceryItemEntity`, `keyValueEntry`) to avoid colliding with the domain model class names (`Task`, `GroceryItem`) SQLDelight would otherwise generate.
- Every mutating SQLDelight query (`insert`/`update`/`deleteById`) is followed by a `SELECT changes()` check; a mismatch throws `IllegalStateException`, which `safeQuery` catches and turns into `DomainError.Database` — this is how the legacy app's "didn't affect the expected row count" hard-failure is reproduced.
- Real datasource tests run against a real in-memory SQLite database via the JDBC `app.cash.sqldelight:sqlite-driver`, scoped only to each module's `androidHostTest` source set (JVM-only; this repo has no existing pattern for testing generated SQLDelight code, this establishes one). If the Kotlin DSL's typed `androidHostTest.dependencies { }` accessor doesn't resolve, use `sourceSets.getByName("androidHostTest") { dependencies { ... } }` instead — check the actual error and adjust; both are standard Gradle Kotlin DSL forms.
- Navigation 3's exact API (`entryProvider { }`, `entry<T> { }`, `NavDisplay`) hasn't been exercised anywhere in this repo yet (nothing currently calls it). The code below is written to the standard `androidx.navigation3` 1.1.1 surface as documented; if a name doesn't resolve, check the actual compiler error/IDE completion for the installed version and adjust the call site — the *behavior* (render the right screen for the back stack's top key) is what matters, not the exact call shape.
- Commit after every green test, per step.

---

## Task 1: `core:common` — `kotlinx-datetime` + `TodayProvider`

**Files:**
- Modify: `gradle/libs.versions.toml`
- Create: `core/common/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/common/TodayProvider.kt`
- Modify: `core/common/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/common/di/CommonModule.kt`
- Modify: `core/common/build.gradle.kts`

**Interfaces:**
- Produces: `interface TodayProvider { fun today(): LocalDate }`, `class SystemTodayProvider : TodayProvider`, registered as a Koin `single<TodayProvider>`.

- [ ] **Step 1: Add kotlinx-datetime to the version catalog**

In `gradle/libs.versions.toml`, add to `[versions]` (alphabetical among the existing entries):

```toml
kotlinx-datetime = "0.6.1"
```

Add to `[libraries]`, under the existing `# kotlinx` section:

```toml
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
```

- [ ] **Step 2: Add the dependency to `core:common`**

In `core/common/build.gradle.kts`, find the `commonMain.dependencies { }` block (currently likely empty or minimal — read the file first) and add:

```kotlin
commonMain.dependencies {
    api(libs.kotlinx.datetime)
}
```

Use `api` (not `implementation`) since `TodayProvider`'s public signature exposes `LocalDate`, and every downstream module (`core:database`, `feature:tasks`, `feature:grocery`) needs `LocalDate` visible transitively.

- [ ] **Step 3: Write `TodayProvider`**

Create `core/common/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/common/TodayProvider.kt`:

```kotlin
package pl.lejdi.plannerkmp.core.common

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

interface TodayProvider {
    fun today(): LocalDate
}

class SystemTodayProvider : TodayProvider {
    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
```

No dedicated test for `SystemTodayProvider` — it's a one-line wrapper over the platform clock, same as `CoroutineDispatchers.android.kt`'s `ioDispatcher` actual has none. `TodayProvider` gets exercised for real starting at Task 12 via a fake.

- [ ] **Step 4: Register it in Koin**

Read `core/common/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/common/di/CommonModule.kt`, then add the binding:

```kotlin
package pl.lejdi.plannerkmp.core.common.di

import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.DefaultCoroutineDispatchers
import pl.lejdi.plannerkmp.core.common.SystemTodayProvider
import pl.lejdi.plannerkmp.core.common.TodayProvider

val commonModule = module {
    single<CoroutineDispatchers> { DefaultCoroutineDispatchers() }
    single<TodayProvider> { SystemTodayProvider() }
}
```

- [ ] **Step 5: Build to confirm it compiles**

Run: `./gradlew :core:common:compileKotlinIosSimulatorArm64 :core:common:testAndroidHostTest`
Expected: BUILD SUCCESSFUL (no new tests yet, existing `AppResultTest` still passes).

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml core/common/build.gradle.kts core/common/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/common/TodayProvider.kt core/common/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/common/di/CommonModule.kt
git commit -m "Add kotlinx-datetime and TodayProvider to core:common"
```

---

## Task 2: `core:database` — persisted `KeyValueCache` via SQLDelight

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/database/build.gradle.kts`
- Create: `core/database/src/commonMain/sqldelight/pl/lejdi/plannerkmp/core/database/KeyValueEntry.sq`
- Create: `core/database/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/database/SqlDelightKeyValueCache.kt`
- Create: `core/database/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/database/di/KeyValueCacheModule.kt`
- Create: `core/database/src/androidHostTest/kotlin/pl/lejdi/plannerkmp/core/database/SqlDelightKeyValueCacheTest.kt`

**Interfaces:**
- Consumes: `KeyValueCache<K, V>` (existing, `core/database/src/commonMain/kotlin/.../KeyValueCache.kt`), `DatabaseDriverFactory` (existing).
- Produces: `class SqlDelightKeyValueCache<K, V>(queries: KeyValueEntryQueries, encodeKey: (K) -> String, serialize: (V) -> String, deserialize: (String) -> V) : KeyValueCache<K, V>`; Koin `single { Database(...) }` and `single { get<Database>().keyValueEntryQueries }` registered in `keyValueCacheModule`, added to `:shared`'s module list in Task 20.

- [ ] **Step 1: Add the SQLDelight plugin and JDBC test driver**

In `gradle/libs.versions.toml`, add to `[libraries]` under the existing `# SQLDelight` section:

```toml
sqldelight-sqliteDriver = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
```

(The `sqldelight` plugin id is already in the catalog's `[plugins]` section — no change needed there.)

- [ ] **Step 2: Apply the plugin and configure the database**

Read `core/database/build.gradle.kts`, then modify it to:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.sqldelight)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

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
            implementation(project(":core:common"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.koin.core)
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
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.sqldelight.sqliteDriver)
            }
        }
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("pl.lejdi.plannerkmp.core.database")
        }
    }
}
```

- [ ] **Step 3: Write the schema**

Create `core/database/src/commonMain/sqldelight/pl/lejdi/plannerkmp/core/database/KeyValueEntry.sq`:

```sql
CREATE TABLE keyValueEntry (
    id TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);

get:
SELECT value FROM keyValueEntry WHERE id = :id;

put:
INSERT OR REPLACE INTO keyValueEntry(id, value) VALUES (:id, :value);

remove:
DELETE FROM keyValueEntry WHERE id = :id;

clear:
DELETE FROM keyValueEntry;
```

- [ ] **Step 4: Write the failing test**

Create `core/database/src/androidHostTest/kotlin/pl/lejdi/plannerkmp/core/database/SqlDelightKeyValueCacheTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlDelightKeyValueCacheTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var cache: SqlDelightKeyValueCache<String, Int>

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val queries = Database(driver).keyValueEntryQueries
        cache = SqlDelightKeyValueCache(
            queries = queries,
            encodeKey = { it },
            serialize = { it.toString() },
            deserialize = { it.toInt() },
        )
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun getReturnsNullForMissingKey() = runTest {
        assertNull(cache.get("missing"))
    }

    @Test
    fun putThenGetReturnsStoredValue() = runTest {
        cache.put("answer", 42)

        assertEquals(42, cache.get("answer"))
    }

    @Test
    fun putOverwritesExistingValue() = runTest {
        cache.put("key", 1)

        cache.put("key", 2)

        assertEquals(2, cache.get("key"))
    }

    @Test
    fun removeDeletesOnlyThatKey() = runTest {
        cache.put("a", 1)
        cache.put("b", 2)

        cache.remove("a")

        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b"))
    }

    @Test
    fun clearRemovesEveryEntry() = runTest {
        cache.put("a", 1)
        cache.put("b", 2)

        cache.clear()

        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}
```

- [ ] **Step 5: Run it to verify it fails**

Run: `./gradlew :core:database:testAndroidHostTest --tests "pl.lejdi.plannerkmp.core.database.SqlDelightKeyValueCacheTest"`
Expected: compile failure — `SqlDelightKeyValueCache` doesn't exist yet.

- [ ] **Step 6: Implement `SqlDelightKeyValueCache`**

Create `core/database/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/database/SqlDelightKeyValueCache.kt`:

```kotlin
package pl.lejdi.plannerkmp.core.database

class SqlDelightKeyValueCache<K, V>(
    private val queries: KeyValueEntryQueries,
    private val encodeKey: (K) -> String,
    private val serialize: (V) -> String,
    private val deserialize: (String) -> V,
) : KeyValueCache<K, V> {

    override suspend fun get(key: K): V? =
        queries.get(encodeKey(key)).executeAsOneOrNull()?.let(deserialize)

    override suspend fun put(key: K, value: V) {
        queries.put(encodeKey(key), serialize(value))
    }

    override suspend fun remove(key: K) {
        queries.remove(encodeKey(key))
    }

    override suspend fun clear() {
        queries.clear()
    }
}
```

- [ ] **Step 7: Run it to verify it passes**

Run: `./gradlew :core:database:testAndroidHostTest --tests "pl.lejdi.plannerkmp.core.database.SqlDelightKeyValueCacheTest"`
Expected: PASS (5 tests).

- [ ] **Step 8: Wire Koin registration**

Create `core/database/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/database/di/KeyValueCacheModule.kt`:

```kotlin
package pl.lejdi.plannerkmp.core.database.di

import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.database.Database
import pl.lejdi.plannerkmp.core.database.DatabaseDriverFactory

val keyValueCacheModule = module {
    single {
        Database(get<DatabaseDriverFactory>().createDriver(Database.Schema, "keyvalue.db"))
    }
    single { get<Database>().keyValueEntryQueries }
}
```

This module is added to `:shared`'s `initKoin()` module list in Task 20 (it isn't wired up yet — nothing outside this module can see it until then, which is fine, it isn't exercised until `feature:tasks` needs it in Task 11).

- [ ] **Step 9: Run the full module test suite**

Run: `./gradlew :core:database:testAndroidHostTest`
Expected: PASS (existing `InMemoryKeyValueCacheTest` + new `SqlDelightKeyValueCacheTest`, 10 tests total).

- [ ] **Step 10: Commit**

```bash
git add gradle/libs.versions.toml core/database/build.gradle.kts core/database/src/commonMain/sqldelight core/database/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/database/SqlDelightKeyValueCache.kt core/database/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/database/di/KeyValueCacheModule.kt core/database/src/androidHostTest
git commit -m "Add SQLDelight-backed KeyValueCache to core:database"
```

---

## Task 3: `core:database` — `safeQuery` helper

**Files:**
- Create: `core/database/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/database/SafeQuery.kt`
- Create: `core/database/src/commonTest/kotlin/pl/lejdi/plannerkmp/core/database/SafeQueryTest.kt`
- Modify: `core/database/build.gradle.kts` (commonTest needs `kotlinx.coroutines.test`, already added in Task 2)

**Interfaces:**
- Produces: `suspend fun <T> safeQuery(timeout: Duration = 5.seconds, block: suspend () -> T): AppResult<T>` — used by every datasource method in `feature:tasks`/`feature:grocery` from Task 5 onward.

- [ ] **Step 1: Write the failing tests**

Create `core/database/src/commonTest/kotlin/pl/lejdi/plannerkmp/core/database/SafeQueryTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.core.database

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import pl.lejdi.plannerkmp.core.common.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SafeQueryTest {

    @Test
    fun safeQueryReturnsSuccessForCompletedBlock() = runTest {
        val result = safeQuery { 42 }

        assertEquals(AppResult.Success(42), result)
    }

    @Test
    fun safeQueryMapsThrownExceptionToFailure() = runTest {
        val result = safeQuery<Int> { throw IllegalStateException("boom") }

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun safeQueryMapsTimeoutToFailure() = runTest {
        val result = safeQuery(timeout = 10.milliseconds) {
            delay(1000.milliseconds)
            42
        }

        assertTrue(result is AppResult.Failure)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :core:database:testAndroidHostTest --tests "pl.lejdi.plannerkmp.core.database.SafeQueryTest"`
Expected: compile failure — `safeQuery` doesn't exist yet.

- [ ] **Step 3: Implement `safeQuery`**

Create `core/database/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/database/SafeQuery.kt`:

```kotlin
package pl.lejdi.plannerkmp.core.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [block] as a database read/write and maps the outcome to [AppResult]
 * instead of letting SQLDelight exceptions escape to callers. A timeout is a
 * hard failure, same as any other exception — it is not rethrown as
 * cancellation.
 */
suspend fun <T> safeQuery(
    timeout: Duration = 5.seconds,
    block: suspend () -> T,
): AppResult<T> = try {
    AppResult.Success(withTimeout(timeout) { block() })
} catch (e: TimeoutCancellationException) {
    AppResult.Failure(DomainError.Database("Database operation timed out", e))
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Failure(DomainError.Database(e.message ?: "Unknown database error", e))
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :core:database:testAndroidHostTest --tests "pl.lejdi.plannerkmp.core.database.SafeQueryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add core/database/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/database/SafeQuery.kt core/database/src/commonTest/kotlin/pl/lejdi/plannerkmp/core/database/SafeQueryTest.kt
git commit -m "Add safeQuery helper to core:database"
```

---

## Task 4: `:feature:grocery` module scaffold + `GroceryItem` domain model

**Files:**
- Modify: `settings.gradle.kts`
- Create: `feature/grocery/build.gradle.kts`
- Create: `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/domain/GroceryItem.kt`
- Create: `feature/grocery/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/domain/GroceryItemTest.kt`

**Interfaces:**
- Produces: `data class GroceryItem(val id: Long, val name: String, val description: String?)`, used by every later grocery task.

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, add after the `core:*` includes:

```kotlin
include(":feature:grocery")
```

- [ ] **Step 2: Create the module's build file**

Create `feature/grocery/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "pl.lejdi.plannerkmp.feature.grocery"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:mvi"))
            implementation(project(":core:database"))
            implementation(project(":core:navigation"))
            implementation(project(":core:ui"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation3.ui)
            implementation(libs.sqldelight.runtime)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.sqldelight.androidDriver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.sqldelight.sqliteDriver)
            }
        }
    }
}

sqldelight {
    databases {
        create("GroceryDatabase") {
            packageName.set("pl.lejdi.plannerkmp.feature.grocery.data")
        }
    }
}
```

- [ ] **Step 3: Write the failing test**

Create `feature/grocery/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/domain/GroceryItemTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GroceryItemTest {

    @Test
    fun copyWithChangedNameProducesDistinctEqualityAgainstOriginal() {
        val original = GroceryItem(id = 1, name = "Milk", description = null)

        val renamed = original.copy(name = "Oat milk")

        assertEquals("Oat milk", renamed.name)
        assertNotEquals(original, renamed)
    }

    @Test
    fun itemsWithSameFieldsAreEqual() {
        val a = GroceryItem(id = 1, name = "Bread", description = "Sourdough")
        val b = GroceryItem(id = 1, name = "Bread", description = "Sourdough")

        assertEquals(a, b)
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./gradlew :feature:grocery:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItemTest"`
Expected: compile failure — `GroceryItem` doesn't exist yet.

- [ ] **Step 5: Implement `GroceryItem`**

Create `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/domain/GroceryItem.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.domain

data class GroceryItem(
    val id: Long,
    val name: String,
    val description: String?,
)
```

- [ ] **Step 6: Run it to verify it passes**

Run: `./gradlew :feature:grocery:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItemTest"`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts feature/grocery
git commit -m "Scaffold feature:grocery module with GroceryItem domain model"
```

---

## Task 5: `feature:grocery` — datasource + SQLDelight schema

**Files:**
- Create: `feature/grocery/src/commonMain/sqldelight/pl/lejdi/plannerkmp/feature/grocery/data/GroceryItemEntity.sq`
- Create: `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/data/GroceryDatasource.kt`
- Create: `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/data/SqlDelightGroceryDatasource.kt`
- Create: `feature/grocery/src/androidHostTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/data/SqlDelightGroceryDatasourceTest.kt`

**Interfaces:**
- Consumes: `safeQuery` (Task 3), `AppResult`/`DomainError` (core:common), `GroceryItem` (Task 4).
- Produces: `interface GroceryDatasource { suspend fun getAllItems(): AppResult<List<GroceryItem>>; suspend fun addItem(item: GroceryItem): AppResult<Unit>; suspend fun editItem(item: GroceryItem): AppResult<Unit>; suspend fun deleteItem(id: Long): AppResult<Unit> }` and `class SqlDelightGroceryDatasource(queries: GroceryItemEntityQueries) : GroceryDatasource` — consumed by usecases in Task 6.

- [ ] **Step 1: Write the schema**

Create `feature/grocery/src/commonMain/sqldelight/pl/lejdi/plannerkmp/feature/grocery/data/GroceryItemEntity.sq`:

```sql
CREATE TABLE groceryItemEntity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT
);

selectAll:
SELECT * FROM groceryItemEntity;

insert:
INSERT INTO groceryItemEntity(name, description) VALUES (:name, :description);

update:
UPDATE groceryItemEntity SET name = :name, description = :description WHERE id = :id;

deleteById:
DELETE FROM groceryItemEntity WHERE id = :id;

changes:
SELECT changes();
```

- [ ] **Step 2: Declare the datasource contract**

Create `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/data/GroceryDatasource.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.data

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

interface GroceryDatasource {
    suspend fun getAllItems(): AppResult<List<GroceryItem>>
    suspend fun addItem(item: GroceryItem): AppResult<Unit>
    suspend fun editItem(item: GroceryItem): AppResult<Unit>
    suspend fun deleteItem(id: Long): AppResult<Unit>
}
```

- [ ] **Step 3: Write the failing test**

Create `feature/grocery/src/androidHostTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/data/SqlDelightGroceryDatasourceTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlDelightGroceryDatasourceTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var datasource: SqlDelightGroceryDatasource

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GroceryDatabase.Schema.create(driver)
        datasource = SqlDelightGroceryDatasource(GroceryDatabase(driver).groceryItemEntityQueries)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun addThenGetAllReturnsTheItem() = runTest {
        datasource.addItem(GroceryItem(id = 0, name = "Milk", description = "2%"))

        val result = datasource.getAllItems()

        assertTrue(result is AppResult.Success)
        assertEquals(1, result.data.size)
        assertEquals("Milk", result.data.single().name)
        assertEquals("2%", result.data.single().description)
    }

    @Test
    fun editUpdatesNameAndDescription() = runTest {
        datasource.addItem(GroceryItem(id = 0, name = "Milk", description = "2%"))
        val added = (datasource.getAllItems() as AppResult.Success).data.single()

        datasource.editItem(added.copy(name = "Oat milk", description = "Unsweetened"))

        val result = (datasource.getAllItems() as AppResult.Success).data.single()
        assertEquals("Oat milk", result.name)
        assertEquals("Unsweetened", result.description)
    }

    @Test
    fun deleteRemovesTheItem() = runTest {
        datasource.addItem(GroceryItem(id = 0, name = "Milk", description = null))
        val added = (datasource.getAllItems() as AppResult.Success).data.single()

        datasource.deleteItem(added.id)

        assertEquals(emptyList(), (datasource.getAllItems() as AppResult.Success).data)
    }

    @Test
    fun deleteOfNonExistentIdReturnsFailure() = runTest {
        val result = datasource.deleteItem(999)

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun editOfNonExistentIdReturnsFailure() = runTest {
        val result = datasource.editItem(GroceryItem(id = 999, name = "Ghost", description = null))

        assertTrue(result is AppResult.Failure)
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./gradlew :feature:grocery:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.grocery.data.SqlDelightGroceryDatasourceTest"`
Expected: compile failure — `SqlDelightGroceryDatasource` doesn't exist yet.

- [ ] **Step 5: Implement `SqlDelightGroceryDatasource`**

Create `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/data/SqlDelightGroceryDatasource.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.data

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.database.safeQuery
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

class SqlDelightGroceryDatasource(
    private val queries: GroceryItemEntityQueries,
) : GroceryDatasource {

    override suspend fun getAllItems(): AppResult<List<GroceryItem>> = safeQuery {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun addItem(item: GroceryItem): AppResult<Unit> = safeQuery {
        queries.insert(name = item.name, description = item.description)
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row inserted, got $rowsAffected" }
    }

    override suspend fun editItem(item: GroceryItem): AppResult<Unit> = safeQuery {
        queries.update(name = item.name, description = item.description, id = item.id)
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row updated, got $rowsAffected" }
    }

    override suspend fun deleteItem(id: Long): AppResult<Unit> = safeQuery {
        queries.deleteById(id)
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row deleted, got $rowsAffected" }
    }
}

private fun GroceryItemEntity.toDomain(): GroceryItem = GroceryItem(
    id = id,
    name = name,
    description = description,
)
```

- [ ] **Step 6: Run it to verify it passes**

Run: `./gradlew :feature:grocery:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.grocery.data.SqlDelightGroceryDatasourceTest"`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add feature/grocery/src/commonMain/sqldelight feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/data feature/grocery/src/androidHostTest
git commit -m "Add SQLDelight-backed GroceryDatasource"
```

---

## Task 6: `feature:grocery` — usecases + fake datasource

**Files:**
- Create: `feature/grocery/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/data/FakeGroceryDatasource.kt`
- Create: `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/domain/GroceryUseCases.kt`
- Create: `feature/grocery/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/domain/GroceryUseCasesTest.kt`

**Interfaces:**
- Consumes: `GroceryDatasource` (Task 5), `UseCase<P, R>` (core:mvi).
- Produces: `GetGroceryItems`, `AddGrocery`, `EditGrocery`, `DeleteGrocery` (all `UseCase` implementations) — consumed by `GroceryListViewModel` in Task 7. `FakeGroceryDatasource` — reused by Tasks 6 and 7's tests.

- [ ] **Step 1: Write the fake datasource (test fixture, no test of its own — it's exercised by the tests that use it)**

Create `feature/grocery/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/data/FakeGroceryDatasource.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.data

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

class FakeGroceryDatasource(initialItems: List<GroceryItem> = emptyList()) : GroceryDatasource {

    val items = mutableListOf<GroceryItem>().apply { addAll(initialItems) }
    var nextId: Long = (initialItems.maxOfOrNull { it.id } ?: 0L) + 1
    var failNextCall: Boolean = false

    override suspend fun getAllItems(): AppResult<List<GroceryItem>> {
        if (consumeFailure()) return failure()
        return AppResult.Success(items.toList())
    }

    override suspend fun addItem(item: GroceryItem): AppResult<Unit> {
        if (consumeFailure()) return failure()
        items.add(item.copy(id = nextId++))
        return AppResult.Success(Unit)
    }

    override suspend fun editItem(item: GroceryItem): AppResult<Unit> {
        if (consumeFailure()) return failure()
        val index = items.indexOfFirst { it.id == item.id }
        if (index == -1) return failure()
        items[index] = item
        return AppResult.Success(Unit)
    }

    override suspend fun deleteItem(id: Long): AppResult<Unit> {
        if (consumeFailure()) return failure()
        val removed = items.removeAll { it.id == id }
        return if (removed) AppResult.Success(Unit) else failure()
    }

    private fun consumeFailure(): Boolean {
        if (!failNextCall) return false
        failNextCall = false
        return true
    }

    private fun <T> failure(): AppResult<T> = AppResult.Failure(DomainError.Database("fake failure"))
}
```

- [ ] **Step 2: Write the failing tests**

Create `feature/grocery/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/domain/GroceryUseCasesTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.domain

import kotlinx.coroutines.test.runTest
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.grocery.data.FakeGroceryDatasource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroceryUseCasesTest {

    @Test
    fun getGroceryItemsReturnsWhatTheDatasourceHas() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )

        val result = GetGroceryItems(datasource).invoke(Unit)

        assertEquals(AppResult.Success(listOf(GroceryItem(id = 1, name = "Milk", description = null))), result)
    }

    @Test
    fun addGroceryDelegatesToDatasource() = runTest {
        val datasource = FakeGroceryDatasource()

        val result = AddGrocery(datasource).invoke(GroceryItem(id = 0, name = "Bread", description = null))

        assertTrue(result is AppResult.Success)
        assertEquals(1, datasource.items.size)
    }

    @Test
    fun editGroceryDelegatesToDatasource() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )

        val result = EditGrocery(datasource).invoke(GroceryItem(id = 1, name = "Oat milk", description = null))

        assertTrue(result is AppResult.Success)
        assertEquals("Oat milk", datasource.items.single().name)
    }

    @Test
    fun deleteGroceryDelegatesToDatasource() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )

        val result = DeleteGrocery(datasource).invoke(1)

        assertTrue(result is AppResult.Success)
        assertTrue(datasource.items.isEmpty())
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :feature:grocery:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.grocery.domain.GroceryUseCasesTest"`
Expected: compile failure — the usecases don't exist yet.

- [ ] **Step 4: Implement the usecases**

Create `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/domain/GroceryUseCases.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.domain

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.mvi.UseCase
import pl.lejdi.plannerkmp.feature.grocery.data.GroceryDatasource

class GetGroceryItems(
    private val datasource: GroceryDatasource,
) : UseCase<Unit, AppResult<List<GroceryItem>>> {
    override suspend fun invoke(params: Unit): AppResult<List<GroceryItem>> = datasource.getAllItems()
}

class AddGrocery(
    private val datasource: GroceryDatasource,
) : UseCase<GroceryItem, AppResult<Unit>> {
    override suspend fun invoke(params: GroceryItem): AppResult<Unit> = datasource.addItem(params)
}

class EditGrocery(
    private val datasource: GroceryDatasource,
) : UseCase<GroceryItem, AppResult<Unit>> {
    override suspend fun invoke(params: GroceryItem): AppResult<Unit> = datasource.editItem(params)
}

class DeleteGrocery(
    private val datasource: GroceryDatasource,
) : UseCase<Long, AppResult<Unit>> {
    override suspend fun invoke(params: Long): AppResult<Unit> = datasource.deleteItem(params)
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :feature:grocery:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.grocery.domain.GroceryUseCasesTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add feature/grocery/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/data/FakeGroceryDatasource.kt feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/domain/GroceryUseCases.kt feature/grocery/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/domain/GroceryUseCasesTest.kt
git commit -m "Add grocery usecases and fake datasource"
```

---

## Task 7: `feature:grocery` — `GroceryListViewModel`

**Files:**
- Create: `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/ui/GroceryListContract.kt`
- Create: `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/ui/GroceryListViewModel.kt`
- Create: `feature/grocery/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/ui/GroceryListViewModelTest.kt`

**Interfaces:**
- Consumes: `GetGroceryItems`, `AddGrocery`, `EditGrocery`, `DeleteGrocery` (Task 6), `BaseViewModel`/`MviState`/`MviEvent`/`MviEffect` (core:mvi).
- Produces: `GroceryListState`, `GroceryListEvent`, `GroceryListEffect`, `class GroceryListViewModel(...) : BaseViewModel<GroceryListState, GroceryListEvent, GroceryListEffect>()` — consumed by the DI module (Task 8) and the screen (Task 9).

- [ ] **Step 1: Write the contract**

Create `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/ui/GroceryListContract.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.ui

import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.MviState
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

data class GroceryListState(
    val isLoading: Boolean = true,
    val items: List<GroceryItem> = emptyList(),
    val isAddExpanded: Boolean = false,
    val addName: String = "",
    val addDescription: String = "",
    val addNameError: Boolean = false,
    val editingItemId: Long? = null,
    val editName: String = "",
    val editDescription: String = "",
    val editNameError: Boolean = false,
) : MviState

sealed interface GroceryListEvent : MviEvent {
    data object AddExpandClicked : GroceryListEvent
    data class AddNameChanged(val value: String) : GroceryListEvent
    data class AddDescriptionChanged(val value: String) : GroceryListEvent
    data object AddConfirmClicked : GroceryListEvent
    data object AddCancelClicked : GroceryListEvent
    data class EditExpandClicked(val item: GroceryItem) : GroceryListEvent
    data class EditNameChanged(val value: String) : GroceryListEvent
    data class EditDescriptionChanged(val value: String) : GroceryListEvent
    data object EditConfirmClicked : GroceryListEvent
    data object EditCancelClicked : GroceryListEvent
    data class CompleteItem(val id: Long) : GroceryListEvent
}

sealed interface GroceryListEffect : MviEffect {
    data class ShowError(val message: String) : GroceryListEffect
}
```

- [ ] **Step 2: Write the failing tests**

Create `feature/grocery/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/ui/GroceryListViewModelTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pl.lejdi.plannerkmp.feature.grocery.data.FakeGroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.domain.AddGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.DeleteGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.EditGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.GetGroceryItems
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroceryListViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(datasource: FakeGroceryDatasource) = GroceryListViewModel(
        getGroceryItems = GetGroceryItems(datasource),
        addGrocery = AddGrocery(datasource),
        editGrocery = EditGrocery(datasource),
        deleteGrocery = DeleteGrocery(datasource),
    )

    @Test
    fun loadsItemsOnCreation() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )

        val viewModel = viewModel(datasource)

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(1, viewModel.state.value.items.size)
    }

    @Test
    fun addConfirmWithBlankNameShowsErrorAndDoesNotCallAddGrocery() = runTest {
        val datasource = FakeGroceryDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(GroceryListEvent.AddExpandClicked)

        viewModel.onEvent(GroceryListEvent.AddConfirmClicked)

        assertTrue(viewModel.state.value.addNameError)
        assertTrue(datasource.items.isEmpty())
    }

    @Test
    fun addConfirmWithNameAddsItemAndCollapsesRow() = runTest {
        val datasource = FakeGroceryDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(GroceryListEvent.AddExpandClicked)
        viewModel.onEvent(GroceryListEvent.AddNameChanged("Bread"))

        viewModel.onEvent(GroceryListEvent.AddConfirmClicked)

        assertEquals(1, datasource.items.size)
        assertEquals("Bread", datasource.items.single().name)
        assertFalse(viewModel.state.value.isAddExpanded)
        assertEquals("", viewModel.state.value.addName)
    }

    @Test
    fun editExpandPrefillsCurrentValues() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = "2%")),
        )
        val viewModel = viewModel(datasource)

        viewModel.onEvent(GroceryListEvent.EditExpandClicked(datasource.items.single()))

        assertEquals(1, viewModel.state.value.editingItemId)
        assertEquals("Milk", viewModel.state.value.editName)
        assertEquals("2%", viewModel.state.value.editDescription)
    }

    @Test
    fun editConfirmWithBlankNameShowsErrorAndDoesNotCallEditGrocery() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )
        val viewModel = viewModel(datasource)
        viewModel.onEvent(GroceryListEvent.EditExpandClicked(datasource.items.single()))
        viewModel.onEvent(GroceryListEvent.EditNameChanged(""))

        viewModel.onEvent(GroceryListEvent.EditConfirmClicked)

        assertTrue(viewModel.state.value.editNameError)
        assertEquals("Milk", datasource.items.single().name)
    }

    @Test
    fun editConfirmWithNameUpdatesItemAndCollapsesRow() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )
        val viewModel = viewModel(datasource)
        viewModel.onEvent(GroceryListEvent.EditExpandClicked(datasource.items.single()))
        viewModel.onEvent(GroceryListEvent.EditNameChanged("Oat milk"))

        viewModel.onEvent(GroceryListEvent.EditConfirmClicked)

        assertEquals("Oat milk", datasource.items.single().name)
        assertNull(viewModel.state.value.editingItemId)
    }

    @Test
    fun completeItemDeletesIt() = runTest {
        val datasource = FakeGroceryDatasource(
            initialItems = listOf(GroceryItem(id = 1, name = "Milk", description = null)),
        )
        val viewModel = viewModel(datasource)

        viewModel.onEvent(GroceryListEvent.CompleteItem(1))

        assertTrue(datasource.items.isEmpty())
        assertTrue(viewModel.state.value.items.isEmpty())
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :feature:grocery:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.grocery.ui.GroceryListViewModelTest"`
Expected: compile failure — `GroceryListViewModel` doesn't exist yet.

- [ ] **Step 4: Implement `GroceryListViewModel`**

Create `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/ui/GroceryListViewModel.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.mvi.BaseViewModel
import pl.lejdi.plannerkmp.feature.grocery.domain.AddGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.DeleteGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.EditGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.GetGroceryItems
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

class GroceryListViewModel(
    private val getGroceryItems: GetGroceryItems,
    private val addGrocery: AddGrocery,
    private val editGrocery: EditGrocery,
    private val deleteGrocery: DeleteGrocery,
) : BaseViewModel<GroceryListState, GroceryListEvent, GroceryListEffect>() {

    override fun createInitialState() = GroceryListState()

    init {
        loadItems()
    }

    override fun onEvent(event: GroceryListEvent) {
        when (event) {
            is GroceryListEvent.AddExpandClicked -> setState { copy(isAddExpanded = true) }
            is GroceryListEvent.AddNameChanged -> setState { copy(addName = event.value, addNameError = false) }
            is GroceryListEvent.AddDescriptionChanged -> setState { copy(addDescription = event.value) }
            is GroceryListEvent.AddConfirmClicked -> confirmAdd()
            is GroceryListEvent.AddCancelClicked -> setState {
                copy(isAddExpanded = false, addName = "", addDescription = "", addNameError = false)
            }
            is GroceryListEvent.EditExpandClicked -> setState {
                copy(
                    editingItemId = event.item.id,
                    editName = event.item.name,
                    editDescription = event.item.description.orEmpty(),
                    editNameError = false,
                )
            }
            is GroceryListEvent.EditNameChanged -> setState { copy(editName = event.value, editNameError = false) }
            is GroceryListEvent.EditDescriptionChanged -> setState { copy(editDescription = event.value) }
            is GroceryListEvent.EditConfirmClicked -> confirmEdit()
            is GroceryListEvent.EditCancelClicked -> setState {
                copy(editingItemId = null, editName = "", editDescription = "", editNameError = false)
            }
            is GroceryListEvent.CompleteItem -> completeItem(event.id)
        }
    }

    private fun loadItems() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            when (val result = getGroceryItems(Unit)) {
                is AppResult.Success -> setState { copy(isLoading = false, items = result.data) }
                is AppResult.Failure -> {
                    setState { copy(isLoading = false) }
                    sendEffect(GroceryListEffect.ShowError(result.error.message))
                }
            }
        }
    }

    private fun confirmAdd() {
        val current = state.value
        if (current.addName.isBlank()) {
            setState { copy(addNameError = true) }
            return
        }
        viewModelScope.launch {
            val item = GroceryItem(id = 0, name = current.addName, description = current.addDescription.ifBlank { null })
            when (val result = addGrocery(item)) {
                is AppResult.Success -> {
                    setState { copy(isAddExpanded = false, addName = "", addDescription = "", addNameError = false) }
                    loadItems()
                }
                is AppResult.Failure -> sendEffect(GroceryListEffect.ShowError(result.error.message))
            }
        }
    }

    private fun confirmEdit() {
        val current = state.value
        val editingId = current.editingItemId ?: return
        if (current.editName.isBlank()) {
            setState { copy(editNameError = true) }
            return
        }
        viewModelScope.launch {
            val item = GroceryItem(id = editingId, name = current.editName, description = current.editDescription.ifBlank { null })
            when (val result = editGrocery(item)) {
                is AppResult.Success -> {
                    setState { copy(editingItemId = null, editName = "", editDescription = "", editNameError = false) }
                    loadItems()
                }
                is AppResult.Failure -> sendEffect(GroceryListEffect.ShowError(result.error.message))
            }
        }
    }

    private fun completeItem(id: Long) {
        viewModelScope.launch {
            when (val result = deleteGrocery(id)) {
                is AppResult.Success -> loadItems()
                is AppResult.Failure -> sendEffect(GroceryListEffect.ShowError(result.error.message))
            }
        }
    }
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :feature:grocery:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.grocery.ui.GroceryListViewModelTest"`
Expected: PASS (7 tests).

- [ ] **Step 6: Commit**

```bash
git add feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/ui feature/grocery/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/grocery/ui
git commit -m "Add GroceryListViewModel"
```

---

## Task 8: `feature:grocery` — Koin DI module

**Files:**
- Create: `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/di/GroceryModule.kt`

**Interfaces:**
- Consumes: `DatabaseDriverFactory` (core:database), everything from Tasks 5–7.
- Produces: `val groceryModule: Module` — added to `:shared`'s `initKoin()` in Task 20.

- [ ] **Step 1: Write the module**

Create `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/di/GroceryModule.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.database.DatabaseDriverFactory
import pl.lejdi.plannerkmp.feature.grocery.data.GroceryDatabase
import pl.lejdi.plannerkmp.feature.grocery.data.GroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.data.SqlDelightGroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.domain.AddGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.DeleteGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.EditGrocery
import pl.lejdi.plannerkmp.feature.grocery.domain.GetGroceryItems
import pl.lejdi.plannerkmp.feature.grocery.ui.GroceryListViewModel

val groceryModule = module {
    single { GroceryDatabase(get<DatabaseDriverFactory>().createDriver(GroceryDatabase.Schema, "grocery.db")) }
    single { get<GroceryDatabase>().groceryItemEntityQueries }
    single<GroceryDatasource> { SqlDelightGroceryDatasource(get()) }

    factory { GetGroceryItems(get()) }
    factory { AddGrocery(get()) }
    factory { EditGrocery(get()) }
    factory { DeleteGrocery(get()) }

    viewModel { GroceryListViewModel(get(), get(), get(), get()) }
}
```

No dedicated test — this is pure DI wiring; it's exercised for real once `:shared` registers it in Task 20 and the app runs.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :feature:grocery:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/di
git commit -m "Add grocery Koin module"
```

---

## Task 9: `feature:grocery` — Compose UI, nav keys, entry contributor

**Files:**
- Create: `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/GroceryNavKey.kt`
- Create: `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/GroceryNavEntryProviderContributor.kt`
- Create: `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/ui/GroceryListScreen.kt`
- Modify: `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/di/GroceryModule.kt`

**Interfaces:**
- Consumes: `NavEntryProviderContributor`, `NavKey` (core:navigation), `GroceryListViewModel` (Task 7), `LoadingView`/`ErrorView` (core:ui).
- Produces: `object GroceryNavKey : NavKey`, `class GroceryNavEntryProviderContributor : NavEntryProviderContributor` — the latter Koin-multibound so `:shared` picks it up automatically in Task 20 without referencing `feature:grocery` types directly.

No unit test for this task — this repo has no Compose UI testing infrastructure yet (no `compose.uiTest`/Robolectric dependency in the catalog), so this is verified by a compile check instead, same as the existing `App.kt` placeholder.

- [ ] **Step 1: Define the nav key**

Create `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/GroceryNavKey.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery

import androidx.navigation3.runtime.NavKey

data object GroceryNavKey : NavKey
```

- [ ] **Step 2: Write the screen**

Create `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/ui/GroceryListScreen.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.lejdi.plannerkmp.core.ui.components.ErrorView
import pl.lejdi.plannerkmp.core.ui.components.LoadingView
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem

@Composable
fun GroceryListScreen(viewModel: GroceryListViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onEvent(GroceryListEvent.AddExpandClicked) }) {
                Text("+")
            }
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingView(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isAddExpanded) {
                item { AddRow(state, viewModel) }
            }
            items(state.items, key = { it.id }) { item ->
                if (state.editingItemId == item.id) {
                    EditRow(state, viewModel)
                } else {
                    GroceryRow(item, viewModel)
                }
            }
        }
    }
}

@Composable
private fun AddRow(state: GroceryListState, viewModel: GroceryListViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = state.addName,
                onValueChange = { viewModel.onEvent(GroceryListEvent.AddNameChanged(it)) },
                label = { Text("Name") },
                isError = state.addNameError,
            )
            OutlinedTextField(
                value = state.addDescription,
                onValueChange = { viewModel.onEvent(GroceryListEvent.AddDescriptionChanged(it)) },
                label = { Text("Description") },
            )
            Row {
                Button(onClick = { viewModel.onEvent(GroceryListEvent.AddConfirmClicked) }) { Text("Add") }
                Button(onClick = { viewModel.onEvent(GroceryListEvent.AddCancelClicked) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun EditRow(state: GroceryListState, viewModel: GroceryListViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = state.editName,
                onValueChange = { viewModel.onEvent(GroceryListEvent.EditNameChanged(it)) },
                label = { Text("Name") },
                isError = state.editNameError,
            )
            OutlinedTextField(
                value = state.editDescription,
                onValueChange = { viewModel.onEvent(GroceryListEvent.EditDescriptionChanged(it)) },
                label = { Text("Description") },
            )
            Row {
                Button(onClick = { viewModel.onEvent(GroceryListEvent.EditConfirmClicked) }) { Text("Save") }
                Button(onClick = { viewModel.onEvent(GroceryListEvent.EditCancelClicked) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun GroceryRow(item: GroceryItem, viewModel: GroceryListViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .pointerInput(item.id) {
                detectTapGestures(
                    onLongPress = { viewModel.onEvent(GroceryListEvent.EditExpandClicked(item)) },
                    onTap = { viewModel.onEvent(GroceryListEvent.CompleteItem(item.id)) },
                )
            },
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(item.name)
            item.description?.let { Text(it) }
        }
    }
}
```

This uses `detectTapGestures` from `androidx.compose.foundation.gestures` — add that import:

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
```

- [ ] **Step 3: Write the entry provider contributor**

Create `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/GroceryNavEntryProviderContributor.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.grocery

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.grocery.ui.GroceryListScreen

class GroceryNavEntryProviderContributor : NavEntryProviderContributor {
    override fun EntryProviderScope<NavKey>.contribute() {
        entry<GroceryNavKey> { GroceryListScreen() }
    }
}
```

- [ ] **Step 4: Register the contributor in Koin**

Modify `feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery/di/GroceryModule.kt`, adding to the existing module body:

```kotlin
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.grocery.GroceryNavEntryProviderContributor
```

and inside `module { }`, add:

```kotlin
    factoryOf(::GroceryNavEntryProviderContributor) { bind<NavEntryProviderContributor>() }
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :feature:grocery:compileKotlinIosSimulatorArm64 :feature:grocery:testAndroidHostTest`
Expected: BUILD SUCCESSFUL, all existing grocery tests still pass.

- [ ] **Step 6: Commit**

```bash
git add feature/grocery/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/grocery
git commit -m "Add grocery Compose UI, nav key, and entry provider contributor"
```

---

## Task 10: `:feature:tasks` module scaffold + `Task`/`TaskType` domain model

**Files:**
- Modify: `settings.gradle.kts`
- Create: `feature/tasks/build.gradle.kts`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/Task.kt`
- Create: `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/TaskTest.kt`

**Interfaces:**
- Produces: `data class Task(id: Long, name: String, description: String?, startDate: LocalDate, endDate: LocalDate?, hour: LocalTime?, daysInterval: Int, asap: Boolean)`, `enum class TaskType { Asap, OneTime, Periodic }`, `val Task.type: TaskType` — used by every later tasks-feature task.

- [ ] **Step 1: Register the module**

In `settings.gradle.kts`, add after `include(":feature:grocery")`:

```kotlin
include(":feature:tasks")
```

- [ ] **Step 2: Create the module's build file**

Create `feature/tasks/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "pl.lejdi.plannerkmp.feature.tasks"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:mvi"))
            implementation(project(":core:database"))
            implementation(project(":core:navigation"))
            implementation(project(":core:ui"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation3.ui)
            implementation(libs.sqldelight.runtime)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.sqldelight.androidDriver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.sqldelight.sqliteDriver)
            }
        }
    }
}

sqldelight {
    databases {
        create("TasksDatabase") {
            packageName.set("pl.lejdi.plannerkmp.feature.tasks.data")
        }
    }
}
```

- [ ] **Step 3: Write the failing test**

Create `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/TaskTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskTest {

    private val baseTask = Task(
        id = 1,
        name = "Water plants",
        description = null,
        startDate = LocalDate(2026, 1, 1),
        endDate = null,
        hour = null,
        daysInterval = 0,
        asap = false,
    )

    @Test
    fun asapTrueIsAsapTypeRegardlessOfDaysInterval() {
        val task = baseTask.copy(asap = true, daysInterval = 5)

        assertEquals(TaskType.Asap, task.type)
    }

    @Test
    fun notAsapWithZeroDaysIntervalIsOneTime() {
        val task = baseTask.copy(asap = false, daysInterval = 0)

        assertEquals(TaskType.OneTime, task.type)
    }

    @Test
    fun notAsapWithPositiveDaysIntervalIsPeriodic() {
        val task = baseTask.copy(asap = false, daysInterval = 7)

        assertEquals(TaskType.Periodic, task.type)
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.domain.TaskTest"`
Expected: compile failure — `Task`/`TaskType` don't exist yet.

- [ ] **Step 5: Implement `Task` and `TaskType`**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/Task.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class Task(
    val id: Long,
    val name: String,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val hour: LocalTime?,
    val daysInterval: Int,
    val asap: Boolean,
)

enum class TaskType { Asap, OneTime, Periodic }

val Task.type: TaskType
    get() = when {
        asap -> TaskType.Asap
        daysInterval > 0 -> TaskType.Periodic
        else -> TaskType.OneTime
    }
```

- [ ] **Step 6: Run it to verify it passes**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.domain.TaskTest"`
Expected: PASS (3 tests).

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts feature/tasks
git commit -m "Scaffold feature:tasks module with Task/TaskType domain model"
```

---

## Task 11: `feature:tasks` — datasource + SQLDelight schema (incl. last-cleanup date)

**Files:**
- Create: `feature/tasks/src/commonMain/sqldelight/pl/lejdi/plannerkmp/feature/tasks/data/TaskEntity.sq`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/data/ColumnAdapters.kt`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/data/TasksDatasource.kt`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/data/SqlDelightTasksDatasource.kt`
- Create: `feature/tasks/src/androidHostTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/data/SqlDelightTasksDatasourceTest.kt`

**Interfaces:**
- Consumes: `safeQuery` (Task 3), `Task` (Task 10), `KeyValueCache<K, V>` (core:database).
- Produces: `interface TasksDatasource { getAllTasks(); addTask(task); editTask(task); deleteTask(id); getLastCleanupDate(); setLastCleanupDate(date) }` and `class SqlDelightTasksDatasource(queries: TaskEntityQueries, lastCleanupDateCache: KeyValueCache<Unit, LocalDate>) : TasksDatasource` — consumed by every usecase from Task 12 onward.

- [ ] **Step 1: Write the schema**

Create `feature/tasks/src/commonMain/sqldelight/pl/lejdi/plannerkmp/feature/tasks/data/TaskEntity.sq`:

```sql
CREATE TABLE taskEntity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT,
    startDate TEXT AS kotlinx.datetime.LocalDate NOT NULL,
    endDate TEXT AS kotlinx.datetime.LocalDate,
    hour TEXT AS kotlinx.datetime.LocalTime,
    daysInterval INTEGER NOT NULL DEFAULT 0,
    asap INTEGER NOT NULL DEFAULT 0
);

selectAll:
SELECT * FROM taskEntity;

insert:
INSERT INTO taskEntity(name, description, startDate, endDate, hour, daysInterval, asap)
VALUES (:name, :description, :startDate, :endDate, :hour, :daysInterval, :asap);

update:
UPDATE taskEntity
SET name = :name, description = :description, startDate = :startDate, endDate = :endDate, hour = :hour, daysInterval = :daysInterval, asap = :asap
WHERE id = :id;

deleteById:
DELETE FROM taskEntity WHERE id = :id;

changes:
SELECT changes();
```

- [ ] **Step 2: Write the custom column adapters**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/data/ColumnAdapters.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.data

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

internal object LocalDateColumnAdapter : ColumnAdapter<LocalDate, String> {
    override fun decode(databaseValue: String): LocalDate = LocalDate.parse(databaseValue)
    override fun encode(value: LocalDate): String = value.toString()
}

internal object LocalTimeColumnAdapter : ColumnAdapter<LocalTime, String> {
    override fun decode(databaseValue: String): LocalTime = LocalTime.parse(databaseValue)
    override fun encode(value: LocalTime): String = value.toString()
}
```

- [ ] **Step 3: Declare the datasource contract**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/data/TasksDatasource.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.data

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

interface TasksDatasource {
    suspend fun getAllTasks(): AppResult<List<Task>>
    suspend fun addTask(task: Task): AppResult<Unit>
    suspend fun editTask(task: Task): AppResult<Unit>
    suspend fun deleteTask(id: Long): AppResult<Unit>
    suspend fun getLastCleanupDate(): AppResult<LocalDate?>
    suspend fun setLastCleanupDate(date: LocalDate): AppResult<Unit>
}
```

- [ ] **Step 4: Write the failing test**

Create `feature/tasks/src/androidHostTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/data/SqlDelightTasksDatasourceTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.database.InMemoryKeyValueCache
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightTasksDatasourceTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var datasource: SqlDelightTasksDatasource

    private val sampleTask = Task(
        id = 0,
        name = "Water plants",
        description = "Every houseplant",
        startDate = LocalDate(2026, 1, 1),
        endDate = LocalDate(2026, 6, 1),
        hour = LocalTime(9, 30),
        daysInterval = 7,
        asap = false,
    )

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        TasksDatabase.Schema.create(driver)
        val queries = TasksDatabase(
            driver,
            taskEntityAdapter = TaskEntity.Adapter(
                startDateAdapter = LocalDateColumnAdapter,
                endDateAdapter = LocalDateColumnAdapter,
                hourAdapter = LocalTimeColumnAdapter,
            ),
        ).taskEntityQueries
        datasource = SqlDelightTasksDatasource(queries, InMemoryKeyValueCache())
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun addThenGetAllRoundTripsEveryField() = runTest {
        datasource.addTask(sampleTask)

        val result = datasource.getAllTasks()

        assertTrue(result is AppResult.Success)
        val stored = result.data.single()
        assertEquals(sampleTask.name, stored.name)
        assertEquals(sampleTask.description, stored.description)
        assertEquals(sampleTask.startDate, stored.startDate)
        assertEquals(sampleTask.endDate, stored.endDate)
        assertEquals(sampleTask.hour, stored.hour)
        assertEquals(sampleTask.daysInterval, stored.daysInterval)
        assertEquals(sampleTask.asap, stored.asap)
    }

    @Test
    fun editUpdatesStoredFields() = runTest {
        datasource.addTask(sampleTask)
        val added = (datasource.getAllTasks() as AppResult.Success).data.single()

        datasource.editTask(added.copy(name = "Water plants twice", daysInterval = 3))

        val stored = (datasource.getAllTasks() as AppResult.Success).data.single()
        assertEquals("Water plants twice", stored.name)
        assertEquals(3, stored.daysInterval)
    }

    @Test
    fun deleteRemovesTheTask() = runTest {
        datasource.addTask(sampleTask)
        val added = (datasource.getAllTasks() as AppResult.Success).data.single()

        datasource.deleteTask(added.id)

        assertEquals(emptyList(), (datasource.getAllTasks() as AppResult.Success).data)
    }

    @Test
    fun deleteOfNonExistentIdReturnsFailure() = runTest {
        val result = datasource.deleteTask(999)

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun lastCleanupDateIsNullUntilSet() = runTest {
        val result = datasource.getLastCleanupDate()

        assertTrue(result is AppResult.Success)
        assertNull(result.data)
    }

    @Test
    fun setThenGetLastCleanupDateRoundTrips() = runTest {
        datasource.setLastCleanupDate(LocalDate(2026, 9, 8))

        val result = datasource.getLastCleanupDate()

        assertEquals(AppResult.Success(LocalDate(2026, 9, 8)), result)
    }
}
```

Note: `InMemoryKeyValueCache` (already implemented in `core:database`, Task 3's prerequisite) stands in for the real `SqlDelightKeyValueCache` here — this test is about `SqlDelightTasksDatasource`'s own task-table logic, not re-testing the key-value cache (already covered in Task 2's test). The real `SqlDelightKeyValueCache` is wired in for production in Task 18.

- [ ] **Step 5: Run it to verify it fails**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.data.SqlDelightTasksDatasourceTest"`
Expected: compile failure — `SqlDelightTasksDatasource` doesn't exist yet.

- [ ] **Step 6: Implement `SqlDelightTasksDatasource`**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/data/SqlDelightTasksDatasource.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.data

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.database.KeyValueCache
import pl.lejdi.plannerkmp.core.database.safeQuery
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

class SqlDelightTasksDatasource(
    private val queries: TaskEntityQueries,
    private val lastCleanupDateCache: KeyValueCache<Unit, LocalDate>,
) : TasksDatasource {

    override suspend fun getAllTasks(): AppResult<List<Task>> = safeQuery {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun addTask(task: Task): AppResult<Unit> = safeQuery {
        queries.insert(
            name = task.name,
            description = task.description,
            startDate = task.startDate,
            endDate = task.endDate,
            hour = task.hour,
            daysInterval = task.daysInterval.toLong(),
            asap = if (task.asap) 1L else 0L,
        )
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row inserted, got $rowsAffected" }
    }

    override suspend fun editTask(task: Task): AppResult<Unit> = safeQuery {
        queries.update(
            id = task.id,
            name = task.name,
            description = task.description,
            startDate = task.startDate,
            endDate = task.endDate,
            hour = task.hour,
            daysInterval = task.daysInterval.toLong(),
            asap = if (task.asap) 1L else 0L,
        )
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row updated, got $rowsAffected" }
    }

    override suspend fun deleteTask(id: Long): AppResult<Unit> = safeQuery {
        queries.deleteById(id)
        val rowsAffected = queries.changes().executeAsOne()
        check(rowsAffected == 1L) { "Expected 1 row deleted, got $rowsAffected" }
    }

    override suspend fun getLastCleanupDate(): AppResult<LocalDate?> = safeQuery {
        lastCleanupDateCache.get(Unit)
    }

    override suspend fun setLastCleanupDate(date: LocalDate): AppResult<Unit> = safeQuery {
        lastCleanupDateCache.put(Unit, date)
    }
}

private fun TaskEntity.toDomain(): Task = Task(
    id = id,
    name = name,
    description = description,
    startDate = startDate,
    endDate = endDate,
    hour = hour,
    daysInterval = daysInterval.toInt(),
    asap = asap != 0L,
)
```

- [ ] **Step 7: Run it to verify it passes**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.data.SqlDelightTasksDatasourceTest"`
Expected: PASS (6 tests).

- [ ] **Step 8: Commit**

```bash
git add feature/tasks/src/commonMain/sqldelight feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/data feature/tasks/src/androidHostTest
git commit -m "Add SQLDelight-backed TasksDatasource"
```

---

## Task 12: `feature:tasks` — test fixtures + `GetTasksForDashboard`

**Files:**
- Create: `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/data/FakeTasksDatasource.kt`
- Create: `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/FakeTodayProvider.kt`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/DashboardDay.kt`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/GetTasksForDashboard.kt`
- Create: `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/GetTasksForDashboardTest.kt`

**Interfaces:**
- Consumes: `TasksDatasource` (Task 11), `TodayProvider` (core:common), `Task`/`TaskType` (Task 10).
- Produces: `data class DashboardDay(date: LocalDate, tasks: List<Task>)`, `class GetTasksForDashboard(datasource, todayProvider) : UseCase<Unit, AppResult<List<DashboardDay>>>`. `FakeTasksDatasource` and `FakeTodayProvider` are test fixtures reused by every remaining task/ViewModel test in this module (Tasks 13–17).

- [ ] **Step 1: Write the fake datasource fixture**

Create `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/data/FakeTasksDatasource.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.data

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

class FakeTasksDatasource(
    initialTasks: List<Task> = emptyList(),
    initialLastCleanupDate: LocalDate? = null,
) : TasksDatasource {

    val tasks = mutableListOf<Task>().apply { addAll(initialTasks) }
    var lastCleanupDate: LocalDate? = initialLastCleanupDate
    var nextId: Long = (initialTasks.maxOfOrNull { it.id } ?: 0L) + 1
    var failNextCall: Boolean = false

    override suspend fun getAllTasks(): AppResult<List<Task>> {
        if (consumeFailure()) return failure()
        return AppResult.Success(tasks.toList())
    }

    override suspend fun addTask(task: Task): AppResult<Unit> {
        if (consumeFailure()) return failure()
        tasks.add(task.copy(id = nextId++))
        return AppResult.Success(Unit)
    }

    override suspend fun editTask(task: Task): AppResult<Unit> {
        if (consumeFailure()) return failure()
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index == -1) return failure()
        tasks[index] = task
        return AppResult.Success(Unit)
    }

    override suspend fun deleteTask(id: Long): AppResult<Unit> {
        if (consumeFailure()) return failure()
        val removed = tasks.removeAll { it.id == id }
        return if (removed) AppResult.Success(Unit) else failure()
    }

    override suspend fun getLastCleanupDate(): AppResult<LocalDate?> {
        if (consumeFailure()) return failure()
        return AppResult.Success(lastCleanupDate)
    }

    override suspend fun setLastCleanupDate(date: LocalDate): AppResult<Unit> {
        if (consumeFailure()) return failure()
        lastCleanupDate = date
        return AppResult.Success(Unit)
    }

    private fun consumeFailure(): Boolean {
        if (!failNextCall) return false
        failNextCall = false
        return true
    }

    private fun <T> failure(): AppResult<T> = AppResult.Failure(DomainError.Database("fake failure"))
}
```

- [ ] **Step 2: Write the fake today-provider fixture**

Create `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/FakeTodayProvider.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.TodayProvider

class FakeTodayProvider(private var date: LocalDate) : TodayProvider {
    override fun today(): LocalDate = date
    fun setToday(newDate: LocalDate) { date = newDate }
}
```

- [ ] **Step 3: Add `DashboardDay`**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/DashboardDay.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.LocalDate

data class DashboardDay(val date: LocalDate, val tasks: List<Task>)
```

- [ ] **Step 4: Write the failing tests**

Create `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/GetTasksForDashboardTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.tasks.FakeTodayProvider
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetTasksForDashboardTest {

    private val today = LocalDate(2026, 9, 8)

    private fun task(
        id: Long = 1,
        name: String = "Task",
        startDate: LocalDate = today,
        endDate: LocalDate? = null,
        hour: LocalTime? = null,
        daysInterval: Int = 0,
        asap: Boolean = false,
    ) = Task(id, name, null, startDate, endDate, hour, daysInterval, asap)

    private suspend fun days(vararg tasks: Task): List<DashboardDay> {
        val datasource = FakeTasksDatasource(initialTasks = tasks.toList())
        val useCase = GetTasksForDashboard(datasource, FakeTodayProvider(today))
        val result = useCase.invoke(Unit)
        assertTrue(result is AppResult.Success)
        return result.data
    }

    @Test
    fun alwaysReturnsExactlyEightDaysStartingToday() = runTest {
        val result = days()

        assertEquals(8, result.size)
        assertEquals(today, result.first().date)
        assertEquals(today.plus(7, kotlinx.datetime.DateTimeUnit.DAY), result.last().date)
    }

    @Test
    fun asapTaskShowsOnlyOnToday() = runTest {
        val asapTask = task(asap = true, startDate = today.minus(3, kotlinx.datetime.DateTimeUnit.DAY))

        val result = days(asapTask)

        assertEquals(listOf(asapTask), result[0].tasks)
        (1..7).forEach { offset -> assertTrue(result[offset].tasks.isEmpty()) }
    }

    @Test
    fun oneTimeTaskShowsOnlyOnItsStartDate() = runTest {
        val futureDate = today.plus(3, kotlinx.datetime.DateTimeUnit.DAY)
        val oneTimeTask = task(startDate = futureDate, daysInterval = 0, asap = false)

        val result = days(oneTimeTask)

        assertEquals(listOf(oneTimeTask), result[3].tasks)
        (0..7).filter { it != 3 }.forEach { offset -> assertTrue(result[offset].tasks.isEmpty()) }
    }

    @Test
    fun periodicTaskRecursEveryIntervalWithNoEndDate() = runTest {
        val periodicTask = task(startDate = today, daysInterval = 3, endDate = null)

        val result = days(periodicTask)

        assertEquals(listOf(periodicTask), result[0].tasks)
        assertTrue(result[1].tasks.isEmpty())
        assertTrue(result[2].tasks.isEmpty())
        assertEquals(listOf(periodicTask), result[3].tasks)
        assertEquals(listOf(periodicTask), result[6].tasks)
    }

    @Test
    fun periodicTaskStopsAfterEndDate() = runTest {
        val periodicTask = task(startDate = today, daysInterval = 3, endDate = today.plus(3, kotlinx.datetime.DateTimeUnit.DAY))

        val result = days(periodicTask)

        assertEquals(listOf(periodicTask), result[0].tasks)
        assertEquals(listOf(periodicTask), result[3].tasks)
        assertTrue(result[6].tasks.isEmpty())
    }

    @Test
    fun periodicTaskNotYetStartedDoesNotShow() = runTest {
        val periodicTask = task(startDate = today.plus(5, kotlinx.datetime.DateTimeUnit.DAY), daysInterval = 2)

        val result = days(periodicTask)

        (0..4).forEach { offset -> assertTrue(result[offset].tasks.isEmpty()) }
        assertEquals(listOf(periodicTask), result[5].tasks)
    }

    @Test
    fun tasksWithHourSortBeforeAsapBeforeOneTimeBeforePeriodic() = runTest {
        val withHour = task(id = 1, hour = LocalTime(14, 0), startDate = today)
        val asapTask = task(id = 2, asap = true)
        val oneTime = task(id = 3, startDate = today, daysInterval = 0, asap = false)
        val periodic = task(id = 4, startDate = today, daysInterval = 1)

        val result = days(periodic, oneTime, asapTask, withHour)

        assertEquals(listOf(withHour, asapTask, oneTime, periodic), result[0].tasks)
    }

    @Test
    fun multipleTasksWithHourSortByHourAscending() = runTest {
        val later = task(id = 1, hour = LocalTime(18, 0), startDate = today)
        val earlier = task(id = 2, hour = LocalTime(8, 0), startDate = today)

        val result = days(later, earlier)

        assertEquals(listOf(earlier, later), result[0].tasks)
    }

    @Test
    fun emptyDayStillRendersAsAnEntryWithNoTasks() = runTest {
        val result = days()

        result.forEach { day -> assertTrue(day.tasks.isEmpty()) }
    }

    @Test
    fun propagatesDatasourceFailure() = runTest {
        val datasource = FakeTasksDatasource().apply { failNextCall = true }
        val useCase = GetTasksForDashboard(datasource, FakeTodayProvider(today))

        val result = useCase.invoke(Unit)

        assertTrue(result is AppResult.Failure)
    }
}
```

- [ ] **Step 5: Run it to verify it fails**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.domain.GetTasksForDashboardTest"`
Expected: compile failure — `GetTasksForDashboard` doesn't exist yet.

- [ ] **Step 6: Implement `GetTasksForDashboard`**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/GetTasksForDashboard.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.mvi.UseCase
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatasource

private const val NUMBER_OF_VISIBLE_DAYS = 8

class GetTasksForDashboard(
    private val datasource: TasksDatasource,
    private val todayProvider: TodayProvider,
) : UseCase<Unit, AppResult<List<DashboardDay>>> {

    override suspend fun invoke(params: Unit): AppResult<List<DashboardDay>> {
        val tasksResult = datasource.getAllTasks()
        if (tasksResult is AppResult.Failure) return tasksResult
        val tasks = (tasksResult as AppResult.Success).data

        val today = todayProvider.today()
        val days = (0 until NUMBER_OF_VISIBLE_DAYS).map { offset ->
            val date = today.plus(offset, DateTimeUnit.DAY)
            DashboardDay(
                date = date,
                tasks = tasks.filter { isVisibleOn(it, date, today) }.sortedWith(dashboardTaskComparator),
            )
        }
        return AppResult.Success(days)
    }

    private fun isVisibleOn(task: Task, date: LocalDate, today: LocalDate): Boolean = when {
        task.asap -> date == today
        task.daysInterval == 0 -> date == task.startDate
        else -> date >= task.startDate &&
            task.startDate.daysUntil(date) % task.daysInterval == 0 &&
            (task.endDate == null || date <= task.endDate)
    }
}

private val dashboardTaskComparator = compareBy<Task>(
    { priorityBucket(it) },
    { it.hour?.toString() ?: "" },
)

private fun priorityBucket(task: Task): Int = when {
    task.hour != null -> 0
    task.asap -> 1
    task.daysInterval == 0 -> 2
    else -> 3
}
```

- [ ] **Step 7: Run it to verify it passes**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.domain.GetTasksForDashboardTest"`
Expected: PASS (10 tests).

- [ ] **Step 8: Commit**

```bash
git add feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/data/FakeTasksDatasource.kt feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/FakeTodayProvider.kt feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/DashboardDay.kt feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/GetTasksForDashboard.kt feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/GetTasksForDashboardTest.kt
git commit -m "Add GetTasksForDashboard usecase with test fixtures"
```

---

## Task 13: `feature:tasks` — `MarkTaskComplete`

**Files:**
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/MarkTaskComplete.kt`
- Create: `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/MarkTaskCompleteTest.kt`

**Interfaces:**
- Consumes: `TasksDatasource` (Task 11), `Task` (Task 10), `FakeTasksDatasource` (Task 12).
- Produces: `class MarkTaskComplete(datasource) : UseCase<Task, AppResult<Unit>>` — consumed by `DashboardViewModel` in Task 16.

- [ ] **Step 1: Write the failing tests**

Create `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/MarkTaskCompleteTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkTaskCompleteTest {

    private val startDate = LocalDate(2026, 9, 1)

    private fun task(daysInterval: Int, endDate: LocalDate? = null, asap: Boolean = false) = Task(
        id = 1,
        name = "Task",
        description = null,
        startDate = startDate,
        endDate = endDate,
        hour = null,
        daysInterval = daysInterval,
        asap = asap,
    )

    @Test
    fun asapTaskIsDeleted() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 0, asap = true)))

        MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun oneTimeTaskIsDeleted() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 0)))

        MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun periodicTaskWithNoEndDateAdvancesByOneInterval() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 7)))

        MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertEquals(1, datasource.tasks.size)
        assertEquals(startDate.plus(7, DateTimeUnit.DAY), datasource.tasks.single().startDate)
    }

    @Test
    fun periodicTaskAdvancingWithinEndDateIsUpdatedNotDeleted() = runTest {
        val endDate = startDate.plus(10, DateTimeUnit.DAY)
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 7, endDate = endDate)))

        MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertEquals(1, datasource.tasks.size)
        assertEquals(startDate.plus(7, DateTimeUnit.DAY), datasource.tasks.single().startDate)
    }

    @Test
    fun periodicTaskAdvancingPastEndDateIsDeleted() = runTest {
        val endDate = startDate.plus(5, DateTimeUnit.DAY)
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 7, endDate = endDate)))

        MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun propagatesDatasourceFailure() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(task(daysInterval = 0))).apply { failNextCall = true }

        val result = MarkTaskComplete(datasource).invoke(datasource.tasks.single())

        assertTrue(result is AppResult.Failure)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskCompleteTest"`
Expected: compile failure — `MarkTaskComplete` doesn't exist yet.

- [ ] **Step 3: Implement `MarkTaskComplete`**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/MarkTaskComplete.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.mvi.UseCase
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatasource

class MarkTaskComplete(
    private val datasource: TasksDatasource,
) : UseCase<Task, AppResult<Unit>> {

    override suspend fun invoke(params: Task): AppResult<Unit> {
        if (params.daysInterval == 0) return datasource.deleteTask(params.id)

        val newStartDate = params.startDate.plus(params.daysInterval, DateTimeUnit.DAY)
        val endDate = params.endDate
        return if (endDate != null && newStartDate > endDate) {
            datasource.deleteTask(params.id)
        } else {
            datasource.editTask(params.copy(startDate = newStartDate))
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskCompleteTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/MarkTaskComplete.kt feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/MarkTaskCompleteTest.kt
git commit -m "Add MarkTaskComplete usecase"
```

---

## Task 14: `feature:tasks` — `UpdateTasksDates` (cleanup)

**Files:**
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/UpdateTasksDates.kt`
- Create: `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/UpdateTasksDatesTest.kt`

**Interfaces:**
- Consumes: `TasksDatasource` (Task 11), `TodayProvider` (core:common), `FakeTasksDatasource`/`FakeTodayProvider` (Task 12).
- Produces: `class UpdateTasksDates(datasource, todayProvider) : UseCase<Unit, AppResult<Unit>>` — consumed by `DashboardViewModel` in Task 16.

- [ ] **Step 1: Write the failing tests**

Create `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/UpdateTasksDatesTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.tasks.FakeTodayProvider
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateTasksDatesTest {

    private val today = LocalDate(2026, 9, 8)

    private fun task(
        id: Long = 1,
        startDate: LocalDate,
        endDate: LocalDate? = null,
        daysInterval: Int = 0,
        asap: Boolean = false,
    ) = Task(id, "Task", null, startDate, endDate, null, daysInterval, asap)

    @Test
    fun runsWhenNoCleanupDateStoredYet() = runTest {
        val datasource = FakeTasksDatasource(initialLastCleanupDate = null)

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertEquals(today, datasource.lastCleanupDate)
    }

    @Test
    fun skipsWhenLastCleanupWasAlreadyToday() = runTest {
        val staleTask = task(startDate = today.minus(5, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(staleTask), initialLastCleanupDate = today)

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertEquals(1, datasource.tasks.size, "a stale one-time task would have been deleted if cleanup ran")
    }

    @Test
    fun runsWhenLastCleanupWasBeforeToday() = runTest {
        val staleTask = task(startDate = today.minus(5, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(
            initialTasks = listOf(staleTask),
            initialLastCleanupDate = today.minus(1, DateTimeUnit.DAY),
        )

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertTrue(datasource.tasks.isEmpty())
        assertEquals(today, datasource.lastCleanupDate)
    }

    @Test
    fun asapTaskIsNeverTouched() = runTest {
        val asapTask = task(startDate = today.minus(30, DateTimeUnit.DAY), asap = true)
        val datasource = FakeTasksDatasource(initialTasks = listOf(asapTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertEquals(listOf(asapTask), datasource.tasks)
    }

    @Test
    fun oneTimeTaskInThePastIsDeleted() = runTest {
        val pastTask = task(startDate = today.minus(1, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(pastTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun oneTimeTaskTodayOrFutureIsKept() = runTest {
        val futureTask = task(startDate = today, daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(futureTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertEquals(listOf(futureTask), datasource.tasks)
    }

    @Test
    fun periodicTaskPastItsEndDateIsDeleted() = runTest {
        val expiredTask = task(startDate = today.minus(20, DateTimeUnit.DAY), daysInterval = 5, endDate = today.minus(1, DateTimeUnit.DAY))
        val datasource = FakeTasksDatasource(initialTasks = listOf(expiredTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun periodicTaskWithNoEndDateIsNeverDeletedByEndDateRule() = runTest {
        val longRunningTask = task(startDate = today, daysInterval = 5, endDate = null)
        val datasource = FakeTasksDatasource(initialTasks = listOf(longRunningTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertEquals(1, datasource.tasks.size)
    }

    @Test
    fun periodicTaskWithPastStartDateCatchesUpInOneJumpPreservingPhase() = runTest {
        // started 2026-08-01, every 3 days: due dates are 8/1, 8/4, ..., the next due date on/after
        // today (2026-09-08) is 9/9 (8/1 + 13*3 = 9/9), since 9/8 itself isn't a multiple of 3 away.
        val lateTask = task(startDate = LocalDate(2026, 8, 1), daysInterval = 3, endDate = null)
        val datasource = FakeTasksDatasource(initialTasks = listOf(lateTask))

        UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        val updated = datasource.tasks.single()
        assertEquals(LocalDate(2026, 9, 9), updated.startDate)
        assertTrue(updated.startDate >= today)
        assertEquals(0, lateTask.startDate.daysUntil(updated.startDate) % 3)
    }

    @Test
    fun lastCleanupDateOnlyAdvancesWhenTheWholePassSucceeds() = runTest {
        val pastTask = task(startDate = today.minus(1, DateTimeUnit.DAY), daysInterval = 0)
        val datasource = FakeTasksDatasource(initialTasks = listOf(pastTask)).apply { failNextCall = true }

        val result = UpdateTasksDates(datasource, FakeTodayProvider(today)).invoke(Unit)

        assertTrue(result is AppResult.Failure)
        assertEquals(null, datasource.lastCleanupDate)
    }
}
```

Note the test imports `kotlinx.datetime.daysUntil` implicitly through `LocalDate` — add `import kotlinx.datetime.daysUntil` to this test file's imports alongside the others.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.domain.UpdateTasksDatesTest"`
Expected: compile failure — `UpdateTasksDates` doesn't exist yet.

- [ ] **Step 3: Implement `UpdateTasksDates`**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/UpdateTasksDates.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.mvi.UseCase
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatasource
import kotlin.math.ceil

class UpdateTasksDates(
    private val datasource: TasksDatasource,
    private val todayProvider: TodayProvider,
) : UseCase<Unit, AppResult<Unit>> {

    override suspend fun invoke(params: Unit): AppResult<Unit> {
        val today = todayProvider.today()

        val lastCleanupResult = datasource.getLastCleanupDate()
        if (lastCleanupResult is AppResult.Failure) return lastCleanupResult
        val lastCleanupDate = (lastCleanupResult as AppResult.Success).data
        if (lastCleanupDate != null && lastCleanupDate >= today) return AppResult.Success(Unit)

        val tasksResult = datasource.getAllTasks()
        if (tasksResult is AppResult.Failure) return tasksResult

        for (task in (tasksResult as AppResult.Success).data) {
            val result = processTask(task, today)
            if (result is AppResult.Failure) return result
        }

        return datasource.setLastCleanupDate(today)
    }

    private suspend fun processTask(task: Task, today: LocalDate): AppResult<Unit> = when {
        task.asap -> AppResult.Success(Unit)
        task.daysInterval == 0 -> {
            if (task.startDate < today) datasource.deleteTask(task.id) else AppResult.Success(Unit)
        }
        task.endDate != null && task.endDate < today -> datasource.deleteTask(task.id)
        task.startDate < today -> {
            val daysSinceStart = task.startDate.daysUntil(today)
            val intervalsElapsed = ceil(daysSinceStart.toDouble() / task.daysInterval).toInt()
            val newStartDate = task.startDate.plus(intervalsElapsed * task.daysInterval, DateTimeUnit.DAY)
            datasource.editTask(task.copy(startDate = newStartDate))
        }
        else -> AppResult.Success(Unit)
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.domain.UpdateTasksDatesTest"`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/UpdateTasksDates.kt feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/UpdateTasksDatesTest.kt
git commit -m "Add UpdateTasksDates cleanup usecase"
```

---

## Task 15: `feature:tasks` — `AddTask`/`EditTask`/`DeleteTask`

**Files:**
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/TaskCrudUseCases.kt`
- Create: `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/TaskCrudUseCasesTest.kt`

**Interfaces:**
- Consumes: `TasksDatasource` (Task 11), `FakeTasksDatasource` (Task 12).
- Produces: `AddTask`, `EditTask`, `DeleteTask` (`UseCase` implementations) — consumed by `TaskEditViewModel` in Task 17.

- [ ] **Step 1: Write the failing tests**

Create `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/TaskCrudUseCasesTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.domain

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskCrudUseCasesTest {

    private val sampleTask = Task(
        id = 0,
        name = "Water plants",
        description = null,
        startDate = LocalDate(2026, 9, 8),
        endDate = null,
        hour = null,
        daysInterval = 0,
        asap = false,
    )

    @Test
    fun addTaskDelegatesToDatasource() = runTest {
        val datasource = FakeTasksDatasource()

        val result = AddTask(datasource).invoke(sampleTask)

        assertTrue(result is AppResult.Success)
        assertEquals(1, datasource.tasks.size)
    }

    @Test
    fun editTaskDelegatesToDatasource() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(sampleTask.copy(id = 1)))

        val result = EditTask(datasource).invoke(sampleTask.copy(id = 1, name = "Water plants twice"))

        assertTrue(result is AppResult.Success)
        assertEquals("Water plants twice", datasource.tasks.single().name)
    }

    @Test
    fun deleteTaskDelegatesToDatasource() = runTest {
        val datasource = FakeTasksDatasource(initialTasks = listOf(sampleTask.copy(id = 1)))

        val result = DeleteTask(datasource).invoke(1)

        assertTrue(result is AppResult.Success)
        assertTrue(datasource.tasks.isEmpty())
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.domain.TaskCrudUseCasesTest"`
Expected: compile failure — the usecases don't exist yet.

- [ ] **Step 3: Implement the usecases**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/TaskCrudUseCases.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.domain

import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.mvi.UseCase
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatasource

class AddTask(
    private val datasource: TasksDatasource,
) : UseCase<Task, AppResult<Unit>> {
    override suspend fun invoke(params: Task): AppResult<Unit> = datasource.addTask(params)
}

class EditTask(
    private val datasource: TasksDatasource,
) : UseCase<Task, AppResult<Unit>> {
    override suspend fun invoke(params: Task): AppResult<Unit> = datasource.editTask(params)
}

class DeleteTask(
    private val datasource: TasksDatasource,
) : UseCase<Long, AppResult<Unit>> {
    override suspend fun invoke(params: Long): AppResult<Unit> = datasource.deleteTask(params)
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.domain.TaskCrudUseCasesTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/TaskCrudUseCases.kt feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/domain/TaskCrudUseCasesTest.kt
git commit -m "Add AddTask/EditTask/DeleteTask usecases"
```

---

## Task 16: `feature:tasks` — `DashboardViewModel`

**Files:**
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardContract.kt`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardViewModel.kt`
- Create: `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `GetTasksForDashboard` (Task 12), `UpdateTasksDates` (Task 14), `MarkTaskComplete` (Task 13), `BaseViewModel`/`MviState`/`MviEvent`/`MviEffect` (core:mvi).
- Produces: `DashboardState`, `DashboardEvent`, `DashboardEffect`, `class DashboardViewModel(...) : BaseViewModel<DashboardState, DashboardEvent, DashboardEffect>()` — consumed by the DI module (Task 18) and the screen (Task 19).

- [ ] **Step 1: Write the contract**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardContract.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.datetime.LocalDate
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.MviState
import pl.lejdi.plannerkmp.feature.tasks.domain.DashboardDay
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

data class DashboardState(
    val isLoading: Boolean = true,
    val today: LocalDate? = null,
    val days: List<DashboardDay> = emptyList(),
    val revealedTaskId: Long? = null,
) : MviState

sealed interface DashboardEvent : MviEvent {
    data class RevealActions(val taskId: Long) : DashboardEvent
    data object DismissActions : DashboardEvent
    data class CompleteTask(val task: Task) : DashboardEvent
    data object AddTaskClicked : DashboardEvent
    data class EditTaskClicked(val task: Task) : DashboardEvent
}

sealed interface DashboardEffect : MviEffect {
    data object NavigateToAddTask : DashboardEffect
    data class NavigateToEditTask(val task: Task) : DashboardEffect
    data class ShowError(val message: String) : DashboardEffect
}
```

- [ ] **Step 2: Write the failing tests**

Create `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardViewModelTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.feature.tasks.FakeTodayProvider
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.domain.GetTasksForDashboard
import pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskComplete
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.UpdateTasksDates
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val today = LocalDate(2026, 9, 8)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(datasource: FakeTasksDatasource, todayProvider: FakeTodayProvider = FakeTodayProvider(today)) =
        DashboardViewModel(
            getTasksForDashboard = GetTasksForDashboard(datasource, todayProvider),
            updateTasksDates = UpdateTasksDates(datasource, todayProvider),
            markTaskComplete = MarkTaskComplete(datasource),
        )

    @Test
    fun loadsEightDaysOnCreation() = runTest {
        val viewModel = viewModel(FakeTasksDatasource())

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(8, viewModel.state.value.days.size)
        assertEquals(today, viewModel.state.value.today)
    }

    @Test
    fun runsCleanupBeforeLoadingSoStaleTasksAreAlreadyGone() = runTest {
        val staleTask = Task(1, "Stale", null, today.minus(5, DateTimeUnit.DAY), null, null, 0, false)
        val datasource = FakeTasksDatasource(initialTasks = listOf(staleTask))

        val viewModel = viewModel(datasource)

        assertEquals(0, viewModel.state.value.days.sumOf { it.tasks.size })
    }

    @Test
    fun revealActionsSetsRevealedTaskId() = runTest {
        val viewModel = viewModel(FakeTasksDatasource())

        viewModel.onEvent(DashboardEvent.RevealActions(taskId = 5))

        assertEquals(5, viewModel.state.value.revealedTaskId)
    }

    @Test
    fun dismissActionsClearsRevealedTaskId() = runTest {
        val viewModel = viewModel(FakeTasksDatasource())
        viewModel.onEvent(DashboardEvent.RevealActions(taskId = 5))

        viewModel.onEvent(DashboardEvent.DismissActions)

        assertNull(viewModel.state.value.revealedTaskId)
    }

    @Test
    fun completeTaskDeletesOneTimeTaskAndReloads() = runTest {
        val oneTimeTask = Task(1, "Task", null, today, null, null, 0, false)
        val datasource = FakeTasksDatasource(initialTasks = listOf(oneTimeTask))
        val viewModel = viewModel(datasource)

        viewModel.onEvent(DashboardEvent.CompleteTask(oneTimeTask))

        assertEquals(0, viewModel.state.value.days.sumOf { it.tasks.size })
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.ui.DashboardViewModelTest"`
Expected: compile failure — `DashboardViewModel` doesn't exist yet.

- [ ] **Step 4: Implement `DashboardViewModel`**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardViewModel.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.mvi.BaseViewModel
import pl.lejdi.plannerkmp.feature.tasks.domain.GetTasksForDashboard
import pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskComplete
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.UpdateTasksDates

class DashboardViewModel(
    private val getTasksForDashboard: GetTasksForDashboard,
    private val updateTasksDates: UpdateTasksDates,
    private val markTaskComplete: MarkTaskComplete,
) : BaseViewModel<DashboardState, DashboardEvent, DashboardEffect>() {

    override fun createInitialState() = DashboardState()

    init {
        loadDashboard()
    }

    override fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.RevealActions -> setState { copy(revealedTaskId = event.taskId) }
            is DashboardEvent.DismissActions -> setState { copy(revealedTaskId = null) }
            is DashboardEvent.CompleteTask -> completeTask(event.task)
            is DashboardEvent.AddTaskClicked -> sendEffect(DashboardEffect.NavigateToAddTask)
            is DashboardEvent.EditTaskClicked -> sendEffect(DashboardEffect.NavigateToEditTask(event.task))
        }
    }

    private fun loadDashboard() {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            updateTasksDates(Unit)
            when (val result = getTasksForDashboard(Unit)) {
                is AppResult.Success -> setState {
                    copy(isLoading = false, days = result.data, today = result.data.firstOrNull()?.date)
                }
                is AppResult.Failure -> {
                    setState { copy(isLoading = false) }
                    sendEffect(DashboardEffect.ShowError(result.error.message))
                }
            }
        }
    }

    private fun completeTask(task: Task) {
        viewModelScope.launch {
            when (val result = markTaskComplete(task)) {
                is AppResult.Success -> {
                    setState { copy(revealedTaskId = null) }
                    loadDashboard()
                }
                is AppResult.Failure -> sendEffect(DashboardEffect.ShowError(result.error.message))
            }
        }
    }
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.ui.DashboardViewModelTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardContract.kt feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardViewModel.kt feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardViewModelTest.kt
git commit -m "Add DashboardViewModel"
```

---

## Task 17: `feature:tasks` — `TaskEditViewModel`

**Files:**
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/TaskEditContract.kt`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/TaskEditViewModel.kt`
- Create: `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/TaskEditViewModelTest.kt`

**Interfaces:**
- Consumes: `AddTask`/`EditTask`/`DeleteTask` (Task 15), `TodayProvider` (core:common), `Task`/`TaskType` (Task 10).
- Produces: `TaskEditState`, `TaskEditEvent`, `TaskEditEffect`, `class TaskEditViewModel(initialTask: Task?, ...) : BaseViewModel<TaskEditState, TaskEditEvent, TaskEditEffect>()` — consumed by the DI module (Task 18) and the screen (Task 19).

This is the densest single file in the plan — it encodes every add/edit-form rule from the spec (type-driven field visibility handled by the UI reading `state.type`; ASAP start-date reset; `endDate` clearing; `daysInterval` forcing with no minimum; name validation; auto-snap `endDate`; delete-without-save for a brand-new task).

- [ ] **Step 1: Write the contract**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/TaskEditContract.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.MviState
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType

data class TaskEditState(
    val taskId: Long? = null,
    val name: String = "",
    val description: String = "",
    val type: TaskType = TaskType.Asap,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val hour: LocalTime? = null,
    val daysInterval: String = "",
    val nameError: Boolean = false,
) : MviState

sealed interface TaskEditEvent : MviEvent {
    data class NameChanged(val value: String) : TaskEditEvent
    data class DescriptionChanged(val value: String) : TaskEditEvent
    data class TypeChanged(val value: TaskType) : TaskEditEvent
    data class StartDateChanged(val value: LocalDate) : TaskEditEvent
    data class EndDateChanged(val value: LocalDate) : TaskEditEvent
    data class HourChanged(val value: LocalTime?) : TaskEditEvent
    data class DaysIntervalChanged(val value: String) : TaskEditEvent
    data object SaveClicked : TaskEditEvent
    data object DeleteClicked : TaskEditEvent
}

sealed interface TaskEditEffect : MviEffect {
    data object NavigateBack : TaskEditEffect
    data class ShowError(val message: String) : TaskEditEffect
}
```

- [ ] **Step 2: Write the failing tests**

Create `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/TaskEditViewModelTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pl.lejdi.plannerkmp.feature.tasks.FakeTodayProvider
import pl.lejdi.plannerkmp.feature.tasks.data.FakeTasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.domain.AddTask
import pl.lejdi.plannerkmp.feature.tasks.domain.DeleteTask
import pl.lejdi.plannerkmp.feature.tasks.domain.EditTask
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditViewModelTest {

    private val today = LocalDate(2026, 9, 8)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(datasource: FakeTasksDatasource, initialTask: Task? = null) = TaskEditViewModel(
        initialTask = initialTask,
        addTask = AddTask(datasource),
        editTask = EditTask(datasource),
        deleteTask = DeleteTask(datasource),
        todayProvider = FakeTodayProvider(today),
    )

    @Test
    fun newTaskDefaultsToAsapStartingToday() = runTest {
        val viewModel = viewModel(FakeTasksDatasource())

        assertEquals(TaskType.Asap, viewModel.state.value.type)
        assertEquals(today, viewModel.state.value.startDate)
    }

    @Test
    fun editingExistingTaskPrefillsAllFields() = runTest {
        val existing = Task(1, "Water plants", "Every plant", today, null, null, 0, false)

        val viewModel = viewModel(FakeTasksDatasource(), initialTask = existing)

        assertEquals("Water plants", viewModel.state.value.name)
        assertEquals("Every plant", viewModel.state.value.description)
        assertEquals(TaskType.OneTime, viewModel.state.value.type)
    }

    @Test
    fun saveWithBlankNameShowsErrorAndDoesNotSave() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertTrue(viewModel.state.value.nameError)
        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun savingAsapTaskAlwaysResetsStartDateToToday() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(TaskEditEvent.NameChanged("Call mom"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Asap))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today.plus(10, DateTimeUnit.DAY)))

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertEquals(today, datasource.tasks.single().startDate)
        assertTrue(datasource.tasks.single().asap)
    }

    @Test
    fun savingNonPeriodicTypeClearsEndDate() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(TaskEditEvent.NameChanged("One-off"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.OneTime))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today))
        viewModel.onEvent(TaskEditEvent.EndDateChanged(today.plus(30, DateTimeUnit.DAY)))

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertNull(datasource.tasks.single().endDate)
    }

    @Test
    fun savingPeriodicTypeKeepsEndDate() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        val endDate = today.plus(30, DateTimeUnit.DAY)
        viewModel.onEvent(TaskEditEvent.NameChanged("Recurring"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today))
        viewModel.onEvent(TaskEditEvent.EndDateChanged(endDate))
        viewModel.onEvent(TaskEditEvent.DaysIntervalChanged("7"))

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertEquals(endDate, datasource.tasks.single().endDate)
        assertEquals(7, datasource.tasks.single().daysInterval)
    }

    @Test
    fun savingPeriodicTypeWithBlankIntervalSilentlySavesZero() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(TaskEditEvent.NameChanged("Recurring"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today))

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertEquals(0, datasource.tasks.single().daysInterval)
    }

    @Test
    fun savingOneTimeTypeForcesDaysIntervalToZeroEvenIfFieldHadAStaleValue() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource)
        viewModel.onEvent(TaskEditEvent.NameChanged("One-off"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.DaysIntervalChanged("5"))
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.OneTime))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today))

        viewModel.onEvent(TaskEditEvent.SaveClicked)

        assertEquals(0, datasource.tasks.single().daysInterval)
    }

    @Test
    fun changingStartDatePastCurrentEndDateSnapsEndDateForward() = runTest {
        val viewModel = viewModel(FakeTasksDatasource())
        viewModel.onEvent(TaskEditEvent.TypeChanged(TaskType.Periodic))
        viewModel.onEvent(TaskEditEvent.StartDateChanged(today))
        viewModel.onEvent(TaskEditEvent.EndDateChanged(today.plus(2, DateTimeUnit.DAY)))

        viewModel.onEvent(TaskEditEvent.StartDateChanged(today.plus(5, DateTimeUnit.DAY)))

        assertEquals(today.plus(5, DateTimeUnit.DAY), viewModel.state.value.endDate)
    }

    @Test
    fun deletingBrandNewTaskJustNavigatesBackWithoutCallingAddTask() = runTest {
        val datasource = FakeTasksDatasource()
        val viewModel = viewModel(datasource, initialTask = null)

        viewModel.onEvent(TaskEditEvent.DeleteClicked)

        assertTrue(datasource.tasks.isEmpty())
    }

    @Test
    fun deletingExistingTaskCallsDeleteTask() = runTest {
        val existing = Task(1, "Task", null, today, null, null, 0, false)
        val datasource = FakeTasksDatasource(initialTasks = listOf(existing))
        val viewModel = viewModel(datasource, initialTask = existing)

        viewModel.onEvent(TaskEditEvent.DeleteClicked)

        assertTrue(datasource.tasks.isEmpty())
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.ui.TaskEditViewModelTest"`
Expected: compile failure — `TaskEditViewModel` doesn't exist yet.

- [ ] **Step 4: Implement `TaskEditViewModel`**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/TaskEditViewModel.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pl.lejdi.plannerkmp.core.common.AppResult
import pl.lejdi.plannerkmp.core.common.TodayProvider
import pl.lejdi.plannerkmp.core.mvi.BaseViewModel
import pl.lejdi.plannerkmp.feature.tasks.domain.AddTask
import pl.lejdi.plannerkmp.feature.tasks.domain.DeleteTask
import pl.lejdi.plannerkmp.feature.tasks.domain.EditTask
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType
import pl.lejdi.plannerkmp.feature.tasks.domain.type

class TaskEditViewModel(
    initialTask: Task?,
    private val addTask: AddTask,
    private val editTask: EditTask,
    private val deleteTask: DeleteTask,
    private val todayProvider: TodayProvider,
) : BaseViewModel<TaskEditState, TaskEditEvent, TaskEditEffect>() {

    private val originalTaskId: Long? = initialTask?.id

    override fun createInitialState(): TaskEditState = if (initialTask != null) {
        TaskEditState(
            taskId = initialTask.id,
            name = initialTask.name,
            description = initialTask.description.orEmpty(),
            type = initialTask.type,
            startDate = initialTask.startDate,
            endDate = initialTask.endDate,
            hour = initialTask.hour,
            daysInterval = if (initialTask.daysInterval > 0) initialTask.daysInterval.toString() else "",
        )
    } else {
        TaskEditState(type = TaskType.Asap, startDate = todayProvider.today())
    }

    override fun onEvent(event: TaskEditEvent) {
        when (event) {
            is TaskEditEvent.NameChanged -> setState { copy(name = event.value, nameError = false) }
            is TaskEditEvent.DescriptionChanged -> setState { copy(description = event.value) }
            is TaskEditEvent.TypeChanged -> setState { copy(type = event.value) }
            is TaskEditEvent.StartDateChanged -> setState {
                val snappedEndDate = endDate?.takeIf { it >= event.value } ?: event.value
                copy(startDate = event.value, endDate = snappedEndDate)
            }
            is TaskEditEvent.EndDateChanged -> setState { copy(endDate = event.value) }
            is TaskEditEvent.HourChanged -> setState { copy(hour = event.value) }
            is TaskEditEvent.DaysIntervalChanged -> setState { copy(daysInterval = event.value) }
            is TaskEditEvent.SaveClicked -> save()
            is TaskEditEvent.DeleteClicked -> delete()
        }
    }

    private fun save() {
        val current = state.value
        if (current.name.isBlank()) {
            setState { copy(nameError = true) }
            return
        }

        val task = Task(
            id = current.taskId ?: 0L,
            name = current.name,
            description = current.description.ifBlank { null },
            startDate = if (current.type == TaskType.Asap) todayProvider.today() else current.startDate ?: todayProvider.today(),
            endDate = if (current.type == TaskType.Periodic) current.endDate else null,
            hour = current.hour,
            daysInterval = if (current.type == TaskType.Periodic) current.daysInterval.toIntOrNull() ?: 0 else 0,
            asap = current.type == TaskType.Asap,
        )

        viewModelScope.launch {
            val result = if (current.taskId != null) editTask(task) else addTask(task)
            when (result) {
                is AppResult.Success -> sendEffect(TaskEditEffect.NavigateBack)
                is AppResult.Failure -> sendEffect(TaskEditEffect.ShowError(result.error.message))
            }
        }
    }

    private fun delete() {
        val taskId = originalTaskId
        if (taskId == null) {
            sendEffect(TaskEditEffect.NavigateBack)
            return
        }
        viewModelScope.launch {
            when (val result = deleteTask(taskId)) {
                is AppResult.Success -> sendEffect(TaskEditEffect.NavigateBack)
                is AppResult.Failure -> sendEffect(TaskEditEffect.ShowError(result.error.message))
            }
        }
    }
}
```

- [ ] **Step 5: Run it to verify it passes**

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.ui.TaskEditViewModelTest"`
Expected: PASS (11 tests).

- [ ] **Step 6: Commit**

```bash
git add feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/TaskEditContract.kt feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/TaskEditViewModel.kt feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/TaskEditViewModelTest.kt
git commit -m "Add TaskEditViewModel"
```

---

## Task 18: `feature:tasks` — Koin DI module

**Files:**
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/di/TasksModule.kt`

**Interfaces:**
- Consumes: `DatabaseDriverFactory` (core:database), `Database`/`KeyValueEntryQueries` (core:database, Task 2), everything from Tasks 10–17.
- Produces: `val tasksModule: Module` — added to `:shared`'s `initKoin()` in Task 20.

- [ ] **Step 1: Write the module**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/di/TasksModule.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.di

import kotlinx.datetime.LocalDate
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pl.lejdi.plannerkmp.core.database.DatabaseDriverFactory
import pl.lejdi.plannerkmp.core.database.KeyValueCache
import pl.lejdi.plannerkmp.core.database.SqlDelightKeyValueCache
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.tasks.TasksNavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.tasks.data.LocalDateColumnAdapter
import pl.lejdi.plannerkmp.feature.tasks.data.LocalTimeColumnAdapter
import pl.lejdi.plannerkmp.feature.tasks.data.SqlDelightTasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.data.TaskEntity
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatabase
import pl.lejdi.plannerkmp.feature.tasks.data.TasksDatasource
import pl.lejdi.plannerkmp.feature.tasks.domain.AddTask
import pl.lejdi.plannerkmp.feature.tasks.domain.DeleteTask
import pl.lejdi.plannerkmp.feature.tasks.domain.EditTask
import pl.lejdi.plannerkmp.feature.tasks.domain.GetTasksForDashboard
import pl.lejdi.plannerkmp.feature.tasks.domain.MarkTaskComplete
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.UpdateTasksDates
import pl.lejdi.plannerkmp.feature.tasks.ui.DashboardViewModel
import pl.lejdi.plannerkmp.feature.tasks.ui.TaskEditViewModel

val tasksModule = module {
    single {
        TasksDatabase(
            get<DatabaseDriverFactory>().createDriver(TasksDatabase.Schema, "tasks.db"),
            taskEntityAdapter = TaskEntity.Adapter(
                startDateAdapter = LocalDateColumnAdapter,
                endDateAdapter = LocalDateColumnAdapter,
                hourAdapter = LocalTimeColumnAdapter,
            ),
        )
    }
    single { get<TasksDatabase>().taskEntityQueries }
    single<KeyValueCache<Unit, LocalDate>> {
        SqlDelightKeyValueCache(
            queries = get(),
            encodeKey = { "lastCleanupDate" },
            serialize = { it.toString() },
            deserialize = { LocalDate.parse(it) },
        )
    }
    single<TasksDatasource> { SqlDelightTasksDatasource(get(), get()) }

    factory { GetTasksForDashboard(get(), get()) }
    factory { MarkTaskComplete(get()) }
    factory { UpdateTasksDates(get(), get()) }
    factory { AddTask(get()) }
    factory { EditTask(get()) }
    factory { DeleteTask(get()) }

    viewModel { DashboardViewModel(get(), get(), get()) }
    viewModel { (task: Task?) -> TaskEditViewModel(task, get(), get(), get(), get()) }

    factoryOf(::TasksNavEntryProviderContributor) { bind<NavEntryProviderContributor>() }
}
```

Note: `get<KeyValueCache<Unit, LocalDate>>()`'s `queries = get()` resolves `KeyValueEntryQueries`, which comes from `core:database`'s `keyValueCacheModule` (Task 2) — that module must be present in the same Koin graph, which it will be once `:shared` registers both in Task 20.

`TasksNavEntryProviderContributor` (referenced here) is written in Task 19 alongside the screens — this module won't compile until that file exists; that's fine, Task 19 is next and includes both.

- [ ] **Step 2: Commit (deferred compile check to Task 19, since this file references not-yet-written UI types)**

```bash
git add feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/di
git commit -m "Add tasks Koin module"
```

---

## Task 19: `feature:tasks` — Compose UI, nav keys, entry contributor

**Files:**
- Create: `core/navigation/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/navigation/LocalNavigator.kt`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/TasksNavKey.kt`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/TasksNavEntryProviderContributor.kt`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DateFormatting.kt`
- Create: `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DateFormattingTest.kt`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardScreen.kt`
- Create: `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/TaskEditScreen.kt`

**Interfaces:**
- Consumes: `NavEntryProviderContributor`, `NavKey` (core:navigation), `DashboardViewModel` (Task 16), `TaskEditViewModel` (Task 17), `LoadingView`/`ErrorView` (core:ui).
- Produces: `sealed interface TasksNavKey : NavKey { Dashboard, TaskEdit(task: Task?) }`, `class TasksNavEntryProviderContributor : NavEntryProviderContributor` — Koin-bound in Task 18, picked up by `:shared` in Task 20.

`DateFormatting.kt` is the one piece of pure logic in this task, so it gets a real test; the screens are verified by compiling, same reasoning as Task 9.

- [ ] **Step 1: Write the card date formatter (TDD)**

Create `feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DateFormattingTest.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateFormattingTest {

    @Test
    fun formatsAsTwoLineDayMonthYearAndWeekdayName() {
        val date = LocalDate(2025, 7, 13)

        assertEquals("13 July 2025\nSunday", date.toCardDisplayString())
    }
}
```

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.ui.DateFormattingTest"`
Expected: compile failure — `toCardDisplayString` doesn't exist yet.

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DateFormatting.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.ui

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char

private val cardDateFormat = LocalDate.Format {
    dayOfMonth()
    char(' ')
    monthName(MonthNames.ENGLISH_FULL)
    char(' ')
    year()
}

private val weekdayNames = mapOf(
    DayOfWeek.MONDAY to "Monday",
    DayOfWeek.TUESDAY to "Tuesday",
    DayOfWeek.WEDNESDAY to "Wednesday",
    DayOfWeek.THURSDAY to "Thursday",
    DayOfWeek.FRIDAY to "Friday",
    DayOfWeek.SATURDAY to "Saturday",
    DayOfWeek.SUNDAY to "Sunday",
)

fun LocalDate.toCardDisplayString(): String =
    "${cardDateFormat.format(this)}\n${weekdayNames.getValue(dayOfWeek)}"
```

Run: `./gradlew :feature:tasks:testAndroidHostTest --tests "pl.lejdi.plannerkmp.feature.tasks.ui.DateFormattingTest"`
Expected: PASS (1 test).

Commit:

```bash
git add feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DateFormatting.kt feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DateFormattingTest.kt
git commit -m "Add task card date formatting"
```

- [ ] **Step 2: Define the nav keys**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/TasksNavKey.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks

import androidx.navigation3.runtime.NavKey
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

sealed interface TasksNavKey : NavKey {
    data object Dashboard : TasksNavKey
    data class TaskEdit(val task: Task?) : TasksNavKey
}
```

- [ ] **Step 3: Write the dashboard screen**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardScreen.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.lejdi.plannerkmp.core.ui.components.LoadingView
import pl.lejdi.plannerkmp.feature.tasks.domain.DashboardDay
import pl.lejdi.plannerkmp.feature.tasks.domain.Task

@Composable
fun DashboardScreen(
    onNavigateToAddTask: () -> Unit,
    onNavigateToEditTask: (Task) -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffectCollectEffects(viewModel) { effect ->
        when (effect) {
            is DashboardEffect.NavigateToAddTask -> onNavigateToAddTask()
            is DashboardEffect.NavigateToEditTask -> onNavigateToEditTask(effect.task)
            is DashboardEffect.ShowError -> Unit
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onEvent(DashboardEvent.AddTaskClicked) }) {
                Text("+")
            }
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingView(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        LazyRow(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(state.days, key = { it.date.toString() }) { day ->
                DayColumn(day, state.revealedTaskId, viewModel)
            }
        }
    }
}

@Composable
private fun DayColumn(day: DashboardDay, revealedTaskId: Long?, viewModel: DashboardViewModel) {
    Column(modifier = Modifier.width(220.dp).padding(8.dp)) {
        Text(day.date.toCardDisplayString())
        LazyColumn {
            items(day.tasks, key = { it.id }) { task ->
                TaskCard(task, revealed = task.id == revealedTaskId, viewModel)
            }
        }
    }
}

@Composable
private fun TaskCard(task: Task, revealed: Boolean, viewModel: DashboardViewModel) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(task.name)
            task.hour?.let { Text(it.toString()) }
            if (!revealed) {
                Button(onClick = { viewModel.onEvent(DashboardEvent.RevealActions(task.id)) }) {
                    Text("...")
                }
            } else {
                Row {
                    Button(onClick = { viewModel.onEvent(DashboardEvent.EditTaskClicked(task)) }) { Text("Edit") }
                    Button(onClick = { viewModel.onEvent(DashboardEvent.CompleteTask(task)) }) { Text("Complete") }
                }
            }
        }
    }
}
```

This references a `LaunchedEffectCollectEffects` helper for collecting the one-off `effect` flow — add it once, reused by both screens in this task:

Create it inline at the top of `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DashboardScreen.kt` (below the imports, above `DashboardScreen`):

```kotlin
import androidx.compose.runtime.LaunchedEffect
import pl.lejdi.plannerkmp.core.mvi.BaseViewModel
import pl.lejdi.plannerkmp.core.mvi.MviEffect
import pl.lejdi.plannerkmp.core.mvi.MviEvent
import pl.lejdi.plannerkmp.core.mvi.MviState
import kotlinx.coroutines.flow.collect

@Composable
private fun <S : MviState, E : MviEvent, F : MviEffect> LaunchedEffectCollectEffects(
    viewModel: BaseViewModel<S, E, F>,
    onEffect: suspend (F) -> Unit,
) {
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { onEffect(it) }
    }
}
```

(add these five imports to the existing import block at the top of the file, and this composable function above `DashboardScreen`).

- [ ] **Step 4: Write the task edit screen**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/TaskEditScreen.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import pl.lejdi.plannerkmp.feature.tasks.domain.Task
import pl.lejdi.plannerkmp.feature.tasks.domain.TaskType

@Composable
fun TaskEditScreen(
    task: Task?,
    onNavigateBack: () -> Unit,
    viewModel: TaskEditViewModel = koinViewModel { parametersOf(task) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffectCollectEffects(viewModel) { effect ->
        when (effect) {
            is TaskEditEffect.NavigateBack -> onNavigateBack()
            is TaskEditEffect.ShowError -> Unit
        }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { viewModel.onEvent(TaskEditEvent.NameChanged(it)) },
                label = { Text("Name") },
                isError = state.nameError,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.onEvent(TaskEditEvent.DescriptionChanged(it)) },
                label = { Text("Description") },
            )

            Row {
                TaskType.entries.forEach { type ->
                    Row {
                        RadioButton(
                            selected = state.type == type,
                            onClick = { viewModel.onEvent(TaskEditEvent.TypeChanged(type)) },
                        )
                        Text(type.name)
                    }
                }
            }

            if (state.type != TaskType.Asap) {
                OutlinedTextField(
                    value = state.startDate?.toString().orEmpty(),
                    onValueChange = {},
                    label = { Text("Start date (dd-MM-yyyy)") },
                    readOnly = true,
                )
            }
            if (state.type == TaskType.Periodic) {
                OutlinedTextField(
                    value = state.endDate?.toString().orEmpty(),
                    onValueChange = {},
                    label = { Text("End date (dd-MM-yyyy)") },
                    readOnly = true,
                )
                OutlinedTextField(
                    value = state.daysInterval,
                    onValueChange = { viewModel.onEvent(TaskEditEvent.DaysIntervalChanged(it)) },
                    label = { Text("Repeat every (days)") },
                )
            }
            if (state.type != TaskType.Asap) {
                OutlinedTextField(
                    value = state.hour?.toString().orEmpty(),
                    onValueChange = {},
                    label = { Text("Time (optional)") },
                    readOnly = true,
                )
            }

            Row {
                Button(onClick = { viewModel.onEvent(TaskEditEvent.SaveClicked) }) { Text("Save") }
                Button(onClick = { viewModel.onEvent(TaskEditEvent.DeleteClicked) }) { Text("Delete") }
            }
        }
    }
}
```

The start/end date and hour fields are `readOnly` text fields showing the current value rather than full date/time pickers — wiring an actual platform date/time picker dialog is a UI-polish concern outside this plan's scope (business logic + a working vertical slice); `TaskEditEvent.StartDateChanged`/`EndDateChanged`/`HourChanged` are already in place for whoever wires a real picker up to call them.

- [ ] **Step 5: Write the entry provider contributor**

Create `feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks/TasksNavEntryProviderContributor.kt`:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.tasks.ui.DashboardScreen
import pl.lejdi.plannerkmp.feature.tasks.ui.TaskEditScreen

class TasksNavEntryProviderContributor(
    private val navigator: pl.lejdi.plannerkmp.core.navigation.Navigator,
) : NavEntryProviderContributor {
    override fun EntryProviderScope<NavKey>.contribute() {
        entry<TasksNavKey.Dashboard> {
            DashboardScreen(
                onNavigateToAddTask = { navigator.navigateTo(TasksNavKey.TaskEdit(task = null)) },
                onNavigateToEditTask = { task -> navigator.navigateTo(TasksNavKey.TaskEdit(task)) },
            )
        }
        entry<TasksNavKey.TaskEdit> { key ->
            TaskEditScreen(task = key.task, onNavigateBack = { navigator.goBack() })
        }
    }
}
```

Unlike grocery (a single screen, no in-feature navigation), the tasks feature needs to navigate from the dashboard to the edit screen and back. `Navigator` isn't a normal Koin singleton — there'll be **two** instances at runtime (one per bottom-nav tab, Task 20), each `remember`ed inside its own part of the Compose tree, not application-scoped. So contributors read the *currently active* `Navigator` via a `CompositionLocal` instead of constructor injection.

Add that `CompositionLocal` to `core:navigation` first:

Create `core/navigation/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/navigation/LocalNavigator.kt`:

```kotlin
package pl.lejdi.plannerkmp.core.navigation

import androidx.compose.runtime.compositionLocalOf

val LocalNavigator = compositionLocalOf<Navigator> {
    error("No Navigator provided — wrap this composable in CompositionLocalProvider(LocalNavigator provides ...)")
}
```

Then rewrite `TasksNavEntryProviderContributor` to read it instead of taking a constructor parameter:

```kotlin
package pl.lejdi.plannerkmp.feature.tasks

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import pl.lejdi.plannerkmp.core.navigation.LocalNavigator
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.feature.tasks.ui.DashboardScreen
import pl.lejdi.plannerkmp.feature.tasks.ui.TaskEditScreen

class TasksNavEntryProviderContributor : NavEntryProviderContributor {
    override fun EntryProviderScope<NavKey>.contribute() {
        entry<TasksNavKey.Dashboard> {
            val navigator = LocalNavigator.current
            DashboardScreen(
                onNavigateToAddTask = { navigator.navigateTo(TasksNavKey.TaskEdit(task = null)) },
                onNavigateToEditTask = { task -> navigator.navigateTo(TasksNavKey.TaskEdit(task)) },
            )
        }
        entry<TasksNavKey.TaskEdit> { key ->
            val navigator = LocalNavigator.current
            TaskEditScreen(task = key.task, onNavigateBack = { navigator.goBack() })
        }
    }
}
```

With this shape, `TasksNavEntryProviderContributor` has a no-arg constructor again — Task 18's `factoryOf(::TasksNavEntryProviderContributor) { bind<NavEntryProviderContributor>() }` needs no further change, and Koin never needs to know about `Navigator` at all. `:shared` (Task 20) is what provides `LocalNavigator` per tab.

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :feature:tasks:compileKotlinIosSimulatorArm64 :feature:tasks:testAndroidHostTest`
Expected: BUILD SUCCESSFUL, all existing tasks-feature tests still pass.

- [ ] **Step 7: Commit**

```bash
git add core/navigation/src/commonMain/kotlin/pl/lejdi/plannerkmp/core/navigation/LocalNavigator.kt feature/tasks/src/commonMain/kotlin/pl/lejdi/plannerkmp/feature/tasks feature/tasks/src/commonTest/kotlin/pl/lejdi/plannerkmp/feature/tasks/ui/DateFormattingTest.kt
git commit -m "Add tasks Compose UI, nav keys, and entry provider contributor"
```

---

## Task 20: `:shared` — wire both features into Koin and a bottom-nav `App()`

**Files:**
- Modify: `shared/build.gradle.kts`
- Modify: `shared/src/commonMain/kotlin/pl/lejdi/plannerkmp/Koin.kt`
- Modify: `shared/src/commonMain/kotlin/pl/lejdi/plannerkmp/App.kt`

**Interfaces:**
- Consumes: `keyValueCacheModule` (Task 2), `tasksModule` (Task 18), `groceryModule` (Task 8), `TasksNavKey`/`GroceryNavKey` (Tasks 19, 9), `LocalNavigator`/`Navigator`/`NavEntryProviderContributor` (core:navigation).

No dedicated automated test — this is app-shell wiring, verified by the full-app build in Task 21.

- [ ] **Step 1: Add module dependencies**

In `shared/build.gradle.kts`, in `commonMain.dependencies { }`, add:

```kotlin
implementation(project(":feature:tasks"))
implementation(project(":feature:grocery"))
implementation(libs.koin.compose)
```

- [ ] **Step 2: Register both features' Koin modules**

Read `shared/src/commonMain/kotlin/pl/lejdi/plannerkmp/Koin.kt`, then modify it to:

```kotlin
package pl.lejdi.plannerkmp

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import pl.lejdi.plannerkmp.core.common.di.commonModule
import pl.lejdi.plannerkmp.core.database.di.databaseModule
import pl.lejdi.plannerkmp.core.database.di.keyValueCacheModule
import pl.lejdi.plannerkmp.core.network.di.networkModule
import pl.lejdi.plannerkmp.feature.grocery.di.groceryModule
import pl.lejdi.plannerkmp.feature.tasks.di.tasksModule

fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication = startKoin {
    appDeclaration()
    modules(commonModule, databaseModule, keyValueCacheModule, networkModule, tasksModule, groceryModule)
}
```

- [ ] **Step 3: Wire the bottom-nav `App()`**

Read `shared/src/commonMain/kotlin/pl/lejdi/plannerkmp/App.kt`, then replace its content with:

```kotlin
package pl.lejdi.plannerkmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import org.koin.compose.getKoin
import pl.lejdi.plannerkmp.core.navigation.LocalNavigator
import pl.lejdi.plannerkmp.core.navigation.NavEntryProviderContributor
import pl.lejdi.plannerkmp.core.navigation.Navigator
import pl.lejdi.plannerkmp.core.ui.theme.PlannerTheme
import pl.lejdi.plannerkmp.feature.grocery.GroceryNavKey
import pl.lejdi.plannerkmp.feature.tasks.TasksNavKey

private enum class BottomNavTab { Tasks, Grocery }

@Composable
fun App() {
    PlannerTheme {
        val koin = getKoin()
        val entryProvider = remember {
            entryProvider<NavKey> {
                koin.getAll<NavEntryProviderContributor>().forEach { contributor ->
                    with(contributor) { contribute() }
                }
            }
        }

        var selectedTab by remember { mutableStateOf(BottomNavTab.Tasks) }
        val tasksNavigator = remember { Navigator(TasksNavKey.Dashboard) }
        val groceryNavigator = remember { Navigator(GroceryNavKey) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.Tasks,
                        onClick = { selectedTab = BottomNavTab.Tasks },
                        icon = {},
                        label = { Text("Tasks") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.Grocery,
                        onClick = { selectedTab = BottomNavTab.Grocery },
                        icon = {},
                        label = { Text("Grocery") },
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (selectedTab) {
                    BottomNavTab.Tasks -> CompositionLocalProvider(LocalNavigator provides tasksNavigator) {
                        NavDisplay(
                            backStack = tasksNavigator.backStack,
                            onBack = { tasksNavigator.goBack() },
                            entryProvider = entryProvider,
                        )
                    }
                    BottomNavTab.Grocery -> CompositionLocalProvider(LocalNavigator provides groceryNavigator) {
                        NavDisplay(
                            backStack = groceryNavigator.backStack,
                            onBack = { groceryNavigator.goBack() },
                            entryProvider = entryProvider,
                        )
                    }
                }
            }
        }
    }
}
```

`NavigationBarItem`'s `icon` slot is left empty (`{}`) — there's no icon library in this repo's dependency catalog yet (see Global Constraints), so this ships with text-only tabs; swapping in real icons later is a drop-in visual change, not a logic change.

- [ ] **Step 4: Build the whole app**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL. This is the first point every module — `core:*`, `feature:tasks`, `feature:grocery`, `:shared`, `:androidApp` — compiles and links together; fix whatever surfaces (likely candidates: a Navigation 3 API name mismatch per the Global Constraints caveat, or a missed import) before moving on.

- [ ] **Step 5: Commit**

```bash
git add shared/build.gradle.kts shared/src/commonMain/kotlin/pl/lejdi/plannerkmp/Koin.kt shared/src/commonMain/kotlin/pl/lejdi/plannerkmp/App.kt
git commit -m "Wire tasks and grocery features into shared Koin and a bottom-nav App()"
```

---

## Task 21: Documentation + full verification

**Files:**
- Modify: `CLAUDE.md`

**Interfaces:** none — this task updates docs and re-runs the full test/build suite; no new production code.

- [ ] **Step 1: Update `CLAUDE.md`'s `core:database` description**

Read `CLAUDE.md`, find the `**`:core:database`**` bullet in the Architecture section, and add a sentence noting it now owns one small generic schema:

```markdown
- **`:core:database`** — SQLDelight wrapper: `DatabaseDriverFactory` (`expect`/`actual`,
  no common constructor since Android needs a `Context` and iOS doesn't — each
  platform's Koin module supplies the actual instance) plus the generic
  `KeyValueCache<K, V>` contract (with an `InMemoryKeyValueCache` usable as a
  test fake, and a `SqlDelightKeyValueCache` real implementation backed by
  this module's own small `keyValueEntry` SQLDelight schema — the one schema
  this module owns itself; every other schema belongs to the feature module
  that needs it). Feature modules apply the SQLDelight Gradle plugin
  themselves for their own `.sq` schema and build their generated `Database`
  from a driver obtained here.
- **`:core:common`** — pure Kotlin: `AppResult`/`DomainError`, `CoroutineDispatchers`,
  `TodayProvider` (the single source of "today" — a Koin-swappable wrapper
  over `kotlinx.datetime.Clock.System`, used anywhere a feature needs the
  current date instead of reading the system clock directly).
```

Replace the existing `core:database` and `core:common` bullets with these updated versions (keep everything else in the Architecture section as-is — the `:feature:*` bullet's "none yet" line also needs updating, see next step).

- [ ] **Step 2: Update the `:feature:*` bullet and module layout list**

In the same `CLAUDE.md` Architecture section, replace:

```markdown
- **`:feature:*`** — none yet. Each one depends on whichever `:core:*` modules
  it needs, owns its own MVI contract (`State`/`Event`/`Effect` extending the
  `core:mvi` marker interfaces), its own datasources, and contributes a Koin
  module + `NavEntryProviderContributor`.
```

with:

```markdown
- **`:feature:tasks`** — TODO/task list: `Task`/`TaskType` domain model,
  `GetTasksForDashboard` (8-day dashboard filter/sort), `MarkTaskComplete`,
  `UpdateTasksDates` (daily cleanup job run from the dashboard ViewModel's
  init), `AddTask`/`EditTask`/`DeleteTask`, its own `taskEntity` SQLDelight
  schema, `DashboardScreen`/`TaskEditScreen`.
- **`:feature:grocery`** — grocery list: `GroceryItem` domain model,
  `GetGroceryItems`/`AddGrocery`/`EditGrocery`/`DeleteGrocery`, its own
  `groceryItemEntity` SQLDelight schema, `GroceryListScreen` (inline-expand
  add/edit rows).
```

Also update the settings.gradle.kts module-graph note earlier in the file (the one listing `include(...)` calls) if `CLAUDE.md` echoes it anywhere else — search the file for "none yet" and fix every occurrence.

- [ ] **Step 3: Update the Commands section if needed**

Read the `## Commands` section — the existing example (`./gradlew :core:mvi:testAndroidHostTest`) already generalizes to `./gradlew :feature:tasks:testAndroidHostTest` / `:feature:grocery:testAndroidHostTest`, so no change is required there unless the wording explicitly says "no real feature module yet" — if it does, update it to reflect that `:feature:tasks` and `:feature:grocery` now exist.

- [ ] **Step 4: Run the full test suite**

Run: `./gradlew testAndroidHostTest`
Expected: BUILD SUCCESSFUL — every test from Tasks 1–19 passes together (roughly 70+ tests across `core:common`, `core:database`, `feature:grocery`, `feature:tasks`).

- [ ] **Step 5: Run the full app build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL (re-confirms Task 20's build still passes after the doc-only changes in this task).

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md
git commit -m "Update CLAUDE.md for the tasks and grocery feature modules"
```

---

## Self-Review

**Spec coverage:** every section of `docs/superpowers/specs/2026-09-08-tasks-grocery-implementation-design.md` maps to a task — module graph (Tasks 4, 10), `TodayProvider` (Task 1), persisted `KeyValueCache` (Task 2), `safeQuery` (Task 3), tasks domain/data/usecases (Tasks 10–15), tasks ViewModels (Tasks 16–17), grocery domain/data/usecases (Tasks 4–6), grocery ViewModel (Task 7), both features' DI + UI + nav (Tasks 8, 9, 18, 19), shared wiring (Task 20), docs (Task 21). All 5 resolved open decisions from the legacy spec appear in concrete task code: daily throttle (Task 14), no min-interval validation (Task 17's blank-interval test), grocery name required (Task 7's validation tests), real grocery editing (Task 7/9), `endDate` cleared off-Periodic (Task 17's test).

**Placeholder scan:** no TBD/TODO markers; every step has runnable code, not a description of code.

**Type consistency:** `Task`/`TaskType`/`DashboardDay` (Task 10, 12) are used identically in Tasks 13–19; `GroceryItem` (Task 4) identically through Tasks 5–9; `TasksDatasource`/`GroceryDatasource` method signatures (Tasks 11, 5) match every caller in later tasks; `AppResult`/`DomainError` usage is consistent with the existing `core:common` contract throughout.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-08-tasks-grocery-implementation.md`. Two execution options:

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
