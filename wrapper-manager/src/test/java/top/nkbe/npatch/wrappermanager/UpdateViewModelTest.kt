package top.nkbe.npatch.wrappermanager

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val release = UpdateChecker.parseRelease(releaseJson())

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }

    private fun model(current: String = "1.0.9", loader: () -> ReleaseInfo = { release }) =
        UpdateViewModel(loader, current, dispatcher).also { store.put("updates", it) }

    @Test fun openingScreenAndObservingStateDoNotTriggerChecks() = runTest(dispatcher) {
        var calls = 0
        val model = model(loader = { calls++; release })
        assertEquals(UpdateState.Idle, model.state.value)
        advanceUntilIdle()
        assertEquals(0, calls)
    }

    @Test fun checkIsManualAndRepeatedClicksDoNotStartParallelRequests() = runTest(dispatcher) {
        var calls = 0
        val model = model(loader = { calls++; release })
        model.checkForUpdates()
        model.checkForUpdates()
        assertEquals(UpdateState.Checking, model.state.value)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(UpdateState.Available(release), model.state.value)
        model.checkForUpdates()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun sameVersionAndNewerInstalledVersionAreCurrent() = runTest(dispatcher) {
        for (version in listOf("1.0.10", "1.0.11")) {
            val model = model(version)
            model.checkForUpdates()
            advanceUntilIdle()
            assertEquals(UpdateState.Current(release), model.state.value)
        }
    }

    @Test fun failureCanBeRetriedManually() = runTest(dispatcher) {
        var calls = 0
        val model = model(loader = {
            if (calls++ == 0) throw UpdateCheckException(UpdateFailure.NETWORK)
            release
        })
        model.checkForUpdates()
        advanceUntilIdle()
        assertEquals(UpdateState.Failed(UpdateFailure.NETWORK), model.state.value)
        model.checkForUpdates()
        advanceUntilIdle()
        assertEquals(UpdateState.Available(release), model.state.value)
        assertEquals(2, calls)
    }

    @Test fun invalidInstalledVersionDoesNotProduceAnUpdateOffer() = runTest(dispatcher) {
        val model = model("unknown")
        model.checkForUpdates()
        advanceUntilIdle()
        assertEquals(UpdateState.Failed(UpdateFailure.INVALID_RELEASE), model.state.value)
    }
}
