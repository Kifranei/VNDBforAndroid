package app.vndb.ui.discover

import app.vndb.AppContainer
import app.vndb.data.api.VndbPage
import app.vndb.data.model.DbStats
import app.vndb.data.repo.VndbRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<VndbRepository>()
    private val container = mockk<AppContainer>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { container.repository } returns repository
        coEvery { repository.randomQuote() } returns null
        coEvery { repository.stats() } returns DbStats()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun vnRequestFailureBecomesErrorStateInsteadOfCrashing() = runTest(dispatcher) {
        coEvery { repository.topRated(any(), any()) } throws RuntimeException("Request timeout has expired")
        coEvery { repository.recentlyReleased(any(), any()) } returns VndbPage()
        coEvery { repository.mostVoted(any(), any()) } returns VndbPage()

        val vm = DiscoverViewModel(container)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertFalse(state.refreshing)
        assertEquals("Request timeout has expired", state.error)
        assertTrue(state.topRated.isEmpty())
    }
}
