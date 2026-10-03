package com.rewordly.app.core.common.di

import com.rewordly.app.core.common.IdProvider
import com.rewordly.app.core.common.SystemTimeProvider
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.core.common.UuidProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {
    @Binds
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds
    abstract fun bindIdProvider(impl: UuidProvider): IdProvider
}
