package com.countflow.core.common.di

import javax.inject.Qualifier

/**
 * Identifies which [kotlinx.coroutines.CoroutineDispatcher] an injection site wants.
 *
 * Injecting dispatchers rather than referencing [kotlinx.coroutines.Dispatchers] directly is
 * what makes repositories and use cases testable: a test can substitute a single
 * `TestDispatcher` for all of them and drive time deterministically.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: CountFlowDispatcher)

/** The dispatchers CountFlow injects. */
enum class CountFlowDispatcher {
    /** Disk and database work. */
    IO,

    /** CPU-bound work such as countdown computation and bitmap rendering. */
    Default,
}

/**
 * Marks the application-lifetime [kotlinx.coroutines.CoroutineScope].
 *
 * Used for work that must outlive any single screen or broadcast — never for work a
 * ViewModel could own, which belongs in `viewModelScope`.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
