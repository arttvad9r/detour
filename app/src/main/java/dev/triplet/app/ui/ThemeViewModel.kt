package dev.triplet.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.triplet.app.data.RoutesStore
import dev.triplet.app.data.TriSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ThemeUiState(
    val selectedThemeId: String = AppTheme.byId("").id,
)

internal fun themeUiState(
    settings: TriSettings?,
    themeOverride: String? = null,
): ThemeUiState = ThemeUiState(
    selectedThemeId = themeOverride ?: AppTheme.byId(settings?.themeId.orEmpty()).id,
)

class ThemeViewModel(
    private val settings: StateFlow<TriSettings?>,
    private val persistTheme: suspend (String) -> Unit,
) : ViewModel() {
    private val themeOverride = MutableStateFlow<String?>(null)
    private val writeMutex = Mutex()

    val uiState: StateFlow<ThemeUiState> = combine(
        settings,
        themeOverride,
        ::themeUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = themeUiState(settings.value),
    )

    fun selectTheme(themeId: String) {
        require(AppTheme.entries.any { it.id == themeId })
        val currentIntent = themeOverride.value ?: AppTheme.byId(settings.value?.themeId.orEmpty()).id
        if (currentIntent == themeId) return
        themeOverride.value = themeId

        viewModelScope.launch {
            writeMutex.withLock {
                val desired = themeOverride.value ?: return@withLock
                try {
                    if (AppTheme.byId(settings.value?.themeId.orEmpty()).id != desired) {
                        persistTheme(desired)
                    }
                    if (AppTheme.byId(settings.value?.themeId.orEmpty()).id != desired) {
                        settings.first { AppTheme.byId(it?.themeId.orEmpty()).id == desired }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (themeOverride.value == desired) themeOverride.value = null
                    return@withLock
                }

                if (themeOverride.value == desired) themeOverride.value = null
            }
        }
    }

    companion object {
        fun factory(store: RoutesStore): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ThemeViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return ThemeViewModel(
                    settings = store.settings,
                    persistTheme = store::setTheme,
                ) as T
            }
        }
    }
}
