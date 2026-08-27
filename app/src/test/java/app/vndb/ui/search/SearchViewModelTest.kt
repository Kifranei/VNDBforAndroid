package app.vndb.ui.search

import app.vndb.data.api.VndbPage
import app.vndb.data.model.SearchKind
import app.vndb.data.model.Staff
import app.vndb.data.model.VisualNovel
import app.vndb.data.repo.VndbRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<VndbRepository>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun submitDuringAutomaticSearchIgnoresLateResponse() = runTest(dispatcher) {
        var calls = 0
        coEvery { repository.searchVn(any(), any(), any()) } coAnswers {
            if (++calls == 1) {
                // Simulate a response that arrives even after cancellation.
                withContext(NonCancellable) { delay(1000) }
                VndbPage(listOf(VisualNovel(id = "v1", title = "stale")))
            } else {
                VndbPage(listOf(VisualNovel(id = "v1", title = "current")))
            }
        }
        val vm = SearchViewModel(repository)
        vm.onQueryChange("test")
        advanceTimeBy(380)
        runCurrent()
        vm.submit()
        advanceUntilIdle()
        assertEquals(listOf("current"), vm.state.value.vns.map { it.title })
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
        coVerify(exactly = 2) { repository.searchVn("test", 1, any()) }
    }

    @Test fun submitCancelsPendingDebounce() = runTest(dispatcher) {
        coEvery { repository.searchVn(any(), any(), any()) } returns VndbPage()
        val vm = SearchViewModel(repository)
        vm.onQueryChange("test")
        advanceTimeBy(100)
        vm.submit()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.searchVn("test", 1, any()) }
    }

    @Test fun changingQueryCancelsOldRequestWithoutDisplayingCancellationError() = runTest(dispatcher) {
        coEvery { repository.searchVn("old", any(), any()) } coAnswers {
            delay(1000)
            VndbPage(listOf(VisualNovel(id = "v1")))
        }
        coEvery { repository.searchVn("new", any(), any()) } returns
            VndbPage(listOf(VisualNovel(id = "v2")))
        val vm = SearchViewModel(repository)
        vm.onQueryChange("old")
        advanceTimeBy(380)
        runCurrent()
        vm.onQueryChange("new")
        runCurrent()
        assertNull(vm.state.value.error)
        advanceUntilIdle()
        assertEquals(listOf("v2"), vm.state.value.vns.map { it.id })
        assertNull(vm.state.value.error)
    }

    @Test fun overlappingPagesAreUniqueAndFailedPageCanBeRetried() = runTest(dispatcher) {
        coEvery { repository.searchVn(any(), 1, any()) } returns
            VndbPage(listOf(VisualNovel(id = "v1")), more = true)
        var attempts = 0
        coEvery { repository.searchVn(any(), 2, any()) } coAnswers {
            if (++attempts == 1) error("network failure")
            VndbPage(listOf(VisualNovel(id = "v1"), VisualNovel(id = "v2")))
        }
        val vm = SearchViewModel(repository)
        vm.submit()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.page)
        vm.loadMore()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.page)
        assertEquals(listOf("v1", "v2"), vm.state.value.vns.map { it.id })
        assertNull(vm.state.value.error)
        coVerify(exactly = 2) { repository.searchVn(any(), 2, any()) }
    }

    @Test fun switchingKindCancelsPendingAutomaticSearchAndPreservesStaffAliases() = runTest(dispatcher) {
        coEvery { repository.searchStaff(any(), any()) } returns VndbPage(
            listOf(
                Staff(id = "s1", aid = 23),
                Staff(id = "s1", aid = 23),
                Staff(id = "s1", aid = 24),
                Staff(id = "s12", aid = 3),
            ),
        )
        val vm = SearchViewModel(repository)
        vm.onQueryChange("test")
        advanceTimeBy(100)
        vm.onKind(SearchKind.STAFF)
        advanceUntilIdle()
        assertEquals(3, vm.state.value.staff.size)
        coVerify(exactly = 1) { repository.searchStaff("test", 1) }
        coVerify(exactly = 0) { repository.searchVn(any(), any(), any()) }
    }

}
