package pl.lejdi.plannerkmp.feature.grocery.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pl.lejdi.plannerkmp.core.common.DomainError
import pl.lejdi.plannerkmp.core.testing.NoOpLogger
import pl.lejdi.plannerkmp.feature.grocery.domain.FakeGroceryDatasource
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItem
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryItemDraft
import pl.lejdi.plannerkmp.feature.grocery.domain.GroceryWrite
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroceryListViewModelTest {

    private val milk = GroceryItem(id = 1, name = "Milk", description = null)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        datasource: FakeGroceryDatasource,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = GroceryListViewModel(savedStateHandle, NoOpLogger(), datasource)

    @Test
    fun loadsTheListOnCreation() = runTest {
        val viewModel = viewModel(FakeGroceryDatasource(initialItems = listOf(milk)))
        runCurrent()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf(milk), viewModel.state.value.items)
    }

    @Test
    fun picksUpAWriteWithoutReloading() = runTest {
        val datasource = FakeGroceryDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()

        datasource.addItem(GroceryItemDraft.ofStored(name = "Bread", description = null))
        runCurrent()

        assertEquals("Bread", viewModel.state.value.items.single().name)
    }

    @Test
    fun openingTheAddEditorStartsItEmpty() = runTest {
        val viewModel = viewModel(FakeGroceryDatasource(initialItems = listOf(milk)))
        runCurrent()

        viewModel.onEvent(GroceryListEvent.EditorOpened(EditorTarget.New))

        val editor = viewModel.state.value.editor
        assertEquals(EditorTarget.New, editor?.target)
        assertEquals("", editor?.name)
        assertEquals("", editor?.description)
    }

    @Test
    fun openingTheEditEditorPrefillsFromTheItem() = runTest {
        val item = GroceryItem(id = 1, name = "Milk", description = "Oat")
        val viewModel = viewModel(FakeGroceryDatasource(initialItems = listOf(item)))
        runCurrent()

        viewModel.onEvent(GroceryListEvent.EditorOpened(EditorTarget.Existing(1)))

        val editor = viewModel.state.value.editor
        assertEquals(EditorTarget.Existing(1), editor?.target)
        assertEquals("Milk", editor?.name)
        assertEquals("Oat", editor?.description)
    }

    @Test
    fun cancellingClosesTheEditorWithoutWriting() = runTest {
        val datasource = FakeGroceryDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()
        viewModel.onEvent(GroceryListEvent.EditorOpened(EditorTarget.New))
        viewModel.onEvent(GroceryListEvent.EditorNameChanged("Bread"))

        viewModel.onEvent(GroceryListEvent.EditorCancelled)

        assertNull(viewModel.state.value.editor)
        assertTrue(datasource.items.isEmpty())
    }

    @Test
    fun confirmingAnAddStoresTheItemAndClosesTheEditor() = runTest {
        val datasource = FakeGroceryDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GroceryListEvent.EditorOpened(EditorTarget.New))
        viewModel.onEvent(GroceryListEvent.EditorNameChanged("Bread"))
        viewModel.onEvent(GroceryListEvent.EditorDescriptionChanged("Sourdough"))
        viewModel.onEvent(GroceryListEvent.EditorConfirmed)
        runCurrent()

        assertNull(viewModel.state.value.editor)
        assertEquals("Bread", datasource.items.single().name)
        assertEquals("Sourdough", datasource.items.single().description)
        assertEquals(listOf("Bread"), viewModel.state.value.items.map { it.name })
    }

    @Test
    fun confirmingAnEditUpdatesInPlace() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GroceryListEvent.EditorOpened(EditorTarget.Existing(1)))
        viewModel.onEvent(GroceryListEvent.EditorNameChanged("Oat milk"))
        viewModel.onEvent(GroceryListEvent.EditorConfirmed)
        runCurrent()

        assertNull(viewModel.state.value.editor)
        assertEquals(1, datasource.items.size)
        assertEquals("Oat milk", datasource.items.single().name)
        assertEquals(1L, datasource.items.single().id)
    }

    @Test
    fun aBlankNameIsMarkedOnTheEditorAndNothingIsWritten() = runTest {
        val datasource = FakeGroceryDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GroceryListEvent.EditorOpened(EditorTarget.New))
        viewModel.onEvent(GroceryListEvent.EditorNameChanged("   "))
        viewModel.onEvent(GroceryListEvent.EditorConfirmed)
        runCurrent()

        assertTrue(viewModel.state.value.editor?.nameError == true)
        assertTrue(datasource.items.isEmpty())
    }

    @Test
    fun typingClearsTheNameError() = runTest {
        val viewModel = viewModel(FakeGroceryDatasource())
        runCurrent()
        viewModel.onEvent(GroceryListEvent.EditorOpened(EditorTarget.New))
        viewModel.onEvent(GroceryListEvent.EditorConfirmed)
        runCurrent()
        assertTrue(viewModel.state.value.editor?.nameError == true)

        viewModel.onEvent(GroceryListEvent.EditorNameChanged("Bread"))

        assertFalse(viewModel.state.value.editor?.nameError == true)
    }

    @Test
    fun completingAnItemRemovesIt() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GroceryListEvent.CompleteItem(1))
        runCurrent()

        assertTrue(datasource.items.isEmpty())
        assertTrue(viewModel.state.value.items.isEmpty())
    }

    @Test
    fun aFailedLoadSurfacesATypedMessage() = runTest {
        val datasource = FakeGroceryDatasource()
        datasource.observeFailure = DomainError.Database("no such table: groceryItemEntity")
        val viewModel = viewModel(datasource)
        runCurrent()

        assertEquals(GroceryMessage.LoadFailed, viewModel.state.value.message?.value)
    }

    @Test
    fun aFailedSaveSurfacesATypedMessage() = runTest {
        val datasource = FakeGroceryDatasource()
        val viewModel = viewModel(datasource)
        runCurrent()
        viewModel.onEvent(GroceryListEvent.EditorOpened(EditorTarget.New))
        viewModel.onEvent(GroceryListEvent.EditorNameChanged("Bread"))
        datasource.failNext(GroceryWrite.Add)

        viewModel.onEvent(GroceryListEvent.EditorConfirmed)
        runCurrent()

        assertEquals(GroceryMessage.SaveFailed, viewModel.state.value.message?.value)
    }

    @Test
    fun aFailedDeleteSurfacesATypedMessage() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(GroceryWrite.Delete)

        viewModel.onEvent(GroceryListEvent.CompleteItem(1))
        runCurrent()

        assertEquals(GroceryMessage.DeleteFailed, viewModel.state.value.message?.value)
    }

    @Test
    fun aLoadFailureWithNothingToShowIsTerminal() = runTest {
        val datasource = FakeGroceryDatasource()
        datasource.observeFailure = DomainError.Database("boom")
        val viewModel = viewModel(datasource)
        runCurrent()

        assertTrue(viewModel.state.value.hasTerminalLoadFailure)
    }

    @Test
    fun aFailureWithTheListStillOnScreenIsNotTerminal() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(GroceryWrite.Delete)

        viewModel.onEvent(GroceryListEvent.CompleteItem(1))
        runCurrent()

        assertEquals(GroceryMessage.DeleteFailed, viewModel.state.value.message?.value)
        assertFalse(viewModel.state.value.hasTerminalLoadFailure)
    }

    @Test
    fun messageShownClearsTheMessage() = runTest {
        val datasource = FakeGroceryDatasource()
        datasource.observeFailure = DomainError.Database("boom")
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GroceryListEvent.MessageShown)

        assertNull(viewModel.state.value.message)
    }

    @Test
    fun retryResubscribesAfterAFailure() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        datasource.observeFailure = DomainError.Database("boom")
        val viewModel = viewModel(datasource)
        runCurrent()
        assertEquals(GroceryMessage.LoadFailed, viewModel.state.value.message?.value)

        datasource.observeFailure = null
        viewModel.onEvent(GroceryListEvent.RetryClicked)
        runCurrent()

        assertNull(viewModel.state.value.message)
        assertEquals(listOf(milk), viewModel.state.value.items)
    }

    /**
     * The open editor is user input, so it has to survive process death — the list behind it does
     * not, because it comes back from the database on its own.
     */
    @Test
    fun theOpenEditorSurvivesProcessDeath() = runTest {
        val savedStateHandle = SavedStateHandle()
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val before = viewModel(datasource, savedStateHandle)
        before.onEvent(GroceryListEvent.EditorOpened(EditorTarget.New))
        before.onEvent(GroceryListEvent.EditorNameChanged("Bread"))
        runCurrent()

        val after = viewModel(datasource, savedStateHandle)
        runCurrent()

        val editor = after.state.value.editor
        assertEquals(EditorTarget.New, editor?.target)
        assertEquals("Bread", editor?.name)
    }

    @Test
    fun aVanishedItemIsReportedAsGoneRatherThanAsAFailedSave() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()
        viewModel.onEvent(GroceryListEvent.EditorOpened(EditorTarget.Existing(milk.id)))
        viewModel.onEvent(GroceryListEvent.EditorNameChanged("Oat milk"))

        datasource.deleteItem(milk.id)
        viewModel.onEvent(GroceryListEvent.EditorConfirmed)
        runCurrent()

        assertEquals(GroceryMessage.ItemNoLongerExists, viewModel.state.value.message?.value)
        assertNull(viewModel.state.value.editor)
    }

    /**
     * "Complete" is a permanent delete with no confirmation, which is right for a shopping list —
     * the tap per item is the whole point. Undo is the other half of that bargain, and it was
     * missing: a mis-tap destroyed the row with no way back.
     */
    @Test
    fun completingAnItemOffersItBack() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GroceryListEvent.CompleteItem(milk.id))
        runCurrent()

        assertTrue(datasource.items.isEmpty())
        assertEquals(GroceryMessage.ItemCompleted, viewModel.state.value.message?.value)
        assertEquals(milk, viewModel.state.value.undoableDeletion)
    }

    @Test
    fun undoPutsACompletedItemBack() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()
        viewModel.onEvent(GroceryListEvent.CompleteItem(milk.id))
        runCurrent()

        viewModel.onEvent(GroceryListEvent.UndoCompleteClicked)
        runCurrent()

        val restored = datasource.items.single()
        assertEquals("Milk", restored.name)
        assertNull(viewModel.state.value.undoableDeletion, "the offer is spent once taken")
    }

    /** The offer lasts exactly as long as the snackbar carrying it, never longer. */
    @Test
    fun dismissingTheMessageWithdrawsTheUndoOffer() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()
        viewModel.onEvent(GroceryListEvent.CompleteItem(milk.id))
        runCurrent()

        viewModel.onEvent(GroceryListEvent.MessageShown)
        viewModel.onEvent(GroceryListEvent.UndoCompleteClicked)
        runCurrent()

        assertTrue(datasource.items.isEmpty(), "there was nothing left to undo")
    }

    /**
     * The re-entry guard. Confirming used to launch and return, so a second tap on the tick before
     * the first write came back added the same item twice.
     */
    @Test
    fun confirmingTwiceInARowAddsOneItem() = runTest {
        val datasource = FakeGroceryDatasource()
        datasource.blockWrites = true
        val viewModel = viewModel(datasource)
        runCurrent()
        viewModel.onEvent(GroceryListEvent.EditorOpened(EditorTarget.New))
        viewModel.onEvent(GroceryListEvent.EditorNameChanged("Bread"))

        viewModel.onEvent(GroceryListEvent.EditorConfirmed)
        viewModel.onEvent(GroceryListEvent.EditorConfirmed)
        // releaseWrites both completes the pending write and clears the gate; assigning
        // blockWrites = false would drop the deferred the first call is still suspended on.
        datasource.releaseWrites()
        runCurrent()

        assertEquals(1, datasource.items.size)
    }

    /**
     * Completing a row that is already gone is the outcome the tap wanted, so it says nothing.
     *
     * Unreachable before: the fake could only return a generic Database failure, and reaching the
     * NotFound branch by actually removing the row first emptied `items`, so `completeItem` returned
     * early and never got as far as the write.
     */
    @Test
    fun completingAnAlreadyDeletedItemReportsNothing() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.failNext(GroceryWrite.Delete, DomainError.NotFound("already gone"))

        viewModel.onEvent(GroceryListEvent.CompleteItem(1))
        runCurrent()

        assertNull(viewModel.state.value.message, "the row being gone is what the tap asked for")
    }

    /** A failed undo has to say so, or the item is silently not back. */
    @Test
    fun aFailedUndoReportsAMessage() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()
        viewModel.onEvent(GroceryListEvent.CompleteItem(1))
        runCurrent()
        datasource.failNext(GroceryWrite.Add)

        viewModel.onEvent(GroceryListEvent.UndoCompleteClicked)
        runCurrent()

        assertEquals(GroceryMessage.SaveFailed, viewModel.state.value.message?.value)
    }

    /**
     * Completing one item must not block another.
     *
     * A tap per item is how this screen is used, and a single `isSubmitting` made the second tap a
     * no-op with no feedback at all while the first write was open.
     */
    @Test
    fun completingOneItemLeavesTheOthersTappable() = runTest {
        val bread = GroceryItem(id = 2, name = "Bread", description = null)
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk, bread))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.blockWrites = true

        viewModel.onEvent(GroceryListEvent.CompleteItem(1))
        assertTrue(viewModel.state.value.isCompleting(1))
        assertFalse(viewModel.state.value.isCompleting(2), "the other row stays live")

        viewModel.onEvent(GroceryListEvent.CompleteItem(2))
        datasource.releaseWrites()
        runCurrent()

        assertTrue(datasource.items.isEmpty(), "both taps landed")
    }

    /** …but two taps on the *same* row are still one completion. */
    @Test
    fun tappingTheSameItemTwiceCompletesItOnce() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()
        datasource.blockWrites = true

        viewModel.onEvent(GroceryListEvent.CompleteItem(1))
        viewModel.onEvent(GroceryListEvent.CompleteItem(1))
        datasource.releaseWrites()
        runCurrent()

        assertNull(viewModel.state.value.message?.value?.takeIf { it == GroceryMessage.DeleteFailed })
        assertTrue(datasource.items.isEmpty())
    }

    /**
     * Two completions in a row each get their own snackbar.
     *
     * The message used to be the bare enum, so the second `ItemCompleted` was equal to the first and
     * the host — which keys its effect on the value — never re-showed it. On this screen that is the
     * normal way to work down a list.
     */
    @Test
    fun asecondCompletionRaisesADistinctMessage() = runTest {
        val bread = GroceryItem(id = 2, name = "Bread", description = null)
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk, bread))
        val viewModel = viewModel(datasource)
        runCurrent()

        viewModel.onEvent(GroceryListEvent.CompleteItem(1))
        runCurrent()
        val first = viewModel.state.value.message

        viewModel.onEvent(GroceryListEvent.CompleteItem(2))
        runCurrent()
        val second = viewModel.state.value.message

        assertEquals(GroceryMessage.ItemCompleted, first?.value)
        assertEquals(GroceryMessage.ItemCompleted, second?.value)
        assertNotEquals(first, second, "the host keys on this, so a repeat has to be a new value")
    }

    /** A stream that breaks after emitting keeps the list on screen rather than blanking it. */
    @Test
    fun aFailureAfterTheListLoadedIsNotTerminal() = runTest {
        val datasource = FakeGroceryDatasource(initialItems = listOf(milk))
        val viewModel = viewModel(datasource)
        runCurrent()

        datasource.failLiveStream()
        runCurrent()

        assertEquals(GroceryMessage.LoadFailed, viewModel.state.value.message?.value)
        assertFalse(viewModel.state.value.hasTerminalLoadFailure)
        assertEquals(listOf(milk), viewModel.state.value.items)
    }
}
