package com.countflow.widget.glance

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import com.countflow.core.domain.countdown.CountdownLabel
import com.countflow.core.domain.model.AppWidgetId
import com.countflow.core.domain.model.EventId
import com.countflow.core.domain.model.EventTarget
import com.countflow.core.domain.model.ProgressStyle
import com.countflow.core.domain.model.WidgetStyle
import com.countflow.widget.engine.model.WidgetProgress
import com.countflow.widget.engine.model.WidgetRenderModel
import com.countflow.widget.engine.model.WidgetTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * [CountdownWidgetContent] is a top-level `@Composable` rather than a method on
 * [CountdownGlanceWidget] for exactly this: it can be driven directly with a fabricated
 * [WidgetRenderModel] through [runGlanceAppWidgetUnitTest], with no repository, no Hilt entry
 * point, and no real widget placement involved — the same stateful-loader/stateless-renderer
 * split the app's own screens use.
 *
 * `setContext` is required before rendering anything that reads `androidx.glance.LocalContext`
 * — unlike regular Compose UI, Glance's `LocalContext` has no default and throws
 * `IllegalStateException("No default context")` if nothing supplies one first.
 *
 * Default style is [WidgetStyle.MATERIAL], the one style the design brief calls "the safest and
 * most polished" and the only one guaranteed to show every element a model can carry — Minimal,
 * Glass, Rounded, and Modern all deliberately omit or relocate some of them (see
 * `docs/WIDGET_DESIGN_GUIDE.md`), which would make MATERIAL a misleading default to assert
 * general behavior against if a test cared about, say, the progress bar or percent text existing
 * at all rather than which style is active.
 *
 * **Every test that renders anything calls `setAppWidgetSize` explicitly**, as the first line
 * inside the `runGlanceAppWidgetUnitTest` block (`STANDARD_SIZE` unless a test is specifically
 * about [WidgetSizeClass.COMPACT] or [WidgetSizeClass.WIDE]) — found while adding Session 10's
 * responsive sizes: the harness's own default size is `DpSize(349dp, 455dp)`, a full
 * small-phone-screen size with no relationship to any real widget footprint, which
 * [classifyWidgetSize] buckets as [WidgetSizeClass.WIDE]. Every test in this file predating
 * Session 10 had therefore been silently exercising the *wide* layouts the whole time, not the
 * `STANDARD` ones the design brief and `docs/screenshots/` were built around, without ever
 * setting a size at all. Setting `STANDARD_SIZE` explicitly is what makes this file test what it
 * claims to test again.
 */
@RunWith(RobolectricTestRunner::class)
class CountdownWidgetContentTest {

    private val zone = ZoneId.of("UTC")

    private fun model(
        label: CountdownLabel = CountdownLabel.InDays(12),
        showDaysValue: Boolean = true,
        showTitle: Boolean = true,
        showEmoji: Boolean = true,
        progressStyle: ProgressStyle = ProgressStyle.LINEAR,
        showPercentageText: Boolean = false,
        backgroundColorArgb: Int? = null,
        isHighContrast: Boolean = false,
        style: WidgetStyle = WidgetStyle.MATERIAL,
        cornerRadiusDp: Int? = 16,
        target: EventTarget = EventTarget.allDay(LocalDate.of(2026, 6, 27), zone),
        showDate: Boolean = false,
        isCompleted: Boolean = false,
        isExpired: Boolean = false,
    ) = WidgetRenderModel(
        eventId = EventId("event-1"),
        appWidgetId = AppWidgetId(1),
        title = "Trip to Kyoto",
        emoji = "🌸",
        daysRemaining = 12,
        showDaysValue = showDaysValue,
        label = label,
        progress = WidgetProgress(
            style = progressStyle,
            fraction = 0.4f,
            percent = 40,
            percentText = "40%",
            isVisible = progressStyle != ProgressStyle.NONE,
        ),
        theme = WidgetTheme(
            style = style,
            accentColorArgb = 0xFF00695C.toInt(),
            backgroundColorArgb = backgroundColorArgb,
            cornerRadiusDp = cornerRadiusDp,
            isHighContrast = isHighContrast,
        ),
        target = target,
        targetZone = zone,
        showTitle = showTitle,
        showEmoji = showEmoji,
        showDate = showDate,
        showPercentageText = showPercentageText,
        isCompleted = isCompleted,
        isExpired = isExpired,
    )

