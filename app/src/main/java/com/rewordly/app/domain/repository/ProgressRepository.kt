package com.rewordly.app.domain.repository

import com.rewordly.app.domain.model.ProgressOverview
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    fun observeOverview(): Flow<ProgressOverview>
}
