package com.nickorton.ghac.ui.playlist

import com.nickorton.ghac.repo.MpdRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the selection state machine, which is the only logic the playlist
 * screen owns — everything else is delegated to the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    /**
     * A repository that is never connected. Its command methods short-circuit
     * when there is no client, so they are safe to call, and pending
     * coroutines stay queued on the test dispatcher rather than running.
     */
    private fun viewModel(): PlaylistViewModel =
        PlaylistViewModel(MpdRepository(CoroutineScope(StandardTestDispatcher())))

    @Test
    fun `starts with nothing selected`() {
        assertTrue(viewModel().selected.value.isEmpty())
    }

    @Test
    fun `toggling adds then removes a position`() {
        val vm = viewModel()

        vm.toggleSelection(3)
        assertEquals(setOf(3), vm.selected.value)

        vm.toggleSelection(3)
        assertTrue(vm.selected.value.isEmpty())
    }

    @Test
    fun `selection accumulates across rows`() {
        val vm = viewModel()

        vm.toggleSelection(1)
        vm.toggleSelection(4)
        vm.toggleSelection(2)

        assertEquals(setOf(1, 2, 4), vm.selected.value)
    }

    @Test
    fun `clearing selection empties it`() {
        val vm = viewModel()

        vm.toggleSelection(1)
        vm.toggleSelection(2)
        vm.clearSelection()

        assertTrue(vm.selected.value.isEmpty())
    }

    @Test
    fun `removing a row drops it from the selection`() {
        // Otherwise the set would keep a position that no longer exists, and a
        // later bulk delete would act on the wrong song.
        val vm = viewModel()

        vm.toggleSelection(2)
        vm.toggleSelection(5)
        vm.remove(2)

        assertEquals(setOf(5), vm.selected.value)
    }

    @Test
    fun `bulk remove clears the selection afterwards`() {
        val vm = viewModel()

        vm.toggleSelection(1)
        vm.toggleSelection(2)
        vm.removeSelected()

        assertTrue(vm.selected.value.isEmpty())
    }

    @Test
    fun `moving a row leaves selection mode`() {
        // Positions shift after a move, so holding on to the old indices would
        // leave the selection pointing at the wrong rows.
        val vm = viewModel()

        vm.toggleSelection(3)
        vm.move(3, 2)

        assertTrue(vm.selected.value.isEmpty())
    }

    @Test
    fun `moving a row onto itself is ignored`() {
        val vm = viewModel()

        vm.toggleSelection(3)
        vm.move(3, 3)

        // No move happened, so the selection survives.
        assertEquals(setOf(3), vm.selected.value)
    }
}
