package com.countflow.core.common.di

import com.countflow.core.common.log.AndroidLogger
import com.countflow.core.common.log.Logger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the logging facade to its Logcat-backed implementation. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class LoggingModule {

    @Binds
    @Singleton
    abstract fun bindsLogger(impl: AndroidLogger): Logger
}
