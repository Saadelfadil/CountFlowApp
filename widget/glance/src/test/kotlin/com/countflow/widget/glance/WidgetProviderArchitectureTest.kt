package com.countflow.widget.glance

import com.countflow.widget.glance.refresh.GlanceWidgetRefreshScheduler
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the three-widget-picker-entry architecture (D-079): "CountFlow Compact" (2×1), "CountFlow
 * Square" (2×2), and "CountFlow Wide" (4×2) share one `CountdownGlanceWidget` renderer and one
 * `WidgetConfigurationActivity`, differing only in their own `res/xml/countdown_widget_info_*.xml`
 * metadata and picker preview — never in rendering logic.
 *
 * Reads the real manifest/resource XML text directly off disk, the same pattern
 * `app/src/test/kotlin/.../AdMobConfigTest.kt`'s own RELEASE-variant assertion already
 * established for source-of-truth files a plain unit test cannot otherwise observe (Robolectric's
 * `AppWidgetManager`/`AppWidgetProviderInfo` shadow support does not cover picker-entry metadata
 * like `targetCellWidth`, so parsing the real XML is more honest than simulating it). `File(...)`
 * resolves relative to this module's own project directory — Gradle's `Test` task default working
 * directory, unchanged by this module's build script, matching `AdMobConfigTest`'s own assumption.
 *
 * `@RunWith(RobolectricTestRunner::class)`, matching `WidgetConfigurationViewModelTest`'s own
 * choice for the same reason: the receiver classes under test extend `BroadcastReceiver`/
 * `GlanceAppWidget`, real Android/Glance framework types, and Robolectric is what makes
 * instantiating them safe in a JVM unit test rather than relying on their constructors happening
 * not to touch anything the plain Android SDK stub jar would reject.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetProviderArchitectureTest {

    // ── Provider metadata / default footprint (items 1-3) ──

    @Test
    fun `the Compact provider defaults to a 2x1 footprint`() {
        val info = parseWidgetInfo("countdown_widget_info_compact.xml")

        assertThat(info.targetCellWidth).isEqualTo(2)
        assertThat(info.targetCellHeight).isEqualTo(1)
    }

    @Test
    fun `the Square provider defaults to a 2x2 footprint`() {
        val info = parseWidgetInfo("countdown_widget_info_square.xml")

        assertThat(info.targetCellWidth).isEqualTo(2)
        assertThat(info.targetCellHeight).isEqualTo(2)
    }

    @Test
    fun `the Wide provider defaults to a 4x2 footprint`() {
        val info = parseWidgetInfo("countdown_widget_info_wide.xml")

        assertThat(info.targetCellWidth).isEqualTo(4)
        assertThat(info.targetCellHeight).isEqualTo(2)
    }

    // ── Shared configuration Activity, and identical resizability across all three (item 4) ──

    @Test
    fun `all three providers launch the same configuration Activity`() {
        val configureActivity = "com.countflow.widget.glance.configuration.WidgetConfigurationActivity"

        listOf("countdown_widget_info_compact.xml", "countdown_widget_info_square.xml", "countdown_widget_info_wide.xml")
            .map { parseWidgetInfo(it) }
            .forEach { assertThat(it.configure).isEqualTo(configureActivity) }
    }

    @Test
    fun `all three providers allow resizing across the full 2x1 to 4x2 range, not just their own default`() {
        // The picker choice only ever sets the DEFAULT placement footprint (targetCellWidth/
        // Height, asserted above) — min/max/resizeMode must stay identical across all three so a
        // widget placed Compact can still be dragged up to Wide, and one placed Wide can still be
        // dragged down to Compact, exactly as a Square one always could.
        val infos = listOf("countdown_widget_info_compact.xml", "countdown_widget_info_square.xml", "countdown_widget_info_wide.xml")
            .map { parseWidgetInfo(it) }

        infos.forEach { info ->
            assertThat(info.minWidth).isEqualTo("110dp")
            assertThat(info.minHeight).isEqualTo("40dp")
            assertThat(info.maxResizeWidth).isEqualTo("250dp")
            assertThat(info.maxResizeHeight).isEqualTo("110dp")
            assertThat(info.resizeMode).isEqualTo("horizontal|vertical")
        }
    }

    // ── Shared renderer (item 4, Kotlin side) ──

    @Test
    fun `all three receivers extend the same shared base and render with the same GlanceAppWidget class`() {
        val receivers = listOf(
            CountdownGlanceWidgetReceiverCompact(),
            CountdownGlanceWidgetReceiver(),
            CountdownGlanceWidgetReceiverWide(),
        )

        receivers.forEach { receiver ->
            assertThat(receiver).isInstanceOf(BaseCountdownGlanceWidgetReceiver::class.java)
            assertThat(receiver.glanceAppWidget).isInstanceOf(CountdownGlanceWidget::class.java)
        }
    }

    // ── The pruneOrphanedBindings fix this architecture required (D-079) ──

    @Test
    fun `the refresh scheduler's provider list covers exactly the three declared receivers`() {
        assertThat(GlanceWidgetRefreshScheduler.COUNTDOWN_WIDGET_PROVIDER_CLASSES).containsExactly(
            CountdownGlanceWidgetReceiverCompact::class.java,
            CountdownGlanceWidgetReceiver::class.java,
            CountdownGlanceWidgetReceiverWide::class.java,
        )
    }

    @Test
    fun `AndroidManifest declares exactly three CountdownGlanceWidgetReceiver components`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertThat(manifest).contains("""android:name=".CountdownGlanceWidgetReceiver"""")
        assertThat(manifest).contains("""android:name=".CountdownGlanceWidgetReceiverCompact"""")
        assertThat(manifest).contains("""android:name=".CountdownGlanceWidgetReceiverWide"""")
    }

    private data class WidgetInfo(
        val targetCellWidth: Int,
        val targetCellHeight: Int,
        val configure: String,
        val minWidth: String,
        val minHeight: String,
        val maxResizeWidth: String,
        val maxResizeHeight: String,
        val resizeMode: String,
    )

    private fun parseWidgetInfo(fileName: String): WidgetInfo {
        val file = File("src/main/res/xml/$fileName")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(file)
        val root = document.documentElement
        fun attr(name: String) = root.getAttributeNS("http://schemas.android.com/apk/res/android", name)

        return WidgetInfo(
            targetCellWidth = attr("targetCellWidth").toInt(),
            targetCellHeight = attr("targetCellHeight").toInt(),
            configure = attr("configure"),
            minWidth = attr("minWidth"),
            minHeight = attr("minHeight"),
            maxResizeWidth = attr("maxResizeWidth"),
            maxResizeHeight = attr("maxResizeHeight"),
            resizeMode = attr("resizeMode"),
        )
    }
}
