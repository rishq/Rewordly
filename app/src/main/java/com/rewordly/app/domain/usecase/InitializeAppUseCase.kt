package com.rewordly.app.domain.usecase

import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

enum class StartDestination { ONBOARDING, HOME }

/** Prepares local data and decides where the app should start. */
class InitializeAppUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): StartDestination {
        vocabularyRepository.ensureSeeded()
        val onboardingCompleted = settingsRepository.settings.first().onboardingCompleted
        return if (onboardingCompleted) StartDestination.HOME else StartDestination.ONBOARDING
    }
}
