package com.countflow.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Provides the coroutine dispatchers and the application-lifetime scope. */
@Module
@InstallIn(SingletonComponent::class)
internal object CoroutinesModule {

    @Provides
    @Dispatcher(CountFlowDispatcher.IO)
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(CountFlowDispatcher.Default)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * A [SupervisorJob] is used so that one failed child coroutine does not cancel unrelated
     * application-scoped work — a failed widget refresh must not take down reminder scheduling.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun providesApplicationScope(
        @Dispatcher(CountFlowDispatcher.Default) dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
