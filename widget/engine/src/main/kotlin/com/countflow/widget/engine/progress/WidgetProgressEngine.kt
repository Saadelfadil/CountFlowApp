package com.countflow.widget.engine.progress

import com.countflow.core.domain.countdown.CountdownResult
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.widget.engine.model.WidgetProgress

/**
 * Calculates progress values. No UI, no drawing — that split matters because the two ways a
 * widget can *show* progress (a bar today, a Canvas-drawn ring from Milestone 5 per
 * KNOWN_ISSUES.md LIM-001) both need the exact same numbers. Putting the arithmetic here once
 * means the ring renderer, when it arrives, consumes this rather than re-deriving it.
 *
 * An `object`: [calculate] is a pure function of a [CountdownResult] and a [ProgressStyle], with
 * nothing to inject.
 */
object WidgetProgressEngine {

    /**
     * Computes the progress state for a widget whose countdown is [countdown], styled as
     * [style].
     */
    fun calculate(countdown: CountdownResult, style: ProgressStyle): WidgetProgress {
        val percent = countdown.percentCompleteWhole
        return WidgetProgress(
            style = style,
            fraction = countdown.percentComplete,
            percent = percent,
            percentText = "$percent%",
            isVisible = style != ProgressStyle.NONE,
        )
    }
}
