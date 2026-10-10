package com.rewordly.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.domain.model.ReviewDay
import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.usecase.ActivityCalendarBuilder
import com.rewordly.app.domain.usecase.CalendarCell
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data object Empty : HistoryUiState

    data class Content(
        val days: List<ReviewDay>,
        /** Weeks oldest first, each with seven days starting at [firstDayOfWeek]. */
        val calendar: List<List<CalendarCell>>,
        val firstDayOfWeek: DayOfWeek,
    ) : HistoryUiState

    data class Error(val error: AppError) : HistoryUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    progressRepository: ProgressRepository,
    timeProvider: TimeProvider,
) : ViewModel() {
    private val retry = MutableStateFlow(0)

    val uiState: StateFlow<HistoryUiState> = retry.flatMapLatest {
        progressRepository.observeHistory().map { history ->
            if (history.days.isEmpty()) {
                HistoryUiState.Empty
            } else {
                val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
                HistoryUiState.Content(
                    days = history.days,
                    calendar = ActivityCalendarBuilder.build(
                        activity = history.days.associate { it.date to it.activity },
                        today = timeProvider.today(),
                        weeks = CALENDAR_WEEKS,
                        firstDayOfWeek = firstDay,
                    ),
                    firstDayOfWeek = firstDay,
                )
            } as HistoryUiState
        }
            // Grouping 365 days and rebuilding a 15x7 calendar is not main-thread work.
            .flowOn(Dispatchers.Default)
            .catch { emit(HistoryUiState.Error(AppError.Database(it))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HistoryUiState.Loading)

    fun retry() {
        retry.value++
    }

    private companion object {
        const val CALENDAR_WEEKS = 15
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
