package com.countflow.widget.glance.progress

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.glance.ImageProvider

/**
 * Draws determinate circular progress to a [Bitmap], the only way to get one at all — Glance's
 * own `CircularProgressIndicator` is indeterminate-only in 1.1.1, confirmed directly against the
 * AAR (KNOWN_ISSUES.md LIM-001).
 *
 * ### Memory budget (revised Session 10 — now two ring diameters, not one)
 *
 * A host silently drops a widget whose bitmaps exceed `6 × screenWidthPx × screenHeightPx` bytes
 * total (LIM-003; ≈15.5MB on a 1080×2400 screen). Before Session 10's responsive sizes, exactly
 * one ring diameter ever existed (`WidgetSizeClass.STANDARD`'s, ~200px at typical density, `200×
 * 200×4 ≈ 160KB` as `ARGB_8888`). [WidgetSizeClass.WIDE]'s ring (`CountdownWidgetLayouts.kt`'s
 * `ProgressLayoutWide`) is genuinely larger — sized against half the card's width instead of the
 * whole card's shorter side — at ~258px, `258×258×4 ≈ 266KB`, the largest diameter this cache
 * ever holds. Every style's own smaller decorative ring (`ProgressGraphic`'s fixed 56dp/64dp,
 * added once Style and Progress became independent settings) stays well under that same worst
 * case rather than adding to it. Worst case with [MAX_CACHED] at [MAX_CACHED] entries,
 * pessimistically assuming *every* cached entry is the largest WIDE size regardless of how many
 * distinct diameters are actually in play, is `266KB × 40 ≈ 10.6MB` — still comfortably under
 * budget, with real headroom left for everything else the launcher itself needs to keep resident.
 * [WidgetSizeClass.COMPACT] never reaches this class at all (`ProgressLayoutCompact` falls back
 * to plain text, D-054, and no other Compact layout draws progress either) — there is no
 * ring-below-[MIN_RING_DP] case to budget for.
 *
 * ### Quantization
 *
 * [percent] is a whole number, not a `Float` fraction, so the cache holds at most 101 entries per
 * (size, color) combination rather than regenerating a new bitmap on every fractional-percent
 * recomposition — the same reasoning [com.countflow.widget.engine.model.WidgetProgress.percent]
 * exists as its own field rather than being derived from `fraction` at render time. Session 10
 * adds a second quantization: `sizePx` itself is rounded to the nearest 8px by
 * `CountdownWidgetLayouts.kt`'s `ProgressRing` before it ever reaches this cache — `SizeMode.Exact`
 * (D-053) reports a widget's genuine current size, which can vary by a handful of dp between
 * otherwise-identical placements (density rounding, per-launcher grid quirks), and without that
 * rounding each such placement would mint its own cache entry for no visual difference.
 */
internal object CircularProgressRenderer {

    /**
     * The bitmap for a ring [sizePx] wide, [percent] complete (`0..100`), track color
     * [trackArgb], progress color [progressArgb], and [strokeWidthPx] wide — from the cache if
     * this exact combination was already drawn, otherwise rendered once and cached.
     */
    fun provider(
        sizePx: Int,
        percent: Int,
        trackArgb: Int,
        progressArgb: Int,
        strokeWidthPx: Float,
    ): ImageProvider {
        val key = CacheKey(sizePx, percent.coerceIn(0, 100), trackArgb, progressArgb, strokeWidthPx)
        val bitmap = synchronized(cache) {
            cache.getOrPut(key) { render(key) }
        }
        return ImageProvider(bitmap)
    }

    private fun render(key: CacheKey): Bitmap {
        val bitmap = createBitmap(key.sizePx, key.sizePx)
        val canvas = Canvas(bitmap)
        val inset = key.strokeWidthPx / 2f
        val rect = RectF(inset, inset, key.sizePx - inset, key.sizePx - inset)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = key.trackArgb
            style = Paint.Style.STROKE
            strokeWidth = key.strokeWidthPx
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(rect, START_ANGLE, FULL_SWEEP, false, trackPaint)

        val sweep = FULL_SWEEP * (key.percent / 100f)
        if (sweep > 0f) {
            val progressPaint = Paint(trackPaint).apply { color = key.progressArgb }
            canvas.drawArc(rect, START_ANGLE, sweep, false, progressPaint)
        }
        return bitmap
    }

    private data class CacheKey(
        val sizePx: Int,
        val percent: Int,
        val trackArgb: Int,
        val progressArgb: Int,
        val strokeWidthPx: Float,
    )

    // Access-order LinkedHashMap doubling as an LRU cache: the eldest (least recently used) entry
    // is evicted once size exceeds MAX_CACHED, so a widget cycling through many percent values
    // over its lifetime cannot grow this without bound.
    private val cache = object : LinkedHashMap<CacheKey, Bitmap>(MAX_CACHED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, Bitmap>): Boolean =
            size > MAX_CACHED
    }

    private const val MAX_CACHED = 40
    private const val START_ANGLE = -90f
    private const val FULL_SWEEP = 360f
}
