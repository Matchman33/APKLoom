package top.nkbe.npatch.wrappermanager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Current(val release: ReleaseInfo) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Failed(val reason: UpdateFailure) : UpdateState
}

internal class UpdateViewModel(
    private val loadRelease: () -> ReleaseInfo = { UpdateChecker().latest() },
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutable = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = mutable.asStateFlow()

    fun checkForUpdates() {
        if (mutable.value == UpdateState.Checking) return
        mutable.value = UpdateState.Checking
        viewModelScope.launch {
            try {
                val release = withContext(ioDispatcher) { loadRelease() }
                val installed = AppVersion.parse(currentVersion)
                    ?: throw UpdateCheckException(UpdateFailure.INVALID_RELEASE)
                val latest = AppVersion.parse(release.version)
                    ?: throw UpdateCheckException(UpdateFailure.INVALID_RELEASE)
                mutable.value = if (latest > installed) UpdateState.Available(release) else UpdateState.Current(release)
            } catch (error: CancellationException) {
                throw error
            } catch (error: UpdateCheckException) {
                mutable.value = UpdateState.Failed(error.failure)
            } catch (_: Exception) {
                mutable.value = UpdateState.Failed(UpdateFailure.SERVICE)
            }
        }
    }
}