    @Test
    fun `draws the title and emoji when both are enabled`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model()) }

            onNode(hasText("Trip to Kyoto")).assertExists()
            onNode(hasText("🌸")).assertExists()
        }
    }

    @Test
    fun `hides the title when the binding says not to show it`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showTitle = false)) }

            onNode(hasText("Trip to Kyoto")).assertDoesNotExist()
        }
    }

    @Test
    fun `draws the day count when the model says it is meaningful`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showDaysValue = true)) }

            // hasTextEqualTo, not hasText: hasText always matches as a substring, and other
            // nodes (the day unit, a percent value) could also contain "12" as a substring.
            onNode(hasTextEqualTo("12")).assertExists()
        }
    }

    @Test
    fun `omits the day count for a near-term label`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(label = CountdownLabel.Tomorrow, showDaysValue = false))
            }

            onNode(hasText("12")).assertDoesNotExist()
        }
    }

    @Test
    fun `an unconfigured widget shows the setup prompt instead of crashing`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(null) }

            onNode(hasText("choose a countdown")).assertExists()
        }
    }

    @Test
    fun `draws the percent text only when the model asks for it`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showPercentageText = true)) }

            onNode(hasTextEqualTo("40%")).assertExists()
        }
    }

    @Test
    fun `omits the percent text when the model does not ask for it`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showPercentageText = false)) }

            onNode(hasTextEqualTo("40%")).assertDoesNotExist()
        }
    }

    @Test
    fun `still shows the percent text when progress itself is not visible, if requested`() = runTest {
        // Percentage and the progress graphic are independent choices now (the Samsung Galaxy A55
        // finding this fixes): a user can ask for "40%" as plain text with no bar or ring drawn.
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(progressStyle = ProgressStyle.NONE, showPercentageText = true))
            }

            onNode(hasTextEqualTo("40%")).assertExists()
            onNode(hasTestTag("progress-bar")).assertDoesNotExist()
            onNode(hasTestTag("progress-ring")).assertDoesNotExist()
        }
    }

    // ── Content hierarchy: the "Tomorrow / Tomorrow" redundancy this session's brief called out ──

    @Test
    fun `a NextWeek label is shown once as the headline, not duplicated as a second line`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(label = CountdownLabel.Tomorrow, showDaysValue = false))
            }

            // "Tomorrow" appears from the headline; it must not appear a second time as its own
            // secondary line underneath itself.
            onNode(hasTextEqualTo("Tomorrow")).assertExists()
        }
    }

    @Test
    fun `an ordinary day count does not repeat itself as a redundant label line`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(label = CountdownLabel.InDays(12), showDaysValue = true))
            }

            // "12" is the headline. The label text for InDays is "In 12 days" — showing that too
            // would be the exact redundancy the brief calls BAD; it must not appear.
            onNode(hasText("In 12 days")).assertDoesNotExist()
        }
    }

    @Test
    fun `NextWeek keeps its label as a genuinely informative second line`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(label = CountdownLabel.NextWeek, showDaysValue = true))
            }

            // Unlike InDays, NextWeek's label says something the bare number doesn't.
            onNode(hasText("Next week")).assertExists()
        }
    }

    @Test
    fun `a timed near-term event shows a clock time as its second line`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            val timed = EventTarget.timed(LocalDateTime.of(2026, 6, 28, 8, 0), zone)
            provideComposable {
                CountdownWidgetContent(
                    model(label = CountdownLabel.Tomorrow, showDaysValue = false, target = timed),
                )
            }

            onNode(hasText("8:00")).assertExists()
        }
    }

    @Test
    fun `completed events show the status as the headline and the title as the second line, not the identity row`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(label = CountdownLabel.Completed, isCompleted = true))
            }

            onNode(hasTextEqualTo("Completed")).assertExists()
            // The title now carries the "which event" information the identity row used to —
            // it must still appear exactly once, not disappear and not double up.
            onNode(hasText("Trip to Kyoto")).assertExists()
        }
    }

    // ── Every style renders its headline without crashing, and actually differs in what it shows ──

    @Test
    fun `minimal draws percentage text when requested — corrected from an earlier session's assumption`() = runTest {
        // A real Samsung Galaxy A55 finding overturns what used to be asserted here: this test
        // previously required Minimal to omit percentage text entirely, on the theory that it had
        // "no percentage-text slot" by design. That theory did not survive physical-device
        // evidence — the Customize Widget preview already showed percentage for every style
        // unconditionally, so Minimal silently omitting it on the real widget was a Preview/Glance
        // parity bug, not a deliberate minimalist choice (docs/WIDGET_SIZE_MATRIX.md's Minimal
        // entry itself was already stale on this exact point, predating Style/Progress becoming
        // independent settings). Percentage visibility must never be conditional on which
        // WidgetStyle is active, only on the binding's own toggle — see the truth-table sweep below.
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(style = WidgetStyle.MINIMAL, showPercentageText = true))
            }

            onNode(hasTextEqualTo("40%")).assertExists()
        }
    }

    // ── OLED identity/date/percentage (second Samsung Galaxy A55 physical-device finding): OLED
    // used to never draw its identity row at all, regardless of showTitle/showEmoji — not a
    // contrast defect (its palette was already the highest-contrast one any style resolves), a
    // missing render path. Title, emoji, target date, and percentage now all follow their own
    // toggle for OLED exactly like every other style already does. ──

    @Test
    fun `oled draws the title when enabled`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(style = WidgetStyle.OLED, showTitle = true)) }

            onNode(hasText("Trip to Kyoto")).assertExists()
        }
    }

    @Test
    fun `oled draws the emoji when enabled`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(style = WidgetStyle.OLED)) }

            // model() always builds showEmoji = true — every other style's own tests already rely
            // on that same fixed value rather than each test threading its own emoji flag through.
            onNode(hasText("🌸")).assertExists()
        }
    }

    @Test
    fun `oled omits the title when disabled`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(style = WidgetStyle.OLED, showTitle = false)) }

            onNode(hasText("Trip to Kyoto")).assertDoesNotExist()
        }
    }

    @Test
    fun `oled draws the target date and percentage when both are enabled`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(style = WidgetStyle.OLED, showDate = true, showPercentageText = true))
            }

            onNode(hasText("Jun")).assertExists()
            onNode(hasTextEqualTo("40%")).assertExists()
        }
    }

    @Test
    fun `oled at wide still draws title, date, and percentage when enabled, alongside a ring that fits`() = runTest {
        // The exact combination the Samsung Galaxy A55 exposed: OLED, Ring progress, at 4x2 — now
        // with title/date/percentage also respected rather than silently dropped. This cannot
        // assert the ring's real on-device pixel bounds (Robolectric has no such measurement, see
        // the sweep test below), only that every toggle still renders correctly alongside it.
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(WIDE_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(
                    model(
                        style = WidgetStyle.OLED,
                        progressStyle = ProgressStyle.CIRCULAR,
                        showDate = true,
                        showPercentageText = true,
                    ),
                )
            }

            onNode(hasText("Trip to Kyoto")).assertExists()
            onNode(hasText("Jun")).assertExists()
            onNode(hasTextEqualTo("40%")).assertExists()
            onNode(hasTestTag("progress-ring")).assertExists()
        }
    }

    // ── Style × Progress independence (Samsung Galaxy A55 physical-device finding): every
    // selectable style must draw None/Bar/Ring truthfully off its own progress.style, never a
    // style-specific special case. Sweeps WidgetStyle.selectable (not .entries — legacy PROGRESS
    // is covered separately, below) × every ProgressStyle, which subsumes the brief's own named
    // spot checks (OLED+Ring, Modern+Ring, Glass+Ring, Material+Ring all appear as one iteration
    // each) rather than duplicating them as four near-identical tests. ──

    @Test
    fun `every selectable style draws none, bar, or ring truthfully, never substituting one for another`() = runTest {
        WidgetStyle.selectable.forEach { style ->
            ProgressStyle.entries.forEach { progressStyle ->
                runGlanceAppWidgetUnitTest {
                    setAppWidgetSize(STANDARD_SIZE)
                    setContext(ApplicationProvider.getApplicationContext())
                    provideComposable { CountdownWidgetContent(model(style = style, progressStyle = progressStyle)) }

                    when (progressStyle) {
                        ProgressStyle.NONE -> {
                            onNode(hasTestTag("progress-bar")).assertDoesNotExist()
                            onNode(hasTestTag("progress-ring")).assertDoesNotExist()
                        }
                        ProgressStyle.LINEAR -> {
                            onNode(hasTestTag("progress-bar")).assertExists()
                            onNode(hasTestTag("progress-ring")).assertDoesNotExist()
                        }
                        ProgressStyle.CIRCULAR -> {
                            // If Ring is selected, a Bar must never render silently in its place —
                            // the exact bug the Samsung Galaxy A55 exposed.
                            onNode(hasTestTag("progress-ring")).assertExists()
                            onNode(hasTestTag("progress-bar")).assertDoesNotExist()
                        }
                    }
                }
            }
        }
    }

    // ── Percentage/progress-graphic independence, per style, per size (this session's own
    // Samsung Galaxy A55 finding: Ring correctly displayed in the Customize Widget preview with
    // its percentage underneath, but the real widget dropped the percentage for Minimal, Glass,
    // and Rounded — a Style-conditional omission the truth table below forbids). Percentage
    // visibility must depend only on the binding's own toggle: never on Linear vs Circular, and
    // never on which WidgetStyle is active. Sweeps every selectable style × every ProgressStyle at
    // both size classes that draw a percentage at all (COMPACT never does — see its own dedicated
    // test further down) — this subsumes the brief's minimum matrix (STANDARD/WIDE × Bar/Ring/None
    // × percentage on) as one pair of loops rather than enumerating each cell by hand. ──

    @Test
    fun `percentage renders when requested, for every selectable style and every progress style, at standard and wide`() = runTest {
        for (dpSize in listOf(STANDARD_SIZE, WIDE_SIZE)) {
            WidgetStyle.selectable.forEach { style ->
                ProgressStyle.entries.forEach { progressStyle ->
                    runGlanceAppWidgetUnitTest {
                        setAppWidgetSize(dpSize)
                        setContext(ApplicationProvider.getApplicationContext())
                        provideComposable {
                            CountdownWidgetContent(
                                model(style = style, progressStyle = progressStyle, showPercentageText = true),
                            )
                        }

                        onNode(hasTextEqualTo("40%")).assertExists()
                    }
                }
            }
        }
    }

    @Test
    fun `percentage never renders when not requested, for every selectable style and every progress style, at standard and wide`() = runTest {
        for (dpSize in listOf(STANDARD_SIZE, WIDE_SIZE)) {
            WidgetStyle.selectable.forEach { style ->
                ProgressStyle.entries.forEach { progressStyle ->
                    runGlanceAppWidgetUnitTest {
                        setAppWidgetSize(dpSize)
                        setContext(ApplicationProvider.getApplicationContext())
                        provideComposable {
                            CountdownWidgetContent(
                                model(style = style, progressStyle = progressStyle, showPercentageText = false),
                            )
                        }

                        onNode(hasTextEqualTo("40%")).assertDoesNotExist()
                    }
                }
            }
        }
    }

    // ── Today/Tomorrow: a semantic headline word, not a 1-3 digit count (the brief's own explicit
    // warning against assuming otherwise) — verified alongside a Ring and a percentage, at both
    // sizes, since that combination stresses the vertical budget the most and is exactly the
    // combination BUG 2/BUG 3's spacing and ring-size fixes target. Robolectric cannot assert real
    // pixel wrapping (see WidgetHeadline.isNumeric's own tests above), only that the correct word
    // renders, once, alongside everything else that should still be present. ──

    @Test
    fun `today and tomorrow headlines render correctly alongside a ring and percentage, at standard and wide`() = runTest {
        for (dpSize in listOf(STANDARD_SIZE, WIDE_SIZE)) {
            listOf(CountdownLabel.Today to "Today", CountdownLabel.Tomorrow to "Tomorrow").forEach { (label, text) ->
                WidgetStyle.selectable.forEach { style ->
                    runGlanceAppWidgetUnitTest {
                        setAppWidgetSize(dpSize)
                        setContext(ApplicationProvider.getApplicationContext())
                        provideComposable {
                            CountdownWidgetContent(
                                model(
                                    style = style,
                                    label = label,
                                    showDaysValue = false,
                                    progressStyle = ProgressStyle.CIRCULAR,
                                    showPercentageText = true,
                                ),
                            )
                        }

                        onNode(hasTextEqualTo(text)).assertExists()
                        onNode(hasTestTag("progress-ring")).assertExists()
                        onNode(hasTextEqualTo("40%")).assertExists()
                    }
                }
            }
        }
    }

    // ── WIDE design system (Milestone 5A follow-up): every selectable style's WIDE form is now a
    // genuine two-region "context (left) ↔ countdown (right)" composition, not a centered 2×2
    // stretched wide — including Minimal and OLED, previously the two single-column exceptions.
    // Identity anchors to the left edge of its column (StartIdentity, never CenteredIdentity —
    // the real Samsung Galaxy A55 dead-zone finding named specifically for Glass in the
    // originating report: CenteredIdentity inside a stretched defaultWeight() column drifts toward
    // the card's middle instead of staying near the edge). Robolectric has no bounds/position
    // assertion for Glance nodes, so none of this can assert pixel positions directly — only that
    // every region's content renders correctly, together, end to end. ──

    @Test
    fun `every selectable style still renders identity, headline, and ring together at wide`() = runTest {
        WidgetStyle.selectable.forEach { style ->
            runGlanceAppWidgetUnitTest {
                setAppWidgetSize(WIDE_SIZE)
                setContext(ApplicationProvider.getApplicationContext())
                provideComposable {
                    CountdownWidgetContent(
                        model(style = style, progressStyle = ProgressStyle.CIRCULAR, showDate = true, showPercentageText = true),
                    )
                }

                onNode(hasText("Trip to Kyoto")).assertExists()
                onNode(hasTextEqualTo("12")).assertExists()
                onNode(hasTestTag("progress-ring")).assertExists()
                onNode(hasTextEqualTo("40%")).assertExists()
            }
        }
    }

    // ── hasWideContext rebalancing: when a style's WIDE left ("context") region would have
    // nothing to draw at all, the countdown region takes the full card instead of leaving a
    // meaningless empty left half — the brief's own explicit requirement. Robolectric cannot
    // assert that the empty column was never reserved, only that the countdown itself still
    // renders correctly when context disappears; the structural change (no weighted left Column at
    // all in that case) is verified by code review, not by this test. ──

    @Test
    fun `at wide, with no context to show at all, the countdown still renders correctly for every selectable style`() = runTest {
        WidgetStyle.selectable.forEach { style ->
            runGlanceAppWidgetUnitTest {
                setAppWidgetSize(WIDE_SIZE)
                setContext(ApplicationProvider.getApplicationContext())
                provideComposable {
                    CountdownWidgetContent(
                        model(
                            style = style,
                            showTitle = false,
                            showEmoji = false,
                            showDate = false,
                            progressStyle = ProgressStyle.CIRCULAR,
                            showPercentageText = true,
                        ),
                    )
                }

                onNode(hasText("Trip to Kyoto")).assertDoesNotExist()
                onNode(hasText("🌸")).assertDoesNotExist()
                onNode(hasTextEqualTo("12")).assertExists()
                onNode(hasTestTag("progress-ring")).assertExists()
                onNode(hasTextEqualTo("40%")).assertExists()
            }
        }
    }

    @Test
    fun `at wide, emoji alone still shows identity when title is off`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(WIDE_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showTitle = false, showEmoji = true)) }

            onNode(hasText("🌸")).assertExists()
            onNode(hasText("Trip to Kyoto")).assertDoesNotExist()
        }
    }

    @Test
    fun `at wide, title alone still shows identity when emoji is off`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(WIDE_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showTitle = true, showEmoji = false)) }

            onNode(hasText("Trip to Kyoto")).assertExists()
            onNode(hasText("🌸")).assertDoesNotExist()
        }
    }

    @Test
    fun `at wide, target date toggles independently of the rest of the context region`() = runTest {
        listOf(WidgetStyle.MATERIAL, WidgetStyle.MODERN).forEach { style ->
            runGlanceAppWidgetUnitTest {
                setAppWidgetSize(WIDE_SIZE)
                setContext(ApplicationProvider.getApplicationContext())
                provideComposable { CountdownWidgetContent(model(style = style, showDate = true)) }
                onNode(hasText("Jun")).assertExists()
            }
            runGlanceAppWidgetUnitTest {
                setAppWidgetSize(WIDE_SIZE)
                setContext(ApplicationProvider.getApplicationContext())
                provideComposable { CountdownWidgetContent(model(style = style, showDate = false)) }
                onNode(hasText("Jun")).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `every style renders the headline day count without throwing`() = runTest {
        WidgetStyle.entries.forEach { style ->
            runGlanceAppWidgetUnitTest {
                setAppWidgetSize(STANDARD_SIZE)
                setContext(ApplicationProvider.getApplicationContext())
                provideComposable { CountdownWidgetContent(model(style = style)) }

                onNode(hasTextEqualTo("12")).assertExists()
            }
        }
    }

    // ── WidgetHeadline.isNumeric: the flag that stops a status word wrapping mid-word ──
    // Found on a real device (docs/WIDGET_DESIGN_REVIEW.md): a headline font sized for "7"
    // wraps "Completed" into "Compl" / "eted" with no hyphen. Every layout selects a smaller
    // font for a non-numeric headline via this flag — Glance's own testing API cannot assert a
    // resolved font size or observe TextView-level wrapping, so this is the testable half of the
    // fix, not a claim it also proves the wrap is gone on every device.

    @Test
    fun `a bare day count is numeric`() {
        val headline = WidgetHeadline(primary = "218", unit = "days", secondary = null, showIdentity = true)
        assertThat(headline.isNumeric).isTrue()
    }

    @Test
    fun `a status word is not numeric`() {
        val headline = WidgetHeadline(primary = "Completed", unit = null, secondary = "Finished Project", showIdentity = false)
        assertThat(headline.isNumeric).isFalse()
    }

    @Test
    fun `modern shows the target date when the binding asks for it`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(style = WidgetStyle.MODERN, showDate = true))
            }

            onNode(hasText("Jun")).assertExists()
        }
    }

    // ── classifyWidgetSize: the pure function every size-aware test and every real render both
    // trust to bucket a DpSize correctly — verified directly, not just through its callers.
    // Sizes below are the real on-device measurements this session took to correct the original,
    // formula-derived thresholds (see WidgetSizeClass.kt's own doc for the full account of why
    // the formula was wrong): a real 2×2 default placement measured 172×224dp; the same widget
    // resized to a real, launcher-confirmed 2×1 measured 172×104dp. The 4×2 width (320dp) is
    // reasoned, not measured — see the same doc comment. ──

    @Test
    fun `a real on-device 2x1 resize classifies as compact`() {
        assertThat(classifyWidgetSize(widthDp = 172f, heightDp = 104f)).isEqualTo(WidgetSizeClass.COMPACT)
    }

    @Test
    fun `a real on-device 2x2 default placement classifies as standard`() {
        assertThat(classifyWidgetSize(widthDp = 172f, heightDp = 224f)).isEqualTo(WidgetSizeClass.STANDARD)
    }

    @Test
    fun `a wide footprint classifies as wide`() {
        assertThat(classifyWidgetSize(widthDp = 320f, heightDp = 224f)).isEqualTo(WidgetSizeClass.WIDE)
    }

    @Test
    fun `height decides compact ahead of width, even for a short and wide footprint`() {
        // A launcher offering a 4x1-shaped combination (wide, but as short as the real 2x1
        // measurement) should still get the single-row compact treatment, not a two-column wide
        // one it has no vertical room for.
        assertThat(classifyWidgetSize(widthDp = 320f, heightDp = 104f)).isEqualTo(WidgetSizeClass.COMPACT)
    }

    @Test
    fun `sizes right at the calibrated breakpoints resolve to the smaller neighbor`() {
        assertThat(classifyWidgetSize(widthDp = 172f, heightDp = COMPACT_MAX_HEIGHT_DP)).isEqualTo(WidgetSizeClass.STANDARD)
        assertThat(classifyWidgetSize(widthDp = WIDE_MIN_WIDTH_DP, heightDp = 224f)).isEqualTo(WidgetSizeClass.WIDE)
    }

    // ── Size-aware rendering: COMPACT hides fields STANDARD shows, and WIDE never repeats a fact
    // across its two columns. Session 10. ──

    @Test
    fun `compact never draws the secondary line`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(COMPACT_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(label = CountdownLabel.NextWeek, showDaysValue = true))
            }

            // "Next week" is a genuine secondary line at STANDARD (see the test above with the
            // same model) — COMPACT drops it regardless, since the layout has no room for a
            // second fact per docs/WIDGET_SIZE_MATRIX.md.
            onNode(hasTextEqualTo("12")).assertExists()
            onNode(hasText("Next week")).assertDoesNotExist()
        }
    }

    @Test
    fun `compact never draws a percentage even when the binding asks for it`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(COMPACT_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showPercentageText = true)) }

            onNode(hasTextEqualTo("40%")).assertDoesNotExist()
        }
    }

    @Test
    fun `every style renders the headline exactly once at every size class`() = runTest {
        // The regression this guards: an early draft of ProgressLayoutWide drew the headline as
        // text in its left column *and* again inside the ring in its right column — two nodes
        // for one fact. Sweeping every style across all three sizes catches the same class of
        // mistake anywhere else it might recur, not just in the one layout it already did.
        for (dpSize in listOf(COMPACT_SIZE, STANDARD_SIZE, WIDE_SIZE)) {
            WidgetStyle.entries.forEach { style ->
                runGlanceAppWidgetUnitTest {
                    setAppWidgetSize(dpSize)
                    setContext(ApplicationProvider.getApplicationContext())
                    provideComposable { CountdownWidgetContent(model(style = style)) }

                    onNode(hasTextEqualTo("12")).assertExists()
                }
            }
        }
    }

    @Test
    fun `wide progress draws the ring with no duplicate headline text inside it`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(WIDE_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(style = WidgetStyle.PROGRESS)) }

            // Exactly one "12" — the textual reading in the left column. The ring beside it is a
            // pure visual, per ProgressLayoutWide's own doc.
            onNode(hasTextEqualTo("12")).assertExists()
        }
    }

    @Test
    fun `accessibility description omits the percentage at compact even though it is visible at standard`() = runTest {
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(STANDARD_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showPercentageText = true)) }
            onNode(hasContentDescription("40% complete")).assertExists()
        }
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(COMPACT_SIZE)
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showPercentageText = true)) }
            // A screen reader must not announce a fact COMPACT never draws on screen.
            onNode(hasContentDescription("40% complete")).assertDoesNotExist()
        }
    }

    private companion object {
        // Real on-device measurements (WidgetSizeClass.kt's own doc has the full account) —
        // WIDE_SIZE's width is reasoned, not measured; everything else here is what a real
        // Pixel Launcher on a real device actually reported this session.
        val COMPACT_SIZE = DpSize(172.dp, 104.dp)
        val STANDARD_SIZE = DpSize(172.dp, 224.dp)
        val WIDE_SIZE = DpSize(320.dp, 224.dp)
    }
}
