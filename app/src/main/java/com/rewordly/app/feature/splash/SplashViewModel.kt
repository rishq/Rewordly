package com.rewordly.app.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rewordly.app.domain.usecase.InitializeAppUseCase
import com.rewordly.app.domain.usecase.StartDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val initializeApp: InitializeAppUseCase,
) : ViewModel() {
    private val _destination = MutableStateFlow<StartDestination?>(null)
    val destination: StateFlow<StartDestination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            val minimumDisplay = async { delay(MIN_SPLASH_MILLIS) }
            val start = initializeApp()
            minimumDisplay.await()
            _destination.value = start
        }
    }

    private companion object {
        const val MIN_SPLASH_MILLIS = 900L
    }
}
