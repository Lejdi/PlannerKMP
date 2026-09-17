package pl.lejdi.plannerkmp.core.testing

import kotlinx.coroutines.CoroutineDispatcher
import pl.lejdi.plannerkmp.core.common.CoroutineDispatchers

/**
 * Routes every dispatcher to one the test controls.
 *
 * This was written out four times — once in `core:database`'s tests and once in each datasource
 * test — which is three copies of a decision (which dispatcher stands in for IO) that has to be the
 * same everywhere for the tests to mean the same thing.
 */
class TestCoroutineDispatchers(private val dispatcher: CoroutineDispatcher) : CoroutineDispatchers {
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}
