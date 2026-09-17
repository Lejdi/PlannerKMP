package pl.lejdi.plannerkmp.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.PolymorphicModuleBuilder

/**
 * Registers a feature's [NavKey] subclasses for polymorphic serialization.
 *
 * `rememberNavBackStack` can only restore a stack whose key types it can deserialize, and on
 * non-Android targets that registration cannot be done reflectively — so each feature declares its
 * own keys and `:shared` merges the contributions, exactly as it does for nav entries and tabs.
 */
fun interface NavKeySerializersContributor {
    fun PolymorphicModuleBuilder<NavKey>.contribute()
}
