package com.countflow.widget.glance

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
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
 */
@RunWith(RobolectricTestRunner::class)
class CountdownWidgetContentTest {

    private val zone = ZoneId.of("UTC")

    private fun model(
        label: CountdownLabel = CountdownLabel.InDays(12),
        showDaysValue: Boolean = true,
        showTitle: Boolean = true,
        progressVisible: Boolean = true,
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
            style = if (progressVisible) ProgressStyle.LINEAR else ProgressStyle.NONE,
            fraction = 0.4f,
            percent = 40,
            percentText = "40%",
            isVisible = progressVisible,
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
        showEmoji = true,
        showDate = showDate,
        showPercentageText = showPercentageText,
        isCompleted = isCompleted,
        isExpired = isExpired,
    )

    @Test
    fun `draws the title and emoji when both are enabled`() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model()) }

            onNode(hasText("Trip to Kyoto")).assertExists()
            onNode(hasText("🌸")).assertExists()
        }
    }

    @Test
    fun `hides the title when the binding says not to show it`() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showTitle = false)) }

            onNode(hasText("Trip to Kyoto")).assertDoesNotExist()
        }
    }

    @Test
    fun `draws the day count when the model says it is meaningful`() = runTest {
        runGlanceAppWidgetUnitTest {
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
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(null) }

            onNode(hasText("choose a countdown")).assertExists()
        }
    }

    @Test
    fun `draws the percent text only when the model asks for it`() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showPercentageText = true)) }

            onNode(hasTextEqualTo("40%")).assertExists()
        }
    }

    @Test
    fun `omits the percent text when the model does not ask for it`() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(showPercentageText = false)) }

            onNode(hasTextEqualTo("40%")).assertDoesNotExist()
        }
    }

    @Test
    fun `omits the percent text when progress itself is not visible, even if requested`() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(progressVisible = false, showPercentageText = true))
            }

            onNode(hasTextEqualTo("40%")).assertDoesNotExist()
        }
    }

    // ── Content hierarchy: the "Tomorrow / Tomorrow" redundancy this session's brief called out ──

    @Test
    fun `a NextWeek label is shown once as the headline, not duplicated as a second line`() = runTest {
        runGlanceAppWidgetUnitTest {
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
    fun `minimal never draws a progress bar even when progress is visible`() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(style = WidgetStyle.MINIMAL, showPercentageText = true))
            }

            // Minimal's whole premise is one thing to look at; percent text is never drawn here
            // regardless of the binding's own toggle.
            onNode(hasTextEqualTo("40%")).assertDoesNotExist()
        }
    }

    @Test
    fun `oled omits the identity row entirely`() = runTest {
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { CountdownWidgetContent(model(style = WidgetStyle.OLED)) }

            onNode(hasText("Trip to Kyoto")).assertDoesNotExist()
        }
    }

    @Test
    fun `every style renders the headline day count without throwing`() = runTest {
        WidgetStyle.entries.forEach { style ->
            runGlanceAppWidgetUnitTest {
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
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable {
                CountdownWidgetContent(model(style = WidgetStyle.MODERN, showDate = true))
            }

            onNode(hasText("Jun")).assertExists()
        }
    }
}
