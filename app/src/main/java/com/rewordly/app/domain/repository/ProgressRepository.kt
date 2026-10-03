package com.rewordly.app.domain.repository

import com.rewordly.app.domain.model.ProgressOverview
import com.rewordly.app.domain.model.ReviewHistory
import com.rewordly.app.domain.model.ReviewStatistics
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    fun observeOverview(): Flow<ProgressOverview>

    /** Review statistics computed from the persisted review log and word schedules. */
    fun observeStatistics(): Flow<ReviewStatistics>

    /** Daily review history, newest day first, grouped in the user's current local calendar. */
    fun observeHistory(): Flow<ReviewHistory>
}
