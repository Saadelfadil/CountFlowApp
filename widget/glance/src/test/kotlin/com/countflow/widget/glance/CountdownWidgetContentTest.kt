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
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
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
            style = WidgetStyle.MINIMAL,
            accentColorArgb = 0xFF00695C.toInt(),
            backgroundColorArgb = backgroundColorArgb,
            cornerRadiusDp = 16,
            isHighContrast = isHighContrast,
        ),
        target = EventTarget.allDay(LocalDate.of(2026, 6, 27), zone),
        targetZone = zone,
        showTitle = showTitle,
        showEmoji = true,
        showDate = false,
        showPercentageText = showPercentageText,
        isCompleted = false,
        isExpired = false,
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

            // hasTextEqualTo, not hasText: hasText always matches as a substring, and the
            // label text reads "In 12 days" — a substring match on "12" would pass even if the
            // standalone headline number were never drawn at all.
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

            // Substring match here, deliberately: this asserts no node's text *contains* "12"
            // at all — including inside a label like "In 12 days" — not just that no node
            // equals "12" exactly.
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
}
