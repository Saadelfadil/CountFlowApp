package com.countflow.app.ads

import com.countflow.widget.glance.configuration.RewardedStyleAdController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [RewardedStyleAdController] — the abstraction `:widget:glance` depends on — to its real,
 * `:app`-only, AdMob-backed implementation. `:widget:glance`'s own Hilt graph never needs to know
 * this binding exists; Hilt resolves it here, at the top of the module graph, the same way any
 * `:app`-level implementation of a lower-module interface is wired.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AdsModule {

    @Binds
    @Singleton
    abstract fun bindsRewardedStyleAdController(
        impl: AdMobRewardedStyleAdController,
    ): RewardedStyleAdController
}
